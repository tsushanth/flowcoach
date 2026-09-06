package com.factory.flowcoach.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.factory.flowcoach.ui.theme.StreakGold

@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Text(
        text = "PRO",
        modifier = modifier
            .background(StreakGold, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = Color.Black,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelSmall.copy(color = Color.Black)
    )
}
