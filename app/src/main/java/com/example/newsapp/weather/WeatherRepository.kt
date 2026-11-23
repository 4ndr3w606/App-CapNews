package com.example.newsapp.weather

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

internal data class WeatherCity(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val admin1: String? = null,
    val country: String? = null,
    @SerializedName("country_code") val countryCode: String? = null
) {
    val label: String get() = listOfNotNull(name, admin1, country).distinct().joinToString(", ")
}

internal data class CurrentWeather(
    @SerializedName("temperature_2m") val temperature: Double?,
    @SerializedName("weather_code") val code: Int?
)

internal object WeatherRepository {
    private data class CityResults(val results: List<WeatherCity>?)
    private data class Forecast(val current: CurrentWeather?)

    private interface CitiesApi {
        @GET("search")
        suspend fun search(
            @Query("name") query: String,
            @Query("language") language: String = "es",
            @Query("count") count: Int = 8
        ): CityResults
    }

    private interface WeatherApi {
        @GET("forecast")
        suspend fun current(
            @Query("latitude") latitude: Double,
            @Query("longitude") longitude: Double,
            @Query("current") fields: String = "temperature_2m,weather_code"
        ): Forecast
    }

    private val cities by lazy {
        retrofit("https://geocoding-api.open-meteo.com/v1/").create(CitiesApi::class.java)
    }
    private val weather by lazy {
        retrofit("https://api.open-meteo.com/v1/").create(WeatherApi::class.java)
    }

    suspend fun search(query: String): List<WeatherCity> = cities.search(query).results.orEmpty()

    suspend fun current(city: WeatherCity): CurrentWeather? =
        weather.current(city.latitude, city.longitude).current

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
