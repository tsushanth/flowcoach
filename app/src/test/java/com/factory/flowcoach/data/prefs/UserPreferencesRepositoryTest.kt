package com.factory.flowcoach.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.factory.flowcoach.testutil.MainDispatcherRule
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Uses a fresh, temp-file-backed [androidx.datastore.core.DataStore] per test instead of the real
 * `Context.dataStore` singleton - Jetpack DataStore's `preferencesDataStore` delegate caches one
 * instance for the whole process regardless of which [Context] requests it, so reusing it here
 * would leak state between test methods.
 */
class UserPreferencesRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun repository(): UserPreferencesRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(mainDispatcherRule.testDispatcher),
            produceFile = { File(tempFolder.newFolder(), "test.preferences_pb") }
        )
        // context is unused here since dataStore is supplied explicitly, but the constructor
        // parameter requires a non-null Context.
        return UserPreferencesRepository(context = mockk<Context>(relaxed = true), dataStore = dataStore)
    }

    @Test
    fun `preferencesFlow emits defaults before anything is written`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repo = repository()

            repo.preferencesFlow.test {
                val prefs = awaitItem()
                assertEquals(UserPreferencesRepository.DEFAULT_GOAL_ML, prefs.dailyGoalMl)
                assertTrue(prefs.reminderEnabled)
                assertEquals(
                    UserPreferencesRepository.DEFAULT_REMINDER_INTERVAL_MINUTES,
                    prefs.reminderIntervalMinutes
                )
                assertEquals(UserPreferencesRepository.DEFAULT_CUP_SIZE_ML, prefs.cupSizeMl)
                assertEquals(0, prefs.currentStreak)
                assertEquals(0, prefs.bestStreak)
                assertEquals("", prefs.lastStreakDateKey)
            }
        }

    @Test
    fun `setDailyGoalMl persists and is reflected in preferencesFlow`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repo = repository()

            repo.preferencesFlow.test {
                assertEquals(UserPreferencesRepository.DEFAULT_GOAL_ML, awaitItem().dailyGoalMl)

                repo.setDailyGoalMl(3200)

                assertEquals(3200, awaitItem().dailyGoalMl)
            }
        }

    @Test
    fun `setReminderEnabled persists and is reflected in preferencesFlow`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repo = repository()

            repo.preferencesFlow.test {
                assertTrue(awaitItem().reminderEnabled)

                repo.setReminderEnabled(false)

                assertFalse(awaitItem().reminderEnabled)
            }
        }

    @Test
    fun `setReminderIntervalMinutes persists and is reflected in preferencesFlow`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repo = repository()

            repo.preferencesFlow.test {
                awaitItem()

                repo.setReminderIntervalMinutes(120)

                assertEquals(120, awaitItem().reminderIntervalMinutes)
            }
        }

    @Test
    fun `setCupSizeMl persists and is reflected in preferencesFlow`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repo = repository()

            repo.preferencesFlow.test {
                awaitItem()

                repo.setCupSizeMl(400)

                assertEquals(400, awaitItem().cupSizeMl)
            }
        }

    @Test
    fun `setStreak persists current, best and last date key together`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repo = repository()

            repo.preferencesFlow.test {
                awaitItem()

                repo.setStreak(current = 5, best = 7, lastStreakDateKey = "2026-09-06")

                val prefs = awaitItem()
                assertEquals(5, prefs.currentStreak)
                assertEquals(7, prefs.bestStreak)
                assertEquals("2026-09-06", prefs.lastStreakDateKey)
            }
        }
}
