package com.factory.flowcoach.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.factory.flowcoach.billing.PremiumManager
import com.factory.flowcoach.data.local.DailyTotal
import com.factory.flowcoach.data.repository.HydrationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val dailyTotals: List<DailyTotal> = emptyList(),
    val dailyGoalMl: Int = 2500,
    val isPremium: Boolean = false
) {
    companion object {
        const val FREE_DAYS_VISIBLE = 7
        const val PREMIUM_DAYS_VISIBLE = 30
    }

    // dailyTotals is ordered most-recent-first, so free users see their most recent week.
    val visibleTotals: List<DailyTotal>
        get() = if (isPremium) dailyTotals else dailyTotals.take(FREE_DAYS_VISIBLE)

    val hiddenDaysCount: Int
        get() = (dailyTotals.size - visibleTotals.size).coerceAtLeast(0)
}

class HistoryViewModel(
    repository: HydrationRepository,
    premiumManager: PremiumManager
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.historyFlow(HistoryUiState.PREMIUM_DAYS_VISIBLE),
        repository.preferencesFlow,
        premiumManager.entitlements
    ) { totals, prefs, entitlements ->
        HistoryUiState(
            dailyTotals = totals,
            dailyGoalMl = prefs.dailyGoalMl,
            isPremium = entitlements.isPremium
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )
}
