package com.hsr.railfocus.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * 旅行手账语音录制工具类
 */
class JournalAudioRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var startRealtimeMs: Long = 0L
    private var currentOutputFile: File? = null

    val isRecording: Boolean
        get() = mediaRecorder != null

    fun start(outputFile: File): Boolean {
        return try {
            cancel()
            outputFile.parentFile?.mkdirs()
            currentOutputFile = outputFile

            val recorder = if (Build.VERSION.SDK_INT >= 31) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            startRealtimeMs = SystemClock.elapsedRealtime()
            mediaRecorder = recorder
            true
        } catch (_: Exception) {
            cancel()
            false
        }
    }

    /**
     * 结束录音，返回录制时长（秒），若失败返回 0
     */
    fun stop(): Int {
        val recorder = mediaRecorder ?: return 0
        return try {
            val durationSec = ((SystemClock.elapsedRealtime() - startRealtimeMs) / 1000L).toInt().coerceAtLeast(1)
            recorder.stop()
            recorder.release()
            mediaRecorder = null
            durationSec
        } catch (_: Exception) {
            cancel()
            0
        }
    }

    fun cancel() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {}
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        currentOutputFile?.let {
            if (it.exists()) {
                it.delete()
            }
        }
        currentOutputFile = null
        startRealtimeMs = 0L
    }
}

