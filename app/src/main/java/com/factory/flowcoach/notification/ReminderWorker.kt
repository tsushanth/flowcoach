package com.factory.flowcoach.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.factory.flowcoach.MainActivity
import com.factory.flowcoach.R
import com.factory.flowcoach.data.local.AppDatabase
import com.factory.flowcoach.data.prefs.UserPreferencesRepository
import com.factory.flowcoach.data.repository.HydrationRepository
import kotlinx.coroutines.flow.first

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val prefsRepo = UserPreferencesRepository(appContext)
        val prefs = prefsRepo.preferencesFlow.first()

        if (!prefs.reminderEnabled) {
            return Result.success()
        }

        val dao = AppDatabase.getInstance(appContext).waterDao()
        val repository = HydrationRepository(dao, prefsRepo)
        val todayTotal = repository.todayTotalFlow().first()

        if (todayTotal < prefs.dailyGoalMl) {
            showNotification(appContext)
        }

        return Result.success()
    }

    private fun showNotification(context: Context) {
        val channelId = "hydration_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        androidx.core.app.NotificationManagerCompat.from(context)
            .notify(REMINDER_NOTIFICATION_ID, notification)
    }

    companion object {
        const val REMINDER_NOTIFICATION_ID = 1001
    }
}
