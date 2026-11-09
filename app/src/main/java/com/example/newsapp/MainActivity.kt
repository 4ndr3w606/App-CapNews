package com.example.newsapp

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.example.newsapp.data.PreferencesManager
import com.example.newsapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PreferencesManager(this)

        // Recuperar el correo desde el Intent o desde PreferencesManager
        val emailIntent = intent.getStringExtra("email")
        val email = if (!emailIntent.isNullOrEmpty()) emailIntent else prefsManager.getEmail()
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
        // 1. Limpiar SharedPreferences
        prefsManager.clearSession()

        // 2. Redirigir a LoginActivity y limpiar la pila de actividades
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}