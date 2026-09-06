package com.factory.flowcoach.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    private const val WORK_NAME = "flowcoach_hydration_reminder"
    private const val MIN_INTERVAL_MINUTES = 15L

    fun schedule(context: Context, intervalMinutes: Int) {
        val interval = intervalMinutes.toLong().coerceAtLeast(MIN_INTERVAL_MINUTES)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(
            interval, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
