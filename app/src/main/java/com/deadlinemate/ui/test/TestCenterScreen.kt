package com.deadlinemate.ui.test

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadlinemate.deepseek.DeepSeekTaskParseDebugResult
import com.deadlinemate.deepseek.buildDeepSeekTaskExtractionPrompt
import com.deadlinemate.reminder.NotificationHelper
import com.deadlinemate.reminder.ReminderScheduler
import com.deadlinemate.ui.UiSettings
import com.deadlinemate.ui.components.GlassPanel
import com.deadlinemate.ui.components.PageTopBar
import com.deadlinemate.ui.i18n.AppLanguage
import com.deadlinemate.ui.i18n.LocalAppLanguage
import com.deadlinemate.ui.i18n.text
import com.deadlinemate.ui.recognition.createAppSpeechRecognizer
import com.deadlinemate.ui.recognition.onDeviceSpeechRecognitionAvailable
import com.deadlinemate.ui.recognition.probeMicrophoneInput
import com.deadlinemate.ui.recognition.recognizeImageText
import com.deadlinemate.ui.recognition.speechErrorMessage
import com.deadlinemate.ui.recognition.speechIntent
import com.deadlinemate.ui.recognition.speechRecognitionAvailable
import com.deadlinemate.ui.recognition.speechRecognitionMode
import com.deadlinemate.ui.recognition.speechRecognitionServiceName
import com.deadlinemate.ui.recognition.SherpaLocalSpeechRecognizer
import com.deadlinemate.ui.theme.AppBg
import com.deadlinemate.ui.theme.AppBlue
import com.deadlinemate.ui.theme.AppGreen
import com.deadlinemate.ui.theme.AppLine
import com.deadlinemate.ui.theme.AppOrange
import com.deadlinemate.ui.theme.AppPurple
import com.deadlinemate.ui.theme.AppRed
import com.deadlinemate.ui.theme.AppSubtext
import com.deadlinemate.ui.theme.AppText
import com.deadlinemate.util.DateTimeUtils
import kotlinx.coroutines.launch

private enum class SpeechTestEngine { ParaformerZh, AndroidSystem }

