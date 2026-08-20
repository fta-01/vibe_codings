package com.example.workout.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workout.audio.SoundHelper
import com.example.workout.data.exercise.PlanItemType
import com.example.workout.data.exercise.WorkoutPlan
import kotlinx.coroutines.delay

/**
 * 组合训练执行器
 *
 * 依次执行 plan.items 中的每一项：
 * - EXERCISE + isTimed: 自动倒计时（autoFinish 模式）
 * - EXERCISE + 非 timed: 手动确认完成
 * - REST: 倒计时休息，开始和结束都有提示音
 */
@Composable
fun WorkoutPlanRunner(
    plan: WorkoutPlan,
    onAllFinished: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** 今日已打卡时，完成对话框不再出现「立即打卡」入口 */
    todayChecked: Boolean = false,
) {
    val context = LocalContext.current
    var currentIndex by remember { mutableIntStateOf(0) }
    var showFinishDialog by remember { mutableStateOf(false) }

    val items = plan.items
    val currentItem = items.getOrNull(currentIndex)

    if (currentItem == null) {
        LaunchedEffect(Unit) {
            showFinishDialog = true
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("🎉 组合训练完成！") },
            text = {
                Text(
                    if (todayChecked) {
                        "「${plan.name}」全部动作已完成！\n" +
                            "运动时长 ${plan.exerciseMinutesDisplay}，" +
                            "总时长 ${plan.totalMinutesDisplay}。\n" +
                            "今日已打卡，无需重复打卡。"
                    } else {
                        "「${plan.name}」全部动作已完成！\n" +
                            "运动时长 ${plan.exerciseMinutesDisplay}，" +
                            "总时长 ${plan.totalMinutesDisplay}。\n" +
                            "点击下方按钮立即记录本次打卡。"
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    showFinishDialog = false
                    onAllFinished()
                }) { Text(if (todayChecked) "完成" else "✅ 立即打卡") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFinishDialog = false
                    onDismiss()
                }) { Text(if (todayChecked) "关闭" else "暂不打卡") }
            },
        )
        return
    }

    val totalItems = items.size
    val completedCount = currentIndex

    fun advance() {
        currentIndex++
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 组合标题 + 进度
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                plan.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${completedCount}/${totalItems}",
                fontSize = 14.sp,
                color = Color(0xFF888888),
            )
        }

        // 进度条
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(totalItems) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .size(height = 6.dp, width = 0.dp)
                        .background(
                            when {
                                i < completedCount -> Color(0xFF4CAF50)
                                i == currentIndex && currentItem.type == PlanItemType.REST -> Color(0xFFFFB74D)
                                i == currentIndex -> Color(0xFF2196F3)
                                else -> Color(0xFFE0E0E0)
                            },
                            RoundedCornerShape(3.dp),
                        ),
                )
            }
        }

        // ── 休息节点 ──
        if (currentItem.type == PlanItemType.REST) {
            RestTimer(
                restSeconds = currentItem.restSeconds ?: 15,
                onFinished = { advance() },
                onSkip = { advance() },
            )
        } else {
            // ── 运动动作 ──
            val exercise = currentItem.exercise!!
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(
                                    when (currentItem.label) {
                                        "热身" -> Color(0xFFFFF3E0)
                                        "主运动" -> Color(0xFFE3F2FD)
                                        "拉伸" -> Color(0xFFF3E5F5)
                                        else -> Color(0xFFF0F0F0)
                                    },
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                currentItem.label,
                                fontSize = 12.sp,
                                color = Color(0xFF666666),
                            )
                        }
                        Text(
                            "  第 ${currentIndex + 1} 项 / 共 ${totalItems} 项",
                            fontSize = 12.sp,
                            color = Color(0xFF999999),
                        )
                    }

                    Text(
                        exercise.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        exercise.summary,
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // 步骤列表：限制最多显示 3 条，超出显示省略
                    val maxSteps = 3
                    exercise.steps.forEachIndexed { i, step ->
                        if (i < maxSteps) {
                            Text(
                                "${i + 1}. $step",
                                fontSize = 13.sp,
                                color = Color(0xFF555555),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (exercise.steps.size > maxSteps) {
                        Text(
                            "...（更多步骤见运动库详情）",
                            fontSize = 12.sp,
                            color = Color(0xFFAAAAAA),
                        )
                    }

                    if (!exercise.isTimed) {
                        exercise.reps?.let {
                            Text(
                                "📊 $it",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1976D2),
                            )
                        }
                    }
                }
            }

            // 计时型运动：autoFinish 模式倒计时（自动启动）
            if (exercise.isTimed) {
                Countdown(
                    defaultSeconds = exercise.defaultSeconds ?: 60,
                    onFinished = { advance() },
                    autoFinish = true,
                )
            } else {
                Button(
                    onClick = { advance() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Text("  完成此动作，下一个")
                }
            }
        }

        // 跳过/退出
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(onClick = { advance() }) {
                Text("跳过")
            }
            OutlinedButton(onClick = onDismiss) {
                Text("退出组合")
            }
        }

        // ── 接下来的项目预览（固定区域，背景区分）──
        val nextItems = items.drop(currentIndex + 1).take(3)
        if (nextItems.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "接下来：",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF666666),
                )
                nextItems.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (item.type == PlanItemType.REST) Color(0xFFFFB74D)
                                    else Color(0xFFBDBDBD),
                                    CircleShape,
                                ),
                        )
                        Text(
                            if (item.type == PlanItemType.REST) {
                                "☕ 休息 ${item.restSeconds} 秒"
                            } else {
                                val ex = item.exercise!!
                                "${ex.name}（${item.label}）" +
                                    if (ex.isTimed) " · ${ex.defaultSeconds}秒"
                                    else " · ${ex.reps ?: ""}"
                            },
                            fontSize = 13.sp,
                            color = if (item.type == PlanItemType.REST) Color(0xFFE65100) else Color(0xFF888888),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 休息计时器 —— 显示倒计时，开始和结束有提示音
 */
@Composable
private fun RestTimer(
    restSeconds: Int,
    onFinished: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    var seconds by remember { mutableIntStateOf(restSeconds) }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    // 自动开始休息倒计时
    LaunchedEffect(Unit) {
        // 休息开始提示音（内部异步）
        try {
            SoundHelper.playRestStartSound()
        } catch (_: Exception) {
        }
        running = true
        while (seconds > 0) {
            delay(1000)
            seconds--
            if (seconds in 1..3) {
                try {
                    SoundHelper.playCountdownTick()
                } catch (_: Exception) {
                }
            }
        }
        running = false
        finished = true
        // 休息结束提示音
        try {
            SoundHelper.playRestEndSound()
        } catch (_: Exception) {
        }
        // 短暂延迟让用户看到"休息结束"后自动进入下一个
        delay(800)
        onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF3E0), RoundedCornerShape(8.dp))
            .border(2.dp, Color(0xFFFFB74D), RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Pause,
                contentDescription = null,
                tint = Color(0xFFE65100),
                modifier = Modifier.size(28.dp),
            )
            Text(
                "  休息时间",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE65100),
            )
        }

        Text(
            "%02d:%02d".format(seconds / 60, seconds % 60),
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = if (seconds <= 3) Color(0xFFD32F2F) else Color(0xFFE65100),
        )

        if (finished) {
            Text(
                "休息结束，马上开始下一个！",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
            )
        } else if (running) {
            Text(
                if (seconds <= 3) "⏰ ${seconds} 秒后开始..." else "深呼吸，放松一下",
                fontSize = 14.sp,
                color = if (seconds <= 3) Color(0xFFD32F2F) else Color(0xFF888888),
            )
        }

        OutlinedButton(onClick = onSkip) {
            Text("跳过休息")
        }
    }
}
