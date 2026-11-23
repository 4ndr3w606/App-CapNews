package com.example.newsapp

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newsapp.data.NewsRepository
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

internal enum class NewsNotice {
    SHORT_QUERY, EMPTY_HEADLINES, CACHED_HEADLINES, NO_CACHED_HEADLINES, EMPTY_SEARCH, SEARCH_FAILED
}

internal data class NewsListState(
    val articles: List<Article> = emptyList(),
    val loading: Boolean = true,
    val notice: NewsNotice? = null
)

internal class NewsViewModel(
    private val repository: NewsRepository,
    category: String,
    private val savedState: SavedStateHandle,
    requestScope: CoroutineScope? = null
) : ViewModel() {
    private class Request(val category: String, val query: String, val submitted: Boolean)

    private val requests = MutableStateFlow(
        Request(savedState[CATEGORY] ?: category, savedState[QUERY] ?: "", savedState[SUBMITTED] ?: false)
    )
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
                    request.query.isEmpty() -> loadHeadlines(request)
                    else -> loadSearch(request)
                }
            }
        }
    }

    fun changeQuery(input: String) {
        val query = input.trim()
        if (query != this.query) enqueue(Request(category, query, submitted = false))
    }

    fun changeCategory(category: String) {
        if (category != this.category) enqueue(Request(category, "", submitted = false))
    }

    fun submitQuery(input: String) {
        val query = input.trim()
        if (query.isEmpty()) return
        val current = requests.value
        val alreadyRequested = current.query == query && (current.submitted || current === startedRequest)
        if (alreadyRequested && state.value.notice != NewsNotice.SEARCH_FAILED) return
        enqueue(Request(current.category, query, submitted = true))
    }

    private fun enqueue(request: Request) {
        savedState[CATEGORY] = request.category
        savedState[QUERY] = request.query
        savedState[SUBMITTED] = request.submitted
        mutableState.value = NewsListState()
        requests.value = request
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
    }
}
