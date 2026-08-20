package com.example.workout.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workout.audio.FinishSoundPreference
import com.example.workout.audio.SoundHelper

/**
 * 提示音设置对话框
 *
 * - 选择倒计时结束的提示音类型（11 种纯合成音）
 * - 开关震动
 * - 点击试听
 * - 音效较多，列表可滚动
 */
@Composable
fun FinishSoundSettingsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selectedType by remember {
        mutableStateOf(FinishSoundPreference.getFinishSoundType(context))
    }
    var vibrationEnabled by remember {
        mutableStateOf(FinishSoundPreference.isVibrationEnabled(context))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF1976D2))
                Text("  倒计时结束提示", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("提示音类型", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF666666))
                SoundHelper.FinishSoundType.entries.forEach { type ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                FinishSoundPreference.setFinishSoundType(context, type)
                            },
                        )
                        Text(
                            type.displayName,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        // 试听按钮（playFinishSound 内部异步播放，可直接调用）
                        TextButton(
                            onClick = {
                                SoundHelper.playFinishSound(type)
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text("试听", fontSize = 13.sp)
                        }
                    }
                }

                // 震动开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("震动提示", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = {
                            vibrationEnabled = it
                            FinishSoundPreference.setVibrationEnabled(context, it)
                        },
                    )
                }

                Text(
                    "提示音和震动在倒计时归零时同时触发。所有提示音均为合成音，跟随媒体音量，若试听无声请调高媒体音量。",
                    fontSize = 12.sp,
                    color = Color(0xFFAAAAAA),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}
