package com.example.newsapp.data

import java.util.Locale

internal data class NewsPlace(val city: String, val region: String?, val countryCode: String?) {
    val cityLabel: String get() = listOfNotNull(city, region).distinct().joinToString(", ")

    fun cacheKey(regional: Boolean): String {
        val parts = if (regional) listOf("region", countryCode, region)
        else listOf("city", countryCode, region, city)
        return parts.joinToString("|") { it.orEmpty().trim().lowercase(Locale.ROOT) }
    }
}
