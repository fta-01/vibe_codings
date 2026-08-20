package com.example.workout.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workout.data.exercise.Exercise
import com.example.workout.data.exercise.WorkoutPlan
import com.example.workout.data.exercise.WorkoutPlanBuilder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckinForm(
    exercises: List<Exercise>,
    onCheckIn: (exerciseId: String, durationMinutes: Int) -> Unit,
    modifier: Modifier = Modifier,
    todayChecked: Boolean = false,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var durationText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showPlans by remember { mutableStateOf(false) }
    var activePlan by remember { mutableStateOf<WorkoutPlan?>(null) }
    var showSoundSettings by remember { mutableStateOf(false) }

    val selected = exercises.find { it.id == selectedId }
    val plans = remember(exercises) { WorkoutPlanBuilder.buildPlans(exercises) }

    // 组合训练完成后：直接打卡（一条路径）；今日已打卡则只收尾不重复打卡
    fun onPlanAllFinished(plan: WorkoutPlan) {
        activePlan = null
        if (todayChecked) {
            return
        }
        val totalMin = (plan.totalSeconds / 60).coerceAtLeast(1)
        onCheckIn("组合 · ${plan.name}", totalMin)
    }

    // 提示音设置对话框
    if (showSoundSettings) {
        FinishSoundSettingsDialog(onDismiss = { showSoundSettings = false })
    }

    // 组合训练激活时，全屏显示执行器
    val plan = activePlan
    if (plan != null) {
        Column(modifier = modifier.fillMaxWidth()) {
            // 组合训练界面也保留设置入口
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { showSoundSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
                    Text(" 提示音设置", fontSize = 13.sp, color = Color(0xFF888888))
                }
            }
            WorkoutPlanRunner(
                plan = plan,
                todayChecked = todayChecked,
                onAllFinished = { onPlanAllFinished(plan) },
                onDismiss = { activePlan = null },
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        // ── 顶部行：已打卡提示 + 提示音设置入口 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (todayChecked) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                    Text("  今日已打卡", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
            }
            TextButton(onClick = { showSoundSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
                Text(" 提示音设置", fontSize = 13.sp, color = Color(0xFF888888))
            }
        }

        // ── 一键快速打卡：无任何限制，点一下即完成（已打卡时隐藏）──
        if (!todayChecked) {
            Button(
                onClick = { onCheckIn("快速打卡", 0) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00)),
            ) {
                Text("⚡ 一键打卡 · 无需选择运动", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── 推荐组合入口 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "推荐运动组合",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = { showPlans = !showPlans }) {
                Text(if (showPlans) "收起" else "查看")
                Icon(
                    if (showPlans) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }
        }

        AnimatedVisibility(
            visible = showPlans,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plans.forEach { p ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activePlan = p },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    p.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    p.totalMinutesDisplay,
                                    fontSize = 13.sp,
                                    color = Color(0xFF888888),
                                )
                            }
                            Text(
                                p.description,
                                fontSize = 12.sp,
                                color = Color(0xFF777777),
                            )
                            Text(
                                "${p.items.size} 项 · 运动 ${p.exerciseMinutesDisplay}" +
                                    (if (p.totalSeconds != p.exerciseSeconds) " + 休息" else ""),
                                fontSize = 12.sp,
                                color = Color(0xFFAAAAAA),
                            )
                        }
                    }
                }
            }
        }

        // 分隔线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(Color(0xFFEEEEEE), RoundedCornerShape(1.dp))
                .padding(vertical = 0.5.dp),
        )

        // ── 单运动选择（保留）──
        Text(
            "或单独选择运动",
            fontSize = 14.sp,
            color = Color(0xFF888888),
        )

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = exercises.find { it.id == selectedId }?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("选择运动类型") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                exercises.forEach { ex ->
                    DropdownMenuItem(
                        text = { Text("${ex.name}（${ex.category}）") },
                        onClick = {
                            selectedId = ex.id
                            expanded = false
                            message = null
                        },
                    )
                }
            }
        }

        if (selected?.isTimed == true) {
            Countdown(
                defaultSeconds = selected.defaultSeconds ?: 60,
                onFinished = { message = if (todayChecked) "运动完成！🎉" else "运动完成！可以打卡啦" },
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        // 时长输入与打卡按钮：今日已打卡后整体隐藏，不再出现打卡入口
        if (!todayChecked) {
            OutlinedTextField(
                value = durationText,
                onValueChange = {
                    durationText = it.filter { c -> c.isDigit() }.take(4)
                },
                label = { Text("时长（分钟，可选）") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )

            Button(
                onClick = {
                    val sel = selected
                    if (sel == null) {
                        message = "请选择运动类型"
                    } else {
                        val minutes = durationText.toIntOrNull() ?: 0
                        onCheckIn(sel.name, minutes)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text("打卡")
            }
        }

        message?.let {
            Text(it, color = Color(0xFFD33), modifier = Modifier.padding(top = 8.dp))
        }
    }
}
