package com.example.newsapp

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.example.newsapp.adapter.NewsAdapter
import com.example.newsapp.data.OfflineNewsActions
import com.example.newsapp.data.toDomain
import com.example.newsapp.databinding.ActivityFavoritosBinding
import com.example.newsapp.local.AppDatabase
import com.example.newsapp.utils.LectorWeb
import com.example.newsapp.utils.applyScreenInsets
import com.example.newsapp.utils.mostrarAviso
import kotlinx.coroutines.launch

class FavoritosActivity : AuthenticatedActivity() {

    private lateinit var binding: ActivityFavoritosBinding
    private lateinit var newsAdapter: NewsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cuentaId = activeSession?.userId ?: return
        binding = ActivityFavoritosBinding.inflate(layoutInflater)
        binding.root.applyScreenInsets()
        setContentView(binding.root)

        supportActionBar?.title = "Noticias Favoritas"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val database = AppDatabase.getDatabase(this)
        val favoritoDao = database.favoritoDao()
        val downloads = database.downloadedNewsDao()
        val offlineNews = OfflineNewsActions(this, cuentaId, downloads)

        newsAdapter = NewsAdapter(
            articles = emptyList(),
            onItemClick = { article ->
                LectorWeb.abrirNoticia(this, article.url)
            },
            onFavoritoClick = eliminar@{ article ->
                val url = article.url ?: return@eliminar
                lifecycleScope.launch {
                    if (favoritoDao.eliminarFavorito(cuentaId, url) > 0) {
                        binding.root.mostrarAviso(R.string.home_favorite_removed)
                    }
                }
            },
            onDownloadClick = offlineNews::download
        )

        binding.rvFavoritos.apply {
            layoutManager = GridLayoutManager(this@FavoritosActivity, resources.getInteger(R.integer.news_column_count))
            adapter = newsAdapter
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    downloads.observeUrls(cuentaId).collect { newsAdapter.actualizarDescargas(it.toSet()) }
                }
                favoritoDao.observarFavoritos(cuentaId).collect { favorites ->
                    newsAdapter.actualizarFavoritos(favorites.map { it.url }.toSet())
                    newsAdapter.actualizarNews(favorites.map { it.toDomain() })
                    binding.tvFavoritesEmpty.isVisible = favorites.isEmpty()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
