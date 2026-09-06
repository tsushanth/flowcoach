package com.factory.flowcoach.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "flowcoach_prefs")

data class UserPreferences(
    val dailyGoalMl: Int,
    val reminderEnabled: Boolean,
    val reminderIntervalMinutes: Int,
    val cupSizeMl: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val lastStreakDateKey: String
)

class UserPreferencesRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.dataStore
) {

    private object Keys {
        val DAILY_GOAL_ML = intPreferencesKey("daily_goal_ml")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_INTERVAL_MINUTES = intPreferencesKey("reminder_interval_minutes")
        val CUP_SIZE_ML = intPreferencesKey("cup_size_ml")
        val CURRENT_STREAK = intPreferencesKey("current_streak")
        val BEST_STREAK = intPreferencesKey("best_streak")
        val LAST_STREAK_DATE_KEY = stringPreferencesKey("last_streak_date_key")
    }

    companion object {
        const val DEFAULT_GOAL_ML = 2500
        const val DEFAULT_REMINDER_INTERVAL_MINUTES = 60
        const val DEFAULT_CUP_SIZE_ML = 250
    }

    val preferencesFlow: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            dailyGoalMl = prefs[Keys.DAILY_GOAL_ML] ?: DEFAULT_GOAL_ML,
            reminderEnabled = prefs[Keys.REMINDER_ENABLED] ?: true,
            reminderIntervalMinutes = prefs[Keys.REMINDER_INTERVAL_MINUTES]
                ?: DEFAULT_REMINDER_INTERVAL_MINUTES,
            cupSizeMl = prefs[Keys.CUP_SIZE_ML] ?: DEFAULT_CUP_SIZE_ML,
            currentStreak = prefs[Keys.CURRENT_STREAK] ?: 0,
            bestStreak = prefs[Keys.BEST_STREAK] ?: 0,
            lastStreakDateKey = prefs[Keys.LAST_STREAK_DATE_KEY] ?: ""
        )
    }

    suspend fun setDailyGoalMl(goalMl: Int) {
        dataStore.edit { it[Keys.DAILY_GOAL_ML] = goalMl }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderIntervalMinutes(minutes: Int) {
        dataStore.edit { it[Keys.REMINDER_INTERVAL_MINUTES] = minutes }
    }

    suspend fun setCupSizeMl(cupMl: Int) {
        dataStore.edit { it[Keys.CUP_SIZE_ML] = cupMl }
    }

    suspend fun setStreak(current: Int, best: Int, lastStreakDateKey: String) {
        dataStore.edit {
            it[Keys.CURRENT_STREAK] = current
            it[Keys.BEST_STREAK] = best
            it[Keys.LAST_STREAK_DATE_KEY] = lastStreakDateKey
        }
    }
}
