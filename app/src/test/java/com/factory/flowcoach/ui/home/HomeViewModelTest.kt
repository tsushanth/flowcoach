package com.factory.flowcoach.ui.home

import app.cash.turbine.test
import com.factory.flowcoach.billing.PremiumEntitlements
import com.factory.flowcoach.billing.PremiumManager
import com.factory.flowcoach.billing.PremiumSku
import com.factory.flowcoach.data.local.WaterEntry
import com.factory.flowcoach.data.prefs.UserPreferences
import com.factory.flowcoach.data.repository.HydrationRepository
import com.factory.flowcoach.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: HydrationRepository = mockk(relaxed = true)
    private val premiumManager: PremiumManager = mockk(relaxed = true)

    private val totalFlow = MutableStateFlow(0)
    private val prefsFlow = MutableStateFlow(defaultPrefs())
    private val entriesFlow = MutableStateFlow<List<WaterEntry>>(emptyList())
    private val entitlementsFlow = MutableStateFlow(PremiumEntitlements())

    private fun defaultPrefs() = UserPreferences(
        dailyGoalMl = 2500,
        reminderEnabled = true,
        reminderIntervalMinutes = 60,
        cupSizeMl = 250,
        currentStreak = 0,
        bestStreak = 0,
        lastStreakDateKey = ""
    )

    private fun createViewModel(): HomeViewModel {
        every { repository.todayTotalFlow() } returns totalFlow
        every { repository.preferencesFlow } returns prefsFlow
        every { repository.todayEntriesFlow() } returns entriesFlow
        every { premiumManager.entitlements } returns entitlementsFlow
        return HomeViewModel(repository, premiumManager)
    }

    @Test
    fun `initial state reflects repository and entitlements`() =
        runTest(mainDispatcherRule.testDispatcher) {
            totalFlow.value = 500
            val viewModel = createViewModel()

            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(500, state.todayTotalMl)
                assertEquals(2500, state.dailyGoalMl)
                assertFalse(state.isPremium)
            }
        }

    @Test
    fun `isPremium and adsRemoved reflect entitlements`() =
        runTest(mainDispatcherRule.testDispatcher) {
            entitlementsFlow.value = PremiumEntitlements(setOf(PremiumSku.YEARLY))
            val viewModel = createViewModel()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state.isPremium)
                assertTrue(state.adsRemoved)
            }
        }

    @Test
    fun `logWater logs the entry and recomputes streak using the current goal`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { repository.logWater(any()) } returns Unit
            coEvery { repository.recomputeStreak(any()) } returns (1 to 1)
            val viewModel = createViewModel()
            viewModel.uiState.test { awaitItem() }

            viewModel.logWater(250)

            coVerify { repository.logWater(250) }
            coVerify { repository.recomputeStreak(2500) }
        }

    @Test
    fun `deleteEntry deletes then recomputes streak`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val entry = WaterEntry(id = 1, amountMl = 250, timestampEpochMillis = 0, dateKey = "2026-09-06")
            coEvery { repository.deleteEntry(entry) } returns Unit
            coEvery { repository.recomputeStreak(any()) } returns (0 to 1)
            val viewModel = createViewModel()
            viewModel.uiState.test { awaitItem() }

            viewModel.deleteEntry(entry)

            coVerify { repository.deleteEntry(entry) }
            coVerify { repository.recomputeStreak(2500) }
        }

    @Test
    fun `progress and goalReached are derived from total and goal`() =
        runTest(mainDispatcherRule.testDispatcher) {
            totalFlow.value = 2500
            val viewModel = createViewModel()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state.progress == 1f)
                assertTrue(state.goalReached)
            }
        }

    @Test
    fun `progress is coerced and goal is not reached below the target`() =
        runTest(mainDispatcherRule.testDispatcher) {
            totalFlow.value = 100
            val viewModel = createViewModel()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state.progress < 1f)
                assertFalse(state.goalReached)
            }
        }
}
