package com.example.newsapp.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "local_accounts", indices = [Index(value = ["email"], unique = true)])
data class LocalAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val email: String,
    val name: String,
    val passwordSalt: ByteArray,
    val passwordHash: ByteArray,
    val termsVersion: Int,
    val termsAcceptedAt: Long
)

@Dao
interface LocalAccountDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: LocalAccount): Long

    @Query("SELECT * FROM local_accounts WHERE email = :email")
    suspend fun findByEmail(email: String): LocalAccount?

    @Query("SELECT * FROM local_accounts WHERE id = :id")
    suspend fun findById(id: Long): LocalAccount?
}
