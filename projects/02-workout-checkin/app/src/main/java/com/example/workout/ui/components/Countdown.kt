package com.example.workout.ui.components

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workout.audio.FinishSoundPreference
import com.example.workout.audio.SoundHelper
import kotlinx.coroutines.delay

@Composable
fun Countdown(
    defaultSeconds: Int,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 组合训练模式下：
     * - 不显示自定义秒数输入和重置按钮
     * - 最后3秒滴声
     * - 完成后自动衔接下一个（但需用户点击「开始」启动倒计时）
     */
    autoFinish: Boolean = false,
) {
    val context = LocalContext.current
    var seconds by remember { mutableIntStateOf(defaultSeconds) }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf("") }

    fun triggerFinish() {
        running = false
        finished = true

        // 读取用户提示音偏好
        val soundType = FinishSoundPreference.getFinishSoundType(context)
        val vibrationEnabled = FinishSoundPreference.isVibrationEnabled(context)

        // 震动提示（用户可关闭）
        if (vibrationEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(VibratorManager::class.java)
                    vm?.defaultVibrator?.let { vib ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
                        } else {
                            @Suppress("DEPRECATION")
                            vib.vibrate(longArrayOf(0, 400, 200, 400), -1)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val vib = context.getSystemService(Vibrator::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vib?.vibrate(longArrayOf(0, 400, 200, 400), -1)
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 提示音（按用户选择的类型播放，内部异步）
        try {
            SoundHelper.playFinishSound(soundType)
        } catch (_: Exception) {
        }
        onFinished()
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        finished = false
        while (seconds > 0) {
            delay(1000)
            seconds--
            // 最后 3 秒播放滴声
            if (seconds in 1..3) {
                try {
                    SoundHelper.playCountdownTick()
                } catch (_: Exception) {
                }
            }
        }
        triggerFinish()
    }

    DisposableEffect(Unit) {
        onDispose { running = false }
    }

    val cardColor = if (finished) {
        Color(0xFFE8F5E9)
    } else if (running) {
        Color(0xFFFFF8E1)
    } else {
        Color.White
    }

    val border = if (finished) {
        Modifier.border(2.dp, Color(0xFF4CAF50), RoundedCornerShape(8.dp))
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cardColor, RoundedCornerShape(8.dp))
            .then(border)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "%02d:%02d".format(seconds / 60, seconds % 60),
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = if (seconds <= 3 && running) Color(0xFFD32F2F) else if (finished) Color(0xFF2E7D32) else Color.Black,
        )

        if (finished) {
            Text(
                "🎉 运动完成！",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        if (!autoFinish) {
            // ── 单运动模式：自定义秒数 + 开始/重置按钮 ──
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text("自定义秒数（可选）") },
                enabled = !running,
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !running, onClick = {
                    val total = custom.toIntOrNull() ?: defaultSeconds
                    seconds = total.coerceAtLeast(1)
                    running = true
                    finished = false
                }) {
                    Text(if (finished) "再来一次" else "开始倒计时")
                }
                OutlinedButton(onClick = {
                    running = false
                    finished = false
                    seconds = defaultSeconds
                    custom = ""
                }) {
                    Text("重置")
                }
            }
        } else {
            // ── 组合训练模式：用户点击「开始」后启动倒计时 ──
            if (!running && !finished) {
                Button(
                    onClick = {
                        running = true
                        finished = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("开始倒计时", fontSize = 16.sp)
                }
            } else if (running) {
                Text(
                    if (seconds <= 3) "⏰ $seconds 秒..." else "运动中，加油！",
                    fontSize = 14.sp,
                    color = if (seconds <= 3) Color(0xFFD32F2F) else Color(0xFF888888),
                )
            }
        }
    }
}
