package com.example.newsapp

import androidx.lifecycle.SavedStateHandle
import com.example.newsapp.data.NewsRepository
import com.example.newsapp.data.api.ApiService
import com.example.newsapp.data.api.Article
import com.example.newsapp.data.api.NewsResponse
import com.example.newsapp.data.toEntity
import com.example.newsapp.local.EntidadNoticia
import com.example.newsapp.local.NoticiaDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class NewsViewModelTest {
    @Test
    fun waitsForPreviousRequestToCancelAndDiscardsItsLateResult() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val cancelling = CompletableDeferred<Unit>()
        val finishCancelling = CompletableDeferred<Unit>()
        val searchStarted = CompletableDeferred<Unit>()
        val currentArticle = article("resultado")
        val api = object : ApiService {
            override suspend fun getTopHeadlines(category: String, lang: String): Response<NewsResponse> {
                firstStarted.complete(Unit)
                try {
                    awaitCancellation()
                } catch (_: CancellationException) {
                    withContext(NonCancellable) {
                        cancelling.complete(Unit)
                        finishCancelling.await()
                    }
                    return response(article("anterior"))
                }
            }

            override suspend fun buscarNoticias(consulta: String, lang: String): Response<NewsResponse> {
                searchStarted.complete(Unit)
                return response(currentArticle)
            }
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val viewModel = NewsViewModel(NewsRepository(api, CachedDao()), "world", SavedStateHandle(), scope)
        try {
            withTimeout(5_000) {
                firstStarted.await()
                viewModel.submitQuery("tecnología")
                cancelling.await()
                assertFalse(searchStarted.isCompleted)
                finishCancelling.complete(Unit)
                assertEquals(listOf(currentArticle), viewModel.state.first { !it.loading }.articles)
            }
        } finally {
            finishCancelling.complete(Unit)
            scope.cancel()
        }
    }

    @Test
    fun immediateSubmitReplacesPendingDebounceAndClearingRestoresCachedHeadlines() = runBlocking {
        val cached = article("guardado")
        val result = article("buscado")
        var searchCalls = 0
        val api = object : ApiService {
            override suspend fun getTopHeadlines(category: String, lang: String): Response<NewsResponse> =
                throw IOException("offline")

            override suspend fun buscarNoticias(consulta: String, lang: String): Response<NewsResponse> {
                searchCalls++
                return response(result)
            }
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val viewModel = NewsViewModel(
            NewsRepository(api, CachedDao(listOf(cached.toEntity("world")))),
            "world", SavedStateHandle(), scope
        )
        try {
            withTimeout(5_000) {
                val offline = viewModel.state.first { !it.loading }
                assertEquals(NewsNotice.CACHED_HEADLINES, offline.notice)
                assertEquals(cached.url, offline.articles.single().url)

                viewModel.changeQuery("clima")
                yield()
                viewModel.submitQuery("clima")
                assertEquals(listOf(result), viewModel.state.first { !it.loading }.articles)
                viewModel.submitQuery("clima")
                yield()
                assertEquals(1, searchCalls)

                viewModel.changeQuery("")
                val restored = viewModel.state.first { !it.loading }
                assertEquals(NewsNotice.CACHED_HEADLINES, restored.notice)
                assertEquals(cached.url, restored.articles.single().url)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun startsWithSavedCategoryAndChangingCategoryReturnsToHeadlines() = runBlocking {
        val requestedCategories = mutableListOf<String>()
        val api = object : ApiService {
            override suspend fun getTopHeadlines(category: String, lang: String): Response<NewsResponse> {
                requestedCategories += category
                return response(article(category))
            }

            override suspend fun buscarNoticias(consulta: String, lang: String): Response<NewsResponse> =
                response(article(consulta))
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val viewModel = NewsViewModel(NewsRepository(api, CachedDao()), "sports", SavedStateHandle(), scope)
        try {
            withTimeout(5_000) {
                assertEquals("sports", viewModel.state.first { !it.loading }.articles.single().title)
                viewModel.submitQuery("elecciones")
                assertEquals("elecciones", viewModel.state.first { !it.loading }.articles.single().title)
                viewModel.changeCategory("technology")
                assertEquals("", viewModel.query)
                assertEquals("technology", viewModel.state.first { !it.loading }.articles.single().title)
                assertEquals(listOf("sports", "technology"), requestedCategories)
            }
        } finally {
            scope.cancel()
        }
    }

    private fun article(name: String) = Article(null, null, name, null, "https://example.com/$name", null, null, null)

    private fun response(article: Article): Response<NewsResponse> =
        Response.success(NewsResponse(articles = listOf(article)))

    private class CachedDao(private val articles: List<EntidadNoticia> = emptyList()) : NoticiaDao {
        override suspend fun getArticlesByCategory(category: String): List<EntidadNoticia> = articles
        override suspend fun insertArticles(artcles: List<EntidadNoticia>) = Unit
        override suspend fun clearArticlesByCategory(category: String) = Unit
    }
}
