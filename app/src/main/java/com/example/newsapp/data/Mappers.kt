package com.example.newsapp.data

import com.example.newsapp.data.api.Article
import com.example.newsapp.data.api.Source
import com.example.newsapp.local.EntidadNoticia
import com.example.newsapp.local.FavoritoEntity

// Convertir un Article de la API a EntidadNoticia (para caché de noticias)
fun Article.toEntity(category: String): EntidadNoticia {
    return EntidadNoticia(
        url = this.url ?: "",
        title = this.title,
        description = this.description,
        urlToImage = this.urlToImage,
        publishedAt = this.publishedAt,
        sourceName = this.source?.name,
        category = category
    )
}

// Convertir de EntidadNoticia a Article para el RecyclerView
fun EntidadNoticia.toDomain(): Article {
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

// Convertir un Article de la API a Entidad Favorito
fun Article.toFavoritoEntity(): FavoritoEntity {
    return FavoritoEntity(
        url = this.url ?: "",
        title = this.title,
        description = this.description,
        urlToImage = this.urlToImage,
        publishedAt = this.publishedAt,
        sourceName = this.source?.name
    )
}

// Convertir de Entidad Favorito a Article para el RecyclerView
fun FavoritoEntity.toDomain(): Article {
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