@Composable
fun TestCenterScreen(
    settings: UiSettings,
    deepSeekStatus: String,
    pageBg: Color = AppBg,
    parseDeepSeekDebug: suspend (String) -> Result<DeepSeekTaskParseDebugResult>,
    showDeveloperEntry: Boolean,
    onSetShowDeveloperEntry: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val language = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val reminderScheduler = remember { ReminderScheduler(context.applicationContext) }
    var message by remember { mutableStateOf(language.text("测试中心已打开。", "Test center opened.")) }
    var voiceText by remember { mutableStateOf("") }
    var voiceStatus by remember { mutableStateOf(language.text("待测试", "Ready")) }
    var voiceRunning by remember { mutableStateOf(false) }
    var selectedSpeechEngine by remember { mutableStateOf(SpeechTestEngine.ParaformerZh) }
    var speechEngineExpanded by remember { mutableStateOf(false) }
    var micProbeStatus by remember { mutableStateOf(language.text("未检测", "Not tested")) }
    var micProbeRunning by remember { mutableStateOf(false) }
    var ocrText by remember { mutableStateOf("") }
    var ocrStatus by remember { mutableStateOf(language.text("待测试", "Ready")) }
    var ocrRunning by remember { mutableStateOf(false) }
    var deepSeekInput by remember { mutableStateOf("") }
    var deepSeekPrompt by remember { mutableStateOf(buildDeepSeekTaskExtractionPrompt("")) }
    var deepSeekRawResponse by remember { mutableStateOf("") }
    var deepSeekJson by remember { mutableStateOf("") }
    var deepSeekDraft by remember { mutableStateOf("") }
    var deepSeekParseStatus by remember { mutableStateOf(language.text("待测试", "Ready")) }
    var deepSeekRunning by remember { mutableStateOf(false) }
    val speechAvailable = remember { speechRecognitionAvailable(context) }
    val onDeviceSpeechAvailable = remember { onDeviceSpeechRecognitionAvailable(context) }
    val speechServiceName = remember { speechRecognitionServiceName(context) }
    val speechRecognizer = remember(settings.speechLanguage) { createAppSpeechRecognizer(context, settings.speechLanguage) }
    val sherpaRecognizer = remember { SherpaLocalSpeechRecognizer(context.applicationContext) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if (granted) language.text("通知权限已允许。", "Notification permission granted.") else language.text("通知权限被拒绝。", "Notification permission denied.")
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val status = if (granted) language.text("麦克风权限已允许，请重新点击开始识别。", "Microphone permission granted. Tap start again.") else language.text("麦克风权限被拒绝。", "Microphone permission denied.")
        voiceStatus = status
        micProbeStatus = status
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) {
            ocrStatus = language.text("未选择图片。", "No image selected.")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            ocrRunning = true
            ocrStatus = language.text("正在本地 OCR 识别...", "Running local OCR...")
            runCatching { recognizeImageText(context, uri) }
                .onSuccess {
                    ocrText = it
                    ocrStatus = if (it.isBlank()) {
                        language.text("OCR 完成，但没有识别到文字。", "OCR finished, but no text was recognized.")
                    } else {
                        language.text("OCR 识别完成。", "OCR finished.")
                    }
                }
                .onFailure {
                    ocrStatus = language.text("OCR 识别失败：${it.javaClass.simpleName}", "OCR failed: ${it.javaClass.simpleName}")
                }
            ocrRunning = false
        }
    }

    DisposableEffect(speechRecognizer, settings.speechLanguage, language) {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                voiceStatus = language.text("请开始说话。", "Start speaking.")
            }

            override fun onBeginningOfSpeech() {
                voiceStatus = language.text("正在听...", "Listening...")
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                voiceStatus = language.text("正在识别...", "Recognizing...")
            }

            override fun onError(error: Int) {
                voiceRunning = false
                voiceStatus = speechErrorMessage(error, language == AppLanguage.English)
            }

            override fun onResults(results: Bundle?) {
                voiceRunning = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                voiceText = text
                voiceStatus = if (text.isBlank()) {
                    language.text("识别完成，但没有得到文字。", "Recognition finished, but no text was returned.")
                } else {
                    language.text("语音转文字完成。", "Speech-to-text finished.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                    voiceText = it
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose { speechRecognizer.destroy() }
    }

    DisposableEffect(sherpaRecognizer) {
        onDispose { sherpaRecognizer.shutdown() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(pageBg).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageTopBar("Debug", language.text("测试中心", "Test Center"), "<", onBack) }
        item {
            DeveloperVisibilityPanel(
                checked = showDeveloperEntry,
                onCheckedChange = onSetShowDeveloperEntry
            )
        }
        item {
            InfoPanel(
                now = DateTimeUtils.formatDateTime(System.currentTimeMillis(), if (language == AppLanguage.English) "English" else "中文"),
                notifications = if (settings.notificationEnabled) language.text("已开启", "On") else language.text("已关闭", "Off"),
                reminder = reminderText(settings.defaultReminderMinutes, language),
                deepSeekStatus = deepSeekStatus
            )
        }
        item {
            TestSection(language.text("提醒测试", "Reminder Tests")) {
                TestButton(language.text("申请通知权限", "Request notification permission"), AppBlue) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        message = language.text("当前系统版本不需要运行时通知权限。", "Runtime notification permission is not required.")
                    }
                }
                TestButton(language.text("立即发送提醒通知", "Send reminder now"), AppGreen) {
                    NotificationHelper(context).showDeadlineReminder(language.text("测试提醒：Deadline Mate 通知链路正常", "Test reminder: Deadline Mate notifications are working"))
                    message = language.text("已发送一条 App 提醒通知。", "App reminder notification sent.")
                }
                TestButton(language.text("10 秒后触发提醒", "Trigger reminder in 10 seconds"), AppOrange) {
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        message = language.text("请先允许通知权限，再重新点击 10 秒提醒测试。", "Allow notification permission first, then tap the 10 second test again.")
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        reminderScheduler.scheduleTestReminder(language.text("10 秒提醒测试", "10 second reminder test"))
                        message = language.text("已交给系统闹钟，10 秒后即使退到后台也应触发。", "Scheduled through system alarm. It should fire in 10 seconds even in background.")
                    }
                }
                TestButton(language.text("清除本 App 通知", "Clear app notifications"), AppRed) {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.cancelAll()
                    message = language.text("已清除 Deadline Mate 的通知。", "Deadline Mate notifications cleared.")
                }
            }
        }
        item {
            TestSection(language.text("本地语音转文字测试", "Local Speech-to-Text Test")) {
                Box(Modifier.fillMaxWidth()) {
                    ChoiceBox(
                        label = language.text("当前模型：${speechEngineLabel(selectedSpeechEngine, language)}", "Current model: ${speechEngineLabel(selectedSpeechEngine, language)}")
                    ) {
                        speechEngineExpanded = true
                    }
                    DropdownMenu(
                        expanded = speechEngineExpanded,
                        onDismissRequest = { speechEngineExpanded = false },
                        containerColor = Color.White
                    ) {
                        DropdownMenuItem(
                            text = { Text(speechEngineLabel(SpeechTestEngine.ParaformerZh, language), color = AppText, fontWeight = FontWeight.Bold) },
                            onClick = {
                                selectedSpeechEngine = SpeechTestEngine.ParaformerZh
                                speechEngineExpanded = false
                                voiceText = ""
                                voiceStatus = language.text("已选择 Paraformer 中文本地模型。", "Selected Paraformer local Chinese model.")
                            },
                            colors = MenuDefaults.itemColors(textColor = AppText)
                        )
                        DropdownMenuItem(
                            text = { Text(speechEngineLabel(SpeechTestEngine.AndroidSystem, language), color = AppText, fontWeight = FontWeight.Bold) },
                            onClick = {
                                selectedSpeechEngine = SpeechTestEngine.AndroidSystem
                                speechEngineExpanded = false
                                voiceText = ""
                                voiceStatus = language.text("已选择系统语音识别。", "Selected Android system recognizer.")
                            },
                            colors = MenuDefaults.itemColors(textColor = AppText)
                        )
                    }
                }
                MiniRow(language.text("识别语言", "Recognition language"), languageText(settings.speechLanguage))
                if (selectedSpeechEngine == SpeechTestEngine.ParaformerZh) {
                    MiniRow(language.text("联网", "Network"), language.text("不需要", "Not required"))
                    MiniRow(
                        language.text("模型状态", "Model status"),
                        if (sherpaRecognizer.hasBundledModel()) language.text("已内置", "Bundled") else language.text("缺少模型", "Missing model")
                    )
                } else {
                    MiniRow(language.text("识别模式", "Mode"), speechRecognitionMode(context, settings.speechLanguage, language == AppLanguage.English))
                    MiniRow(language.text("系统识别服务", "System service"), if (speechAvailable) language.text("可用", "Available") else language.text("不可用", "Unavailable"))
                    MiniRow(language.text("离线识别", "On-device"), if (onDeviceSpeechAvailable) language.text("支持", "Supported") else language.text("不支持", "Unsupported"))
                    ResultBox(language.text("当前识别服务", "Recognizer Service"), speechServiceName)
                }
                MiniRow(language.text("麦克风输入", "Mic input"), micProbeStatus)
                TestButton(
                    if (micProbeRunning) language.text("检测中，请说话...", "Testing, speak now...") else language.text("检测麦克风输入 3 秒", "Test mic input for 3s"),
                    AppPurple,
                    enabled = !micProbeRunning && !voiceRunning
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        micProbeRunning = true
                        micProbeStatus = language.text("检测中，请对电脑麦克风说话。", "Testing. Speak into the computer microphone.")
                        scope.launch {
                            probeMicrophoneInput(context.applicationContext)
                                .onSuccess {
                                    micProbeStatus = if (it.hasAudibleSignal) {
                                        language.text("检测到输入 max=${it.maxAmplitude}, rms=${it.rms.toInt()}", "Input detected max=${it.maxAmplitude}, rms=${it.rms.toInt()}")
                                    } else {
                                        language.text("未检测到明显输入 max=${it.maxAmplitude}, rms=${it.rms.toInt()}", "No clear input max=${it.maxAmplitude}, rms=${it.rms.toInt()}")
                                    }
                                }
                                .onFailure {
                                    micProbeStatus = it.message ?: language.text("麦克风检测失败。", "Mic test failed.")
                                }
                            micProbeRunning = false
                        }
                    }
                }
                ResultBox(
                    title = language.text("识别结果", "Recognized Text"),
                    value = voiceText.ifBlank { language.text("还没有识别结果。", "No recognized text yet.") }
                )
                MiniRow(language.text("状态", "Status"), voiceStatus)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    TestButton(language.text("开始识别", "Start"), AppBlue, Modifier.weight(1f), enabled = !voiceRunning) {
                        if (selectedSpeechEngine == SpeechTestEngine.AndroidSystem && !speechAvailable) {
                            voiceStatus = language.text("当前系统没有可用的语音识别服务。请在系统设置中安装或启用语音识别服务。", "No speech recognition service is available. Install or enable a speech recognizer in system settings.")
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            voiceText = ""
                            if (selectedSpeechEngine == SpeechTestEngine.ParaformerZh) {
                                voiceStatus = language.text("正在加载 Paraformer 本地模型...", "Loading Paraformer local model...")
                                scope.launch {
                                    sherpaRecognizer.prepare()
                                        .onSuccess {
                                            voiceRunning = true
                                            voiceStatus = language.text("Paraformer 正在本地录音，请开始说话。", "Paraformer is recording locally. Start speaking.")
                                            sherpaRecognizer.start(
                                                onPartial = {
                                                    if (it.isNotBlank()) voiceStatus = it
                                                },
                                                onFinal = {
                                                    voiceRunning = false
                                                    if (it.isNotBlank()) voiceText = it
                                                    voiceStatus = language.text("Paraformer 本地识别已结束。", "Paraformer local recognition finished.")
                                                },
                                                onError = {
                                                    voiceRunning = false
                                                    voiceStatus = it
                                                }
                                            )
                                        }
                                        .onFailure {
                                            voiceRunning = false
                                            voiceStatus = it.message ?: language.text("Paraformer 模型加载失败。", "Failed to load Paraformer model.")
                                        }
                                }
                            } else {
                                voiceRunning = true
                                voiceStatus = language.text("正在启动系统语音识别...", "Starting system speech recognition...")
                                runCatching {
                                    speechRecognizer.cancel()
                                    speechRecognizer.startListening(speechIntent(context, settings.speechLanguage))
                                }.onFailure {
                                    voiceRunning = false
                                    voiceStatus = language.text("语音识别启动失败：${it.javaClass.simpleName}", "Failed to start speech recognition: ${it.javaClass.simpleName}")
                                }
                            }
                        }
                    }
                    TestButton(language.text("停止", "Stop"), AppOrange, Modifier.weight(1f), enabled = voiceRunning) {
                        if (selectedSpeechEngine == SpeechTestEngine.ParaformerZh) {
                            sherpaRecognizer.stop()
                        } else {
                            runCatching { speechRecognizer.stopListening() }
                        }
                        voiceRunning = false
                        voiceStatus = language.text("已请求停止识别。", "Stop requested.")
                    }
                }
                TestButton(language.text("复制语音文字", "Copy speech text"), AppGreen, enabled = voiceText.isNotBlank()) {
                    clipboard.setText(AnnotatedString(voiceText))
                    message = language.text("语音识别文字已复制。", "Speech text copied.")
                }
            }
        }
        item {
            TestSection(language.text("本地 OCR 测试", "Local OCR Test")) {
                ResultBox(
                    title = language.text("OCR 结果", "OCR Text"),
                    value = ocrText.ifBlank { language.text("还没有 OCR 结果。", "No OCR text yet.") }
                )
                MiniRow(language.text("状态", "Status"), ocrStatus)
                TestButton(if (ocrRunning) language.text("识别中...", "Recognizing...") else language.text("选择图片并识别", "Pick Image and Recognize"), AppPurple, enabled = !ocrRunning) {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                TestButton(language.text("复制 OCR 文字", "Copy OCR text"), AppGreen, enabled = ocrText.isNotBlank()) {
                    clipboard.setText(AnnotatedString(ocrText))
                    message = language.text("OCR 文字已复制。", "OCR text copied.")
                }
            }
        }
        item {
            TestSection(language.text("DeepSeek 智能解析调试", "DeepSeek Smart Parse Debug")) {
                MiniRow("DeepSeek", deepSeekStatus)
                OutlinedTextField(
                    value = deepSeekInput,
                    onValueChange = {
                        deepSeekInput = it
                        deepSeekPrompt = buildDeepSeekTaskExtractionPrompt(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    label = { Text(language.text("发送给 DeepSeek 的识别文字", "Recognized text sent to DeepSeek")) },
                    placeholder = {
                        Text(
                            language.text(
                                "例如：帮我记一下，数据库实验报告明天晚上八点前交，要提交 SQL 文件、截图和总结，很重要。",
                                "Example: Database lab report is due tomorrow at 8 PM. Submit SQL files, screenshots, and summary. It is important."
                            ),
                            color = AppSubtext
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppBlue,
                        unfocusedBorderColor = AppLine,
                        focusedContainerColor = Color.White.copy(alpha = 0.72f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.72f)
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    TestButton(language.text("使用语音结果", "Use speech text"), AppBlue, Modifier.weight(1f), enabled = voiceText.isNotBlank()) {
                        deepSeekInput = voiceText
                        deepSeekPrompt = buildDeepSeekTaskExtractionPrompt(voiceText)
                        message = language.text("已填入语音识别结果。", "Speech text inserted.")
                    }
                    TestButton(language.text("使用 OCR 结果", "Use OCR text"), AppPurple, Modifier.weight(1f), enabled = ocrText.isNotBlank()) {
                        deepSeekInput = ocrText
                        deepSeekPrompt = buildDeepSeekTaskExtractionPrompt(ocrText)
                        message = language.text("已填入 OCR 识别结果。", "OCR text inserted.")
                    }
                }
                TestButton(
                    if (deepSeekRunning) language.text("解析中...", "Parsing...") else language.text("发送给 DeepSeek 并查看返回", "Send to DeepSeek and show response"),
                    AppGreen,
                    enabled = !deepSeekRunning && deepSeekInput.isNotBlank()
                ) {
                    deepSeekPrompt = buildDeepSeekTaskExtractionPrompt(deepSeekInput)
                    deepSeekRawResponse = ""
                    deepSeekJson = ""
                    deepSeekDraft = ""
                    deepSeekParseStatus = language.text("正在请求 DeepSeek...", "Requesting DeepSeek...")
                    deepSeekRunning = true
                    scope.launch {
                        parseDeepSeekDebug(deepSeekInput)
                            .onSuccess {
                                deepSeekPrompt = it.prompt
                                deepSeekRawResponse = it.rawResponse
                                deepSeekJson = it.extractedJson
                                deepSeekDraft = draftSummary(it, language)
                                deepSeekParseStatus = language.text("解析成功，下面是 DeepSeek 返回值。", "Parse succeeded. DeepSeek response is shown below.")
                            }
                            .onFailure {
                                deepSeekParseStatus = language.text(
                                    "解析失败：请检查 DeepSeek 配置、网络或模型名称。",
                                    "Parse failed. Check DeepSeek config, network, or model name."
                                )
                                deepSeekRawResponse = it.message.orEmpty()
                            }
                        deepSeekRunning = false
                    }
                }
                MiniRow(language.text("状态", "Status"), deepSeekParseStatus)
                ResultBox(language.text("实际提示词", "Actual Prompt"), deepSeekPrompt)
                ResultBox(
                    language.text("DeepSeek 原始返回", "DeepSeek Raw Response"),
                    deepSeekRawResponse.ifBlank { language.text("还没有返回值。", "No response yet.") }
                )
                ResultBox(
                    language.text("提取出的 JSON", "Extracted JSON"),
                    deepSeekJson.ifBlank { language.text("还没有 JSON。", "No JSON yet.") }
                )
                ResultBox(
                    language.text("表单草稿摘要", "Form Draft Summary"),
                    deepSeekDraft.ifBlank { language.text("还没有生成草稿。", "No draft yet.") }
                )
                TestButton(language.text("复制提示词", "Copy prompt"), AppOrange, enabled = deepSeekPrompt.isNotBlank()) {
                    clipboard.setText(AnnotatedString(deepSeekPrompt))
                    message = language.text("提示词已复制。", "Prompt copied.")
                }
                TestButton(language.text("复制 DeepSeek 返回", "Copy DeepSeek response"), AppGreen, enabled = deepSeekRawResponse.isNotBlank()) {
                    clipboard.setText(AnnotatedString(deepSeekRawResponse))
                    message = language.text("DeepSeek 返回值已复制。", "DeepSeek response copied.")
                }
            }
        }
        item {
            TestSection(language.text("配置快照", "Config Snapshot")) {
                MiniRow(language.text("当前时间", "Current time"), DateTimeUtils.formatDateTime(System.currentTimeMillis(), if (language == AppLanguage.English) "English" else "中文"))
                MiniRow("DeepSeek", deepSeekStatus)
                MiniRow(language.text("默认提醒", "Default reminder"), reminderText(settings.defaultReminderMinutes, language))
                MiniRow(language.text("语言", "Language"), languageText(settings.speechLanguage))
                MiniRow(language.text("主题", "Theme"), settings.themeStyle)
            }
        }
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(AppBlue.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                    .border(1.dp, AppBlue.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                Text(message, color = AppBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DeveloperVisibilityPanel(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val language = LocalAppLanguage.current
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 22.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    language.text("长期展示开发者调试", "Always show developer debug"),
                    color = AppText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    language.text("关闭后，设置页会隐藏测试中心；仍可连续点击版本号再次进入。", "When off, Test Center is hidden from settings. Tap version five times to reopen it."),
                    color = AppSubtext,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun InfoPanel(now: String, notifications: String, reminder: String, deepSeekStatus: String) {
    val language = LocalAppLanguage.current
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(language.text("运行状态", "Runtime State"), color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Black)
            MiniRow(language.text("当前时间", "Current time"), now)
            MiniRow(language.text("通知提醒", "Notifications"), notifications)
            MiniRow(language.text("默认提醒", "Default reminder"), reminder)
            MiniRow("DeepSeek", deepSeekStatus)
        }
    }
}

@Composable
private fun TestSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Black)
            content()
        }
    }
}

@Composable
private fun TestButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp).background(if (enabled) color else color.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.7f))
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ResultBox(title: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.70f), RoundedCornerShape(16.dp))
            .border(1.dp, AppLine, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(value, color = AppSubtext, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun MiniRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppSubtext, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(value, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChoiceBox(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(Color.White.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
            .border(1.dp, AppLine, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(">", color = AppSubtext, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

private fun speechEngineLabel(engine: SpeechTestEngine, language: AppLanguage): String {
    return when (engine) {
        SpeechTestEngine.ParaformerZh -> language.text("Paraformer 中文本地模型", "Paraformer local Chinese model")
        SpeechTestEngine.AndroidSystem -> language.text("系统语音识别", "Android system recognizer")
    }
}

private fun draftSummary(result: DeepSeekTaskParseDebugResult, language: AppLanguage): String {
    val draft = result.draft
    val deadline = draft.deadlineDateTime?.let {
        DateTimeUtils.formatDateTime(it, if (language == AppLanguage.English) "English" else "中文")
    }
    return buildString {
        appendLine("${language.text("任务名称", "Title")}: ${draft.title ?: "null"}")
        appendLine("${language.text("备注", "Description")}: ${draft.description ?: "null"}")
        appendLine("${language.text("截止时间原文", "Deadline text")}: ${draft.deadlineDateTimeText ?: "null"}")
        appendLine("${language.text("本地时间戳转换", "Local timestamp")}: ${deadline ?: "null"}")
        appendLine("${language.text("重要性", "Importance")}: ${draft.importance ?: "null"}")
        appendLine("${language.text("重复", "Repeat")}: ${draft.repeatRule ?: "null"}")
        appendLine("${language.text("类型", "Category")}: ${draft.category ?: "null"}")
        appendLine("${language.text("置信度", "Confidence")}: ${draft.confidence ?: "null"}")
        append("${language.text("缺失字段", "Missing fields")}: ${if (draft.missingFields.isEmpty()) "[]" else draft.missingFields.joinToString()}")
    }
}

private fun reminderText(minutes: Int?, language: AppLanguage): String = when (minutes) {
    1440 -> language.text("提前 1 天", "1 day before")
    180 -> language.text("提前 3 小时", "3 hours before")
    30 -> language.text("提前 30 分钟", "30 minutes before")
    null -> language.text("不提醒", "Off")
    else -> language.text("提前 $minutes 分钟", "$minutes minutes before")
}

private fun languageText(language: String): String {
    return if (language == "English") "English" else "中文"
}
