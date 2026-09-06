package com.factory.flowcoach.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.factory.flowcoach.billing.PremiumManager
import com.factory.flowcoach.data.local.WaterEntry
import com.factory.flowcoach.data.prefs.UserPreferencesRepository
import com.factory.flowcoach.data.repository.HydrationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val todayTotalMl: Int = 0,
    val dailyGoalMl: Int = UserPreferencesRepository.DEFAULT_GOAL_ML,
    val cupSizeMl: Int = UserPreferencesRepository.DEFAULT_CUP_SIZE_ML,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val todayEntries: List<WaterEntry> = emptyList(),
    val isPremium: Boolean = false,
    val adsRemoved: Boolean = false
) {
    val progress: Float
        get() = if (dailyGoalMl <= 0) 0f else (todayTotalMl.toFloat() / dailyGoalMl).coerceIn(0f, 1f)

    val goalReached: Boolean
        get() = todayTotalMl >= dailyGoalMl
}

class HomeViewModel(
    private val repository: HydrationRepository,
    premiumManager: PremiumManager
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        repository.todayTotalFlow(),
        repository.preferencesFlow,
        repository.todayEntriesFlow(),
        premiumManager.entitlements
    ) { total, prefs, entries, entitlements ->
        HomeUiState(
            todayTotalMl = total,
            dailyGoalMl = prefs.dailyGoalMl,
            cupSizeMl = prefs.cupSizeMl,
            currentStreak = prefs.currentStreak,
            bestStreak = prefs.bestStreak,
            todayEntries = entries,
            isPremium = entitlements.isPremium,
            adsRemoved = entitlements.adsRemoved
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun logWater(amountMl: Int) {
        viewModelScope.launch {
            repository.logWater(amountMl)
            val goal = uiState.value.dailyGoalMl
            repository.recomputeStreak(goal)
        }
    }

    fun deleteEntry(entry: WaterEntry) {
        viewModelScope.launch {
            repository.deleteEntry(entry)
            val goal = uiState.value.dailyGoalMl
            repository.recomputeStreak(goal)
        }
    }
}
