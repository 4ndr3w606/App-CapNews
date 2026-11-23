package com.example.newsapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.GridLayoutManager
import com.example.newsapp.adapter.NewsAdapter
import com.example.newsapp.data.NewsRepository
import com.example.newsapp.data.api.InstanciaRetrofit
import com.example.newsapp.data.toFavoritoEntity
import com.example.newsapp.databinding.ActivityNewsBinding
import com.example.newsapp.local.AppDatabase
import com.example.newsapp.utils.LectorWeb
import com.example.newsapp.utils.applyScreenInsets
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class NewsActivity : AuthenticatedActivity() {

    private lateinit var binding: ActivityNewsBinding
    private lateinit var newsAdapter: NewsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cuentaId = activeSession?.userId ?: return
        binding = ActivityNewsBinding.inflate(layoutInflater)
        binding.root.applyScreenInsets()
        setContentView(binding.root)

        val db = AppDatabase.getDatabase(this)
        val favoritoDao = db.favoritoDao()

        newsAdapter = NewsAdapter(
            articles = emptyList(),
            onItemClick = { article ->
                LectorWeb.abrirNoticia(this, article.url)
            },
            onFavoritoClick = alternar@{ article ->
                val url = article.url?.takeIf { it.isNotBlank() } ?: return@alternar
                lifecycleScope.launch {
                    val guardado = favoritoDao.alternarFavorito(article.toFavoritoEntity(cuentaId, url))
                    val mensaje = if (guardado) "Guardada en favoritos" else "Eliminada de favoritos"
                    Toast.makeText(this@NewsActivity, mensaje, Toast.LENGTH_SHORT).show()
                }
            }
        )
        val apiCategory = intent.getStringExtra("EXTRA_SELECTED_CATEGORY") ?: "world"
        val displayCategory = intent.getStringExtra("EXTRA_DISPLAY_CATEGORY") ?: "Mundo"
        val factory = viewModelFactory {
            initializer {
                NewsViewModel(
                    NewsRepository(InstanciaRetrofit.api, db.articleDao()),
                    apiCategory,
                    createSavedStateHandle()
                )
            }
        }
        val viewModel = ViewModelProvider(this, factory)[NewsViewModel::class.java]

        binding.tvHeaderCategory.text = "Noticias de $displayCategory"
        supportActionBar?.title = "Noticias: $displayCategory"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.rvNews.apply {
            layoutManager = GridLayoutManager(this@NewsActivity, resources.getInteger(R.integer.news_column_count))
            adapter = newsAdapter
        }

        configurarBuscador(viewModel)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.combine(favoritoDao.observarUrlsFavoritas(cuentaId)) { state, urls ->
                    state to urls.toSet()
                }.collect { (state, urls) ->
                    newsAdapter.actualizarFavoritos(urls)
                    render(state)
                }
            }
        }
    }

    private fun configurarBuscador(viewModel: NewsViewModel) {
        binding.svBuscador.isSaveFromParentEnabled = false
        binding.svBuscador.setQuery(viewModel.query, false)
        binding.svBuscador.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.submitQuery(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.changeQuery(newText.orEmpty())
                return true
            }
        })
    }

    private fun render(state: NewsListState) {
        newsAdapter.actualizarNews(state.articles)
        binding.newsProgress.isVisible = state.loading
        val message = when (state.notice) {
            NewsNotice.SHORT_QUERY -> R.string.news_short_query
            NewsNotice.EMPTY_HEADLINES -> R.string.news_empty_category
            NewsNotice.CACHED_HEADLINES -> R.string.cached_news
            NewsNotice.NO_CACHED_HEADLINES -> R.string.no_cached_news
            NewsNotice.EMPTY_SEARCH -> R.string.news_no_results
            NewsNotice.SEARCH_FAILED -> R.string.news_search_failed
            null -> null
        }
        binding.tvNewsStatus.isVisible = message != null
        if (message != null) binding.tvNewsStatus.setText(message)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
