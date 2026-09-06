package com.factory.flowcoach

import android.app.Application
import com.factory.flowcoach.billing.BillingManager
import com.factory.flowcoach.billing.PremiumManager
import com.factory.flowcoach.data.local.AppDatabase
import com.factory.flowcoach.data.prefs.PremiumPreferencesRepository
import com.factory.flowcoach.data.prefs.UserPreferencesRepository
import com.factory.flowcoach.data.repository.HydrationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FlowCoachApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(this)
    }

    val hydrationRepository: HydrationRepository by lazy {
        HydrationRepository(database.waterDao(), userPreferencesRepository)
    }

    val premiumPreferencesRepository: PremiumPreferencesRepository by lazy {
        PremiumPreferencesRepository(this)
    }

    val billingManager: BillingManager by lazy { BillingManager(this) }

    val premiumManager: PremiumManager by lazy {
        PremiumManager(billingManager, premiumPreferencesRepository, applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        billingManager.startConnection()
    }
}
