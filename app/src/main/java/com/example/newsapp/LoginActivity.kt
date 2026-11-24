package com.example.newsapp

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.widget.ScrollView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.newsapp.data.LocalAuth
import com.example.newsapp.databinding.ActivityLoginBinding
import com.example.newsapp.utils.applyScreenInsets
import com.example.newsapp.utils.mostrarAviso
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: LocalAuth
    private lateinit var signInButtonTextColors: ColorStateList
    private var registrationMode = false
    private var termsAccepted = false
    private var busy = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        signInButtonTextColors = binding.btnLogin.textColors
        binding.root.applyScreenInsets()
        setContentView(binding.root)
        auth = LocalAuth(this)

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
        setBusy(true)
        lifecycleScope.launch {
            try {
                if (auth.current() != null) openMain()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showError(getString(R.string.local_auth_error))
            } finally {
                setBusy(false)
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

        val registering = registrationMode
        setBusy(true)
        lifecycleScope.launch {
            try {
                if (registering) {
                    if (auth.register(name, email, password, TERMS_VERSION)) {
                        showLoginAfterRegistration(email)
                    } else {
                        showError(getString(R.string.local_auth_duplicate))
                    }
                } else if (auth.login(email, password)) {
                    openMain()
                } else {
                    showError(getString(R.string.local_auth_invalid_credentials))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showError(getString(R.string.local_auth_error))
            } finally {
                setBusy(false)
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

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MAX_PASSWORD_LENGTH = 128
        private const val TERMS_VERSION = 2
        private const val STATE_REGISTRATION = "registration_mode"
        private const val STATE_TERMS_ACCEPTED = "terms_accepted"
    }
}
