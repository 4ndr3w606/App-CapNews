package com.example.newsapp

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import com.example.newsapp.data.PreferencesManager
import com.example.newsapp.data.api.AuthApiClient
import com.example.newsapp.databinding.ActivityMainBinding
import com.example.newsapp.utils.applyScreenInsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : AuthenticatedActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefsManager: PreferencesManager

    // Mapeo en Español (Interfaz) to Inglés (API)
    private val categoriesMap = linkedMapOf(
        "Mundo" to "world",
        "Tecnología" to "technology",
        "Deportes" to "sports",
        "Negocios" to "business",
        "Entretenimiento" to "entertainment", // Corregido: 'Entretenimieto'
        "Salud" to "health",
        "Ciencia" to "science"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (activeSession == null) return
        binding = ActivityMainBinding.inflate(layoutInflater)
        binding.root.applyScreenInsets()
        setContentView(binding.root)

        prefsManager = PreferencesManager(this)
        val email = sessionStore.getEmail()
        binding.tvUserEmail.text = "Correo: $email"

        // 1. Configurar adaptador del Spinner con los nombres en español
        val categoriesInSpanish = categoriesMap.keys.toList()
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            categoriesInSpanish
        )
        binding.spinnerCategories.adapter = adapter

        // 2. Preseleccionar la última categoría guardada
        val lastSavedApiCategory = prefsManager.getCategory()
        val initialSpanishName =
            categoriesMap.entries.firstOrNull { it.value == lastSavedApiCategory }?.key ?: "Mundo"
        val defaultIndex = categoriesInSpanish.indexOf(initialSpanishName)

        if (defaultIndex >= 0) {
            binding.spinnerCategories.setSelection(defaultIndex)
        }

        // 3. Botón para ver favoritos
        binding.btnVerFavoritos.setOnClickListener {
            val intent = Intent(this, FavoritosActivity::class.java)
            startActivity(intent)
        }

        // 4. Botón para explorar noticias de la categoría seleccionada
        binding.btnViewNews.setOnClickListener {
            val selectedSpanishCategory = binding.spinnerCategories.selectedItem?.toString() ?: "Mundo"
            val apiCategory = categoriesMap[selectedSpanishCategory] ?: "world"

            // Guardar en SharedPreferences
            prefsManager.saveCategory(apiCategory)

            // Pasar datos a NewsActivity
            val intent = Intent(this, NewsActivity::class.java).apply {
                putExtra("EXTRA_SELECTED_CATEGORY", apiCategory)
                putExtra("EXTRA_DISPLAY_CATEGORY", selectedSpanishCategory)
            }
            startActivity(intent)
        }

        // 5. Botón para cerrar sesión
        binding.btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun logout() {
        val token = activeSession?.token ?: return
        binding.btnLogout.isEnabled = false
        sessionStore.clear()
        lifecycleScope.launch {
            try {
                withTimeoutOrNull(2_000) {
                    AuthApiClient.service.logout("Bearer $token")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // La sesión ya se cerró en el dispositivo.
            }
            returnToLogin()
        }
    }
}
