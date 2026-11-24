package com.example.newsapp.data

import android.content.Context
import android.content.SharedPreferences

internal data class HeadlinesSettings(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val categories: Set<String>
) {
    val shouldSchedule: Boolean get() = enabled && categories.isNotEmpty()
}

class PreferencesManager(context: Context, accountId: Long) {

    private val prefs: SharedPreferences = context.getSharedPreferences("user_prefs_$accountId", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CATEGORY = "categoria_fav"
    }

    fun saveCategory(category: String) {
        prefs.edit().putString(KEY_CATEGORY, category).apply()
    }

    fun getCategory(): String = prefs.getString(KEY_CATEGORY, "") ?: ""

    internal fun headlinesSettings() = HeadlinesSettings(
        enabled = prefs.getBoolean("headlines_enabled", false),
        hour = prefs.getInt("headlines_hour", 8),
        minute = prefs.getInt("headlines_minute", 0),
        categories = prefs.getStringSet("headlines_categories", emptySet()).orEmpty()
            .intersect(headlineCategories.keys)
    )

    internal fun saveHeadlinesSettings(settings: HeadlinesSettings) {
        prefs.edit()
            .putBoolean("headlines_enabled", settings.enabled)
            .putInt("headlines_hour", settings.hour)
            .putInt("headlines_minute", settings.minute)
            .putStringSet("headlines_categories", settings.categories)
            .apply()
    }

    internal fun lastHeadlinesDay(): String? = prefs.getString("headlines_last_day", null)

    internal fun markHeadlinesDelivered(day: String) {
        prefs.edit().putString("headlines_last_day", day).apply()
    }
}
