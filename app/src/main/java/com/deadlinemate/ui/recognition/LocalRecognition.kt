package com.deadlinemate.ui.recognition

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun recognizeImageText(context: Context, uri: Uri): String {
    val image = InputImage.fromFilePath(context, uri)
    val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    return suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { continuation.resume(it.text) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }
}

fun speechIntent(language: String): Intent {
    val locale = when (language) {
        "English" -> "en-US"
        else -> "zh-CN"
    }
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }
}

fun speechIntent(context: Context, language: String): Intent {
    return speechIntent(language).apply {
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }
}

fun speechRecognitionAvailable(context: Context): Boolean {
    return SpeechRecognizer.isRecognitionAvailable(context)
}

fun onDeviceSpeechRecognitionAvailable(context: Context): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
}

fun createAppSpeechRecognizer(context: Context, language: String): SpeechRecognizer {
    return if (language == "English" && onDeviceSpeechRecognitionAvailable(context)) {
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
    } else {
        SpeechRecognizer.createSpeechRecognizer(context)
    }
}

fun speechRecognitionMode(context: Context, language: String, englishUi: Boolean): String {
    return if (language == "English" && onDeviceSpeechRecognitionAvailable(context)) {
        if (englishUi) "On-device English" else "英文离线本地识别"
    } else {
        if (englishUi) "System recognizer, offline preferred" else "系统识别服务，优先离线"
    }
}

fun speechRecognitionServiceName(context: Context): String {
    return Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
        ?: "未设置"
}

fun speechErrorMessage(error: Int, english: Boolean): String {
    return when (error) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> if (english) {
            "Error 1: network timeout. Try again or use manual input."
        } else {
            "错误码 1：网络超时。请重试或改用手动输入。"
        }
        SpeechRecognizer.ERROR_NETWORK -> if (english) {
            "Error 2: network error. Check the recognizer service or network."
        } else {
            "错误码 2：网络错误。请检查系统语音服务或网络。"
        }
        SpeechRecognizer.ERROR_AUDIO -> if (english) {
            "Error 3: audio recording failed. Check microphone permission, emulator audio input, or another app using the mic."
        } else {
            "错误码 3：音频录制失败。请检查麦克风权限、模拟器音频输入，或是否有其他应用占用麦克风。"
        }
        SpeechRecognizer.ERROR_SERVER -> if (english) {
            "Error 4: recognizer service error. Try again later or switch recognizer service."
        } else {
            "错误码 4：系统识别服务异常。请稍后重试或切换语音识别服务。"
        }
        SpeechRecognizer.ERROR_CLIENT -> if (english) {
            "Error 5: recognizer client error. This usually means the speech service is unavailable, start/stop was called too quickly, or the recognizer needs to be restarted."
        } else {
            "错误码 5：识别客户端错误。通常是系统语音服务不可用、开始/停止过快，或识别器需要重启。"
        }
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (english) {
            "Error 6: no speech was detected. Speak closer to the microphone."
        } else {
            "错误码 6：没有检测到语音。请靠近麦克风后重试。"
        }
        SpeechRecognizer.ERROR_NO_MATCH -> if (english) {
            "Error 7: speech was heard but no text matched. Try speaking more clearly."
        } else {
            "错误码 7：听到了声音但没有匹配到文字。请说清楚后重试。"
        }
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> if (english) {
            "Error 8: recognizer is busy. Stop and retry after a moment."
        } else {
            "错误码 8：识别器忙。请停止后稍等再试。"
        }
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> if (english) {
            "Error 9: microphone permission is missing."
        } else {
            "错误码 9：缺少麦克风权限。"
        }
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> if (english) {
            "Error 10: too many recognition requests. Wait a moment and try again."
        } else {
            "错误码 10：识别请求过于频繁。请稍等后重试。"
        }
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> if (english) {
            "Error 11: recognizer service disconnected. Restart recognition or reopen the app."
        } else {
            "错误码 11：识别服务已断开。请重新开始识别，必要时重启 App。"
        }
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> if (english) {
            "Error 12: the current recognizer does not support this language. Switch recognition language or install a matching speech recognition service."
        } else {
            "错误码 12：当前识别器不支持所选语言。请切换识别语言，或安装/启用支持中文的系统语音识别服务。"
        }
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> if (english) {
            "Error 13: this language is supported but not available now. Download the language pack or use another recognizer."
        } else {
            "错误码 13：该语言理论支持，但当前不可用。请下载对应离线语音包或更换识别服务。"
        }
        SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> if (english) {
            "Error 14: the recognizer cannot check language support."
        } else {
            "错误码 14：当前识别服务不允许检查语言支持情况。"
        }
        SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> if (english) {
            "Error 15: the recognizer cannot listen for language model download events."
        } else {
            "错误码 15：当前识别服务不支持监听语言模型下载状态。"
        }
        else -> if (english) {
            "Speech recognition failed. Error code: $error"
        } else {
            "语音识别失败，错误码：$error"
        }
    }
}
