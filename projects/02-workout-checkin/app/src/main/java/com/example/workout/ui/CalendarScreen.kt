package com.example.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workout.ui.theme.PrimaryBlue
import com.example.workout.viewmodel.CalendarViewModel
import java.time.YearMonth

private fun monthGrid(year: Int, month: Int): List<Int?> {
    val ym = YearMonth.of(year, month)
    val firstDay = ym.atDay(1).dayOfWeek.value % 7 // 0=周日
    val out = MutableList<Int?>(firstDay) { null }
    out.addAll((1..ym.lengthOfMonth()).toList())
    return out
}

@Composable
fun CalendarScreen(viewModel: CalendarViewModel) {
    val state by viewModel.uiState.collectAsState()
    val now = java.time.LocalDate.now()
    var year by remember { mutableIntStateOf(now.year) }
    var month by remember { mutableIntStateOf(now.monthValue) }

    fun monthKey(): String = "%04d-%02d".format(year, month)

    LaunchedEffect(year, month) {
        viewModel.loadMonth(monthKey())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("◀", fontSize = 24.sp, modifier = Modifier.clickable {
                if (month == 1) { month = 12; year-- } else month--
            })
            Text("$year 年 $month 月", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("▶", fontSize = 24.sp, modifier = Modifier.clickable {
                if (month == 12) { month = 1; year++ } else month++
            })
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { w ->
                Text(
                    w,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = Color(0xFF888888),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            monthGrid(year, month).chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        val boxModifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                        if (day == null) {
                            Box(modifier = boxModifier)
                        } else {
                            val dateStr = "%04d-%02d-%02d".format(year, month, day)
                            val checked = dateStr in state.checkedDates
                            Box(
                                modifier = boxModifier
                                    .background(
                                        if (checked) PrimaryBlue else Color.Transparent,
                                        RoundedCornerShape(8.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.toString(),
                                    color = if (checked) Color.White else Color.Black,
                                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
