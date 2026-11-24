package com.example.newsapp

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.newsapp.data.AuthSessionStore
import com.example.newsapp.data.SavedAuthSession
import com.example.newsapp.data.api.AuthApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

abstract class AuthenticatedActivity : AppCompatActivity() {
    protected lateinit var sessionStore: AuthSessionStore
        private set
    internal var activeSession: SavedAuthSession? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sessionStore = AuthSessionStore(this)
        val session = sessionStore.getSession()
        val now = System.currentTimeMillis()
        if (session == null || !session.isValidAt(now)) {
            val expired = session?.hasExpired(now) == true
            if (expired) sessionStore.clear()
            returnToLogin(expired)
            return
        }

        activeSession = session
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (sessionStore.getSession()?.token != session.token) {
                    returnToLogin()
                    return@repeatOnLifecycle
                }
                val now = System.currentTimeMillis()
                if (session.hasExpired(now)) {
                    invalidateSession(session)
                    return@repeatOnLifecycle
                }
                launch {
                    delay(session.expiresAt - now)
                    invalidateSession(session)
                }
                networkChanges().distinctUntilChanged().collectLatest { network ->
                    if (network != null) revalidateSession(session)
                }
            }
        }
    }

    private fun networkChanges() = callbackFlow<Pair<Network, Boolean>?> {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(network to capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }

            override fun onLost(network: Network) {
                trySend(null)
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }

    private suspend fun revalidateSession(session: SavedAuthSession) {
        try {
            val response = AuthApiClient.service.currentUser("Bearer ${session.token}")
            val user = response.body()?.user
            if (response.code() == 401 || response.code() == 403 ||
                (user != null && user.id != session.userId)
            ) {
                invalidateSession(session)
            } else if (response.isSuccessful && user != null && sessionStore.updateName(session, user.name)) {
                onProfileUpdated()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A failed request leaves the locally validated session usable until it expires.
        }
    }

    protected open fun onProfileUpdated() = Unit

    private fun invalidateSession(session: SavedAuthSession) {
        if (sessionStore.getSession()?.token != session.token) return
        sessionStore.clear()
        returnToLogin(expired = true)
    }

    protected fun returnToLogin(expired: Boolean = false) {
        if (isFinishing) return
        activeSession = null
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(LoginActivity.EXTRA_SESSION_EXPIRED, expired)
        })
        finish()
    }
}
