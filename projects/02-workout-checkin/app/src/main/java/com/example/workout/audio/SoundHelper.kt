package com.example.workout.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * 提示音工具 —— 纯合成音，零外部依赖，任何 ROM 都能播放
 *
 * 播放引擎：AudioTrack MODE_STREAM 流式播放（先 play 后分块 write），
 * USAGE_MEDIA 跟随媒体音量。曾尝试 RingtoneManager 播放系统铃声，
 * 在部分 ROM 上静默失败，已弃用。
 *
 * 音符频率参考：
 * C5=523.25  D5=587.33  E5=659.25  F5=698.46  G5=783.99  A5=880  B5=987.77  C6=1046.50
 */
object SoundHelper {

    private const val TAG = "SoundHelper"
    private const val SAMPLE_RATE = 44100

    enum class FinishSoundType(val displayName: String) {
        BEEP("蜂鸣音（三声上升）"),
        SMS("消息音（两短声）"),
        BELL("铃音（长鸣渐弱）"),
        WATER_DROP("水滴音（叮咚）"),
        MELODY("小星星旋律"),
        SCALE_UP("上升音阶（哆咪嗦哆）"),
        SWEEP("电子上滑音"),
        DRUM("鼓点（咚咚咚）"),
        CHORD("三和弦（C-E-G）"),
        MARIMBA("马林巴琴音"),
        VIBRATION_ONLY("仅震动（无声）"),
    }

    // ─────────────────────── 波形生成 ───────────────────────

