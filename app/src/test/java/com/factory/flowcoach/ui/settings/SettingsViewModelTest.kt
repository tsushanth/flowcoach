package com.factory.flowcoach.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.turbine.test
import com.factory.flowcoach.billing.PremiumEntitlements
import com.factory.flowcoach.billing.PremiumManager
import com.factory.flowcoach.billing.PremiumSku
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val REMINDER_WORK_NAME = "flowcoach_hydration_reminder"

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository: HydrationRepository = mockk(relaxed = true)
    private val premiumManager: PremiumManager = mockk(relaxed = true)

    private val prefsFlow = MutableStateFlow(defaultPrefs())
    private val entitlementsFlow = MutableStateFlow(PremiumEntitlements())

    private fun defaultPrefs(
        reminderEnabled: Boolean = true,
        reminderIntervalMinutes: Int = 60
    ) = UserPreferences(
        dailyGoalMl = 2500,
        reminderEnabled = reminderEnabled,
        reminderIntervalMinutes = reminderIntervalMinutes,
        cupSizeMl = 250,
        currentStreak = 0,
        bestStreak = 0,
        lastStreakDateKey = ""
    )

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private fun createViewModel(): SettingsViewModel {
        every { repository.preferencesFlow } returns prefsFlow
        every { premiumManager.entitlements } returns entitlementsFlow
        return SettingsViewModel(repository, context, premiumManager)
    }

    private fun scheduledWorkExists(): Boolean {
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(REMINDER_WORK_NAME).get()
        return infos.any { it.state != WorkInfo.State.CANCELLED }
    }

    @Test
    fun `initial state reflects preferences and entitlements`() =
        runTest(mainDispatcherRule.testDispatcher) {
            entitlementsFlow.value = PremiumEntitlements(setOf(PremiumSku.MONTHLY))
            val viewModel = createViewModel()

            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(2500, state.prefs.dailyGoalMl)
                assertTrue(state.isPremium)
                assertEquals(PremiumSku.MONTHLY, state.activeSubscription)
            }
        }

    @Test
    fun `setDailyGoal persists the goal and recomputes streak`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { repository.setDailyGoalMl(any()) } returns Unit
            coEvery { repository.recomputeStreak(any()) } returns (0 to 0)
            val viewModel = createViewModel()
            viewModel.uiState.test { awaitItem() }

            viewModel.setDailyGoal(3000)

            coVerify { repository.setDailyGoalMl(3000) }
            coVerify { repository.recomputeStreak(3000) }
        }

    @Test
    fun `setCupSize persists the cup size`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { repository.setCupSizeMl(any()) } returns Unit
            val viewModel = createViewModel()

            viewModel.setCupSize(300)

            coVerify { repository.setCupSizeMl(300) }
        }

    @Test
    fun `enabling the reminder persists it and schedules work`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { repository.setReminderEnabled(true) } returns Unit
            val viewModel = createViewModel()
            viewModel.uiState.test { awaitItem() }

            viewModel.setReminderEnabled(true)

            coVerify { repository.setReminderEnabled(true) }
            assertTrue(scheduledWorkExists())
        }

    @Test
    fun `disabling the reminder persists it and cancels work`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { repository.setReminderEnabled(any()) } returns Unit
            val viewModel = createViewModel()
            viewModel.uiState.test { awaitItem() }
            viewModel.setReminderEnabled(true)

            viewModel.setReminderEnabled(false)

            coVerify { repository.setReminderEnabled(false) }
            val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(REMINDER_WORK_NAME).get()
            assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
        }

    @Test
    fun `changing the interval reschedules only when the reminder is enabled`() =
        runTest(mainDispatcherRule.testDispatcher) {
            prefsFlow.value = defaultPrefs(reminderEnabled = false)
            coEvery { repository.setReminderIntervalMinutes(any()) } returns Unit
            val viewModel = createViewModel()
            viewModel.uiState.test { awaitItem() }

            viewModel.setReminderIntervalMinutes(90)

            coVerify { repository.setReminderIntervalMinutes(90) }
            assertTrue(
                WorkManager.getInstance(context).getWorkInfosForUniqueWork(REMINDER_WORK_NAME).get().isEmpty()
            )
        }
}
