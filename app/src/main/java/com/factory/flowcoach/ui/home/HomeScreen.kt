package com.factory.flowcoach.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.WaterDrop as WaterDropOutlined
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.factory.flowcoach.data.local.WaterEntry
import com.factory.flowcoach.ui.common.ProBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigateToPaywall: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val haptics = LocalHapticFeedback.current
    var showCustomAmountDialog by remember { mutableStateOf(false) }

    if (showCustomAmountDialog) {
        CustomAmountDialog(
            onDismiss = { showCustomAmountDialog = false },
            onConfirm = { amount ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.logWater(amount)
                showCustomAmountDialog = false
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add ${uiState.cupSizeMl} ml") },
                icon = { Icon(Icons.Filled.WaterDrop, contentDescription = null) },
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.logWater(uiState.cupSizeMl)
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { ProgressRingSection(uiState) }
            item { StreakSection(uiState) }
            item {
                QuickAddSection(
                    onAdd = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.logWater(it)
                    },
                    isPremium = uiState.isPremium,
                    onCustomAmountClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (uiState.isPremium) showCustomAmountDialog = true else onNavigateToPaywall()
                    }
                )
            }
            item {
                StreakInsightsSection(
                    uiState = uiState,
                    onUnlockClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigateToPaywall()
                    }
                )
            }
            item {
                Text(
                    text = "Today's log",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (uiState.todayEntries.isEmpty()) {
                item { TodayLogEmptyState() }
            } else {
                items(uiState.todayEntries, key = { it.id }) { entry ->
                    EntryRow(
                        entry = entry,
                        onDelete = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.deleteEntry(entry)
                        }
                    )
                }
            }
            if (!uiState.adsRemoved) {
                item {
                    RemoveAdsBanner(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigateToPaywall()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayLogEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "No entries yet today. Log your first glass to get started."
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Outlined.WaterDropOutlined,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        Text(
            text = "No entries yet today",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Log your first glass to start tracking.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun CustomAmountDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val amount = text.toIntOrNull()
    val isValid = amount != null && amount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom amount") },
        text = {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() } },
                label = { Text("Amount (ml)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (isValid) onConfirm(amount!!) }
                ),
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { amount?.let(onConfirm) },
                enabled = isValid
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RemoveAdsBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enjoying FlowCoach? Remove ads for $1.99",
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onClick) { Text("Remove") }
        }
    }
}

@Composable
private fun ProgressRingSection(uiState: HomeUiState) {
    val percent = (uiState.progress * 100).toInt()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription =
                    "${uiState.todayTotalMl} of ${uiState.dailyGoalMl} milliliters, $percent percent of daily goal"
            }
        ) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(220.dp),
                strokeWidth = 14.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            CircularProgressIndicator(
                progress = { uiState.progress },
                modifier = Modifier.size(220.dp),
                strokeWidth = 14.dp,
                color = if (uiState.goalReached) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${uiState.todayTotalMl}",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "of ${uiState.dailyGoalMl} ml",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun StreakSection(uiState: HomeUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "${uiState.currentStreak} day streak. Best streak: ${uiState.bestStreak} days"
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalFireDepartment, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "${uiState.currentStreak} day streak",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = "Best: ${uiState.bestStreak}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun QuickAddSection(
    onAdd: (Int) -> Unit,
    isPremium: Boolean,
    onCustomAmountClick: () -> Unit
) {
    val quickAmounts = listOf(100, 200, 250, 330, 500, 750)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Quick add", style = MaterialTheme.typography.titleMedium)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            quickAmounts.take(3).forEach { amount ->
                FilledTonalButton(
                    onClick = { onAdd(amount) },
                    modifier = Modifier.semantics {
                        contentDescription = "Add $amount milliliters"
                    }
                ) {
                    Text("$amount ml")
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            quickAmounts.drop(3).forEach { amount ->
                FilledTonalButton(
                    onClick = { onAdd(amount) },
                    modifier = Modifier.semantics {
                        contentDescription = "Add $amount milliliters"
                    }
                ) {
                    Text("$amount ml")
                }
            }
        }
        FilledTonalButton(
            onClick = onCustomAmountClick,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = if (isPremium) {
                        "Custom amount"
                    } else {
                        "Custom amount, Premium feature, tap to upgrade"
                    }
                }
        ) {
            Text("Custom amount")
            if (!isPremium) {
                Spacer(modifier = Modifier.size(8.dp))
                ProBadge()
            }
        }
    }
}

@Composable
private fun StreakInsightsSection(uiState: HomeUiState, onUnlockClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Insights, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Streak insights", style = MaterialTheme.typography.titleMedium)
                }
                if (!uiState.isPremium) ProBadge(modifier = Modifier.clearAndSetSemantics {})
            }
            Spacer(modifier = Modifier.size(8.dp))
            if (uiState.isPremium) {
                val consistency = if (uiState.bestStreak <= 0) {
                    0
                } else {
                    (uiState.currentStreak * 100) / uiState.bestStreak
                }
                Text(
                    text = "You're at $consistency% of your best-ever streak (${uiState.bestStreak} days).",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            "Unlock deeper stats on your consistency",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    TextButton(
                        onClick = onUnlockClick,
                        modifier = Modifier.semantics {
                            contentDescription = "Unlock streak insights, Premium feature"
                        }
                    ) { Text("Unlock") }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: WaterEntry, onDelete: () -> Unit) {
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeLabel = remember(entry.timestampEpochMillis) {
        timeFormatter.format(Date(entry.timestampEpochMillis))
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${entry.amountMl} milliliters, logged at $timeLabel"
                }
            ) {
                Icon(
                    Icons.Filled.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.size(12.dp))
                Column {
                    Text(text = "${entry.amountMl} ml", fontWeight = FontWeight.Medium)
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete ${entry.amountMl} milliliter entry logged at $timeLabel"
                )
            }
        }
    }
}
