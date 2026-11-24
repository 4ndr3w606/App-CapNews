package com.example.newsapp

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.widget.ScrollView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.newsapp.data.AuthSessionStore
import com.example.newsapp.data.SavedAuthSession
import com.example.newsapp.data.api.AuthApiClient
import com.example.newsapp.data.api.AuthCredentials
import com.example.newsapp.data.api.AuthUser
import com.example.newsapp.databinding.ActivityLoginBinding
import com.example.newsapp.utils.applyScreenInsets
import com.example.newsapp.utils.mostrarAviso
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var signInButtonTextColors: ColorStateList
    private var registrationMode = false
    private var termsAccepted = false
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
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        signInButtonTextColors = binding.btnLogin.textColors
        binding.root.applyScreenInsets()
        setContentView(binding.root)
        sessionStore = AuthSessionStore(this)

        binding.btnLogin.setOnClickListener { submitCredentials() }
        binding.btnToggleAuth.setOnClickListener { setRegistrationMode(!registrationMode) }
        termsAccepted = savedInstanceState?.getBoolean(STATE_TERMS_ACCEPTED) == true
        setRegistrationMode(savedInstanceState?.getBoolean(STATE_REGISTRATION) == true)
        binding.switchTerms.setOnCheckedChangeListener { _, checked ->
            if (checked == termsAccepted) return@setOnCheckedChangeListener
            termsAccepted = false
            updateTermsControls()
            if (checked) showTerms()
        }
        if (intent.getBooleanExtra(EXTRA_SESSION_EXPIRED, false)) {
            showError(getString(R.string.session_expired))
        }
        restoreSessionIfPresent()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_REGISTRATION, registrationMode)
        outState.putBoolean(STATE_TERMS_ACCEPTED, termsAccepted)
        super.onSaveInstanceState(outState)
    }

    private fun showTerms() {
        val builder = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_NewsApp_RegistrationTerms)
        val content = LayoutInflater.from(builder.context).inflate(R.layout.dialog_registration_terms, null)
        val scroll = content.findViewById<ScrollView>(R.id.terms_scroll)
        val dialog = builder
            .setTitle(R.string.registration_terms_title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.registration_terms_accept) { _, _ ->
                termsAccepted = true
                updateTermsControls()
            }
            .create()

        dialog.setOnShowListener {
            val accept = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
            accept.isEnabled = false
            scroll.setOnScrollChangeListener { _, _, _, _, _ ->
                if (!scroll.canScrollVertically(1)) accept.isEnabled = true
            }
            scroll.doOnLayout {
                if (!scroll.canScrollVertically(1)) accept.isEnabled = true
            }
        }
        dialog.show()
    }

    private fun restoreSessionIfPresent() {
        val session = sessionStore.getSession() ?: return
        val now = System.currentTimeMillis()
        if (session.isValidAt(now)) {
            openMain()
            return
        }
        if (session.hasExpired(now)) {
            sessionStore.clear()
            showError(getString(R.string.session_expired))
            return
        }
        if (needsLocalNetworkPermission()) return
        setBusy(true)
        lifecycleScope.launch {
            try {
                val response = AuthApiClient.service.currentUser("Bearer ${session.token}")
                if (response.isSuccessful) {
                    val authenticated = response.body()
                    openAuthenticatedSession(
                        session.token,
                        authenticated?.user,
                        authenticated?.expiresAt ?: 0
                    )
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
        if (busy || (registrationMode && !termsAccepted)) return
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
        if (registrationMode) {
            val confirmedEmail = binding.etConfirmEmail.text?.toString()?.trim().orEmpty()
            if (!email.equals(confirmedEmail, ignoreCase = true)) {
                binding.tilConfirmEmail.error = getString(
                    if (confirmedEmail.isEmpty()) R.string.registration_confirm_email_required
                    else R.string.registration_email_mismatch
                )
                binding.etConfirmEmail.requestFocus()
                return
            }
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

        val registering = registrationMode
        setBusy(true)
        lifecycleScope.launch {
            try {
                val credentials = AuthCredentials(email, password)
                val response = if (registering) {
                    AuthApiClient.service.register(credentials.copy(name = name, termsVersion = TERMS_VERSION))
                } else {
                    AuthApiClient.service.login(credentials)
                }

                if (response.isSuccessful && registering) {
                    showLoginAfterRegistration(email)
                } else if (response.isSuccessful) {
                    val session = response.body()
                    if (session == null) {
                        setBusy(false)
                        showError("El servidor devolvió una respuesta incompleta.")
                        return@launch
                    }
                    openAuthenticatedSession(session.token, session.user, session.expiresAt)
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

    private fun showLoginAfterRegistration(email: String) {
        setRegistrationMode(false)
        binding.etUsername.text?.clear()
        binding.etEmail.setText(email)
        binding.etPassword.text?.clear()
        setBusy(false)
        binding.etPassword.requestFocus()
        binding.root.mostrarAviso(R.string.registration_success_sign_in, long = true)
    }

    private fun needsLocalNetworkPermission(): Boolean =
        BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 37 &&
            Uri.parse(BuildConfig.AUTH_API_BASE_URL).scheme.equals("http", ignoreCase = true) &&
            checkSelfPermission(ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED

    private fun setRegistrationMode(enabled: Boolean) {
        registrationMode = enabled
        if (!enabled) termsAccepted = false
        updateTermsControls()
        binding.tilUsername.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvAuthTitle.text = if (enabled) "Crear cuenta" else "Iniciar sesión"
        binding.btnLogin.text = if (enabled) "Registrarme" else "Ingresar"
        binding.btnLogin.isActivated = enabled
        binding.btnLogin.setTextColor(
            if (enabled) ColorStateList.valueOf(getColor(R.color.white)) else signInButtonTextColors
        )
        binding.btnToggleAuth.text = if (enabled) {
            "¿Ya tienes cuenta? Inicia sesión"
        } else {
            "¿No tienes cuenta? Regístrate"
        }
        binding.tilPassword.hint = if (enabled) "Contraseña (mínimo 8 caracteres)" else "Contraseña"
        binding.tilConfirmPassword.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
        binding.tilConfirmEmail.isVisible = enabled
        if (!enabled) {
            binding.etConfirmEmail.text?.clear()
            binding.etConfirmPassword.text?.clear()
        }
        clearErrors()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        updateTermsControls()
        binding.btnToggleAuth.isEnabled = !value
        binding.progressAuth.visibility = if (value) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun updateTermsControls() {
        binding.switchTerms.isVisible = registrationMode
        binding.switchTerms.isChecked = termsAccepted
        binding.switchTerms.isEnabled = !busy
        binding.btnLogin.isEnabled = !busy && (!registrationMode || termsAccepted)
    }

    private fun clearErrors() {
        binding.tilUsername.error = null
        binding.tilEmail.error = null
        binding.tilConfirmEmail.error = null
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

    private fun openAuthenticatedSession(token: String, user: AuthUser?, expiresAt: Long) {
        if (user == null || !SavedAuthSession(token, user.id, expiresAt).isValidAt(System.currentTimeMillis())) {
            setBusy(false)
            showError("El servidor devolvió una respuesta incompleta.")
            return
        }

        sessionStore.save(token, user.id, user.email, expiresAt, user.name)
        openMain()
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        internal const val EXTRA_SESSION_EXPIRED = "session_expired"
        private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MAX_PASSWORD_LENGTH = 128
        private const val TERMS_VERSION = 1
        private const val STATE_REGISTRATION = "registration_mode"
        private const val STATE_TERMS_ACCEPTED = "terms_accepted"
    }
}
