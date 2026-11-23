package com.example.newsapp

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newsapp.data.NewsRepository
import com.example.newsapp.data.NewsPlace
import com.example.newsapp.data.api.Article
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

internal const val NEARBY_CATEGORY = "nearby"

internal enum class NewsNotice {
    SHORT_QUERY, EMPTY_HEADLINES, CACHED_HEADLINES, NO_CACHED_HEADLINES, EMPTY_SEARCH, SEARCH_FAILED,
    LOCAL_LOCATION_REQUIRED, LOCAL_NO_RESULTS, LOCAL_CACHED, LOCAL_UNAVAILABLE
}

internal data class NewsListState(
    val articles: List<Article> = emptyList(),
    val loading: Boolean = true,
    val notice: NewsNotice? = null,
    val localArea: String? = null,
    val regional: Boolean = false,
    val alternativeArea: String? = null
)

internal class NewsViewModel(
    private val repository: NewsRepository,
    category: String,
    private val savedState: SavedStateHandle,
    requestScope: CoroutineScope? = null
) : ViewModel() {
    private class Request(
        val category: String,
        val query: String,
        val submitted: Boolean,
        val regional: Boolean = false
    )

    private val requests = MutableStateFlow(
        restoredRequest(category)
    )
    private var localPlace: NewsPlace? = null
    private var placeResolved = false
    private var startedRequest: Request? = null
    private val mutableState = MutableStateFlow(NewsListState())
    val state = mutableState.asStateFlow()
    val query: String get() = requests.value.query
    val category: String get() = requests.value.category

    init {
        (requestScope ?: viewModelScope).launch {
            // This collector cancels and joins the previous request before running the next one.
            requests.collectLatest { request ->
                when {
                    request.category == NEARBY_CATEGORY -> loadLocalNews(request)
                    request.query.isEmpty() -> loadHeadlines(request)
                    else -> loadSearch(request)
                }
            }
        }
    }

    fun changeQuery(input: String) {
        if (category == NEARBY_CATEGORY) return
        val query = input.trim()
        if (query != this.query) enqueue(Request(category, query, submitted = false))
    }

    fun changeCategory(category: String) {
        if (category != this.category) enqueue(Request(category, "", submitted = false))
    }

    fun submitQuery(input: String) {
        if (category == NEARBY_CATEGORY) return
        val query = input.trim()
        if (query.isEmpty()) return
        val current = requests.value
        val alreadyRequested = current.query == query && (current.submitted || current === startedRequest)
        if (alreadyRequested && state.value.notice != NewsNotice.SEARCH_FAILED) return
        enqueue(Request(current.category, query, submitted = true))
    }

    fun updateLocalPlace(place: NewsPlace?) {
        if (placeResolved && localPlace == place) return
        val sameArea = localPlace?.cacheKey(false) == place?.cacheKey(false)
        val regional = sameArea && requests.value.regional
        localPlace = place
        placeResolved = true
        if (category == NEARBY_CATEGORY) enqueue(Request(category, "", false, regional))
    }

    fun showAlternativeLocalArea() {
        val current = requests.value
        if (current.category == NEARBY_CATEGORY && state.value.alternativeArea != null) {
            enqueue(Request(current.category, "", false, regional = !current.regional))
        }
    }

    private fun restoredRequest(defaultCategory: String): Request {
        val category = savedState[CATEGORY] ?: defaultCategory
        return if (category == NEARBY_CATEGORY) Request(category, "", false)
        else Request(category, savedState[QUERY] ?: "", savedState[SUBMITTED] ?: false)
    }

    private fun enqueue(request: Request) {
        savedState[CATEGORY] = request.category
        savedState[QUERY] = request.query
        savedState[SUBMITTED] = request.submitted
        mutableState.value = NewsListState()
        requests.value = request
    }

    private suspend fun loadLocalNews(request: Request) {
        val place = localPlace
        if (place == null) {
            if (placeResolved) {
                publish(request, NewsListState(loading = false, notice = NewsNotice.LOCAL_LOCATION_REQUIRED))
            }
            return
        }

        val area = if (request.regional) requireNotNull(place.region) else place.cityLabel
        val base = NewsListState(localArea = area, regional = request.regional)
        publish(request, base)
        val cached = dataOrNull { repository.cachedLocalNews(place, request.regional) }.orEmpty()
        publish(request, base.copy(articles = cached))

        val fresh = dataOrNull { repository.refreshLocalNews(place, request.regional) }
        val articles = fresh ?: cached
        val notice = when {
            fresh == null && cached.isEmpty() -> NewsNotice.LOCAL_UNAVAILABLE
            fresh == null -> NewsNotice.LOCAL_CACHED
            fresh.isEmpty() -> NewsNotice.LOCAL_NO_RESULTS
            else -> null
        }
        val alternative = when {
            request.regional -> place.city
            articles.size < MIN_LOCAL_RESULTS -> place.region?.takeUnless { it.equals(place.city, true) }
            else -> null
        }
        publish(request, base.copy(
            articles = articles,
            loading = false,
            notice = notice,
            alternativeArea = alternative
        ))
    }

    private suspend fun loadHeadlines(request: Request) {
        val cached = dataOrNull { repository.cachedHeadlines(request.category) }.orEmpty()
        publish(request, NewsListState(articles = cached))

        val fresh = dataOrNull { repository.refreshHeadlines(request.category) }
        val notice = when {
            fresh == null && cached.isEmpty() -> NewsNotice.NO_CACHED_HEADLINES
            fresh == null -> NewsNotice.CACHED_HEADLINES
            fresh.isEmpty() -> NewsNotice.EMPTY_HEADLINES
            else -> null
        }
        publish(request, NewsListState(fresh ?: cached, loading = false, notice = notice))
    }

    private suspend fun loadSearch(request: Request) {
        if (!request.submitted) {
            if (request.query.length < MIN_SEARCH_LENGTH) {
                publish(request, NewsListState(loading = false, notice = NewsNotice.SHORT_QUERY))
                return
            }
            delay(SEARCH_DEBOUNCE_MS)
        }
        startedRequest = request
        val articles = dataOrNull { repository.search(request.query) }
        val notice = when {
            articles == null -> NewsNotice.SEARCH_FAILED
            articles.isEmpty() -> NewsNotice.EMPTY_SEARCH
            else -> null
        }
        publish(request, NewsListState(articles.orEmpty(), loading = false, notice = notice))
    }

    private suspend fun publish(request: Request, state: NewsListState) {
        coroutineContext.ensureActive()
        if (requests.value === request) mutableState.value = state
    }

    private suspend fun dataOrNull(load: suspend () -> List<Article>): List<Article>? =
        try {
            load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    private companion object {
        const val CATEGORY = "news_category"
        const val QUERY = "news_query"
        const val SUBMITTED = "news_query_submitted"
        const val MIN_SEARCH_LENGTH = 3
        const val SEARCH_DEBOUNCE_MS = 500L
        const val MIN_LOCAL_RESULTS = 3
    }
}
