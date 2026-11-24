package com.example.newsapp.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.newsapp.R
import com.example.newsapp.data.NewsPlace
import com.example.newsapp.databinding.CardHomeWeatherBinding
import com.example.newsapp.databinding.DialogWeatherCityBinding
import com.example.newsapp.utils.LectorWeb
import com.example.newsapp.utils.mostrarAviso
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.round
import kotlin.math.roundToInt

internal class HomeWeather(
    private val activity: AppCompatActivity,
    private val binding: CardHomeWeatherBinding,
    private val onPlaceChanged: (NewsPlace?) -> Unit,
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
                        preferences.getFloat("longitude", 0f).toDouble(),
                        admin1 = preferences.getString("city_region", null),
                        countryCode = preferences.getString("city_country_code", null)
                    )
                )
            } else {
                onPlaceChanged(null)
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            preferences.edit().putBoolean("device_location", true).apply()
            loadLocation()
        } else {
            binding.root.mostrarAviso(R.string.weather_permission_denied, error = true)
        }
    }

    fun showLocationOptions() {
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
        val content = DialogWeatherCityBinding.inflate(activity.layoutInflater)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.weather_choose_city)
            .setView(content.root)
            .setPositiveButton(R.string.weather_search_city, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val query = content.cityInput.text?.toString()?.trim().orEmpty()
            if (query.length < 2) {
                content.cityField.error = activity.getString(R.string.weather_city_short)
            } else {
                dialog.dismiss()
                searchCities(query)
            }
        }
    }

    private fun searchCities(query: String) {
        citySearch?.cancel()
        citySearch = activity.lifecycleScope.launch {
            try {
                val cities = WeatherRepository.search(query)
                if (cities.isEmpty()) {
                    binding.root.mostrarAviso(R.string.weather_city_not_found)
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
                            .putString("city_region", city.admin1)
                            .putString("city_country_code", city.countryCode)
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
                binding.root.mostrarAviso(R.string.weather_city_search_failed, error = true)
            }
        }
    }

    private fun loadCity(city: WeatherCity) {
        onPlaceChanged(NewsPlace(city.name, city.admin1, city.countryCode))
        loadWeather(city.name) { city }
    }

    private fun loadLocation() {
        val lastPlace = preferences.getString("device_city", null)?.let { city ->
            NewsPlace(
                city,
                preferences.getString("device_region", null),
                preferences.getString("device_country_code", null)
            )
        }
        val label = lastPlace?.city ?: activity.getString(R.string.weather_your_location)
        loadWeather(label, onDevicePlaceResolved = { resolved ->
            if (resolved != null) {
                preferences.edit()
                    .putString("device_city", resolved.city)
                    .putString("device_region", resolved.region)
                    .putString("device_country_code", resolved.countryCode)
                    .apply()
            }
            val place = resolved ?: lastPlace
            if (place != null) binding.tvWeatherCity.text = place.city
            onPlaceChanged(place)
        }) {
            val location = try {
                withTimeoutOrNull(7_000) { currentLocation() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: return@loadWeather null
            val latitude = round(location.latitude * 100) / 100
            val longitude = round(location.longitude * 100) / 100
            WeatherCity(label, latitude, longitude)
        }
    }

    private fun loadWeather(
        label: String,
        onDevicePlaceResolved: ((NewsPlace?) -> Unit)? = null,
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
                    onDevicePlaceResolved?.invoke(null)
                    binding.tvWeatherCondition.setText(R.string.weather_location_unavailable)
                    return@launch
                }
                binding.tvWeatherCity.text = city.name
                if (onDevicePlaceResolved != null) {
                    launch {
                        onDevicePlaceResolved(withTimeoutOrNull(5_000) { resolvePlace(city) })
                    }
                }
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
    private suspend fun resolvePlace(city: WeatherCity): NewsPlace? {
        return try {
            val geocoder = Geocoder(activity.applicationContext, Locale.forLanguageTag("es"))
            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(city.latitude, city.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            continuation.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            continuation.resume(null)
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    geocoder.getFromLocation(city.latitude, city.longitude, 1)?.firstOrNull()
                }
            } ?: return null
            val name = address.locality?.takeIf { it.isNotBlank() }
                ?: address.subAdminArea?.takeIf { it.isNotBlank() }
                ?: return null
            NewsPlace(name, address.adminArea?.takeIf { it.isNotBlank() }, address.countryCode)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
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

}
