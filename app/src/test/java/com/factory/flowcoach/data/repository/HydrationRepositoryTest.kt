package com.factory.flowcoach.data.repository

import com.factory.flowcoach.data.local.DailyTotal
import com.factory.flowcoach.data.local.WaterDao
import com.factory.flowcoach.data.local.WaterEntry
import com.factory.flowcoach.data.prefs.UserPreferences
import com.factory.flowcoach.data.prefs.UserPreferencesRepository
import com.factory.flowcoach.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HydrationRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val waterDao: WaterDao = mockk(relaxed = true)
    private val preferencesRepository: UserPreferencesRepository = mockk(relaxed = true)

    private fun repository() = HydrationRepository(waterDao, preferencesRepository)

    private fun prefs(bestStreak: Int = 0) = UserPreferences(
        dailyGoalMl = 2500,
        reminderEnabled = true,
        reminderIntervalMinutes = 60,
        cupSizeMl = 250,
        currentStreak = 0,
        bestStreak = bestStreak,
        lastStreakDateKey = ""
    )

    @Test
    fun `logWater inserts an entry stamped with today's date key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val entrySlot = slot<WaterEntry>()
            coEvery { waterDao.insert(capture(entrySlot)) } returns 1L
            val repo = repository()

            repo.logWater(350)

            assertEquals(350, entrySlot.captured.amountMl)
            assertEquals(DateKeys.today(), entrySlot.captured.dateKey)
        }

    @Test
    fun `deleteEntry delegates to the dao`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val entry = WaterEntry(id = 1, amountMl = 250, timestampEpochMillis = 0, dateKey = "2026-09-06")
            coEvery { waterDao.delete(entry) } returns Unit
            val repo = repository()

            repo.deleteEntry(entry)

            coVerify { waterDao.delete(entry) }
        }

    @Test
    fun `setDailyGoalMl delegates to the preferences repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { preferencesRepository.setDailyGoalMl(3000) } returns Unit
            val repo = repository()

            repo.setDailyGoalMl(3000)

            coVerify { preferencesRepository.setDailyGoalMl(3000) }
        }

    @Test
    fun `recomputeStreak counts consecutive days meeting the goal ending today`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val totals = listOf(
                DailyTotal(DateKeys.today(), 3000),
                DailyTotal(DateKeys.daysAgo(1), 3000)
            )
            every { waterDao.allDailyTotals() } returns flowOf(totals)
            every { preferencesRepository.preferencesFlow } returns flowOf(prefs(bestStreak = 1))
            coEvery { preferencesRepository.setStreak(any(), any(), any()) } returns Unit
            val repo = repository()

            val (current, best) = repo.recomputeStreak(2500)

            assertEquals(2, current)
            assertEquals(2, best)
            coVerify { preferencesRepository.setStreak(2, 2, DateKeys.today()) }
        }

    @Test
    fun `recomputeStreak does not let an in-progress today zero out the streak`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val totals = listOf(
                DailyTotal(DateKeys.today(), 500),
                DailyTotal(DateKeys.daysAgo(1), 3000),
                DailyTotal(DateKeys.daysAgo(2), 3000)
            )
            every { waterDao.allDailyTotals() } returns flowOf(totals)
            every { preferencesRepository.preferencesFlow } returns flowOf(prefs(bestStreak = 0))
            coEvery { preferencesRepository.setStreak(any(), any(), any()) } returns Unit
            val repo = repository()

            val (current, _) = repo.recomputeStreak(2500)

            assertEquals(2, current)
        }

    @Test
    fun `recomputeStreak resets to zero when the goal was never met`() =
        runTest(mainDispatcherRule.testDispatcher) {
            every { waterDao.allDailyTotals() } returns flowOf(emptyList())
            every { preferencesRepository.preferencesFlow } returns flowOf(prefs(bestStreak = 4))
            coEvery { preferencesRepository.setStreak(any(), any(), any()) } returns Unit
            val repo = repository()

            val (current, best) = repo.recomputeStreak(2500)

            assertEquals(0, current)
            assertEquals(4, best)
            coVerify { preferencesRepository.setStreak(0, 4, "") }
        }
}
