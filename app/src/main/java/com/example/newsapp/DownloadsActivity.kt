package com.example.newsapp

import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.example.newsapp.adapter.NewsAdapter
import com.example.newsapp.data.api.Article
import com.example.newsapp.data.api.Source
import com.example.newsapp.databinding.ActivityDownloadsBinding
import com.example.newsapp.local.AppDatabase
import com.example.newsapp.utils.applyScreenInsets
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class DownloadsActivity : AuthenticatedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accountId = activeSession?.userId ?: return
        val binding = ActivityDownloadsBinding.inflate(layoutInflater)
        binding.root.applyScreenInsets()
        setContentView(binding.root)
        binding.downloadsToolbar.setNavigationOnClickListener { finish() }

        val dao = AppDatabase.getDatabase(this).downloadedNewsDao()
        val requestedUrl = intent.getStringExtra(EXTRA_URL)
        if (requestedUrl != null) {
            binding.downloadsToolbar.setTitle(R.string.download_reader_title)
            binding.downloadList.isVisible = false
            lifecycleScope.launch {
                val saved = dao.find(accountId, requestedUrl)
                if (saved == null) {
                    binding.downloadStatus.setText(R.string.download_missing)
                    binding.downloadStatus.isVisible = true
                    return@launch
                }
                binding.readerTitle.text = saved.title
                binding.readerSource.text = listOfNotNull(saved.sourceName, saved.publishedAt?.take(10))
                    .joinToString(" · ")
                binding.readerSource.isVisible = binding.readerSource.text.isNotBlank()
                saved.coverImage?.let { bytes ->
                    binding.readerCover.isVisible = true
                    Glide.with(this@DownloadsActivity).load(bytes).into(binding.readerCover)
                }
                binding.readerBody.text = saved.body
                binding.downloadReader.isVisible = true
            }
            return
        }

        val newsAdapter = NewsAdapter(
            articles = emptyList(),
            onItemClick = { article ->
                startActivity(Intent(this, DownloadsActivity::class.java).putExtra(EXTRA_URL, article.url))
            },
            onDeleteDownload = { article ->
                article.url?.let { url ->
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.download_remove)
                        .setMessage(article.title)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.download_delete) { _, _ ->
                            lifecycleScope.launch { dao.delete(accountId, url) }
                        }
                        .show()
                }
            }
        )
        binding.downloadList.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.news_column_count))
        binding.downloadList.adapter = newsAdapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dao.observeDownloads(accountId).collect { downloads ->
                    binding.downloadStatus.isVisible = downloads.isEmpty()
                    newsAdapter.actualizarNews(downloads.map {
                        Article(
                            Source(null, it.sourceName), null, it.title, it.excerpt,
                            it.url, null, it.publishedAt, null
                        )
                    })
                }
            }
        }
    }

    private companion object {
        const val EXTRA_URL = "downloaded_article_url"
    }
}
