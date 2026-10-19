package com.example.newsapp.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NoticiaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(artcles: List<EntidadNoticia>)

    @Query("SELECT * FROM articles WHERE category = :category ORDER BY publishedAt DESC")
    suspend fun getArticlesByCategory(category: String): List<EntidadNoticia>

    @Query("DELETE FROM articles WHERE category = :category")
    suspend fun clearArticlesByCategory(category: String)

}