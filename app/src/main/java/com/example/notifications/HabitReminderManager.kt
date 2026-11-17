package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.concurrent.TimeUnit

private val Context.reminderDataStore: DataStore<Preferences> by preferencesDataStore(name = "chibipet_reminders")

data class ReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = 20,
    val minute: Int = 0
) {
    val formattedTime: String
        get() {
            val h12 = if (hour % 12 == 0) 12 else hour % 12
            val amPm = if (hour < 12) "AM" else "PM"
            return "%d:%02d %s".format(h12, minute, amPm)
        }
}

object HabitReminderManager {
    private const val CHANNEL_ID = "chibipet_habit_reminders"
    private const val WORK_NAME = "chibipet_daily_habit_reminder"
    private const val NOTIFICATION_ID = 4201

    private val KEY_ENABLED = booleanPreferencesKey("reminders_enabled")
    private val KEY_HOUR = intPreferencesKey("reminder_hour")
    private val KEY_MINUTE = intPreferencesKey("reminder_minute")
    private val KEY_PET_NAME = stringPreferencesKey("reminder_pet_name")
    private val KEY_PENDING_COUNT = intPreferencesKey("reminder_pending_count")

    fun observeSettings(context: Context): Flow<ReminderSettings> {
        return context.applicationContext.reminderDataStore.data.map { prefs ->
            ReminderSettings(
                enabled = prefs[KEY_ENABLED] ?: false,
                hour = prefs[KEY_HOUR] ?: 20,
                minute = prefs[KEY_MINUTE] ?: 0
            )
        }
    }

    suspend fun syncHabitSnapshot(context: Context, petName: String, pendingCount: Int) {
        context.applicationContext.reminderDataStore.edit { prefs ->
            prefs[KEY_PET_NAME] = petName
            prefs[KEY_PENDING_COUNT] = pendingCount.coerceAtLeast(0)
        }
    }

    suspend fun setReminderEnabled(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        val appContext = context.applicationContext
        appContext.reminderDataStore.edit { prefs ->
            prefs[KEY_ENABLED] = enabled
            prefs[KEY_HOUR] = hour
            prefs[KEY_MINUTE] = minute
        }
        if (enabled) {
            ensureChannel(appContext)
            scheduleNextDailyWork(appContext, hour, minute)
        } else {
            WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME)
        }
    }

    fun scheduleNextDailyWork(context: Context, hour: Int, minute: Int) {
        val appContext = context.applicationContext
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val delayMs = (next.timeInMillis - now.timeInMillis).coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
        val request = OneTimeWorkRequestBuilder<HabitReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun readSnapshot(context: Context): Triple<ReminderSettings, String, Int> {
        val prefs = context.applicationContext.reminderDataStore.data.first()
        val settings = ReminderSettings(
            enabled = prefs[KEY_ENABLED] ?: false,
            hour = prefs[KEY_HOUR] ?: 20,
            minute = prefs[KEY_MINUTE] ?: 0
        )
        val appContext = context.applicationContext
        val petName = prefs[KEY_PET_NAME] ?: appContext.getString(R.string.default_pet_name)
        val pending = prefs[KEY_PENDING_COUNT] ?: 0
        return Triple(settings, petName, pending)
    }

    fun postPetReminderNotification(context: Context, petName: String, pendingCount: Int): Boolean {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        ensureChannel(appContext)
        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            100,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = if (pendingCount > 0) {
            appContext.getString(R.string.notif_body_pending, petName, pendingCount)
        } else {
            appContext.getString(R.string.notif_body_all_done, petName)
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_chibipet)
            .setContentTitle(appContext.getString(R.string.notif_title, petName))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        return true
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }
}

class HabitReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val (settings, petName, pendingCount) = HabitReminderManager.readSnapshot(applicationContext)
        if (!settings.enabled) return Result.success()

        if (pendingCount > 0) {
            HabitReminderManager.postPetReminderNotification(applicationContext, petName, pendingCount)
        }
        HabitReminderManager.scheduleNextDailyWork(applicationContext, settings.hour, settings.minute)
        return Result.success()
    }
}
