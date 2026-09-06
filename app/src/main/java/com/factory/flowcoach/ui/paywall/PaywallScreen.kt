package com.factory.flowcoach.ui.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.factory.flowcoach.billing.BillingConnectionState
import com.factory.flowcoach.billing.PremiumSku
import kotlinx.coroutines.launch

private data class PremiumFeature(val icon: ImageVector, val title: String, val description: String)

private val PREMIUM_FEATURES = listOf(
    PremiumFeature(
        Icons.Filled.History,
        "Unlimited history",
        "See your full hydration history, not just the last 7 days"
    ),
    PremiumFeature(
        Icons.Filled.Tune,
        "Custom quick-add amounts",
        "Log any amount instantly instead of picking from presets"
    ),
    PremiumFeature(
        Icons.Filled.NotificationsActive,
        "Flexible reminders",
        "Set any custom reminder interval and multiple reminders a day"
    ),
    PremiumFeature(
        Icons.Filled.Insights,
        "Advanced streak insights",
        "Deeper stats on your consistency over time"
    ),
    PremiumFeature(
        Icons.Filled.VisibilityOff,
        "Ad-free experience",
        "Remove all ads from FlowCoach"
    )
)

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(viewModel: PaywallViewModel, onClose: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var selectedSku by remember { mutableStateOf(PremiumSku.YEARLY) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                PaywallUiEvent.PurchaseSuccessful -> "You're Premium! Enjoy FlowCoach."
                PaywallUiEvent.PurchaseCancelled -> null
                PaywallUiEvent.PurchasePending -> "Purchase pending — we'll unlock Premium once it's confirmed."
                PaywallUiEvent.AlreadyOwned -> "You already own this."
                is PaywallUiEvent.PurchaseFailed -> event.message
                is PaywallUiEvent.RestoreFinished ->
                    if (event.restored) "Purchases restored." else "No previous purchases found."
            }
            if (message != null) {
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
            if (event is PaywallUiEvent.PurchaseSuccessful) onClose()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FlowCoach Premium") },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        }
    ) { padding ->
        if (uiState.entitlements.isPremium) {
            AlreadyPremiumContent(modifier = Modifier.padding(padding), onClose = onClose)
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.Filled.WaterDrop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Build a lasting hydration habit",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(PREMIUM_FEATURES) { feature -> FeatureRow(feature) }

            if (uiState.connectionState !is BillingConnectionState.Connected) {
                item { ConnectionNotice(uiState.connectionState, onRetry = viewModel::retryConnection) }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumSku.subscriptionTiers.forEach { sku ->
                        TierCard(
                            sku = sku,
                            price = uiState.prices[sku]?.formattedPrice ?: sku.fallbackPrice,
                            selected = selectedSku == sku,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedSku = sku
                            }
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        activity?.let { viewModel.purchase(it, selectedSku) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = activity != null
                ) {
                    Text("Continue with ${selectedSku.displayName}")
                }
            }

            item {
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        activity?.let { viewModel.purchase(it, PremiumSku.REMOVE_ADS) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Just remove ads — ${uiState.prices[PremiumSku.REMOVE_ADS]?.formattedPrice ?: PremiumSku.REMOVE_ADS.fallbackPrice}"
                    )
                }
            }

            item {
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.restorePurchases()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = if (uiState.isRestoring) {
                                "Restoring purchases"
                            } else {
                                "Restore purchases"
                            }
                        },
                    enabled = !uiState.isRestoring
                ) {
                    if (uiState.isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Restore purchases")
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = { uriHandler.openUri("https://flowcoach.app/terms") }) {
                        Text("Terms of Service", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { uriHandler.openUri("https://flowcoach.app/privacy") }) {
                        Text("Privacy Policy", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlreadyPremiumContent(modifier: Modifier = Modifier, onClose: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("You're already Premium", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Thanks for supporting FlowCoach. All premium features are unlocked.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onClose) { Text("Close") }
    }
}

@Composable
private fun FeatureRow(feature: PremiumFeature) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(feature.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(feature.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ConnectionNotice(state: BillingConnectionState, onRetry: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val message = when (state) {
        is BillingConnectionState.Unavailable -> "Can't reach the Play Store. Prices shown may be out of date."
        BillingConnectionState.Disconnected -> "No connection to the Play Store. Check your network."
        BillingConnectionState.Connecting -> "Connecting to the Play Store…"
        BillingConnectionState.Connected -> return
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            if (state !is BillingConnectionState.Connecting) {
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRetry()
                    }
                ) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun TierCard(sku: PremiumSku, price: String, selected: Boolean, onClick: () -> Unit) {
    val priceLabel = if (sku.billingPeriodLabel != null) "$price per ${sku.billingPeriodLabel}" else price
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = "${sku.displayName}, $priceLabel" },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(sku.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (sku == PremiumSku.YEARLY) {
                    Text(
                        "Best value",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = if (sku.billingPeriodLabel != null) "$price / ${sku.billingPeriodLabel}" else price,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

