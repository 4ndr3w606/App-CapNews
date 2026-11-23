package com.example.newsapp.data

import com.example.newsapp.data.api.ApiService
import com.example.newsapp.data.api.Article
import com.example.newsapp.data.api.NewsResponse
import com.example.newsapp.local.LocalNewsDao
import com.example.newsapp.local.LocalNewsEntity
import com.example.newsapp.local.NoticiaDao
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import retrofit2.HttpException
import retrofit2.Response
import java.util.Locale

internal class NewsRepository(
    private val api: ApiService,
    private val dao: NoticiaDao,
    private val localDao: LocalNewsDao
) {
    private val gson = Gson()
    private val articleListType = object : TypeToken<List<Article>>() {}.type
    suspend fun cachedHeadlines(category: String): List<Article> =
        dao.getArticlesByCategory(category).map { it.toDomain() }

    suspend fun refreshHeadlines(category: String): List<Article> {
        val articles = api.getTopHeadlines(category).validArticles()
        currentCoroutineContext().ensureActive()
        dao.insertArticles(articles.map { it.toEntity(category) })
        return articles
    }

    suspend fun search(query: String): List<Article> =
        api.buscarNoticias(query).validArticles()

    suspend fun cachedLocalNews(place: NewsPlace, regional: Boolean): List<Article> =
        localDao.read(place.cacheKey(regional))?.let {
            gson.fromJson<List<Article>>(it, articleListType)
        }.orEmpty()

    suspend fun refreshLocalNews(place: NewsPlace, regional: Boolean): List<Article> {
        val name = if (regional) requireNotNull(place.region) else place.city
        val phrase = name.replace("\"", "").replace("\\", "").trim()
        val nameInTitle = Regex(
            "(?<![\\p{L}\\p{N}])${Regex.escape(phrase)}(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE
        )
        val articles = api.buscarNoticias(
            consulta = "\"$phrase\"",
            country = place.countryCode?.lowercase(Locale.ROOT)?.takeIf { it in sourceCountries },
            sortBy = "relevance"
        ).validArticles()
            .distinctBy { it.url }
            .sortedByDescending { nameInTitle.containsMatchIn(it.title.orEmpty()) }
        currentCoroutineContext().ensureActive()
        if (articles.isNotEmpty()) {
            localDao.write(LocalNewsEntity(place.cacheKey(regional), gson.toJson(articles)))
        }
        return articles
    }

    private fun Response<NewsResponse>.validArticles(): List<Article> {
        if (!isSuccessful) throw HttpException(this)
        return body()?.articles.orEmpty().filter { !it.url.isNullOrBlank() && !it.title.isNullOrBlank() }
    }

    private companion object {
        val sourceCountries = setOf(
            "ar", "au", "bd", "br", "ca", "cn", "co", "eg", "fr", "de", "gr", "hk", "in", "id",
            "ie", "il", "it", "jp", "my", "mx", "nl", "no", "pk", "pe", "ph", "pt",
            "ro", "ru", "sg", "es", "se", "ch", "tw", "tr", "ua",
            "gb", "us"
        )
    }
}
