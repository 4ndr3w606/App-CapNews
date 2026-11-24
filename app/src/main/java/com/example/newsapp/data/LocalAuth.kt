package com.example.newsapp.data

import android.content.Context
import android.content.SharedPreferences
import com.example.newsapp.local.AppDatabase
import com.example.newsapp.local.LocalAccount
import com.example.newsapp.local.LocalAccountDao
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class LocalProfile(val id: Long, val name: String, val email: String) {
    // Los datos con ID positivo pertenecen a cuentas del servidor anterior.
    val userId: Long get() = -id
}

internal class LocalAuth(
    private val accounts: LocalAccountDao,
    private val preferences: SharedPreferences
) {
    constructor(context: Context) : this(
        AppDatabase.getDatabase(context).localAccountDao(),
        context.getSharedPreferences("local_session", Context.MODE_PRIVATE)
    )

    suspend fun current(): LocalProfile? {
        val id = activeUserId(preferences)?.let { -it } ?: return null
        return accounts.findById(id)?.profile()
    }

    suspend fun register(name: String, email: String, password: String, termsVersion: Int): Boolean {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return accounts.insert(
            LocalAccount(
                email = normalizeEmail(email),
                name = name.trim(),
                passwordSalt = salt,
                passwordHash = deriveHash(password, salt),
                termsVersion = termsVersion,
                termsAcceptedAt = System.currentTimeMillis()
            )
        ) != -1L
    }

    suspend fun login(email: String, password: String): Boolean {
        val account = accounts.findByEmail(normalizeEmail(email)) ?: return false
        if (!MessageDigest.isEqual(deriveHash(password, account.passwordSalt), account.passwordHash)) {
            return false
        }
        preferences.edit().putLong(ACCOUNT_ID, account.id).apply()
        return true
    }

    fun logout() {
        preferences.edit().remove(ACCOUNT_ID).apply()
    }

    private fun LocalAccount.profile() = LocalProfile(id, name, email)

    private fun normalizeEmail(email: String) = email.trim().lowercase(Locale.ROOT)

    private suspend fun deriveHash(password: String, salt: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        // PBKDF2-HMAC-SHA1 está disponible desde el mínimo de Android de la app (API 24).
        val spec = PBEKeySpec(password.toCharArray(), salt, 1_300_000, 256)
        try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private const val ACCOUNT_ID = "account_id"

        fun activeUserId(context: Context): Long? =
            activeUserId(context.getSharedPreferences("local_session", Context.MODE_PRIVATE))

        private fun activeUserId(preferences: SharedPreferences): Long? =
            preferences.getLong(ACCOUNT_ID, 0).takeIf { it > 0 }?.let { -it }
    }
}
