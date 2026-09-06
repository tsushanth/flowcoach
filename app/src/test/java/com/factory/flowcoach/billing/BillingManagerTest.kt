package com.factory.flowcoach.billing

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResult
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.factory.flowcoach.testutil.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs under Robolectric because [BillingClient]'s param builders (e.g. [BillingFlowParams])
 * touch real Android framework classes like `TextUtils` internally, which plain JVM unit tests
 * can't provide.
 */
@RunWith(RobolectricTestRunner::class)
class BillingManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = mockk(relaxed = true)
    private lateinit var billingClient: BillingClient
    private lateinit var capturedListener: PurchasesUpdatedListener

    @Before
    fun setUp() {
        mockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
    }

    @After
    fun tearDown() {
        unmockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
    }

    private fun createManager(): BillingManager {
        billingClient = mockk(relaxed = true)
        return BillingManager(context) { listener ->
            capturedListener = listener
            billingClient
        }
    }

    private fun billingResult(code: Int = BillingClient.BillingResponseCode.OK, message: String = "") =
        BillingResult.newBuilder().setResponseCode(code).setDebugMessage(message).build()

    private fun productDetails(productId: String): ProductDetails = mockk(relaxed = true) {
        every { this@mockk.productId } returns productId
        every { subscriptionOfferDetails } returns emptyList()
    }

    private fun purchase(
        products: List<String>,
        state: Int = Purchase.PurchaseState.PURCHASED,
        acknowledged: Boolean = true,
        token: String = "token"
    ): Purchase = mockk(relaxed = true) {
        every { this@mockk.products } returns products
        every { purchaseState } returns state
        every { isAcknowledged } returns acknowledged
        every { purchaseToken } returns token
    }

    // ---- startConnection ----

    @Test
    fun `startConnection sets Connected and refreshes products and purchases on success`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val manager = createManager()
            every { billingClient.isReady } returns false
            val listenerSlot = slot<BillingClientStateListener>()
            every { billingClient.startConnection(capture(listenerSlot)) } answers {
                listenerSlot.captured.onBillingSetupFinished(billingResult())
            }
            coEvery { billingClient.queryProductDetails(any()) } returns
                ProductDetailsResult(billingResult(), emptyList())
            coEvery { billingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) } returns
                PurchasesResult(billingResult(), emptyList())

            manager.startConnection()
            advanceUntilIdle()

            assertEquals(BillingConnectionState.Connected, manager.connectionState.value)
            coVerify(exactly = 2) { billingClient.queryProductDetails(any()) }
            coVerify(exactly = 2) { billingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) }
        }

    @Test
    fun `startConnection sets Unavailable with message on failure`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val manager = createManager()
            every { billingClient.isReady } returns false
            every { billingClient.startConnection(any()) } answers {
                firstArg<BillingClientStateListener>().onBillingSetupFinished(
                    billingResult(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE, "no billing")
                )
            }

            manager.startConnection()
            advanceUntilIdle()

            val state = manager.connectionState.value
            assertTrue(state is BillingConnectionState.Unavailable)
            assertEquals("no billing", (state as BillingConnectionState.Unavailable).message)
        }

    @Test
    fun `startConnection sets Disconnected when service disconnects`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val manager = createManager()
            every { billingClient.isReady } returns false
            val listenerSlot = slot<BillingClientStateListener>()
            every { billingClient.startConnection(capture(listenerSlot)) } just Runs

            manager.startConnection()
            listenerSlot.captured.onBillingServiceDisconnected()

            assertEquals(BillingConnectionState.Disconnected, manager.connectionState.value)
        }

    @Test
    fun `startConnection is a no-op when the client is already ready`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val manager = createManager()
            every { billingClient.isReady } returns true

            manager.startConnection()

            assertEquals(BillingConnectionState.Disconnected, manager.connectionState.value)
            verify(exactly = 0) { billingClient.startConnection(any()) }
        }

    // ---- product loading ----

    @Test
    fun `queryProductDetails merges SUBS and INAPP product results`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val manager = createManager()
            val weekly = productDetails(PremiumSku.WEEKLY.productId)
            val lifetime = productDetails(PremiumSku.LIFETIME.productId)
            coEvery { billingClient.queryProductDetails(any()) } returnsMany listOf(
                ProductDetailsResult(billingResult(), listOf(weekly)),
                ProductDetailsResult(billingResult(), listOf(lifetime))
            )

            manager.queryProductDetails()

            val details = manager.productDetails.value
            assertEquals(2, details.size)
            assertTrue(details.containsKey(PremiumSku.WEEKLY.productId))
            assertTrue(details.containsKey(PremiumSku.LIFETIME.productId))
        }

    // ---- restore / purchase sync ----

    @Test
    fun `queryPurchases syncs valid purchases and acknowledges unacknowledged ones`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val manager = createManager()
            val unacknowledged = purchase(listOf(PremiumSku.MONTHLY.productId), acknowledged = false)
            coEvery { billingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) } returnsMany listOf(
                PurchasesResult(billingResult(), listOf(unacknowledged)),
                PurchasesResult(billingResult(), emptyList())
            )
            coEvery { billingClient.acknowledgePurchase(any()) } returns billingResult()

            manager.queryPurchases()

            assertEquals(listOf(unacknowledged), manager.purchases.value)
            assertTrue(manager.hasSyncedPurchases.value)
            coVerify { billingClient.acknowledgePurchase(any()) }
        }

    @Test
    fun `queryPurchases does nothing when a query fails`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val manager = createManager()
            coEvery { billingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) } returnsMany listOf(
                PurchasesResult(billingResult(BillingClient.BillingResponseCode.ERROR), emptyList()),
                PurchasesResult(billingResult(), emptyList())
            )

            manager.queryPurchases()

            assertTrue(manager.purchases.value.isEmpty())
            assertFalse(manager.hasSyncedPurchases.value)
        }

    // ---- purchase flow ----

    @Test
    fun `launchBillingFlow returns false when product details are not loaded`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val manager = createManager()
            val activity: android.app.Activity = mockk(relaxed = true)

            val launched = manager.launchBillingFlow(activity, PremiumSku.WEEKLY)

            assertFalse(launched)
            verify(exactly = 0) { billingClient.launchBillingFlow(any(), any()) }
        }

    @Test
    fun `launchBillingFlow launches the flow when product details are loaded`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val manager = createManager()
            val activity: android.app.Activity = mockk(relaxed = true)
            coEvery { billingClient.queryProductDetails(any()) } returnsMany listOf(
                ProductDetailsResult(billingResult(), listOf(productDetails(PremiumSku.WEEKLY.productId))),
                ProductDetailsResult(billingResult(), emptyList())
            )
            manager.queryProductDetails()
            every { billingClient.launchBillingFlow(activity, any<BillingFlowParams>()) } returns billingResult()

            val launched = manager.launchBillingFlow(activity, PremiumSku.WEEKLY)

            assertTrue(launched)
        }

    @Test
    fun `purchase update listener emits Purchased and acknowledges the purchase`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val manager = createManager()
            val newPurchase = purchase(listOf(PremiumSku.YEARLY.productId), acknowledged = false)
            coEvery { billingClient.acknowledgePurchase(any()) } returns billingResult()
            coEvery { billingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) } returns
                PurchasesResult(billingResult(), listOf(newPurchase))

            val events = mutableListOf<PurchaseEvent>()
            val job = launch { manager.purchaseEvents.collect { events.add(it) } }

            capturedListener.onPurchasesUpdated(billingResult(), listOf(newPurchase))
            advanceUntilIdle()

            assertEquals(listOf(PurchaseEvent.Purchased(PremiumSku.YEARLY.productId)), events)
            coVerify { billingClient.acknowledgePurchase(any()) }
            job.cancel()
        }

    @Test
    fun `purchase update listener emits Cancelled on user cancellation`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val manager = createManager()
            val events = mutableListOf<PurchaseEvent>()
            val job = launch { manager.purchaseEvents.collect { events.add(it) } }

            capturedListener.onPurchasesUpdated(
                billingResult(BillingClient.BillingResponseCode.USER_CANCELED),
                null
            )
            advanceUntilIdle()

            assertEquals(listOf(PurchaseEvent.Cancelled), events)
            job.cancel()
        }

    @Test
    fun `purchase update listener emits AlreadyOwned`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val manager = createManager()
            val events = mutableListOf<PurchaseEvent>()
            val job = launch { manager.purchaseEvents.collect { events.add(it) } }

            capturedListener.onPurchasesUpdated(
                billingResult(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED),
                null
            )
            advanceUntilIdle()

            assertEquals(listOf(PurchaseEvent.AlreadyOwned), events)
            job.cancel()
        }

    @Test
    fun `purchase update listener emits Pending for a pending purchase`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val manager = createManager()
            val pending = purchase(listOf(PremiumSku.MONTHLY.productId), state = Purchase.PurchaseState.PENDING)
            coEvery { billingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) } returns
                PurchasesResult(billingResult(), emptyList())
            val events = mutableListOf<PurchaseEvent>()
            val job = launch { manager.purchaseEvents.collect { events.add(it) } }

            capturedListener.onPurchasesUpdated(billingResult(), listOf(pending))
            advanceUntilIdle()

            assertEquals(listOf(PurchaseEvent.Pending), events)
            job.cancel()
        }

    @Test
    fun `purchase update listener emits Failed with debug message on error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val manager = createManager()
            val events = mutableListOf<PurchaseEvent>()
            val job = launch { manager.purchaseEvents.collect { events.add(it) } }

            capturedListener.onPurchasesUpdated(
                billingResult(BillingClient.BillingResponseCode.ERROR, "boom"),
                null
            )
            advanceUntilIdle()

            assertEquals(listOf(PurchaseEvent.Failed("boom")), events)
            job.cancel()
        }
}
