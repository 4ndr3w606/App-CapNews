package com.example.newsapp.data

import com.example.newsapp.data.api.Article
import com.example.newsapp.data.api.Source
import com.example.newsapp.local.EntidadNoticia


fun Article.toEntity(category: String): EntidadNoticia {
    return EntidadNoticia(
        url = this.url ?: "",
        title = this.title,
        description = this.description,
        urlToImage = this.urlToImage,
        publishedAt = this.publishedAt,
        sourceName = this.source?.name,
        category = category

    )}

fun EntidadNoticia.toDomain(): Article{
    return Article(
        source = Source(id = null, name = this.sourceName),
        author = null,
        title = this.title,
        description = this.description,
        url = this.url,
        urlToImage = this.urlToImage,
        publishedAt = this.publishedAt,
        content = null

    )
}