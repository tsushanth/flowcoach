package com.factory.flowcoach.ui.history

import app.cash.turbine.test
import com.factory.flowcoach.billing.PremiumEntitlements
import com.factory.flowcoach.billing.PremiumManager
import com.factory.flowcoach.billing.PremiumSku
import com.factory.flowcoach.data.local.DailyTotal
import com.factory.flowcoach.data.prefs.UserPreferences
import com.factory.flowcoach.data.repository.HydrationRepository
import com.factory.flowcoach.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: HydrationRepository = mockk(relaxed = true)
    private val premiumManager: PremiumManager = mockk(relaxed = true)

    private val historyFlow = MutableStateFlow<List<DailyTotal>>(emptyList())
    private val prefsFlow = MutableStateFlow(
        UserPreferences(
            dailyGoalMl = 2500,
            reminderEnabled = true,
            reminderIntervalMinutes = 60,
            cupSizeMl = 250,
            currentStreak = 0,
            bestStreak = 0,
            lastStreakDateKey = ""
        )
    )
    private val entitlementsFlow = MutableStateFlow(PremiumEntitlements())

    private fun thirtyDaysOfTotals() = (1..30).map { DailyTotal(dateKey = "day-$it", totalMl = 1000) }

    private fun createViewModel(): HistoryViewModel {
        every { repository.historyFlow(HistoryUiState.PREMIUM_DAYS_VISIBLE) } returns historyFlow
        every { repository.preferencesFlow } returns prefsFlow
        every { premiumManager.entitlements } returns entitlementsFlow
        return HistoryViewModel(repository, premiumManager)
    }

    @Test
    fun `free users only see the most recent 7 days`() =
        runTest(mainDispatcherRule.testDispatcher) {
            historyFlow.value = thirtyDaysOfTotals()
            val viewModel = createViewModel()

            viewModel.uiState.test {
                val state = awaitItem()
                assertFalse(state.isPremium)
                assertEquals(HistoryUiState.FREE_DAYS_VISIBLE, state.visibleTotals.size)
                assertEquals(30 - HistoryUiState.FREE_DAYS_VISIBLE, state.hiddenDaysCount)
            }
        }

    @Test
    fun `premium users see the full history with nothing hidden`() =
        runTest(mainDispatcherRule.testDispatcher) {
            historyFlow.value = thirtyDaysOfTotals()
            entitlementsFlow.value = PremiumEntitlements(setOf(PremiumSku.LIFETIME))
            val viewModel = createViewModel()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state.isPremium)
                assertEquals(30, state.visibleTotals.size)
                assertEquals(0, state.hiddenDaysCount)
            }
        }

    @Test
    fun `state updates when new history arrives`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                assertEquals(0, awaitItem().dailyTotals.size)

                historyFlow.value = listOf(DailyTotal("2026-09-06", 1200))

                assertEquals(1, awaitItem().dailyTotals.size)
            }
        }
}
