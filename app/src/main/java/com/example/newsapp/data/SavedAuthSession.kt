package com.example.newsapp.data

internal data class SavedAuthSession(
    val token: String,
    val userId: Long,
    val expiresAt: Long
) {
    fun hasExpired(now: Long): Boolean = expiresAt > 0 && expiresAt <= now

    fun isValidAt(now: Long): Boolean = token.isNotBlank() && userId > 0 && expiresAt > now
}
