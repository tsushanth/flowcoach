package com.factory.flowcoach.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.factory.flowcoach.billing.BillingManager
import com.factory.flowcoach.billing.PremiumManager
import com.factory.flowcoach.data.repository.HydrationRepository
import com.factory.flowcoach.ui.history.HistoryViewModel
import com.factory.flowcoach.ui.home.HomeViewModel
import com.factory.flowcoach.ui.paywall.PaywallViewModel
import com.factory.flowcoach.ui.settings.SettingsViewModel

class ViewModelFactory(
    private val repository: HydrationRepository,
    private val appContext: Context,
    private val billingManager: BillingManager,
    private val premiumManager: PremiumManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(repository, premiumManager) as T
            modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
                HistoryViewModel(repository, premiumManager) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(repository, appContext, premiumManager) as T
            modelClass.isAssignableFrom(PaywallViewModel::class.java) ->
                PaywallViewModel(billingManager, premiumManager) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
