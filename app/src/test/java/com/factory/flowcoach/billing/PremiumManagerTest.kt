package com.factory.flowcoach.billing

import app.cash.turbine.test
import com.android.billingclient.api.Purchase
import com.factory.flowcoach.data.prefs.PremiumPreferencesRepository
import com.factory.flowcoach.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PremiumManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val billingManager: BillingManager = mockk(relaxed = true)
    private val preferencesRepository: PremiumPreferencesRepository = mockk(relaxed = true)

    private val purchasesFlow = MutableStateFlow<List<Purchase>>(emptyList())
    private val hasSyncedFlow = MutableStateFlow(false)
    private val cachedSkusFlow = MutableStateFlow<Set<String>>(emptySet())

    // PremiumManager's entitlements StateFlow keeps its WhileSubscribed sharing coroutine alive
    // in this scope indefinitely, so it must live outside runTest's own TestScope - otherwise
    // runTest fails the test with an UncompletedCoroutinesError at the end of every test.
    private lateinit var managerScope: CoroutineScope

    @Before
    fun createManagerScope() {
        managerScope = CoroutineScope(mainDispatcherRule.testDispatcher)
    }

    @After
    fun cancelManagerScope() {
        managerScope.cancel()
    }

    private fun purchaseFor(vararg productIds: String): Purchase = mockk(relaxed = true) {
        every { products } returns productIds.toList()
    }

    private fun createManager() =
        PremiumManager(billingManager, preferencesRepository, managerScope)

    private fun setUpFlows() {
        every { billingManager.purchases } returns purchasesFlow
        every { billingManager.hasSyncedPurchases } returns hasSyncedFlow
        every { preferencesRepository.cachedSkusFlow } returns cachedSkusFlow
        coEvery { preferencesRepository.setCachedSkus(any()) } returns Unit
    }

    @Test
    fun `entitlements reflect live purchases once Play has synced`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setUpFlows()
            purchasesFlow.value = listOf(purchaseFor(PremiumSku.YEARLY.productId))
            hasSyncedFlow.value = true
            val manager = createManager()

            manager.entitlements.test {
                val state = awaitItem()
                assertTrue(state.isPremium)
                assertEquals(PremiumSku.YEARLY, state.activeSubscription)
            }
        }

    @Test
    fun `entitlements fall back to cached skus before Play has synced`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setUpFlows()
            cachedSkusFlow.value = setOf(PremiumSku.LIFETIME.name)
            hasSyncedFlow.value = false
            val manager = createManager()

            manager.entitlements.test {
                val state = awaitItem()
                assertTrue(state.hasLifetime)
                assertTrue(state.isPremium)
            }
        }

    @Test
    fun `entitlements persist resolved skus back to preferences`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setUpFlows()
            purchasesFlow.value = listOf(purchaseFor(PremiumSku.YEARLY.productId))
            hasSyncedFlow.value = true
            val manager = createManager()

            manager.entitlements.test { awaitItem() }

            coVerify { preferencesRepository.setCachedSkus(setOf(PremiumSku.YEARLY.name)) }
        }

    @Test
    fun `entitlements update when a new purchase arrives`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setUpFlows()
            hasSyncedFlow.value = true
            val manager = createManager()

            manager.entitlements.test {
                assertFalse(awaitItem().isPremium)

                purchasesFlow.value = listOf(purchaseFor(PremiumSku.MONTHLY.productId))

                assertTrue(awaitItem().isPremium)
            }
        }

    @Test
    fun `REMOVE_ADS alone removes ads but does not grant premium`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setUpFlows()
            purchasesFlow.value = listOf(purchaseFor(PremiumSku.REMOVE_ADS.productId))
            hasSyncedFlow.value = true
            val manager = createManager()

            manager.entitlements.test {
                val state = awaitItem()
                assertFalse(state.isPremium)
                assertTrue(state.adsRemoved)
            }
        }

    @Test
    fun `restorePurchases returns true when a known sku is found after re-sync`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setUpFlows()
            coEvery { billingManager.queryPurchases() } coAnswers {
                purchasesFlow.value = listOf(purchaseFor(PremiumSku.WEEKLY.productId))
            }
            val manager = createManager()

            val restored = manager.restorePurchases()

            assertTrue(restored)
            coVerify { billingManager.queryPurchases() }
        }

    @Test
    fun `restorePurchases returns false when no entitlement is found`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setUpFlows()
            coEvery { billingManager.queryPurchases() } returns Unit

            val manager = createManager()

            val restored = manager.restorePurchases()

            assertFalse(restored)
        }

    @Test
    fun `markPaywallIntroSeen delegates to preferences repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setUpFlows()
            coEvery { preferencesRepository.setHasSeenPaywallIntro(true) } returns Unit
            val manager = createManager()

            manager.markPaywallIntroSeen()

            coVerify { preferencesRepository.setHasSeenPaywallIntro(true) }
        }

    @Test
    fun `hasSeenPaywallIntro exposes the preferences flow`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setUpFlows()
            val introFlow = MutableStateFlow(true)
            every { preferencesRepository.hasSeenPaywallIntroFlow } returns introFlow
            val manager = createManager()

            manager.hasSeenPaywallIntro.test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
