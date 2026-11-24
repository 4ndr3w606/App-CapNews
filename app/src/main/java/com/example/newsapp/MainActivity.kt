package com.example.newsapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import com.example.newsapp.data.LocalProfile
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.GridLayoutManager
import com.example.newsapp.adapter.NewsAdapter
import com.example.newsapp.data.NewsRepository
import com.example.newsapp.data.OfflineNewsActions
import com.example.newsapp.data.PreferencesManager
import com.example.newsapp.data.api.InstanciaRetrofit
import com.example.newsapp.data.headlineCategories
import com.example.newsapp.data.toFavoritoEntity
import com.example.newsapp.databinding.ActivityMainBinding
import com.example.newsapp.local.AppDatabase
import com.example.newsapp.notifications.HeadlineNotification
import com.example.newsapp.notifications.HeadlinesSchedule
import com.example.newsapp.notifications.HeadlinesSettingsDialog
import com.example.newsapp.utils.LectorWeb
import com.example.newsapp.utils.applyScreenInsets
import com.example.newsapp.utils.mostrarAviso
import com.example.newsapp.weather.HomeWeather
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AuthenticatedActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var newsAdapter: NewsAdapter
    private lateinit var homeWeather: HomeWeather
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (::homeWeather.isInitialized) homeWeather.onPermissionResult(granted)
    }

    private val notificationsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!::binding.isInitialized) return@registerForActivityResult
        if (HeadlineNotification.canPost(this)) {
            binding.root.mostrarAviso(R.string.headlines_saved)
        } else {
            showNotificationPermissionSettings()
        }
    }

    private val categories = headlineCategories.toList().toMutableList().apply {
        add(1, NEARBY_CATEGORY to R.string.category_nearby)
    }

    override fun onAccountReady(profile: LocalProfile) {
        val accountId = profile.userId
        binding = ActivityMainBinding.inflate(layoutInflater)
        binding.root.applyScreenInsets()
        setContentView(binding.root)

        binding.tvUserName.text = profile.name
        binding.tvUserEmail.text = profile.email
        binding.btnVerFavoritos.setOnClickListener {
            startActivity(Intent(this, FavoritosActivity::class.java))
        }
        binding.btnDownloads.setOnClickListener {
            startActivity(Intent(this, DownloadsActivity::class.java))
        }
        binding.btnLogout.setOnClickListener { switchAccount() }
        configureHeadlinesSettings(accountId)

        val viewModel = configureFeed(accountId)
        homeWeather = HomeWeather(this, accountId, binding.homeWeather, viewModel::updateLocalPlace) {
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

    private fun configureHeadlinesSettings(accountId: Long) {
        HeadlinesSchedule.ensureScheduled(this, accountId)
        supportFragmentManager.setFragmentResultListener(HeadlinesSettingsDialog.RESULT_KEY, this) { _, result ->
            when {
                !result.getBoolean(HeadlinesSettingsDialog.RESULT_ENABLED) ->
                    binding.root.mostrarAviso(R.string.headlines_turned_off)
                HeadlineNotification.canPost(this) ->
                    binding.root.mostrarAviso(R.string.headlines_saved)
                Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED ->
                    notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> showNotificationPermissionSettings()
            }
        }
        binding.btnHeadlinesSettings.setOnClickListener {
            if (supportFragmentManager.findFragmentByTag(HeadlinesSettingsDialog.TAG) == null) {
                HeadlinesSettingsDialog.newInstance(accountId).showNow(supportFragmentManager, HeadlinesSettingsDialog.TAG)
            }
        }
    }

    private fun showNotificationPermissionSettings() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.headlines_permission_title)
            .setMessage(R.string.headlines_permission_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.headlines_open_settings) { _, _ ->
                val intent = if (Build.VERSION.SDK_INT >= 26) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                }
                startActivity(intent)
            }
            .show()
    }

    private fun configureFeed(accountId: Long): NewsViewModel {
        val database = AppDatabase.getDatabase(this)
        val favorites = database.favoritoDao()
        val downloads = database.downloadedNewsDao()
        val offlineNews = OfflineNewsActions(this, accountId, downloads)
        val preferences = PreferencesManager(this, accountId)
        val initialCategory = preferences.getCategory().takeIf { saved ->
            categories.any { it.first == saved }
        } ?: categories.first().first
        val factory = viewModelFactory {
            initializer {
                NewsViewModel(
                    NewsRepository(
                        InstanciaRetrofit.create(),
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

}
