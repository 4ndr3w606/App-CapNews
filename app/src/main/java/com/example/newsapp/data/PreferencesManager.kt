package com.example.newsapp.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_CATEGORY = "categoria_fav"
    }

    fun saveUserSession(email: String, category: String) {
        prefs.edit().apply {
            putBoolean(KEY_USER_ID, true)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_CATEGORY, category)
            apply()
        }
    }

    fun saveCategory(category: String) {
        prefs.edit().putString(KEY_CATEGORY, category).apply()
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_USER_ID, false)

    fun getEmail(): String = prefs.getString(KEY_USER_EMAIL, "general") ?: "general"

    fun getCategory(): String = prefs.getString(KEY_CATEGORY, "") ?: ""

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
