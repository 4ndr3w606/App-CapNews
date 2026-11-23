package com.example.newsapp.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.newsapp.R
import com.example.newsapp.databinding.CardHomeWeatherBinding
import com.example.newsapp.utils.LectorWeb
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.round
import kotlin.math.roundToInt

internal class HomeWeather(
    private val activity: AppCompatActivity,
    private val binding: CardHomeWeatherBinding,
    private val requestPermission: () -> Unit
) {
    private val preferences = activity.getSharedPreferences("home_weather", Context.MODE_PRIVATE)
    private var weatherRequest: Job? = null
    private var citySearch: Job? = null

    fun start() {
        binding.btnWeatherLocation.setOnClickListener { showLocationOptions() }
        binding.tvWeatherSource.setOnClickListener {
            LectorWeb.abrirNoticia(activity, "https://open-meteo.com/")
        }
        if (preferences.getBoolean("device_location", false) && hasLocationPermission()) {
            loadLocation()
        } else {
            val city = preferences.getString("city", null)
            if (city != null) {
                loadCity(
                    WeatherCity(
                        city,
                        preferences.getFloat("latitude", 0f).toDouble(),
                        preferences.getFloat("longitude", 0f).toDouble()
                    )
                )
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            preferences.edit().putBoolean("device_location", true).apply()
            loadLocation()
        } else {
            toast(R.string.weather_permission_denied)
        }
    }

    private fun showLocationOptions() {
        citySearch?.cancel()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.weather_location)
            .setMessage(R.string.weather_location_explanation)
            .setPositiveButton(R.string.weather_use_location) { _, _ ->
                if (hasLocationPermission()) onPermissionResult(true) else requestPermission()
            }
            .setNegativeButton(R.string.weather_choose_city) { _, _ -> showCitySearch() }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCitySearch() {
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.weather_city_hint)
            setSingleLine()
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.weather_choose_city)
            .setView(input)
            .setPositiveButton(R.string.weather_search_city) { _, _ ->
                val query = input.text.toString().trim()
                if (query.length >= 2) searchCities(query) else toast(R.string.weather_city_short)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun searchCities(query: String) {
        citySearch?.cancel()
        citySearch = activity.lifecycleScope.launch {
            try {
                val cities = WeatherRepository.search(query)
                if (cities.isEmpty()) {
                    toast(R.string.weather_city_not_found)
                    return@launch
                }
                if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    return@launch
                }
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.weather_choose_city)
                    .setItems(cities.map { it.label }.toTypedArray()) { _, index ->
                        val city = cities[index]
                        preferences.edit()
                            .putBoolean("device_location", false)
                            .putString("city", city.name)
                            .putFloat("latitude", city.latitude.toFloat())
                            .putFloat("longitude", city.longitude.toFloat())
                            .apply()
                        loadCity(city)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                toast(R.string.weather_city_search_failed)
            }
        }
    }

    private fun loadCity(city: WeatherCity) = loadWeather(city.name) { city }

    private fun loadLocation() = loadWeather(
        activity.getString(R.string.weather_your_location),
        resolveCityName = true
    ) {
        val location = withTimeoutOrNull(7_000) { currentLocation() } ?: return@loadWeather null
        val latitude = round(location.latitude * 100) / 100
        val longitude = round(location.longitude * 100) / 100
        WeatherCity(activity.getString(R.string.weather_your_location), latitude, longitude)
    }

    private fun loadWeather(
        label: String,
        resolveCityName: Boolean = false,
        getCity: suspend () -> WeatherCity?
    ) {
        weatherRequest?.cancel()
        binding.tvWeatherCity.text = label
        binding.tvWeatherCondition.setText(R.string.weather_loading)
        binding.tvWeatherTemperature.isVisible = false
        weatherRequest = activity.lifecycleScope.launch {
            try {
                val city = getCity()
                if (city == null) {
                    binding.tvWeatherCondition.setText(R.string.weather_location_unavailable)
                    return@launch
                }
                binding.tvWeatherCity.text = city.name
                val weather = WeatherRepository.current(city)
                val temperature = weather?.temperature?.takeIf { it.isFinite() }
                if (temperature == null) {
                    binding.tvWeatherCondition.setText(R.string.weather_unavailable)
                    return@launch
                }
                binding.tvWeatherTemperature.text = activity.getString(
                    R.string.weather_temperature, temperature.roundToInt()
                )
                binding.tvWeatherTemperature.isVisible = true
                binding.tvWeatherCondition.setText(condition(weather?.code))
                if (resolveCityName) {
                    binding.tvWeatherCity.text = cityName(city.latitude, city.longitude)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                binding.tvWeatherCondition.setText(R.string.weather_unavailable)
            }
        }
    }

    private fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        activity, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? {
        if (!hasLocationPermission()) return null
        val manager = activity.getSystemService(LocationManager::class.java)
        val providers = manager.getProviders(true)
        val provider = when {
            "fused" in providers -> "fused"
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }
        return suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(
                manager, provider, signal, ContextCompat.getMainExecutor(activity)
            ) { location -> continuation.resume(location) }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun cityName(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        val address = runCatching {
            Geocoder(activity.applicationContext).getFromLocation(latitude, longitude, 1)?.firstOrNull()
        }.getOrNull()
        address?.locality ?: address?.subAdminArea ?: activity.getString(R.string.weather_your_location)
    }

    private fun condition(code: Int?): Int = when (code ?: -1) {
        0 -> R.string.weather_clear
        1, 2 -> R.string.weather_partly_cloudy
        3 -> R.string.weather_cloudy
        45, 48 -> R.string.weather_fog
        in 51..67, in 80..82 -> R.string.weather_rain
        in 71..77, 85, 86 -> R.string.weather_snow
        in 95..99 -> R.string.weather_storm
        else -> R.string.weather_unknown
    }

    private fun toast(message: Int) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
}
