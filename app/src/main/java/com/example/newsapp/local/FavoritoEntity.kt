package com.example.newsapp.local

import androidx.room.Entity

@Entity(tableName = "favoritos_por_cuenta", primaryKeys = ["cuentaId", "url"])
data class FavoritoEntity(
    val cuentaId: Long,
    val url: String,
    val title: String?,
    val description: String?,
    val urlToImage: String?,
    val publishedAt: String?,
    val sourceName: String?
)
