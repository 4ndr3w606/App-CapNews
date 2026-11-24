package com.example.newsapp.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.newsapp.MainActivity
import com.example.newsapp.R
import com.example.newsapp.data.LocalAuth
import com.example.newsapp.data.PreferencesManager
import com.example.newsapp.data.api.InstanciaRetrofit
import com.example.newsapp.data.headlineCategories
import com.google.gson.JsonParseException
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class HeadlinesJobService : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val accountId = params.extras.getLong(HeadlinesSchedule.ACCOUNT)
        if (LocalAuth.activeUserId(this) != accountId) return false
        val scheduledAt = params.extras.getLong(HeadlinesSchedule.SCHEDULED_AT)
        val preferences = PreferencesManager(this, accountId)
        val settings = preferences.headlinesSettings()
        if (!settings.shouldSchedule || !HeadlineNotification.canPost(this) ||
            !HeadlinesSchedule.canDeliver(scheduledAt, preferences)
        ) return false

        running?.cancel()
        running = CoroutineScope(Dispatchers.Main).launch {
            try {
                val network = if (Build.VERSION.SDK_INT >= 28) params.network else null
                val api = InstanciaRetrofit.create(network)
                val headlines = coroutineScope {
                    headlineCategories.filterKeys { it in settings.categories }.map { (category, label) ->
                        async {
                            val title = withTimeoutOrNull(20_000) {
                                try {
                                    val response = api.getTopHeadlines(category)
                                    response.body()?.articles.orEmpty().firstOrNull { !it.title.isNullOrBlank() }?.title
                                } catch (_: IOException) {
                                    null
                                } catch (_: JsonParseException) {
                                    null
                                }
                            }
                            title?.let { getString(R.string.headlines_line, getString(label), it) }
                        }
                    }.awaitAll().filterNotNull()
                }
                if (headlines.isNotEmpty() && LocalAuth.activeUserId(this@HeadlinesJobService) == accountId &&
                    preferences.headlinesSettings() == settings && HeadlinesSchedule.canDeliver(scheduledAt, preferences) &&
                    HeadlineNotification.post(this@HeadlinesJobService, headlines)
                ) {
                    preferences.markHeadlinesDelivered(HeadlinesSchedule.dayOf(scheduledAt))
                }
            } finally {
                if (isActive) jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }

    override fun onDestroy() {
        running?.cancel()
        super.onDestroy()
    }
}

internal object HeadlineNotification {
    const val ID = 102
    private const val CHANNEL = "daily_headlines"

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
    }

    fun post(context: Context, headlines: List<String>): Boolean {
        if (!canPost(context)) return false
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.headlines_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val openApp = PendingIntent.getActivity(
            context, ID, Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_headlines)
            .setContentTitle(context.getString(R.string.headlines_notification_title))
            .setContentText(headlines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(headlines.joinToString("\n")))
            .setContentIntent(openApp)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
