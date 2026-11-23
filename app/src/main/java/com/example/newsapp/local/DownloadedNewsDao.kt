package com.example.newsapp.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloaded_news", primaryKeys = ["accountId", "url"])
data class DownloadedNews(
    val accountId: Long,
    val url: String,
    val title: String,
    val sourceName: String?,
    val publishedAt: String?,
    val body: String,
    val downloadedAt: Long,
    val coverImage: ByteArray? = null
)

data class DownloadedNewsPreview(
    val url: String,
    val title: String,
    val sourceName: String?,
    val publishedAt: String?,
    val excerpt: String
)

@Dao
interface DownloadedNewsDao {
    @Query("SELECT url FROM downloaded_news WHERE accountId = :accountId")
    fun observeUrls(accountId: Long): Flow<List<String>>

    @Query("""
        SELECT url, title, sourceName, publishedAt, substr(body, 1, 240) AS excerpt
        FROM downloaded_news WHERE accountId = :accountId ORDER BY downloadedAt DESC
    """)
    fun observeDownloads(accountId: Long): Flow<List<DownloadedNewsPreview>>

    @Query("SELECT * FROM downloaded_news WHERE accountId = :accountId AND url = :url")
    suspend fun find(accountId: Long, url: String): DownloadedNews?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(news: DownloadedNews)

    @Query("DELETE FROM downloaded_news WHERE accountId = :accountId AND url = :url")
    suspend fun delete(accountId: Long, url: String)
}
