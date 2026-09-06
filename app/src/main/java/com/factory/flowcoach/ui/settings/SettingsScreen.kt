package com.factory.flowcoach.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.factory.flowcoach.R
import com.factory.flowcoach.ui.common.ProBadge

private val REMINDER_INTERVAL_OPTIONS = listOf(30, 60, 90, 120, 180)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onNavigateToPaywall: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showCustomIntervalDialog by remember { mutableStateOf(false) }

    if (showCustomIntervalDialog) {
        CustomIntervalDialog(
            onDismiss = { showCustomIntervalDialog = false },
            onConfirm = { minutes ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.setReminderIntervalMinutes(minutes)
                showCustomIntervalDialog = false
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setReminderEnabled(granted)
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                PremiumSection(
                    isPremium = uiState.isPremium,
                    subscriptionLabel = uiState.activeSubscription?.displayName,
                    onUpgradeClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigateToPaywall()
                    },
                    onManageClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                "https://play.google.com/store/account/subscriptions" +
                                    "?package=com.factory.flowcoach"
                            )
                        )
                        context.startActivity(intent)
                    }
                )
            }

            item { HorizontalDivider() }

            item {
                Text(text = "Goals", style = MaterialTheme.typography.titleLarge)
            }
            item {
                StepperCard(
                    label = "Daily goal",
                    value = uiState.prefs.dailyGoalMl,
                    unit = "ml",
                    step = 250,
                    minValue = 500,
                    maxValue = 6000,
                    onValueChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setDailyGoal(it)
                    }
                )
            }
            item {
                StepperCard(
                    label = "Cup size",
                    value = uiState.prefs.cupSizeMl,
                    unit = "ml",
                    step = 50,
                    minValue = 50,
                    maxValue = 1000,
                    onValueChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setCupSize(it)
                    }
                )
            }

            item { HorizontalDivider() }

            item {
                Text(text = "Reminders", style = MaterialTheme.typography.titleLarge)
            }
            item {
                val reminderEnabled = uiState.prefs.reminderEnabled
                val onReminderToggle: (Boolean) -> Unit = { enabled ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setReminderEnabled(enabled)
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = reminderEnabled,
                                onValueChange = onReminderToggle,
                                role = Role.Switch
                            )
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Hydration reminders", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(R.string.reminder_text),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(checked = reminderEnabled, onCheckedChange = null)
                    }
                }
            }
            item {
                Text(text = "Remind me every", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    REMINDER_INTERVAL_OPTIONS.forEach { minutes ->
                        FilterChip(
                            selected = uiState.prefs.reminderIntervalMinutes == minutes,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setReminderIntervalMinutes(minutes)
                            },
                            label = { Text(formatInterval(minutes)) }
                        )
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!uiState.isPremium) {
                                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            }
                            Text(text = "Custom interval", style = MaterialTheme.typography.titleMedium)
                        }
                        if (uiState.isPremium) {
                            TextButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showCustomIntervalDialog = true
                                }
                            ) { Text("Set") }
                        } else {
                            TextButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onNavigateToPaywall()
                                },
                                modifier = Modifier.semantics {
                                    contentDescription = "Custom interval, Premium feature, tap to upgrade"
                                }
                            ) {
                                ProBadge()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumSection(
    isPremium: Boolean,
    subscriptionLabel: String?,
    onUpgradeClick: () -> Unit,
    onManageClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPremium) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null)
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Column {
                    Text(
                        text = if (isPremium) "FlowCoach Premium" else "Upgrade to Premium",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isPremium) {
                            subscriptionLabel?.let { "$it plan active" } ?: "Lifetime access active"
                        } else {
                            "Unlock unlimited history, custom reminders and more"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            if (isPremium) {
                TextButton(onClick = onManageClick) { Text("Manage") }
            } else {
                TextButton(onClick = onUpgradeClick) { Text("Upgrade") }
            }
        }
    }
}

@Composable
private fun CustomIntervalDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val minutes = text.toIntOrNull()
    val isValid = minutes != null && minutes in 5..1440

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom reminder interval") },
        text = {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() } },
                label = { Text("Minutes") },
                supportingText = { Text("Between 5 and 1440 minutes") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (isValid) onConfirm(minutes!!) }
                ),
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { minutes?.let(onConfirm) },
                enabled = isValid
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StepperCard(
    label: String,
    value: Int,
    unit: String,
    step: Int,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    onValueChange((value - step).coerceAtLeast(minValue))
                }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
                }
                Text(
                    text = "$value $unit",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(onClick = {
                    onValueChange((value + step).coerceAtMost(maxValue))
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase $label")
                }
            }
        }
    }
}

private fun formatInterval(minutes: Int): String {
    return if (minutes % 60 == 0) {
        val hours = minutes / 60
        if (hours == 1) "1 hr" else "$hours hrs"
    } else {
        "$minutes min"
    }
}
