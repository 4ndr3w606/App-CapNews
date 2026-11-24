package com.example.newsapp

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.newsapp.data.OfflineNewsActions
import com.example.newsapp.data.PreferencesManager
import com.example.newsapp.data.api.AuthApiClient
import com.example.newsapp.data.api.InstanciaRetrofit
import com.example.newsapp.data.toFavoritoEntity
import com.example.newsapp.databinding.ActivityMainBinding
import com.example.newsapp.local.AppDatabase
import com.example.newsapp.utils.LectorWeb
import com.example.newsapp.utils.applyScreenInsets
import com.example.newsapp.utils.mostrarAviso
import com.example.newsapp.weather.HomeWeather
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : AuthenticatedActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var newsAdapter: NewsAdapter
    private lateinit var homeWeather: HomeWeather
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (::homeWeather.isInitialized) homeWeather.onPermissionResult(granted)
    }

    private val categories = listOf(
        "world" to R.string.category_world,
        NEARBY_CATEGORY to R.string.category_nearby,
        "technology" to R.string.category_technology,
        "sports" to R.string.category_sports,
        "business" to R.string.category_business,
        "entertainment" to R.string.category_entertainment,
        "health" to R.string.category_health,
        "science" to R.string.category_science
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accountId = activeSession?.userId ?: return
        binding = ActivityMainBinding.inflate(layoutInflater)
        binding.root.applyScreenInsets()
        setContentView(binding.root)

        onProfileUpdated()
        binding.btnVerFavoritos.setOnClickListener {
            startActivity(Intent(this, FavoritosActivity::class.java))
        }
        binding.btnDownloads.setOnClickListener {
            startActivity(Intent(this, DownloadsActivity::class.java))
        }
        binding.btnLogout.setOnClickListener { logout() }

        val viewModel = configureFeed(accountId)
        homeWeather = HomeWeather(this, binding.homeWeather, viewModel::updateLocalPlace) {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        homeWeather.start()
        binding.btnLocalNewsAction.setOnClickListener {
            if (viewModel.state.value.notice == NewsNotice.LOCAL_LOCATION_REQUIRED) {
                homeWeather.showLocationOptions()
            } else {
                viewModel.showAlternativeLocalArea()
                binding.rvNews.scrollToPosition(0)
            }
        }
    }

    override fun onProfileUpdated() {
        binding.tvUserName.text = sessionStore.getName() ?: getString(R.string.home_profile_default_name)
        binding.tvUserEmail.text = sessionStore.getEmail()
    }

    private fun configureFeed(accountId: Long): NewsViewModel {
        val database = AppDatabase.getDatabase(this)
        val favorites = database.favoritoDao()
        val downloads = database.downloadedNewsDao()
        val offlineNews = OfflineNewsActions(this, accountId, downloads)
        val preferences = PreferencesManager(this)
        val initialCategory = preferences.getCategory().takeIf { saved ->
            categories.any { it.first == saved }
        } ?: categories.first().first
        val factory = viewModelFactory {
            initializer {
                NewsViewModel(
                    NewsRepository(
                        InstanciaRetrofit.create { sessionStore.getSession()?.token },
                        database.articleDao(),
                        database.localNewsDao()
                    ),
                    initialCategory,
                    createSavedStateHandle()
                )
            }
        }
        val viewModel = ViewModelProvider(this, factory)[NewsViewModel::class.java]

        newsAdapter = NewsAdapter(
            articles = emptyList(),
            onItemClick = { article -> LectorWeb.abrirNoticia(this, article.url) },
            onFavoritoClick = toggle@{ article ->
                val url = article.url?.takeIf { it.isNotBlank() } ?: return@toggle
                lifecycleScope.launch {
                    val saved = favorites.alternarFavorito(article.toFavoritoEntity(accountId, url))
                    val message = if (saved) R.string.home_favorite_added else R.string.home_favorite_removed
                    binding.root.mostrarAviso(message)
                }
            },
            onDownloadClick = offlineNews::download
        )
        binding.rvNews.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.news_column_count))
        binding.rvNews.adapter = newsAdapter

        configureFilters(viewModel, preferences)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    downloads.observeUrls(accountId).collect { newsAdapter.actualizarDescargas(it.toSet()) }
                }
                viewModel.state.combine(favorites.observarUrlsFavoritas(accountId)) { state, urls ->
                    state to urls.toSet()
                }.collect { (state, urls) ->
                    newsAdapter.actualizarFavoritos(urls)
                    renderNews(state)
                }
            }
        }
        return viewModel
    }

    private fun configureFilters(viewModel: NewsViewModel, preferences: PreferencesManager) {
        binding.svBuscador.isVisible = viewModel.category != NEARBY_CATEGORY
        binding.svBuscador.isSaveFromParentEnabled = false
        if (viewModel.query.isNotEmpty()) {
            binding.svBuscador.setQuery(viewModel.query, false)
            binding.svBuscador.clearFocus()
        }
        binding.svBuscador.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.submitQuery(query.orEmpty())
                binding.svBuscador.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.changeQuery(newText.orEmpty())
                return true
            }
        })
        val adapter = ArrayAdapter(
            this, R.layout.item_category_selected, categories.map { getString(it.second) }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategories.isSaveFromParentEnabled = false
        binding.spinnerCategories.adapter = adapter
        binding.spinnerCategories.setSelection(categories.indexOfFirst { it.first == viewModel.category }.coerceAtLeast(0))
        binding.spinnerCategories.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val category = categories[position].first
                if (category == viewModel.category) return
                preferences.saveCategory(category)
                viewModel.changeCategory(category)
                binding.svBuscador.setQuery("", false)
                binding.svBuscador.clearFocus()
                binding.svBuscador.isVisible = category != NEARBY_CATEGORY
                binding.rvNews.scrollToPosition(0)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun renderNews(state: NewsListState) {
        newsAdapter.actualizarNews(state.articles)
        binding.newsProgress.isVisible = state.loading
        val message = when (state.notice) {
            NewsNotice.SHORT_QUERY -> R.string.news_short_query
            NewsNotice.EMPTY_HEADLINES -> R.string.news_empty_category
            NewsNotice.CACHED_HEADLINES -> R.string.cached_news
            NewsNotice.NO_CACHED_HEADLINES -> R.string.no_cached_news
            NewsNotice.EMPTY_SEARCH -> R.string.news_no_results
            NewsNotice.SEARCH_FAILED -> R.string.news_search_failed
            NewsNotice.LOCAL_LOCATION_REQUIRED -> R.string.news_local_location_required
            NewsNotice.LOCAL_NO_RESULTS -> R.string.news_local_no_results
            NewsNotice.LOCAL_CACHED -> R.string.news_local_cached
            NewsNotice.LOCAL_UNAVAILABLE -> R.string.news_local_unavailable
            null -> null
        }
        binding.tvNewsStatus.isVisible = message != null
        if (message != null) binding.tvNewsStatus.setText(message)
        val area = state.localArea
        binding.tvLocalNewsTitle.isVisible = area != null
        if (area != null) {
            val title = if (state.regional) R.string.news_regional_title else R.string.news_local_title
            binding.tvLocalNewsTitle.text = getString(title, area)
        }
        val action = when {
            state.notice == NewsNotice.LOCAL_LOCATION_REQUIRED -> getString(R.string.news_local_choose_location)
            state.alternativeArea != null -> getString(R.string.news_local_view_area, state.alternativeArea)
            else -> null
        }
        binding.btnLocalNewsAction.isVisible = action != null
        binding.btnLocalNewsAction.text = action
    }

    private fun logout() {
        val token = activeSession?.token ?: return
        binding.btnLogout.isEnabled = false
        sessionStore.clear()
        lifecycleScope.launch {
            try {
                withTimeoutOrNull(2_000) {
                    AuthApiClient.service.logout("Bearer $token")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // La sesión ya se cerró en el dispositivo.
            }
            returnToLogin()
        }
    }
}
