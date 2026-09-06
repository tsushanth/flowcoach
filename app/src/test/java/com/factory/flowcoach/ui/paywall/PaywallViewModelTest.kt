package com.factory.flowcoach.ui.paywall

import android.app.Activity
import app.cash.turbine.test
import com.android.billingclient.api.ProductDetails
import com.factory.flowcoach.billing.BillingConnectionState
import com.factory.flowcoach.billing.BillingManager
import com.factory.flowcoach.billing.PremiumEntitlements
import com.factory.flowcoach.billing.PremiumManager
import com.factory.flowcoach.billing.PremiumSku
import com.factory.flowcoach.billing.PurchaseEvent
import com.factory.flowcoach.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PaywallViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val billingManager: BillingManager = mockk(relaxed = true)
    private val premiumManager: PremiumManager = mockk(relaxed = true)

    private val connectionStateFlow =
        MutableStateFlow<BillingConnectionState>(BillingConnectionState.Connecting)
    private val productDetailsFlow = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    private val entitlementsFlow = MutableStateFlow(PremiumEntitlements())
    private val purchaseEventsFlow = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 4)

    private fun createViewModel(): PaywallViewModel {
        every { billingManager.connectionState } returns connectionStateFlow
        every { billingManager.productDetails } returns productDetailsFlow
        every { billingManager.purchaseEvents } returns purchaseEventsFlow
        every { premiumManager.entitlements } returns entitlementsFlow
        return PaywallViewModel(billingManager, premiumManager)
    }

    @Test
    fun `initial state exposes fallback prices and connecting state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()

            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(BillingConnectionState.Connecting, state.connectionState)
                assertEquals(
                    PremiumSku.WEEKLY.fallbackPrice,
                    state.prices.getValue(PremiumSku.WEEKLY).formattedPrice
                )
                assertFalse(state.isRestoring)
            }
        }

    @Test
    fun `retryConnection delegates to the billing manager`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()

            viewModel.retryConnection()

            verify { billingManager.retryConnection() }
        }

    @Test
    fun `purchase emits PurchaseFailed when the billing flow cannot launch`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val activity: Activity = mockk(relaxed = true)
            every { billingManager.launchBillingFlow(activity, PremiumSku.WEEKLY) } returns false
            val viewModel = createViewModel()

            viewModel.events.test {
                viewModel.purchase(activity, PremiumSku.WEEKLY)
                assertTrue(awaitItem() is PaywallUiEvent.PurchaseFailed)
            }
        }

    @Test
    fun `purchase launches the billing flow and emits nothing on success`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val activity: Activity = mockk(relaxed = true)
            every { billingManager.launchBillingFlow(activity, PremiumSku.WEEKLY) } returns true
            val viewModel = createViewModel()

            viewModel.purchase(activity, PremiumSku.WEEKLY)

            verify { billingManager.launchBillingFlow(activity, PremiumSku.WEEKLY) }
        }

    @Test
    fun `billing purchase events are remapped into ui events`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()

            viewModel.events.test {
                purchaseEventsFlow.emit(PurchaseEvent.Purchased(PremiumSku.YEARLY.productId))
                assertEquals(PaywallUiEvent.PurchaseSuccessful, awaitItem())

                purchaseEventsFlow.emit(PurchaseEvent.Cancelled)
                assertEquals(PaywallUiEvent.PurchaseCancelled, awaitItem())

                purchaseEventsFlow.emit(PurchaseEvent.Failed("nope"))
                assertEquals(PaywallUiEvent.PurchaseFailed("nope"), awaitItem())
            }
        }

    @Test
    fun `restorePurchases toggles isRestoring and emits RestoreFinished`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { premiumManager.restorePurchases() } returns true
            val viewModel = createViewModel()

            viewModel.events.test {
                viewModel.restorePurchases()
                assertEquals(PaywallUiEvent.RestoreFinished(true), awaitItem())
            }
            assertFalse(viewModel.uiState.value.isRestoring)
        }

    @Test
    fun `markIntroSeen delegates to premium manager`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { premiumManager.markPaywallIntroSeen() } returns Unit
            val viewModel = createViewModel()

            viewModel.markIntroSeen()

            coVerify { premiumManager.markPaywallIntroSeen() }
        }
}
