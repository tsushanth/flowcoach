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
 * `Context.premiumDataStore` singleton - Jetpack DataStore's `preferencesDataStore` delegate
 * caches one instance for the whole process regardless of which [Context] requests it, so reusing
 * it here would leak state between test methods.
 */
class PremiumPreferencesRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun repository(): PremiumPreferencesRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(mainDispatcherRule.testDispatcher),
            produceFile = { File(tempFolder.newFolder(), "test.preferences_pb") }
        )
        // context is unused here since dataStore is supplied explicitly, but the constructor
        // parameter requires a non-null Context.
        return PremiumPreferencesRepository(context = mockk<Context>(relaxed = true), dataStore = dataStore)
    }

    @Test
    fun `cachedSkusFlow is empty before anything is written`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repo = repository()

            repo.cachedSkusFlow.test {
                assertTrue(awaitItem().isEmpty())
            }
        }

    @Test
    fun `setCachedSkus persists and is reflected in cachedSkusFlow`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repo = repository()

            repo.cachedSkusFlow.test {
                assertTrue(awaitItem().isEmpty())

                repo.setCachedSkus(setOf("YEARLY", "REMOVE_ADS"))

                assertEquals(setOf("YEARLY", "REMOVE_ADS"), awaitItem())
            }
        }

    @Test
    fun `setCachedSkus overwrites the previous cached set rather than merging`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repo = repository()
            repo.setCachedSkus(setOf("MONTHLY"))

            repo.cachedSkusFlow.test {
                assertEquals(setOf("MONTHLY"), awaitItem())

                repo.setCachedSkus(setOf("LIFETIME"))

                assertEquals(setOf("LIFETIME"), awaitItem())
            }
        }

    @Test
    fun `hasSeenPaywallIntroFlow defaults to false`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repo = repository()

            repo.hasSeenPaywallIntroFlow.test {
                assertFalse(awaitItem())
            }
        }

    @Test
    fun `setHasSeenPaywallIntro persists and is reflected in hasSeenPaywallIntroFlow`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repo = repository()

            repo.hasSeenPaywallIntroFlow.test {
                assertFalse(awaitItem())

                repo.setHasSeenPaywallIntro(true)

                assertTrue(awaitItem())
            }
        }
}
