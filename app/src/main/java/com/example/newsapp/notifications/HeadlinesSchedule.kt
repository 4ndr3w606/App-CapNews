package com.example.newsapp.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import androidx.core.app.NotificationManagerCompat
import com.example.newsapp.data.HeadlinesSettings
import com.example.newsapp.data.LocalAuth
import com.example.newsapp.data.PreferencesManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

class HeadlinesAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val accountId = LocalAuth.activeUserId(context)
        when (intent.action) {
            HeadlinesSchedule.ALARM_ACTION -> {
                if (accountId == null || accountId != intent.getLongExtra(HeadlinesSchedule.ACCOUNT, 0)) return
                val alarmTime = intent.getLongExtra(HeadlinesSchedule.SCHEDULED_AT, 0)
                val preferences = PreferencesManager(context, accountId)
                val settings = preferences.headlinesSettings()
                if (!settings.shouldSchedule) return

                HeadlinesSchedule.scheduleNext(context, accountId, settings)
                if (HeadlinesSchedule.canDeliver(alarmTime, preferences) && HeadlineNotification.canPost(context)) {
                    HeadlinesSchedule.enqueueFetch(context, accountId, alarmTime)
                }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                HeadlinesSchedule.cancel(context, clearNotification = false)
                if (accountId != null) HeadlinesSchedule.ensureScheduled(context, accountId)
            }
        }
    }
}

internal object HeadlinesSchedule {
    const val ALARM_ACTION = "com.example.newsapp.action.DAILY_HEADLINES"
    const val ACCOUNT = "headlines_account"
    const val SCHEDULED_AT = "headlines_scheduled_at"
    private const val FETCH_JOB = 101
    private val deliveryWindow = TimeUnit.HOURS.toMillis(4)

    fun ensureScheduled(context: Context, accountId: Long) {
        val settings = PreferencesManager(context, accountId).headlinesSettings()
        if (settings.shouldSchedule && alarmIntent(context, PendingIntent.FLAG_NO_CREATE) == null) {
            scheduleNext(context, accountId, settings)
        }
    }

    fun scheduleNext(context: Context, accountId: Long, settings: HeadlinesSettings) {
        val now = System.currentTimeMillis()
        val next = Calendar.getInstance().apply { timeInMillis = now }
        next.set(Calendar.HOUR_OF_DAY, settings.hour)
        next.set(Calendar.MINUTE, settings.minute)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)
        val lastDay = PreferencesManager(context, accountId).lastHeadlinesDay()
        if (next.timeInMillis <= now || dayOf(next.timeInMillis) == lastDay) {
            next.add(Calendar.DATE, 1)
            next.set(Calendar.HOUR_OF_DAY, settings.hour)
            next.set(Calendar.MINUTE, settings.minute)
        }

        val alarm = checkNotNull(alarmIntent(context, PendingIntent.FLAG_UPDATE_CURRENT, accountId, next.timeInMillis))
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, alarm)
    }

    fun cancel(context: Context, clearNotification: Boolean = true) {
        alarmIntent(context, PendingIntent.FLAG_NO_CREATE)?.let { alarm ->
            context.getSystemService(AlarmManager::class.java).cancel(alarm)
            alarm.cancel()
        }
        context.getSystemService(JobScheduler::class.java).cancel(FETCH_JOB)
        if (clearNotification) NotificationManagerCompat.from(context).cancel(HeadlineNotification.ID)
    }

    fun enqueueFetch(context: Context, accountId: Long, alarmTime: Long) {
        val extras = PersistableBundle().apply {
            putLong(ACCOUNT, accountId)
            putLong(SCHEDULED_AT, alarmTime)
        }
        val request = JobInfo.Builder(FETCH_JOB, ComponentName(context, HeadlinesJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setExtras(extras)
            .build()
        context.getSystemService(JobScheduler::class.java).schedule(request)
    }

    fun canDeliver(alarmTime: Long, preferences: PreferencesManager): Boolean {
        val now = System.currentTimeMillis()
        return now - alarmTime in 0..deliveryWindow &&
            dayOf(alarmTime) == dayOf(now) && preferences.lastHeadlinesDay() != dayOf(now)
    }

    fun dayOf(time: Long): String = Calendar.getInstance().apply { timeInMillis = time }.let {
        "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun alarmIntent(
        context: Context, flags: Int, accountId: Long = 0, time: Long = 0
    ): PendingIntent? = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, HeadlinesAlarmReceiver::class.java).setAction(ALARM_ACTION)
            .putExtra(ACCOUNT, accountId).putExtra(SCHEDULED_AT, time),
        flags or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
    )
}
