package com.example.newsapp

import androidx.lifecycle.SavedStateHandle
import com.example.newsapp.data.NewsRepository
import com.example.newsapp.data.NewsPlace
import com.example.newsapp.data.api.ApiService
import com.example.newsapp.data.api.Article
import com.example.newsapp.data.api.NewsResponse
import com.example.newsapp.data.toEntity
import com.example.newsapp.local.EntidadNoticia
import com.example.newsapp.local.LocalNewsDao
import com.example.newsapp.local.LocalNewsEntity
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

            override suspend fun buscarNoticias(
                consulta: String, lang: String, country: String?, sortBy: String?, searchFields: String
            ): Response<NewsResponse> {
                searchStarted.complete(Unit)
                return response(currentArticle)
            }
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val viewModel = NewsViewModel(NewsRepository(api, CachedDao(), LocalCache()), "world", SavedStateHandle(), scope)
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

            override suspend fun buscarNoticias(
                consulta: String, lang: String, country: String?, sortBy: String?, searchFields: String
            ): Response<NewsResponse> {
                assertEquals("title,description,content", searchFields)
                searchCalls++
                return response(result)
            }
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val viewModel = NewsViewModel(
            NewsRepository(api, CachedDao(listOf(cached.toEntity("world"))), LocalCache()),
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

            override suspend fun buscarNoticias(
                consulta: String, lang: String, country: String?, sortBy: String?, searchFields: String
            ): Response<NewsResponse> = response(article(consulta))
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val viewModel = NewsViewModel(NewsRepository(api, CachedDao(), LocalCache()), "sports", SavedStateHandle(), scope)
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

    @Test
    fun localNewsPrioritizesCityHeadlinesAndKeepsCityAndRegionalCachesSeparate() = runBlocking {
        val neiva = NewsPlace("Neiva", "Huila", "CO")
        val garzon = NewsPlace("Garzón", "Huila", "co")
        val cityHeadline = article("Obras en Neiva")
        val cityMention = article("Informe de movilidad").copy(
            description = "Obras anunciadas esta semana",
            content = "El informe incluye obras en Neiva."
        )
        val regionalHeadline = cityHeadline.copy(title = "Obras en Huila")
        val searches = mutableListOf<String>()
        var offline = false
        val api = object : ApiService {
            override suspend fun getTopHeadlines(category: String, lang: String): Response<NewsResponse> =
                error("El selector local usa el endpoint de búsqueda")

            override suspend fun buscarNoticias(
                consulta: String, lang: String, country: String?, sortBy: String?, searchFields: String
            ): Response<NewsResponse> {
                assertEquals("es", lang)
                assertEquals("co", country)
                assertEquals("relevance", sortBy)
                assertEquals("title,description,content", searchFields)
                searches += consulta
                if (offline) throw IOException("offline")
                return when (consulta) {
                    "\"Neiva\"" -> Response.success(
                        NewsResponse(articles = listOf(cityMention, cityHeadline, cityHeadline))
                    )
                    "\"Huila\"" -> response(regionalHeadline)
                    else -> error("Consulta inesperada")
                }
            }
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val viewModel = NewsViewModel(
            NewsRepository(api, CachedDao(), LocalCache()), NEARBY_CATEGORY, SavedStateHandle(), scope
        )
        try {
            withTimeout(5_000) {
                viewModel.updateLocalPlace(null)
                assertEquals(NewsNotice.LOCAL_LOCATION_REQUIRED, viewModel.state.first { !it.loading }.notice)
                assertEquals(emptyList<String>(), searches)

                viewModel.updateLocalPlace(neiva)
                val city = viewModel.state.first { !it.loading }
                assertEquals(listOf(cityHeadline, cityMention), city.articles)
                assertEquals("Huila", city.alternativeArea)
                assertEquals(listOf("\"Neiva\""), searches)

                viewModel.showAlternativeLocalArea()
                val region = viewModel.state.first { !it.loading }
                assertEquals("Huila", region.localArea)
                assertEquals("Neiva", region.alternativeArea)
                assertEquals(listOf(regionalHeadline), region.articles)

                offline = true
                viewModel.showAlternativeLocalArea()
                val cachedCity = viewModel.state.first { !it.loading }
                assertEquals(NewsNotice.LOCAL_CACHED, cachedCity.notice)
                assertEquals(city.articles, cachedCity.articles)

                viewModel.updateLocalPlace(garzon)
                val uncachedCity = viewModel.state.first { !it.loading }
                assertEquals(NewsNotice.LOCAL_UNAVAILABLE, uncachedCity.notice)
                assertEquals(emptyList<Article>(), uncachedCity.articles)

                viewModel.showAlternativeLocalArea()
                val cachedRegion = viewModel.state.first { !it.loading }
                assertEquals(NewsNotice.LOCAL_CACHED, cachedRegion.notice)
                assertEquals(listOf(regionalHeadline), cachedRegion.articles)
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

    private class LocalCache : LocalNewsDao {
        private val feeds = mutableMapOf<String, String>()

        override suspend fun read(scopeKey: String): String? = feeds[scopeKey]

        override suspend fun write(feed: LocalNewsEntity) {
            feeds[feed.scopeKey] = feed.articlesJson
        }
    }
}
