package com.factory.flowcoach.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.factory.flowcoach.data.local.DailyTotal
import com.factory.flowcoach.data.repository.DateKeys

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onNavigateToPaywall: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val haptics = LocalHapticFeedback.current

    Scaffold { padding ->
        if (uiState.dailyTotals.isEmpty()) {
            HistoryEmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = if (uiState.isPremium) "Last 30 days" else "Last 7 days",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                items(uiState.visibleTotals, key = { it.dateKey }) { day ->
                    HistoryRow(day = day, goalMl = uiState.dailyGoalMl)
                }
                if (uiState.hiddenDaysCount > 0) {
                    item {
                        LockedHistoryCard(
                            hiddenDays = uiState.hiddenDaysCount,
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
}

@Composable
private fun HistoryEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "No history yet. Start logging water to see your progress here."
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                text = "No history yet",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Start logging water to see your progress here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun LockedHistoryCard(hiddenDays: Int, onClick: () -> Unit) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = "  $hiddenDays more day${if (hiddenDays == 1) "" else "s"} with Premium",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            TextButton(
                onClick = onClick,
                modifier = Modifier.semantics {
                    contentDescription = "Unlock $hiddenDays more day${if (hiddenDays == 1) "" else "s"} of history, Premium feature"
                }
            ) { Text("Unlock") }
        }
    }
}

@Composable
private fun HistoryRow(day: DailyTotal, goalMl: Int) {
    val progress = if (goalMl <= 0) 0f else (day.totalMl.toFloat() / goalMl).coerceIn(0f, 1f)
    val metGoal = day.totalMl >= goalMl
    val percent = (progress * 100).toInt()
    val dateLabel = DateKeys.displayLabel(day.dateKey)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "$dateLabel, ${day.totalMl} milliliters, $percent percent of goal" +
                        if (metGoal) ", goal reached" else ""
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.titleMedium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clearAndSetSemantics {}
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (metGoal) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                    )
                }
                Text(
                    text = "  ${day.totalMl} ml",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (metGoal) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}
