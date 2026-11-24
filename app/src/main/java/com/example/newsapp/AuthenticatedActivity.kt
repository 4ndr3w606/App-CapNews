package com.example.newsapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.newsapp.data.LocalAuth
import com.example.newsapp.data.LocalProfile
import com.example.newsapp.notifications.HeadlinesSchedule
import kotlinx.coroutines.launch

abstract class AuthenticatedActivity : AppCompatActivity() {
    private lateinit var auth: LocalAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        auth = LocalAuth(this)
        lifecycleScope.launch {
            val profile = auth.current()
            if (profile == null) returnToLogin() else onAccountReady(profile)
        }
    }

    internal abstract fun onAccountReady(profile: LocalProfile)

    protected fun switchAccount() {
        auth.logout()
        HeadlinesSchedule.cancel(this)
        returnToLogin()
    }

    private fun returnToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
