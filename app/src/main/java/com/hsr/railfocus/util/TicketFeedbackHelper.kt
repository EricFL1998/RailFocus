package com.hsr.railfocus.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.hsr.railfocus.R

/**
 * 进站检票、验票与车票动效的声学与触觉反馈工具类
 */
object TicketFeedbackHelper {
    @Volatile
    private var soundPool: SoundPool? = null
    private var punchSoundId: Int = 0
    private var isPunchLoaded: Boolean = false

    @Synchronized
    fun preload(context: Context) {
        if (soundPool == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(audioAttributes)
                .build()

            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0 && sampleId == punchSoundId) {
                    isPunchLoaded = true
                }
            }

            punchSoundId = pool.load(context.applicationContext, R.raw.ticket_punch, 1)
            soundPool = pool
        }
    }

    /**
     * 播放短促清脆的检票剪刀机械打孔/闸机刷票音效
     */
    fun playPunchSound(context: Context) {
        try {
            if (soundPool == null) {
                preload(context)
            }
            soundPool?.let { pool ->
                if (punchSoundId != 0) {
                    pool.play(punchSoundId, 1f, 1f, 1, 0, 1f)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * 触发细腻的线性马达机械剪切双脉冲触感反馈
     */
    fun performPunchHaptic(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 模拟机械打孔钳剪穿厚卡纸的机械阻尼感：
                // 18ms 预咬合 -> 12ms 阻尼停顿 -> 32ms 强力切断
                val timings = longArrayOf(0, 18, 12, 32)
                val amplitudes = intArrayOf(0, 140, 0, 255)
                if (vibrator.hasAmplitudeControl()) {
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 18, 12, 32), -1)
            }
        } catch (_: Exception) {}
    }
}

