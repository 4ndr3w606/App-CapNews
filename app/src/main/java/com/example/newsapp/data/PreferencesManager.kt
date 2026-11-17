package com.example.newsapp.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CATEGORY = "categoria_fav"
    }

    fun saveCategory(category: String) {
        prefs.edit().putString(KEY_CATEGORY, category).apply()
    }

    fun getCategory(): String = prefs.getString(KEY_CATEGORY, "") ?: ""
}
