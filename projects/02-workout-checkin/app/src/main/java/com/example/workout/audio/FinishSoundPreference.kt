package com.example.workout.audio

import android.content.Context

/**
 * 提示音偏好管理 —— 用 SharedPreferences 持久化用户选择的完成提示音类型
 */
object FinishSoundPreference {

    private const val PREFS_NAME = "workout_prefs"
    private const val KEY_FINISH_SOUND = "finish_sound_type"
    private const val KEY_VIBRATION_ENABLED = "vibration_enabled"

    /** 读取用户选择的完成提示音类型，默认蜂鸣音 */
    fun getFinishSoundType(context: Context): SoundHelper.FinishSoundType {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_FINISH_SOUND, SoundHelper.FinishSoundType.BEEP.name)
        return runCatching { SoundHelper.FinishSoundType.valueOf(name!!) }.getOrDefault(SoundHelper.FinishSoundType.BEEP)
    }

    /** 保存用户选择的完成提示音类型 */
    fun setFinishSoundType(context: Context, type: SoundHelper.FinishSoundType) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FINISH_SOUND, type.name)
            .apply()
    }

    /** 读取是否启用震动，默认启用 */
    fun isVibrationEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
    }

    /** 保存是否启用震动 */
    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VIBRATION_ENABLED, enabled)
            .apply()
    }
}
