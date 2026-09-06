package com.factory.flowcoach.data.repository

import com.factory.flowcoach.data.local.DailyTotal
import com.factory.flowcoach.data.local.WaterDao
import com.factory.flowcoach.data.local.WaterEntry
import com.factory.flowcoach.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateKeys {
    private val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun forMillis(millis: Long): String = formatter.format(Date(millis))

    fun today(): String = forMillis(System.currentTimeMillis())

    fun daysAgo(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return formatter.format(cal.time)
    }

    fun previousDateKey(dateKey: String): String {
        val date = formatter.parse(dateKey) ?: Date()
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return formatter.format(cal.time)
    }

    fun displayLabel(dateKey: String): String {
        val date = formatter.parse(dateKey) ?: return dateKey
        val display = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        return display.format(date)
    }
}

class HydrationRepository(
    private val waterDao: WaterDao,
    private val preferencesRepository: UserPreferencesRepository
) {

    val preferencesFlow = preferencesRepository.preferencesFlow

    fun todayTotalFlow(): Flow<Int> = waterDao.totalForDate(DateKeys.today())

    fun todayEntriesFlow(): Flow<List<WaterEntry>> = waterDao.entriesForDate(DateKeys.today())

    fun historyFlow(days: Int = 30): Flow<List<DailyTotal>> =
        waterDao.dailyTotalsSince(DateKeys.daysAgo(days))

    suspend fun logWater(amountMl: Int) {
        val now = System.currentTimeMillis()
        waterDao.insert(
            WaterEntry(
                amountMl = amountMl,
                timestampEpochMillis = now,
                dateKey = DateKeys.forMillis(now)
            )
        )
    }

    suspend fun deleteEntry(entry: WaterEntry) {
        waterDao.delete(entry)
    }

    suspend fun setDailyGoalMl(goalMl: Int) = preferencesRepository.setDailyGoalMl(goalMl)

    suspend fun setReminderEnabled(enabled: Boolean) =
        preferencesRepository.setReminderEnabled(enabled)

    suspend fun setReminderIntervalMinutes(minutes: Int) =
        preferencesRepository.setReminderIntervalMinutes(minutes)

    suspend fun setCupSizeMl(cupMl: Int) = preferencesRepository.setCupSizeMl(cupMl)

    /**
     * Streak = consecutive days (ending yesterday or today) where total intake met the goal.
     * Recomputed from history rather than trusted incremental state, since entries can be
     * deleted or logged out of order.
     */
    suspend fun recomputeStreak(goalMl: Int): Pair<Int, Int> {
        val totals = mutableMapOf<String, Int>()
        val allTotals = collectAllTotals()
        allTotals.forEach { totals[it.dateKey] = it.totalMl }

        var cursor = DateKeys.today()
        if ((totals[cursor] ?: 0) < goalMl) {
            // Today isn't finished yet; don't let an in-progress day zero out an active streak.
            cursor = DateKeys.previousDateKey(cursor)
        }
        var current = 0
        var checking = true

        while (checking) {
            val total = totals[cursor] ?: 0
            if (total >= goalMl) {
                current++
                cursor = DateKeys.previousDateKey(cursor)
            } else {
                checking = false
            }
        }

        val previousBest = preferencesRepository.preferencesFlow.first().bestStreak
        val best = maxOf(current, previousBest)
        val lastStreakDateKey = if (current > 0) DateKeys.today() else ""
        preferencesRepository.setStreak(current, best, lastStreakDateKey)
        return current to best
    }

    private suspend fun collectAllTotals(): List<DailyTotal> = waterDao.allDailyTotals().first()
}
