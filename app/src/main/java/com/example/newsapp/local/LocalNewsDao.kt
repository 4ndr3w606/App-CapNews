package com.example.newsapp.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "local_news")
data class LocalNewsEntity(@PrimaryKey val scopeKey: String, val articlesJson: String)

@Dao
interface LocalNewsDao {
    @Query("SELECT articlesJson FROM local_news WHERE scopeKey = :scopeKey")
    suspend fun read(scopeKey: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write(feed: LocalNewsEntity)
}
