package com.factory.flowcoach.billing

import com.android.billingclient.api.BillingClient

/**
 * All purchasable products. Play Billing has no "lifetime subscription" concept, so LIFETIME is
 * modeled as a one-time INAPP product even though its id keeps the "subscription." segment used
 * by the store listing.
 */
enum class PremiumSku(
    val productId: String,
    val productType: String,
    val displayName: String,
    val fallbackPrice: String,
    val billingPeriodLabel: String?
) {
    WEEKLY(
        productId = "com.factory.flowcoach.subscription.weekly",
        productType = BillingClient.ProductType.SUBS,
        displayName = "Weekly",
        fallbackPrice = "$2.99",
        billingPeriodLabel = "week"
    ),
    MONTHLY(
        productId = "com.factory.flowcoach.subscription.monthly",
        productType = BillingClient.ProductType.SUBS,
        displayName = "Monthly",
        fallbackPrice = "$4.99",
        billingPeriodLabel = "month"
    ),
    YEARLY(
        productId = "com.factory.flowcoach.subscription.yearly",
        productType = BillingClient.ProductType.SUBS,
        displayName = "Yearly",
        fallbackPrice = "$29.99",
        billingPeriodLabel = "year"
    ),
    LIFETIME(
        productId = "com.factory.flowcoach.subscription.lifetime",
        productType = BillingClient.ProductType.INAPP,
        displayName = "Lifetime",
        fallbackPrice = "$49.99",
        billingPeriodLabel = null
    ),
    REMOVE_ADS(
        productId = "com.factory.flowcoach.remove_ads",
        productType = BillingClient.ProductType.INAPP,
        displayName = "Remove Ads",
        fallbackPrice = "$1.99",
        billingPeriodLabel = null
    );

    val isSubscription: Boolean get() = productType == BillingClient.ProductType.SUBS

    companion object {
        val subscriptionTiers = listOf(WEEKLY, MONTHLY, YEARLY, LIFETIME)

        fun fromProductId(productId: String): PremiumSku? = entries.find { it.productId == productId }
    }
}
