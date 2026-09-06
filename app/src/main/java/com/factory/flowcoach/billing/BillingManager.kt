package com.factory.flowcoach.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class BillingConnectionState {
    data object Connecting : BillingConnectionState()
    data object Connected : BillingConnectionState()
    data object Disconnected : BillingConnectionState()
    data class Unavailable(val message: String) : BillingConnectionState()
}

sealed class PurchaseEvent {
    data class Purchased(val productId: String) : PurchaseEvent()
    data object Cancelled : PurchaseEvent()
    data object Pending : PurchaseEvent()
    data object AlreadyOwned : PurchaseEvent()
    data class Failed(val message: String) : PurchaseEvent()
}

/**
 * Thin coroutine-friendly wrapper around [BillingClient]. Owns the connection lifecycle and is
 * the single source of truth for product prices and the caller's currently valid purchases.
 * [PremiumManager] turns those purchases into app-level entitlements.
 */
class BillingManager(
    context: Context,
    billingClientFactory: (PurchasesUpdatedListener) -> BillingClient = { listener ->
        BillingClient.newBuilder(context.applicationContext)
            .setListener(listener)
            .enablePendingPurchases()
            .build()
    }
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch {
                purchases.orEmpty().forEach { purchase ->
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> {
                            if (!purchase.isAcknowledged) acknowledge(purchase)
                            purchase.products.forEach {
                                _purchaseEvents.emit(PurchaseEvent.Purchased(it))
                            }
                        }
                        Purchase.PurchaseState.PENDING -> _purchaseEvents.emit(PurchaseEvent.Pending)
                        else -> Unit
                    }
                }
                queryPurchases()
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                scope.launch { _purchaseEvents.emit(PurchaseEvent.Cancelled) }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                scope.launch { _purchaseEvents.emit(PurchaseEvent.AlreadyOwned) }
            else -> scope.launch {
                _purchaseEvents.emit(
                    PurchaseEvent.Failed(result.debugMessage.ifBlank { "Purchase failed" })
                )
            }
        }
    }

    private val billingClient: BillingClient = billingClientFactory(purchasesUpdatedListener)

    private val _connectionState =
        MutableStateFlow<BillingConnectionState>(BillingConnectionState.Disconnected)
    val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    /** Currently valid (non-expired, PURCHASED) purchases, refreshed on connect/resume/restore. */
    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val purchases: StateFlow<List<Purchase>> = _purchases.asStateFlow()

    /** True once [queryPurchases] has completed successfully at least once this process. */
    private val _hasSyncedPurchases = MutableStateFlow(false)
    val hasSyncedPurchases: StateFlow<Boolean> = _hasSyncedPurchases.asStateFlow()

    private val _purchaseEvents = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 4)
    val purchaseEvents: SharedFlow<PurchaseEvent> = _purchaseEvents.asSharedFlow()

    fun startConnection() {
        if (billingClient.isReady) return
        _connectionState.value = BillingConnectionState.Connecting
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _connectionState.value = BillingConnectionState.Connected
                    scope.launch {
                        queryProductDetails()
                        queryPurchases()
                    }
                } else {
                    _connectionState.value = BillingConnectionState.Unavailable(
                        result.debugMessage.ifBlank { "Billing unavailable" }
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                _connectionState.value = BillingConnectionState.Disconnected
            }
        })
    }

    /** Retries the connection, e.g. after the user fixes their network and taps retry. */
    fun retryConnection() = startConnection()

    suspend fun queryProductDetails() {
        val results = mutableMapOf<String, ProductDetails>()
        listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP).forEach { type ->
            val skus = PremiumSku.entries.filter { it.productType == type }
            if (skus.isEmpty()) return@forEach
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    skus.map {
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(it.productId)
                            .setProductType(type)
                            .build()
                    }
                )
                .build()
            val result = billingClient.queryProductDetails(params)
            result.productDetailsList?.forEach { details -> results[details.productId] = details }
        }
        _productDetails.value = results
    }

    /** Refreshes valid purchases from Play. Expired subscriptions simply stop being returned. */
    suspend fun queryPurchases() {
        val subsResult = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        )
        val inAppResult = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        )
        if (subsResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK ||
            inAppResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK
        ) {
            return
        }
        val purchased = (subsResult.purchasesList + inAppResult.purchasesList)
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        purchased.filterNot { it.isAcknowledged }.forEach { acknowledge(it) }
        _purchases.value = purchased
        _hasSyncedPurchases.value = true
    }

    private suspend fun acknowledge(purchase: Purchase) {
        billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        )
    }

    /** Launches the Play purchase sheet for [sku]. Returns false if product details aren't loaded yet. */
    fun launchBillingFlow(activity: Activity, sku: PremiumSku): Boolean {
        val details = _productDetails.value[sku.productId] ?: return false
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        if (sku.isSubscription) {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            offerToken?.let { productParams.setOfferToken(it) }
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams.build()))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }
}
