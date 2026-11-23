package com.example.newsapp.data

import com.example.newsapp.data.api.ApiService
import com.example.newsapp.data.api.Article
import com.example.newsapp.data.api.NewsResponse
import com.example.newsapp.local.NoticiaDao
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import retrofit2.HttpException
import retrofit2.Response

internal class NewsRepository(
    private val api: ApiService,
    private val dao: NoticiaDao
) {
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

    private fun Response<NewsResponse>.validArticles(): List<Article> {
        if (!isSuccessful) throw HttpException(this)
        return body()?.articles.orEmpty().filter { !it.url.isNullOrBlank() && !it.title.isNullOrBlank() }
    }
}
