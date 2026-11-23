package com.example.newsapp.data

import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.newsapp.AuthenticatedActivity
import com.example.newsapp.R
import com.example.newsapp.data.api.Article
import com.example.newsapp.local.DownloadedNews
import com.example.newsapp.local.DownloadedNewsDao
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
                toast(R.string.download_started)
                val body = withContext(Dispatchers.Default) { ArticleDownloader.download(url) }
                if (body == null) {
                    toast(R.string.download_body_unavailable)
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
                toast(R.string.download_saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                toast(R.string.download_failed)
            } finally {
                downloading.remove(url)
            }
        }
    }

    private fun toast(message: Int) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}
