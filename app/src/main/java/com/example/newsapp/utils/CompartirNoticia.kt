package com.example.newsapp.utils

import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.View
import com.example.newsapp.R
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object CompartirNoticia {
    fun enlaceValido(url: String?): Boolean = url?.toHttpUrlOrNull() != null

    fun abrirMenu(view: View, title: String?, url: String?) {
        val link = url?.trim()?.takeIf(::enlaceValido) ?: return
        val headline = title?.trim()?.takeIf { it.isNotEmpty() }
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, listOfNotNull(headline, link).joinToString("\n"))
            if (headline != null) putExtra(Intent.EXTRA_SUBJECT, headline)
        }

        try {
            view.context.startActivity(Intent.createChooser(share, null))
        } catch (_: ActivityNotFoundException) {
            view.mostrarAviso(R.string.news_share_unavailable, error = true)
        }
    }
}