    /** 生成正弦波 PCM 数据（简单淡入淡出包络，避免爆音） */
    private fun generateTone(freqHz: Double, durationMs: Int, amplitude: Double = 0.8): ShortArray {
        val numSamples = SAMPLE_RATE * durationMs / 1000
        val samples = ShortArray(numSamples)
        val twoPiFreq = 2.0 * PI * freqHz
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val raw = sin(twoPiFreq * t).toFloat()
            val env = when {
                i < numSamples * 0.05 -> (i.toDouble() / (numSamples * 0.05))
                i > numSamples * 0.9 -> ((numSamples - i).toDouble() / (numSamples * 0.1))
                else -> 1.0
            }
            samples[i] = (raw * amplitude * env * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    /** 生成带指数衰减包络的正弦波（模拟铃声/琴音/打击乐质感） */
    private fun generateDecayTone(freqHz: Double, durationMs: Int, amplitude: Double = 0.8, decayRate: Double = 3.0): ShortArray {
        val numSamples = SAMPLE_RATE * durationMs / 1000
        val samples = ShortArray(numSamples)
        val twoPiFreq = 2.0 * PI * freqHz
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val raw = sin(twoPiFreq * t).toFloat()
            val env = kotlin.math.exp(-decayRate * t)
            val attack = if (i < numSamples * 0.03) i.toDouble() / (numSamples * 0.03) else 1.0
            samples[i] = (raw * amplitude * env * attack * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    /** 混合多个频率生成和弦 */
    private fun generateChord(freqs: List<Double>, durationMs: Int, amplitude: Double = 0.5): ShortArray {
        val numSamples = SAMPLE_RATE * durationMs / 1000
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var sum = 0.0
            for (freq in freqs) {
                sum += sin(2.0 * PI * freq * t)
            }
            sum /= freqs.size
            val env = when {
                i < numSamples * 0.05 -> (i.toDouble() / (numSamples * 0.05))
                i > numSamples * 0.9 -> ((numSamples - i).toDouble() / (numSamples * 0.1))
                else -> 1.0
            }
            samples[i] = (sum * amplitude * env * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    /** 频率扫描（相位累加保证频率连续过渡）：水滴下滑音、电子上滑音 */
    private fun generateSweep(startFreq: Double, endFreq: Double, durationMs: Int, amplitude: Double = 0.7): ShortArray {
        val numSamples = SAMPLE_RATE * durationMs / 1000
        val samples = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = startFreq + (endFreq - startFreq) * progress
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val raw = sin(phase).toFloat()
            val env = when {
                i < numSamples * 0.05 -> (i.toDouble() / (numSamples * 0.05))
                i > numSamples * 0.9 -> ((numSamples - i).toDouble() / (numSamples * 0.1))
                else -> 1.0
            }
            samples[i] = (raw * amplitude * env * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    /** 旋律拼接：音符列表 (频率, 时长ms)，音符间留 30ms 静音间隙 */
    private fun generateMelody(notes: List<Pair<Double, Int>>, amplitude: Double = 0.7): ShortArray {
        val gap = ShortArray(SAMPLE_RATE * 30 / 1000)
        var result = ShortArray(0)
        notes.forEachIndexed { index, (freq, durationMs) ->
            if (index > 0) result += gap
            result += generateTone(freq, durationMs, amplitude)
        }
        return result
    }

    /** 拼接多段采样（段间可插静音） */
    private fun concat(vararg parts: ShortArray): ShortArray {
        var result = ShortArray(0)
        for (p in parts) result += p
        return result
    }

    private fun silence(durationMs: Int): ShortArray = ShortArray(SAMPLE_RATE * durationMs / 1000)

    // ─────────────────────── 播放引擎 ───────────────────────

    /**
     * 用 AudioTrack MODE_STREAM 流式播放 PCM 采样。
     * 内部自带后台线程，可直接在主线程调用。
     */
    private fun playSamplesAsync(samples: ShortArray) {
        Thread {
            var track: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                if (minBuf <= 0) {
                    Log.w(TAG, "getMinBufferSize invalid: $minBuf")
                    return@Thread
                }
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuf, 8192))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                var offset = 0
                while (offset < samples.size) {
                    val written = track.write(samples, offset, samples.size - offset)
                    if (written <= 0) break
                    offset += written
                }
                // 等待已写入的数据播完（轮询播放头，最多等时长+1s）
                val totalFrames = samples.size
                val waitMaxMs = samples.size * 1000L / SAMPLE_RATE + 1000
                var waited = 0L
                while (waited < waitMaxMs) {
                    if (track.playbackHeadPosition >= totalFrames) break
                    Thread.sleep(50)
                    waited += 50
                }
            } catch (e: Exception) {
                Log.w(TAG, "playSamples failed", e)
            } finally {
                try { track?.stop() } catch (_: Exception) {}
                try { track?.release() } catch (_: Exception) {}
            }
        }.start()
    }

    // ─────────────────────── 对外接口 ───────────────────────

    /** 根据用户选择的类型播放完成提示音（内部异步，可在主线程直接调用） */
    fun playFinishSound(type: FinishSoundType = FinishSoundType.BEEP) {
        when (type) {
            FinishSoundType.BEEP -> {
                // 三声上升：C5 → E5 → G5
                val parts = listOf(
                    generateTone(523.25, 200, 0.7),
                    generateTone(659.25, 200, 0.7),
                    generateTone(783.99, 300, 0.8),
                )
                playSamplesAsync(parts.reduce { acc, arr -> acc + arr })
            }
            FinishSoundType.SMS -> {
                // 仿消息提示：两声短促 1000Hz，中间 80ms 静音
                val beep = generateTone(1000.0, 150, 0.7)
                playSamplesAsync(concat(beep, silence(80), beep))
            }
            FinishSoundType.BELL -> {
                // 仿铃声：基音 880Hz + 泛音 1320Hz 同时衰减，长鸣渐弱
                val base = generateDecayTone(880.0, 900, 0.7, decayRate = 2.5)
                val harmonic = generateDecayTone(1320.0, 900, 0.25, decayRate = 3.5)
                val mixed = ShortArray(base.size) { i ->
                    ((base[i].toInt() + harmonic[i].toInt()) / 2).toShort()
                }
                playSamplesAsync(mixed)
            }
            FinishSoundType.WATER_DROP -> {
                // 水滴「叮-咚」：两次高频快速下滑
                playSamplesAsync(
                    concat(
                        generateSweep(1500.0, 800.0, 120, 0.7),
                        silence(100),
                        generateSweep(1000.0, 500.0, 160, 0.7),
                    )
                )
            }
            FinishSoundType.MELODY -> {
                // 小星星开头：C C G G A A G(--)
                playSamplesAsync(
                    generateMelody(
                        listOf(
                            523.25 to 250, 523.25 to 250, 783.99 to 250, 783.99 to 250,
                            880.0 to 250, 880.0 to 250, 783.99 to 500,
                        )
                    )
                )
            }
            FinishSoundType.SCALE_UP -> {
                // 上升音阶：哆咪嗦哆（终止感强）
                playSamplesAsync(
                    generateMelody(
                        listOf(
                            523.25 to 150, 659.25 to 150, 783.99 to 150, 1046.50 to 400,
                        )
                    )
                )
            }
            FinishSoundType.SWEEP -> {
                // 电子上滑音：400→1300Hz 扫描 + 高频短尾音
                playSamplesAsync(
                    concat(
                        generateSweep(400.0, 1300.0, 350, 0.6),
                        generateTone(1300.0, 150, 0.7),
                    )
                )
            }
            FinishSoundType.DRUM -> {
                // 鼓点三连击：200Hz 快速衰减脉冲，间隔 120ms
                val hit = generateDecayTone(200.0, 220, 0.95, decayRate = 14.0)
                playSamplesAsync(concat(hit, silence(120), hit, silence(120), hit))
            }
            FinishSoundType.CHORD -> {
                // C-E-G 三和弦同时响起
                playSamplesAsync(generateChord(listOf(523.25, 659.25, 783.99), 400, 0.5))
            }
            FinishSoundType.MARIMBA -> {
                // 马林巴琴音：440Hz+880Hz 衰减叠加 → 660Hz 衰减
                val base = generateDecayTone(440.0, 300, 0.6, decayRate = 4.0)
                val harm = generateDecayTone(880.0, 300, 0.3, decayRate = 5.0)
                val mixed = ShortArray(base.size) { i ->
                    ((base[i].toInt() + harm[i].toInt()) / 2).toShort()
                }
                playSamplesAsync(mixed + generateDecayTone(660.0, 300, 0.5, decayRate = 4.0))
            }
            FinishSoundType.VIBRATION_ONLY -> {
                // 不播放声音，仅震动由调用方组件处理
            }
        }
    }

    /** 最后 3 秒滴声：短促 100ms，880Hz */
    fun playCountdownTick() {
        playSamplesAsync(generateTone(880.0, 100, 0.5))
    }

    /** 休息结束：两声提示（440Hz → 660Hz） */
    fun playRestEndSound() {
        val s1 = generateTone(440.0, 200, 0.6)
        val s2 = generateTone(660.0, 300, 0.7)
        playSamplesAsync(s1 + s2)
    }

    /** 休息开始：一声较低音 300Hz，200ms */
    fun playRestStartSound() {
        playSamplesAsync(generateTone(300.0, 200, 0.5))
    }
}
