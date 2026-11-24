package com.example.newsapp.data

import android.view.View
import androidx.lifecycle.lifecycleScope
import com.example.newsapp.AuthenticatedActivity
import com.example.newsapp.R
import com.example.newsapp.data.api.Article
import com.example.newsapp.local.DownloadedNews
import com.example.newsapp.local.DownloadedNewsDao
import com.example.newsapp.utils.mostrarAviso
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class OfflineNewsActions(
    private val activity: AuthenticatedActivity,
    private val accountId: Long,
    private val dao: DownloadedNewsDao
) {
    private val downloading = mutableSetOf<String>()

    fun download(article: Article) {
        val url = article.url ?: return
        val title = article.title ?: return
        if (!downloading.add(url)) return

        activity.lifecycleScope.launch {
            try {
                if (dao.find(accountId, url) != null) return@launch
                showNotice(R.string.download_started)
                val body = withContext(Dispatchers.Default) { ArticleDownloader.download(url) }
                if (body == null) {
                    showNotice(R.string.download_body_unavailable, error = true)
                    return@launch
                }
                val cover = try {
                    withContext(Dispatchers.Default) { ArticleDownloader.downloadCover(article.urlToImage) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                dao.insert(DownloadedNews(
                    accountId, url, title, article.source?.name, article.publishedAt,
                    body, System.currentTimeMillis(), cover
                ))
                showNotice(R.string.download_saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showNotice(R.string.download_failed, error = true)
            } finally {
                downloading.remove(url)
            }
        }
    }

    private fun showNotice(message: Int, error: Boolean = false) {
        activity.findViewById<View>(android.R.id.content).mostrarAviso(message, error = error)
    }
}
