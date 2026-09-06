package com.factory.flowcoach.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.factory.flowcoach.billing.PremiumManager
import com.factory.flowcoach.billing.PremiumSku
import com.factory.flowcoach.data.prefs.UserPreferences
import com.factory.flowcoach.data.repository.HydrationRepository
import com.factory.flowcoach.notification.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val prefs: UserPreferences = UserPreferences(
        dailyGoalMl = 2500,
        reminderEnabled = true,
        reminderIntervalMinutes = 60,
        cupSizeMl = 250,
        currentStreak = 0,
        bestStreak = 0,
        lastStreakDateKey = ""
    ),
    val isPremium: Boolean = false,
    val activeSubscription: PremiumSku? = null
)

class SettingsViewModel(
    private val repository: HydrationRepository,
    private val appContext: Context,
    premiumManager: PremiumManager
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.preferencesFlow,
        premiumManager.entitlements
    ) { prefs, entitlements ->
        SettingsUiState(
            prefs = prefs,
            isPremium = entitlements.isPremium,
            activeSubscription = entitlements.activeSubscription
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setDailyGoal(goalMl: Int) {
        viewModelScope.launch {
            repository.setDailyGoalMl(goalMl)
            repository.recomputeStreak(goalMl)
        }
    }

    fun setCupSize(cupMl: Int) {
        viewModelScope.launch { repository.setCupSizeMl(cupMl) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setReminderEnabled(enabled)
            if (enabled) {
                ReminderScheduler.schedule(appContext, uiState.value.prefs.reminderIntervalMinutes)
            } else {
                ReminderScheduler.cancel(appContext)
            }
        }
    }

    fun setReminderIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            repository.setReminderIntervalMinutes(minutes)
            if (uiState.value.prefs.reminderEnabled) {
                ReminderScheduler.schedule(appContext, minutes)
            }
        }
    }
}
