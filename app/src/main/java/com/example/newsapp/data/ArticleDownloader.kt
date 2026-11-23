package com.example.newsapp.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.text.HtmlCompat
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object ArticleDownloader {
    private const val MAX_PAGE_BYTES = 4L * 1024 * 1024
    private const val MAX_IMAGE_SIDE = 1280
    private const val MAX_STORED_IMAGE_BYTES = 256 * 1024
    private val client = OkHttpClient.Builder()
        .followSslRedirects(false)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonLd = Regex("""<script\b[^>]*\btype\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script\s*>""", options)
    private val article = Regex("""<article\b[^>]*>(.*?)</article\s*>""", options)
    private val paragraph = Regex("""<p\b[^>]*>(.*?)</p\s*>""", options)
    private val nonContent = Regex("""<(script|style|nav|aside|footer)\b[^>]*>.*?</\1\s*>""", options)

    suspend fun download(url: String): String? {
        val address = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
        val request = Request.Builder().url(address).header("Accept", "text/html").build()
        val html = readResponse(request) { response ->
            val body = response.body ?: return@readResponse null
            val type = body.contentType()
            if (type?.subtype !in setOf("html", "xhtml+xml")) return@readResponse null
            val source = body.source()
            if (source.request(MAX_PAGE_BYTES + 1)) return@readResponse null
            source.readString(type?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
        } ?: return null
        return extractBody(html)?.takeIf { it.length in 120..200_000 }
    }

    suspend fun downloadCover(url: String?): ByteArray? {
        val address = url?.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
        val request = Request.Builder().url(address).header("Accept", "image/*").build()
        val bytes = readResponse(request) { response ->
            val body = response.body ?: return@readResponse null
            if (body.contentType()?.type != "image") return@readResponse null
            val source = body.source()
            if (source.request(MAX_PAGE_BYTES + 1)) return@readResponse null
            source.readByteArray()
        } ?: return null

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val longestSide = maxOf(options.outWidth, options.outHeight)
        if (longestSide <= 0) return null
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (longestSide / options.inSampleSize > MAX_IMAGE_SIDE) options.inSampleSize *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        try {
            val output = ByteArrayOutputStream()
            for (quality in listOf(82, 65, 45)) {
                output.reset()
                if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output) &&
                    output.size() <= MAX_STORED_IMAGE_BYTES
                ) return output.toByteArray()
            }
            return null
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun <T> readResponse(request: Request, read: (Response) -> T?): T? =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        continuation.resume(response.use { if (it.isSuccessful) read(it) else null })
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                }
            })
        }

    private fun extractBody(html: String): String? {
        val structured = jsonLd.findAll(html).firstNotNullOfOrNull { match ->
            val data = runCatching { JsonParser.parseString(match.groupValues[1]) }.getOrNull()
            data?.let(::articleBody)?.let { plainText(it.replace("\n", "<br>")) }
                ?.takeIf { it.length >= 120 }
        }
        if (structured != null) return structured

        return article.findAll(nonContent.replace(html, ""))
            .map { match ->
                paragraph.findAll(match.groupValues[1])
                    .map { plainText(it.groupValues[1]) }
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
            }
            .firstOrNull { it.length >= 120 }
    }

    private fun articleBody(value: JsonElement): String? = when {
        value.isJsonArray -> value.asJsonArray.firstNotNullOfOrNull(::articleBody)
        value.isJsonObject -> {
            val item = value.asJsonObject
            item.get("articleBody")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                ?: item.get("@graph")?.let(::articleBody)
        }
        else -> null
    }

    private fun plainText(html: String) =
        HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()

    private val options get() = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
}
