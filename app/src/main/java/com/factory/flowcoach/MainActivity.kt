package com.factory.flowcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.factory.flowcoach.billing.BillingConnectionState
import com.factory.flowcoach.notification.ReminderScheduler
import com.factory.flowcoach.ui.ViewModelFactory
import com.factory.flowcoach.ui.navigation.FlowCoachNavHost
import com.factory.flowcoach.ui.theme.FlowCoachTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as FlowCoachApplication
        val viewModelFactory = ViewModelFactory(
            app.hydrationRepository,
            applicationContext,
            app.billingManager,
            app.premiumManager
        )

        lifecycleScope.launch {
            val prefs = app.hydrationRepository.preferencesFlow.first()
            if (prefs.reminderEnabled) {
                ReminderScheduler.schedule(applicationContext, prefs.reminderIntervalMinutes)
            }
        }

        setContent {
            FlowCoachTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FlowCoachNavHost(
                        viewModelFactory = viewModelFactory,
                        premiumManager = app.premiumManager
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as FlowCoachApplication
        // Re-sync with Play so a subscription that expired while the app was backgrounded
        // is dropped from the user's entitlements promptly.
        if (app.billingManager.connectionState.value is BillingConnectionState.Connected) {
            lifecycleScope.launch { app.billingManager.queryPurchases() }
        }
    }
}
