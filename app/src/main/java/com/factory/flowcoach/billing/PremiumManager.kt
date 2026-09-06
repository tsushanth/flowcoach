package com.factory.flowcoach.billing

import com.factory.flowcoach.data.prefs.PremiumPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

data class PremiumEntitlements(val activeSkus: Set<PremiumSku> = emptySet()) {
    val isPremium: Boolean get() = activeSkus.any { it != PremiumSku.REMOVE_ADS }
    val adsRemoved: Boolean get() = isPremium || PremiumSku.REMOVE_ADS in activeSkus
    val activeSubscription: PremiumSku? get() = activeSkus.firstOrNull { it.isSubscription }
    val hasLifetime: Boolean get() = PremiumSku.LIFETIME in activeSkus
}

/**
 * Resolves [BillingManager]'s raw purchases into app-level entitlements and persists the last
 * known result so gating survives app restarts before Play has been reached again.
 */
class PremiumManager(
    private val billingManager: BillingManager,
    private val preferencesRepository: PremiumPreferencesRepository,
    externalScope: CoroutineScope
) {

    private val liveSkus = billingManager.purchases.map { purchases ->
        purchases.flatMap { it.products }.mapNotNull(PremiumSku::fromProductId).toSet()
    }

    val entitlements: StateFlow<PremiumEntitlements> = combine(
        preferencesRepository.cachedSkusFlow,
        liveSkus,
        billingManager.hasSyncedPurchases
    ) { cachedNames, live, synced ->
        if (synced) {
            live
        } else {
            cachedNames.mapNotNull { name -> PremiumSku.entries.find { it.name == name } }.toSet()
        }
    }
        .onEach { skus -> preferencesRepository.setCachedSkus(skus.map { it.name }.toSet()) }
        .map { PremiumEntitlements(it) }
        .stateIn(externalScope, SharingStarted.WhileSubscribed(5000), PremiumEntitlements())

    val hasSeenPaywallIntro = preferencesRepository.hasSeenPaywallIntroFlow

    suspend fun markPaywallIntroSeen() = preferencesRepository.setHasSeenPaywallIntro(true)

    /** Re-syncs with Play and returns whether the user now holds any entitlement. */
    suspend fun restorePurchases(): Boolean {
        billingManager.queryPurchases()
        return billingManager.purchases.value
            .flatMap { it.products }
            .any { PremiumSku.fromProductId(it) != null }
    }
}
