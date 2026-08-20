package com.example.workout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StreakBadge(streakDays: Int, todayChecked: Boolean, modifier: Modifier = Modifier) {
    val gradient = Brush.horizontalGradient(
        listOf(Color(0xFFFF9A56), Color(0xFFFF5F6D)),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(gradient, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text("🔥 已连续打卡 $streakDays 天", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(if (todayChecked) "今天已打卡 ✅" else "今天还没打卡", color = Color.White)
    }
}
