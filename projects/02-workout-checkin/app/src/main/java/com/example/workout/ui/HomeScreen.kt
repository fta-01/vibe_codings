package com.example.workout.ui

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workout.audio.FinishSoundPreference
import com.example.workout.audio.SoundHelper
import com.example.workout.ui.components.CheckinForm
import com.example.workout.ui.components.StreakBadge
import com.example.workout.viewmodel.HomeViewModel

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 打卡成功：播放用户选择的完成提示音 + 震动
    LaunchedEffect(state.showSuccessDialog) {
        if (state.showSuccessDialog) {
            try {
                SoundHelper.playFinishSound(FinishSoundPreference.getFinishSoundType(context))
            } catch (_: Exception) {
            }
            if (FinishSoundPreference.isVibrationEnabled(context)) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vm = context.getSystemService(VibratorManager::class.java)
                        vm?.defaultVibrator?.let { vib ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
                            } else {
                                @Suppress("DEPRECATION")
                                vib.vibrate(longArrayOf(0, 300, 150, 300), -1)
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val vib = context.getSystemService(Vibrator::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
                        } else {
                            @Suppress("DEPRECATION")
                            vib?.vibrate(longArrayOf(0, 300, 150, 300), -1)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StreakBadge(streakDays = state.streakDays, todayChecked = state.todayChecked)

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            // 打卡后不隐藏表单 —— 继续显示组合训练和计时功能
            // 只把打卡按钮替换为「已打卡」不可按状态
            CheckinForm(
                exercises = state.exercises,
                todayChecked = state.todayChecked,
                onCheckIn = { exId, minutes -> viewModel.checkIn(exId, minutes) },
            )
        }

        state.message?.let {
            Text(it, color = Color(0xFFD33), fontSize = 14.sp)
        }
    }

    // ── 打卡成功反馈对话框（两条打卡路径共用）──
    if (state.showSuccessDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSuccessDialog,
            title = {
                Text(
                    "🎉 打卡成功！",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🔥", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "已连续打卡 ${state.streakDays} 天",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                    )
                    if (state.lastCheckInLabel.isNotEmpty()) {
                        val minutes = if (state.lastCheckInMinutes > 0) " · ${state.lastCheckInMinutes} 分钟" else ""
                        Text(
                            "${state.lastCheckInLabel}$minutes",
                            fontSize = 14.sp,
                            color = Color(0xFF888888),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(
                        "继续保持，加油！💪",
                        fontSize = 13.sp,
                        color = Color(0xFF999999),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::dismissSuccessDialog,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    Text("好的")
                }
            },
        )
    }
}
