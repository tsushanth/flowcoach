package com.factory.flowcoach.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.premiumDataStore by preferencesDataStore(name = "flowcoach_premium_prefs")

/**
 * Local cache of the last-known entitlement state, so premium gating doesn't flash "locked" on
 * cold start while the Play Billing connection is still being established. Play purchase data,
 * once synced, is always the source of truth.
 */
class PremiumPreferencesRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.premiumDataStore
) {

    private object Keys {
        val CACHED_ACTIVE_SKUS = stringSetPreferencesKey("cached_active_skus")
        val HAS_SEEN_PAYWALL_INTRO = booleanPreferencesKey("has_seen_paywall_intro")
    }

    val cachedSkusFlow: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[Keys.CACHED_ACTIVE_SKUS] ?: emptySet()
    }

    val hasSeenPaywallIntroFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.HAS_SEEN_PAYWALL_INTRO] ?: false
    }

    suspend fun setCachedSkus(skuNames: Set<String>) {
        dataStore.edit { it[Keys.CACHED_ACTIVE_SKUS] = skuNames }
    }

    suspend fun setHasSeenPaywallIntro(seen: Boolean) {
        dataStore.edit { it[Keys.HAS_SEEN_PAYWALL_INTRO] = seen }
    }
}
