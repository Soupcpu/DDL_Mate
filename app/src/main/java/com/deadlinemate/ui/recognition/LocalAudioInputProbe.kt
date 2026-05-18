package com.deadlinemate.ui.recognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

data class MicProbeResult(
    val maxAmplitude: Int,
    val rms: Double,
    val samples: Int
) {
    val hasAudibleSignal: Boolean = maxAmplitude >= 800 || rms >= 180.0
}

@SuppressLint("MissingPermission")
suspend fun probeMicrophoneInput(
    context: Context,
    durationMs: Long = 3_000L
): Result<MicProbeResult> = withContext(Dispatchers.IO) {
    runCatching {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            error("缺少麦克风权限。")
        }

        val sampleRate = 16_000
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) error("系统无法创建麦克风输入缓冲区。")

        val bufferSize = minBufferSize * 2
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("系统麦克风录音器初始化失败。")
        }

        val buffer = ShortArray(bufferSize / 2)
        var maxAmplitude = 0
        var sumSquares = 0.0
        var sampleCount = 0
        val endAt = System.currentTimeMillis() + durationMs

        try {
            recorder.startRecording()
            while (System.currentTimeMillis() < endAt) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    for (i in 0 until read) {
                        val abs = kotlin.math.abs(buffer[i].toInt())
                        if (abs > maxAmplitude) maxAmplitude = abs
                        sumSquares += abs.toDouble() * abs.toDouble()
                    }
                    sampleCount += read
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }

        val rms = if (sampleCount > 0) sqrt(sumSquares / sampleCount) else 0.0
        MicProbeResult(maxAmplitude = maxAmplitude, rms = rms, samples = sampleCount)
    }
}
