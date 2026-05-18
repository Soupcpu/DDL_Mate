package com.deadlinemate.ui.recognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

private const val SHERPA_ASSET_MODEL_DIR = "sherpa-onnx-paraformer-zh-small-2024-03-09"
private const val SHERPA_SAMPLE_RATE = 16_000

class SherpaLocalSpeechRecognizer(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isRecording = AtomicBoolean(false)
    private val samples = Collections.synchronizedList(mutableListOf<Float>())
    private var recognizer: OfflineRecognizer? = null
    private var recorder: AudioRecord? = null
    private var finalCallback: ((String) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null

    fun hasBundledModel(): Boolean {
        return runCatching {
            listOf(
                "$SHERPA_ASSET_MODEL_DIR/model.int8.onnx",
                "$SHERPA_ASSET_MODEL_DIR/tokens.txt"
            ).all { asset ->
                context.assets.open(asset).use { true }
            }
        }.getOrDefault(false)
    }

    suspend fun prepare(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasBundledModel()) {
                error("缺少 Sherpa-ONNX 中文 Paraformer 模型。")
            }
            if (recognizer == null) {
                recognizer = OfflineRecognizer(
                    assetManager = context.assets,
                    config = OfflineRecognizerConfig(
                        featConfig = FeatureConfig(
                            sampleRate = SHERPA_SAMPLE_RATE,
                            featureDim = 80
                        ),
                        modelConfig = OfflineModelConfig(
                            paraformer = OfflineParaformerModelConfig(
                                model = "$SHERPA_ASSET_MODEL_DIR/model.int8.onnx"
                            ),
                            tokens = "$SHERPA_ASSET_MODEL_DIR/tokens.txt",
                            numThreads = 2,
                            modelType = "paraformer"
                        )
                    )
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            onError("缺少麦克风权限。")
            return
        }
        val currentRecognizer = recognizer
        if (currentRecognizer == null) {
            onError("Sherpa-ONNX 模型尚未加载。")
            return
        }
        stop(decode = false)
        finalCallback = onFinal
        errorCallback = onError
        samples.clear()

        val minBufferSize = AudioRecord.getMinBufferSize(
            SHERPA_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            onError("系统无法创建麦克风输入缓冲区。")
            return
        }

        val bufferSize = minBufferSize * 2
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SHERPA_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            onError("系统麦克风录音器初始化失败。")
            return
        }

        recorder = audioRecord
        isRecording.set(true)
        onPartial("正在本地录音，松开或点击停止后开始识别。")
        scope.launch {
            val buffer = ShortArray(bufferSize / 2)
            runCatching {
                audioRecord.startRecording()
                while (isRecording.get()) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        for (index in 0 until read) {
                            samples.add(buffer[index] / 32768.0f)
                        }
                    }
                }
            }.onFailure {
                errorCallback?.invoke("本地录音失败：${it.javaClass.simpleName}")
            }
        }
    }

    fun stop() {
        stop(decode = true)
    }

    private fun stop(decode: Boolean) {
        val wasRecording = isRecording.getAndSet(false)
        val currentRecorder = recorder
        recorder = null
        if (currentRecorder != null) {
            runCatching { currentRecorder.stop() }
            currentRecorder.release()
        }
        if (decode && wasRecording) {
            decodeCollectedSamples()
        }
    }

    private fun decodeCollectedSamples() {
        val currentRecognizer = recognizer ?: run {
            errorCallback?.invoke("Sherpa-ONNX 模型尚未加载。")
            return
        }
        val snapshot = synchronized(samples) { samples.toFloatArray() }
        if (snapshot.size < SHERPA_SAMPLE_RATE / 3) {
            finalCallback?.invoke("")
            return
        }
        scope.launch {
            runCatching {
                val stream = currentRecognizer.createStream()
                try {
                    stream.acceptWaveform(snapshot, SHERPA_SAMPLE_RATE)
                    currentRecognizer.decode(stream)
                    currentRecognizer.getResult(stream).text.trim()
                } finally {
                    stream.release()
                }
            }.onSuccess {
                finalCallback?.invoke(it)
            }.onFailure {
                errorCallback?.invoke("Sherpa-ONNX 本地识别失败：${it.javaClass.simpleName}")
            }
        }
    }

    fun shutdown() {
        stop(decode = false)
        recognizer?.release()
        recognizer = null
        scope.cancel()
    }
}
