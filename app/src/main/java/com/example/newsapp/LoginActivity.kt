package com.example.newsapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.newsapp.data.PreferencesManager
import com.example.newsapp.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var prefsManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PreferencesManager(this)

        if (prefsManager.isLoggedIn()) {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("email", prefsManager.getEmail())
            }

            startActivity(intent)
            finish()
            return
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(this, "Ingrese su correo electrónico", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Guardar la sesión y el correo en SharedPreferences
            prefsManager.saveUserSession(email, "general")

            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("email", email)
            }

            startActivity(intent)
            finish()
        }
    }
}
