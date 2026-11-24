package com.example.newsapp.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AuthSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "auth_session",
        Context.MODE_PRIVATE
    )

    fun save(token: String, userId: Long, email: String, expiresAt: Long, name: String?) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encryptedToken = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))

        preferences.edit()
            .putString(KEY_TOKEN, Base64.encodeToString(encryptedToken, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putLong(KEY_USER_ID, userId)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putString(KEY_EMAIL, email)
            .putString(KEY_NAME, name?.trim()?.takeIf { it.isNotEmpty() })
            .apply()
    }

    private fun getToken(): String? {
        val encryptedToken = preferences.getString(KEY_TOKEN, null) ?: return null
        val iv = preferences.getString(KEY_IV, null) ?: return null

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(
                cipher.doFinal(Base64.decode(encryptedToken, Base64.NO_WRAP)),
                StandardCharsets.UTF_8
            )
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun getEmail(): String = preferences.getString(KEY_EMAIL, "") ?: ""

    fun getName(): String? = preferences.getString(KEY_NAME, null)

    internal fun updateName(session: SavedAuthSession, name: String?): Boolean {
        if (getSession() != session) return false
        preferences.edit().putString(KEY_NAME, name?.trim()?.takeIf { it.isNotEmpty() }).apply()
        return true
    }

    internal fun getSession(): SavedAuthSession? = getToken()?.let { token ->
        SavedAuthSession(
            token,
            preferences.getLong(KEY_USER_ID, 0),
            preferences.getLong(KEY_EXPIRES_AT, 0)
        )
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.example.newsapp.auth-session"
        const val KEY_TOKEN = "token"
        const val KEY_IV = "token_iv"
        const val KEY_USER_ID = "user_id"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "name"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
    }
}
