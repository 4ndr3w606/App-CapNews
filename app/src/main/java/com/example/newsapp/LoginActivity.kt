package com.example.newsapp

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.newsapp.data.AuthSessionStore
import com.example.newsapp.data.api.AuthApiClient
import com.example.newsapp.data.api.AuthCredentials
import com.example.newsapp.databinding.ActivityLoginBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionStore: AuthSessionStore
    private var registrationMode = false
    private var busy = false
    private val localNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            submitCredentials()
        } else {
            showError("Permite el acceso a la red local en los ajustes de CapNews para conectarte al servidor de desarrollo.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sessionStore = AuthSessionStore(this)

        binding.btnLogin.setOnClickListener { submitCredentials() }
        binding.btnToggleAuth.setOnClickListener { setRegistrationMode(!registrationMode) }
        setRegistrationMode(false)
        restoreSessionIfPresent()
    }

    private fun restoreSessionIfPresent() {
        val token = sessionStore.getToken() ?: return
        if (needsLocalNetworkPermission()) return
        setBusy(true)
        lifecycleScope.launch {
            try {
                val response = AuthApiClient.service.currentUser("Bearer $token")
                if (response.isSuccessful) {
                    val email = response.body()?.user?.email ?: sessionStore.getEmail()
                    openMain(email)
                } else {
                    if (response.code() == 401) sessionStore.clear()
                    setBusy(false)
                    showError("No se pudo validar la sesión. Inicia sesión de nuevo.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                setBusy(false)
                showError("No se pudo conectar con el servidor para validar la sesión.")
            }
        }
    }

    private fun submitCredentials() {
        if (busy) return
        clearErrors()

        val name = binding.etUsername.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirmation = binding.etConfirmPassword.text?.toString().orEmpty()

        if (registrationMode && name.isBlank()) {
            binding.tilUsername.error = "Ingresa tu nombre de usuario."
            binding.etUsername.requestFocus()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches() || email.length > 254) {
            binding.tilEmail.error = "Escribe un correo válido."
            return
        }
        if (password.length !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            binding.tilPassword.error = "La contraseña debe tener entre 8 y 128 caracteres."
            return
        }
        if (registrationMode && password != confirmation) {
            binding.tilConfirmPassword.error = "Las contraseñas no coinciden."
            return
        }

        if (needsLocalNetworkPermission()) {
            localNetworkPermission.launch(ACCESS_LOCAL_NETWORK)
            return
        }

        setBusy(true)
        lifecycleScope.launch {
            try {
                val credentials = AuthCredentials(email, password)
                val response = if (registrationMode) {
                    AuthApiClient.service.register(credentials.copy(name = name))
                } else {
                    AuthApiClient.service.login(credentials)
                }

                if (response.isSuccessful) {
                    val session = response.body()
                    if (session == null) {
                        setBusy(false)
                        showError("El servidor devolvió una respuesta incompleta.")
                        return@launch
                    }
                    sessionStore.save(session.token, session.user.email)
                    openMain(session.user.email)
                } else {
                    setBusy(false)
                    showError(messageForStatus(response.code()))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                setBusy(false)
                showError("No se pudo conectar con el servidor. Intenta de nuevo.")
            }
        }
    }

    private fun needsLocalNetworkPermission(): Boolean =
        BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 37 &&
            Uri.parse(BuildConfig.AUTH_API_BASE_URL).scheme.equals("http", ignoreCase = true) &&
            checkSelfPermission(ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED

    private fun setRegistrationMode(enabled: Boolean) {
        registrationMode = enabled
        binding.tilUsername.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvAuthTitle.text = if (enabled) "Crear cuenta" else "Iniciar sesión"
        binding.btnLogin.text = if (enabled) "Registrarme" else "Ingresar"
        binding.btnToggleAuth.text = if (enabled) {
            "¿Ya tienes cuenta? Inicia sesión"
        } else {
            "¿No tienes cuenta? Regístrate"
        }
        binding.tilPassword.hint = if (enabled) "Contraseña (mínimo 8 caracteres)" else "Contraseña"
        binding.tilConfirmPassword.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
        if (!enabled) binding.etConfirmPassword.text?.clear()
        clearErrors()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.btnLogin.isEnabled = !value
        binding.btnToggleAuth.isEnabled = !value
        binding.progressAuth.visibility = if (value) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun clearErrors() {
        binding.tilUsername.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null
        binding.tvAuthError.text = ""
        binding.tvAuthError.visibility = android.view.View.GONE
    }

    private fun showError(message: String) {
        binding.tvAuthError.text = message
        binding.tvAuthError.visibility = android.view.View.VISIBLE
    }

    private fun messageForStatus(statusCode: Int): String = when (statusCode) {
        400, 409 -> "No se pudo completar la operación. Revisa los datos o prueba iniciar sesión."
        401 -> "Correo o contraseña incorrectos."
        429 -> "Demasiados intentos. Espera unos minutos y vuelve a probar."
        else -> "El servidor no pudo completar la operación. Intenta más tarde."
    }

    private fun openMain(email: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra("email", email))
        finish()
    }

    private companion object {
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 128
    }
}
