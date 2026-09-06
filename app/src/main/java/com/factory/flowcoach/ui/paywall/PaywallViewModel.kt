package com.factory.flowcoach.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.factory.flowcoach.billing.BillingConnectionState
import com.factory.flowcoach.billing.BillingManager
import com.factory.flowcoach.billing.PremiumEntitlements
import com.factory.flowcoach.billing.PremiumManager
import com.factory.flowcoach.billing.PremiumSku
import com.factory.flowcoach.billing.PurchaseEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PriceInfo(val sku: PremiumSku, val formattedPrice: String)

data class PaywallUiState(
    val connectionState: BillingConnectionState = BillingConnectionState.Connecting,
    val prices: Map<PremiumSku, PriceInfo> = PremiumSku.entries.associateWith {
        PriceInfo(it, it.fallbackPrice)
    },
    val entitlements: PremiumEntitlements = PremiumEntitlements(),
    val isRestoring: Boolean = false
)

sealed class PaywallUiEvent {
    data object PurchaseSuccessful : PaywallUiEvent()
    data object PurchaseCancelled : PaywallUiEvent()
    data object PurchasePending : PaywallUiEvent()
    data object AlreadyOwned : PaywallUiEvent()
    data class PurchaseFailed(val message: String) : PaywallUiEvent()
    data class RestoreFinished(val restored: Boolean) : PaywallUiEvent()
}

class PaywallViewModel(
    private val billingManager: BillingManager,
    private val premiumManager: PremiumManager
) : ViewModel() {

    private val _isRestoring = MutableStateFlow(false)

    val uiState: StateFlow<PaywallUiState> = combine(
        billingManager.connectionState,
        billingManager.productDetails,
        premiumManager.entitlements,
        _isRestoring
    ) { connectionState, productDetails, entitlements, isRestoring ->
        val prices = PremiumSku.entries.associateWith { sku ->
            val details = productDetails[sku.productId]
            val formatted = if (sku.isSubscription) {
                details?.subscriptionOfferDetails?.firstOrNull()
                    ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
            } else {
                details?.oneTimePurchaseOfferDetails?.formattedPrice
            }
            PriceInfo(sku, formatted ?: sku.fallbackPrice)
        }
        PaywallUiState(
            connectionState = connectionState,
            prices = prices,
            entitlements = entitlements,
            isRestoring = isRestoring
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaywallUiState())

    private val _events = MutableSharedFlow<PaywallUiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PaywallUiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            billingManager.purchaseEvents.collect { event ->
                _events.tryEmit(
                    when (event) {
                        is PurchaseEvent.Purchased -> PaywallUiEvent.PurchaseSuccessful
                        PurchaseEvent.Cancelled -> PaywallUiEvent.PurchaseCancelled
                        PurchaseEvent.Pending -> PaywallUiEvent.PurchasePending
                        PurchaseEvent.AlreadyOwned -> PaywallUiEvent.AlreadyOwned
                        is PurchaseEvent.Failed -> PaywallUiEvent.PurchaseFailed(event.message)
                    }
                )
            }
        }
    }

    fun retryConnection() = billingManager.retryConnection()

    fun purchase(activity: Activity, sku: PremiumSku) {
        val launched = billingManager.launchBillingFlow(activity, sku)
        if (!launched) {
            _events.tryEmit(
                PaywallUiEvent.PurchaseFailed("Prices aren't loaded yet. Check your connection and try again.")
            )
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            _isRestoring.value = true
            val restored = premiumManager.restorePurchases()
            _isRestoring.value = false
            _events.tryEmit(PaywallUiEvent.RestoreFinished(restored))
        }
    }

    fun markIntroSeen() {
        viewModelScope.launch { premiumManager.markPaywallIntroSeen() }
    }
}
