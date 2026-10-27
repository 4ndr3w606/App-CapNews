package com.example.newsapp.data.api

import com.google.gson.annotations.SerializedName

// Respuesta de la API REST
data class NewsResponse (
    @SerializedName("status") val status: String? = null,
    @SerializedName("totalArticles") val totalArticles: Int? = null,
    @SerializedName("totalResults") val totalResults: Int? = null,
    @SerializedName("articles") val articles: List<Article>? = null
)

// Modelo de la noticia
data class Article(
    @SerializedName("source") val source: Source?,
    @SerializedName("author") val author: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("image") val urlToImage: String?,
    @SerializedName("publishedAt") val publishedAt: String?,
    @SerializedName("content") val content: String?
)

data class Source(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("url") val url: String? = null
)