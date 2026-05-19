package com.deadlinemate.ui.add

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadlinemate.domain.model.ImportanceLevel
import com.deadlinemate.domain.model.RepeatRule
import com.deadlinemate.domain.model.TaskCategory
import com.deadlinemate.domain.model.TaskDraft
import com.deadlinemate.ui.UiSettings
import com.deadlinemate.ui.i18n.AppLanguage
import com.deadlinemate.ui.i18n.LocalAppLanguage
import com.deadlinemate.ui.i18n.text
import com.deadlinemate.ui.profile.themeAccent
import com.deadlinemate.ui.recognition.recognizeImageText
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar

private enum class AddMode(val title: String, val icon: String, val sub: String) {
    Manual("手动", "手", "完整填写"),
    Voice("语音", "声", "按住说话"),
    Screenshot("截图", "图", "相册识别")
}

private enum class EnhancedSource { Voice, Screenshot }

private data class ScreenshotDraftItem(
    val id: Long,
    val sourceIndex: Int,
    val rawText: String,
    val draft: TaskDraft
)

@Composable
fun AddTaskScreen(
    settings: UiSettings,
    deepSeekEnabled: Boolean,
    pageBg: Color = AppBg,
    onGoConfigure: () -> Unit,
    parseSmartText: suspend (String) -> Result<TaskDraft>,
    onSave: (String, String?, Long, ImportanceLevel, TaskCategory, RepeatRule, Int?, Boolean) -> Unit
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val accent = themeAccent(settings.themeStyle)
    var mode by remember { mutableStateOf(AddMode.Manual) }
    var confirming by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(DateTimeUtils.formatInputDate(System.currentTimeMillis())) }
    var time by remember { mutableStateOf("23:59") }
    var importance by remember { mutableStateOf(ImportanceLevel.HIGH) }
    var category by remember { mutableStateOf(TaskCategory.OTHER) }
    var repeat by remember { mutableStateOf(RepeatRule.NONE) }
    var reminder by remember { mutableStateOf(settings.defaultReminderMinutes) }
    var rawText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var parsing by remember { mutableStateOf(false) }
    var failedSource by remember { mutableStateOf<EnhancedSource?>(null) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var screenshotDrafts by remember { mutableStateOf<List<ScreenshotDraftItem>>(emptyList()) }
    var selectedScreenshotDraftId by remember { mutableStateOf<Long?>(null) }
    var screenshotFailedCount by remember { mutableStateOf(0) }

    fun loadDraft(draft: TaskDraft, sourceText: String? = null) {
        title = draft.title.orEmpty()
        desc = draft.description ?: draft.rawText.orEmpty()
        if (draft.deadlineDateTime != null) {
            date = DateTimeUtils.formatInputDate(draft.deadlineDateTime)
            time = DateTimeUtils.formatTime(draft.deadlineDateTime)
        } else {
            date = ""
            time = ""
        }
        importance = draft.importance ?: ImportanceLevel.MEDIUM
        category = draft.category ?: TaskCategory.OTHER
        repeat = draft.repeatRule ?: RepeatRule.NONE
        confirming = true
        mode = AddMode.Manual
        status = language.text("请确认任务信息后再保存。", "Review the task details before saving.")
        rawText = sourceText ?: draft.rawText.orEmpty()
        failedSource = null
    }

    fun currentFormDraft(raw: String?): TaskDraft {
        return TaskDraft(
            title = title.takeIf { it.isNotBlank() },
            description = desc.takeIf { it.isNotBlank() },
            deadlineDateTime = date.takeIf { it.isNotBlank() }?.let { parseDeadline(date, time) },
            importance = importance,
            repeatRule = repeat,
            category = category,
            rawText = raw
        )
    }

    fun persistSelectedScreenshotDraft() {
        val selectedId = selectedScreenshotDraftId ?: return
        screenshotDrafts = screenshotDrafts.map { item ->
            if (item.id == selectedId) item.copy(draft = currentFormDraft(item.rawText)) else item
        }
    }

    fun selectScreenshotDraft(item: ScreenshotDraftItem) {
        persistSelectedScreenshotDraft()
        selectedScreenshotDraftId = item.id
        loadDraft(item.draft, item.rawText)
        status = language.text("正在查看第 ${item.sourceIndex} 张截图提取结果，请确认后保存。", "Review screenshot ${item.sourceIndex}, then save.")
    }

    fun applyDraft(draft: TaskDraft) {
        loadDraft(draft)
        screenshotDrafts = emptyList()
        selectedScreenshotDraftId = null
        screenshotFailedCount = 0
        status = language.text("请确认任务信息后再保存。", "Review the task details before saving.")
    }

    fun parseText(text: String, source: EnhancedSource) {
        if (text.isBlank()) {
            status = language.text("识别内容为空，请重新识别。", "No text was recognized. Please try again.")
            return
        }
        scope.launch {
            parsing = true
            status = language.text("正在智能解析...", "Parsing with DeepSeek...")
            rawText = text
            val result = parseSmartText(text)
            parsing = false
            result.fold(
                onSuccess = ::applyDraft,
                onFailure = {
                    failedSource = source
                    status = language.text("智能解析失败，请检查 API 配置或稍后重试。", "Smart parsing failed. Check API settings or try again later.")
                }
            )
        }
    }

    suspend fun processScreenshotUris(uris: List<Uri>) {
        parsing = true
        failedSource = null
        screenshotDrafts = emptyList()
        selectedScreenshotDraftId = null
        screenshotFailedCount = 0
        status = language.text("正在并发识别 ${uris.size} 张截图...", "Recognizing ${uris.size} screenshots in parallel...")
        val batchId = System.nanoTime()
        val parsedItems = coroutineScope {
            uris.mapIndexed { index, uri ->
                async(Dispatchers.IO) {
                    val text = runCatching { recognizeImageText(context, uri) }.getOrNull().orEmpty()
                    if (text.isBlank()) return@async null
                    parseSmartText(text).getOrNull()?.let { draft ->
                        ScreenshotDraftItem(
                            id = batchId + index,
                            sourceIndex = index + 1,
                            rawText = text,
                            draft = draft.copy(rawText = text)
                        )
                    }
                }
            }.awaitAll().filterNotNull().sortedBy { it.sourceIndex }
        }
        val failed = uris.size - parsedItems.size
        parsing = false
        screenshotFailedCount = failed
        if (parsedItems.isNotEmpty()) {
            screenshotDrafts = parsedItems
            selectScreenshotDraft(parsedItems.first())
            status = language.text(
                "已生成 ${parsedItems.size} 个任务草稿${if (failed > 0) "，${failed} 张未成功" else ""}。请逐个确认。",
                "Generated ${parsedItems.size} drafts${if (failed > 0) ", $failed failed" else ""}. Review them one by one."
            )
        } else {
            failedSource = EnhancedSource.Screenshot
            status = language.text("截图识别或智能解析失败，请重新选择图片或改用手动填写。", "Screenshot recognition or smart parsing failed. Pick images again or fill manually.")
        }
    }

    lateinit var photoPicker: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>
    photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            processScreenshotUris(uris)
        }
    }

    if (showConfigDialog) {
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text(language.text("请先配置 DeepSeek API", "Configure DeepSeek API first")) },
            text = { Text(language.text("语音添加和截图识别需要使用 DeepSeek API 提取任务名称、截止时间、重要性和重复规则。请先在「我的」页面完成配置。", "Voice add and screenshot recognition need DeepSeek API to extract task fields. Configure it from the Me page first.")) },
            confirmButton = {
                TextButton(onClick = {
                    showConfigDialog = false
                    onGoConfigure()
                }) { Text(language.text("去配置", "Configure"), color = accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showConfigDialog = false }) { Text(language.text("取消", "Cancel")) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(if (confirming) language.text("确认任务信息", "Confirm Task") else language.text("新建 DDL", "New Deadline"), color = AppText, fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AddMode.entries.forEach { item ->
                val disabled = item != AddMode.Manual && !deepSeekEnabled
                ModeCard(
                    mode = item,
                    selected = mode == item && !confirming,
                    disabled = disabled,
                    accent = accent,
                    language = language,
                    modifier = Modifier.weight(1f)
                ) {
                    confirming = false
                    status = null
                    failedSource = null
                    if (disabled) showConfigDialog = true else mode = item
                }
            }
        }
        status?.let {
            StatusBlock(
                text = it,
                rawText = rawText,
                canRecover = failedSource != null,
                onCopy = {
                    clipboard.setText(AnnotatedString(rawText))
                    status = language.text("已复制识别文字。", "Recognized text copied.")
                },
                onManual = {
                    confirming = false
                    mode = AddMode.Manual
                    desc = rawText
                    failedSource = null
                    status = language.text("已转为手动填写。", "Switched to manual entry.")
                },
                onRetry = {
                    val source = failedSource
                    failedSource = null
                    status = null
                    if (source == EnhancedSource.Screenshot) {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else {
                        mode = AddMode.Voice
                        rawText = ""
                    }
                }
            )
        }
        if (screenshotDrafts.isNotEmpty()) {
            ScreenshotDraftCards(
                drafts = screenshotDrafts,
                selectedId = selectedScreenshotDraftId,
                failedCount = screenshotFailedCount,
                language = language,
                accent = accent,
                onSelect = ::selectScreenshotDraft
            )
        }
        when {
            confirming || mode == AddMode.Manual -> ManualPanel(
                title = title,
                onTitle = { title = it },
                desc = desc,
                onDesc = { desc = it },
                date = date,
                onDate = { date = it },
                time = time,
                onTime = { time = it },
                importance = importance,
                onImportance = { importance = it },
                category = category,
                onCategory = { category = it },
                repeat = repeat,
                onRepeat = { repeat = it },
                reminder = reminder,
                onReminder = { reminder = it },
                saveText = if (confirming) language.text("保存任务", "Save Task") else language.text("创建任务", "Create Task"),
                datePlaceholder = if (confirming && date.isBlank()) language.text("请选择截止日期", "Select deadline date") else language.text("选择日期", "Select date"),
                timePlaceholder = if (confirming && time.isBlank()) language.text("请选择截止时间", "Select deadline time") else language.text("选择时间", "Select time"),
                language = language,
                accent = accent,
                compact = screenshotDrafts.isNotEmpty(),
                onSave = {
                    val deadline = parseDeadline(date, time)
                    val reminderAt = reminder?.let { deadline - it * 60_000L }
                    if (reminderAt != null && reminderAt <= System.currentTimeMillis()) {
                        status = language.text("提醒时间已经过去，请调整截止时间或提醒时间。", "The reminder time has already passed. Adjust the deadline or reminder.")
                    } else {
                        val selectedBatchId = selectedScreenshotDraftId
                        if (selectedBatchId != null) {
                            val remaining = screenshotDrafts.filterNot { it.id == selectedBatchId }
                            onSave(title.trim(), desc.trim().ifBlank { null }, deadline, importance, category, repeat, reminder, remaining.isEmpty())
                            if (remaining.isNotEmpty()) {
                                screenshotDrafts = remaining
                                selectedScreenshotDraftId = null
                                selectScreenshotDraft(remaining.first())
                                status = language.text("已保存 1 个任务，继续确认剩余 ${remaining.size} 个。", "Saved 1 task. Continue reviewing ${remaining.size} remaining.")
                            }
                        } else {
                            onSave(title.trim(), desc.trim().ifBlank { null }, deadline, importance, category, repeat, reminder, true)
                        }
                    }
                }
            )
            mode == AddMode.Voice -> VoicePanel(
                language = settings.speechLanguage,
                voiceText = rawText,
                parsing = parsing,
                onText = {
                    rawText = it
                    status = language.text("识别完成，请确认解析。", "Recognition finished. Confirm parsing.")
                },
                onConfirm = { parseText(rawText, EnhancedSource.Voice) },
                languageUi = language,
                accent = accent
            )
            mode == AddMode.Screenshot -> ScreenshotPanel(
                parsing = parsing,
                language = language,
                accent = accent,
                onPickImage = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        }
    }
}

@Composable
private fun ModeCard(mode: AddMode, selected: Boolean, disabled: Boolean, accent: Color, language: AppLanguage, modifier: Modifier, onClick: () -> Unit) {
    val border = if (selected) accent else AppLine
    Column(
        modifier = modifier
            .height(68.dp)
            .alpha(if (disabled) 0.48f else 1f)
            .background(Color.White.copy(alpha = if (disabled) 0.46f else 0.86f), RoundedCornerShape(20.dp))
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                modifier = Modifier.size(28.dp).background(if (selected) accent else accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(if (disabled) language.text("锁", "Lock") else modeIcon(mode, language), color = if (selected) Color.White else accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Column {
                Text(modeTitle(mode, language), color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(if (disabled) language.text("需配置", "Needs setup") else modeSub(mode, language), color = AppSubtext, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ManualPanel(
    title: String,
    onTitle: (String) -> Unit,
    desc: String,
    onDesc: (String) -> Unit,
    date: String,
    onDate: (String) -> Unit,
    time: String,
    onTime: (String) -> Unit,
    importance: ImportanceLevel,
    onImportance: (ImportanceLevel) -> Unit,
    category: TaskCategory,
    onCategory: (TaskCategory) -> Unit,
    repeat: RepeatRule,
    onRepeat: (RepeatRule) -> Unit,
    reminder: Int?,
    onReminder: (Int?) -> Unit,
    saveText: String,
    datePlaceholder: String,
    timePlaceholder: String,
    language: AppLanguage,
    accent: Color,
    compact: Boolean = false,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)) {
        Field(language.text("任务名称", "Task Name"), compact = compact) { StyledField(title, onTitle, language.text("例如：提交课程设计报告", "Example: Submit course report")) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Field(language.text("截止日期", "Date"), Modifier.weight(1f), compact = compact) { DateSelectBox(date, datePlaceholder, onDate) }
            Field(language.text("截止时间", "Time"), Modifier.weight(1f), compact = compact) { TimeSelectBox(time, timePlaceholder, onTime) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Field(language.text("重要性", "Importance"), Modifier.weight(1f), compact = compact) {
                SelectBox(labelImportance(importance, language), listOf(language.text("高", "High") to ImportanceLevel.HIGH, language.text("中", "Medium") to ImportanceLevel.MEDIUM, language.text("低", "Low") to ImportanceLevel.LOW), onImportance)
            }
            Field(language.text("提醒", "Reminder"), Modifier.weight(1f), compact = compact) {
                SelectBox(labelReminder(reminder, language), listOf(language.text("提前 1 天", "1 day before") to 1440, language.text("提前 3 小时", "3 hours before") to 180, language.text("提前 30 分钟", "30 minutes before") to 30, language.text("不提醒", "Off") to null), onReminder)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Field(language.text("重复", "Repeat"), Modifier.weight(1f), compact = compact) {
                SelectBox(
                    labelRepeat(repeat, language),
                    listOf(language.text("不重复", "None") to RepeatRule.NONE, language.text("每天", "Daily") to RepeatRule.DAILY, language.text("每周", "Weekly") to RepeatRule.WEEKLY, language.text("每月", "Monthly") to RepeatRule.MONTHLY, language.text("自定义", "Custom") to RepeatRule.CUSTOM),
                    onRepeat
                )
            }
            Field(language.text("任务类型", "Category"), Modifier.weight(1f), compact = compact) {
                SelectBox(
                    labelCategory(category, language),
                    listOf(language.text("其他", "Other") to TaskCategory.OTHER, language.text("学习", "Study") to TaskCategory.STUDY, language.text("作业", "Homework") to TaskCategory.HOMEWORK, language.text("比赛", "Competition") to TaskCategory.COMPETITION, language.text("会议", "Meeting") to TaskCategory.MEETING, language.text("生活", "Life") to TaskCategory.LIFE),
                    onCategory
                )
            }
        }
        Field(language.text("备注", "Notes"), compact = compact) { StyledField(desc, onDesc, language.text("补充提交方式、注意事项等", "Submission method, requirements, or notes"), minLines = 2) }
        TextButton(
            onClick = onSave,
            enabled = title.isNotBlank() && date.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(if (compact) 42.dp else 46.dp).background(accent, RoundedCornerShape(16.dp)),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.55f))
        ) {
            Text(saveText, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun VoicePanel(
    language: String,
    voiceText: String,
    parsing: Boolean,
    onText: (String) -> Unit,
    onConfirm: () -> Unit,
    languageUi: AppLanguage,
    accent: Color
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(languageUi.text("待录音", "Ready")) }
    var recording by remember { mutableStateOf(false) }
    var permissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val recognizer = remember { SherpaLocalSpeechRecognizer(context.applicationContext) }
    var modelReady by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        status = if (granted) languageUi.text("请按住说话", "Hold to speak") else languageUi.text("需要麦克风权限", "Microphone permission required")
    }
    LaunchedEffect(recognizer) {
        recognizer.prepare()
            .onSuccess {
                modelReady = true
                status = languageUi.text("按住即可说话", "Hold to speak")
            }
            .onFailure {
                status = it.message ?: languageUi.text("本地语音模型不可用，请切换手动输入。", "Local speech model is unavailable. Use manual input.")
            }
    }
    DisposableEffect(recognizer) {
        onDispose { recognizer.shutdown() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(218.dp).background(Color.White.copy(alpha = 0.78f), RoundedCornerShape(24.dp)).border(1.dp, AppLine, RoundedCornerShape(24.dp)).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(languageUi.text("语音识别内容", "Voice Text"), color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(if (voiceText.isBlank()) languageUi.text("按住下方按钮说话，松开后本地识别为文字。", "Hold the button, speak, then release to recognize text locally.") else voiceText, color = AppSubtext, fontSize = 14.sp, lineHeight = 22.sp)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                  .height(56.dp)
                  .background(accent, RoundedCornerShape(20.dp))
                  .pointerInput(permissionGranted, language) {
                      detectTapGestures(
                          onPress = {
                              if (!permissionGranted) {
                                  permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                  return@detectTapGestures
                              }
                              if (!modelReady) {
                                  status = languageUi.text("正在完成首次加载，请稍等片刻...", "Finishing first-time load...")
                              }
                              val prepared = recognizer.prepare()
                              if (prepared.isFailure) {
                                  status = prepared.exceptionOrNull()?.message ?: languageUi.text("本地模型加载失败，请切换手动输入。", "Failed to load local model. Use manual input.")
                                  return@detectTapGestures
                              }
                              modelReady = true
                              status = languageUi.text("正在本地录音，请说话", "Recording locally. Speak now.")
                              recording = true
                              recognizer.start(
                                  onPartial = { status = it },
                                  onFinal = { text ->
                                      recording = false
                                      if (text.isNotBlank()) {
                                          onText(text)
                                          status = languageUi.text("识别完成，请确认解析", "Recognition finished. Confirm parsing.")
                                      } else {
                                          status = languageUi.text("未识别到内容", "No speech recognized")
                                      }
                                  },
                                  onError = {
                                      recording = false
                                      status = it
                                  }
                              )
                              tryAwaitRelease()
                              status = languageUi.text("正在本地识别...", "Recognizing locally...")
                              recognizer.stop()
                          }
                      )
                  },
            contentAlignment = Alignment.Center
        ) {
            Text(if (recording) languageUi.text("松开识别", "Release to Recognize") else languageUi.text("按住说话", "Hold to Speak"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
        Text(status, color = AppSubtext, fontSize = 12.sp)
        TextButton(
            onClick = onConfirm,
            enabled = voiceText.isNotBlank() && !parsing,
            modifier = Modifier.fillMaxWidth().height(50.dp).background(accent, RoundedCornerShape(18.dp)),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.55f))
        ) {
            Text(if (parsing) languageUi.text("解析中...", "Parsing...") else languageUi.text("确认并智能解析", "Confirm and Parse"), fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ScreenshotPanel(parsing: Boolean, language: AppLanguage, accent: Color, onPickImage: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.92f),
                        accent.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.82f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.78f), RoundedCornerShape(28.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(accent.copy(alpha = 0.13f), RoundedCornerShape(17.dp))
                    .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("OCR", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(language.text("批量识别截图里的 DDL", "Batch Extract Deadlines"), color = AppText, fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
                Text(
                    language.text("可一次选择多张通知、课程平台或比赛要求截图。图片只在本地 OCR，DeepSeek 只接收识别出的文字并生成待确认草稿。", "Select multiple screenshots from notifications, course platforms, or requirements. Images stay local; only recognized text is sent to DeepSeek for drafts."),
                    color = AppSubtext,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ScreenshotFeatureChip(language.text("多图", "Multi"), accent, Modifier.weight(1f))
            ScreenshotFeatureChip(language.text("本地 OCR", "Local OCR"), accent, Modifier.weight(1f))
            ScreenshotFeatureChip(language.text("逐个确认", "Review"), accent, Modifier.weight(1f))
        }
        ScreenshotScanPreview(accent = accent)
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(accent, RoundedCornerShape(18.dp))
                .clickable(enabled = !parsing) { onPickImage() }
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(if (parsing) language.text("正在提取截图文字...", "Extracting screenshot text...") else language.text("选择截图并开始识别", "Choose Screenshots"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ScreenshotScanPreview(accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(146.dp)
            .background(Color.White.copy(alpha = 0.58f), RoundedCornerShape(22.dp))
            .border(1.dp, AppLine.copy(alpha = 0.72f), RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1.1f).height(16.dp).background(AppText.copy(alpha = 0.12f), RoundedCornerShape(8.dp)))
                Box(Modifier.weight(0.55f).height(16.dp).background(accent.copy(alpha = 0.16f), RoundedCornerShape(8.dp)))
            }
            Box(Modifier.fillMaxWidth().height(12.dp).background(AppSubtext.copy(alpha = 0.10f), RoundedCornerShape(7.dp)))
            Box(Modifier.fillMaxWidth(0.72f).height(12.dp).background(AppSubtext.copy(alpha = 0.10f), RoundedCornerShape(7.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(top = 4.dp)) {
                repeat(3) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .background(
                                if (index == 1) accent.copy(alpha = 0.11f) else Color.White.copy(alpha = 0.64f),
                                RoundedCornerShape(15.dp)
                            )
                            .border(1.dp, if (index == 1) accent.copy(alpha = 0.18f) else AppLine.copy(alpha = 0.55f), RoundedCornerShape(15.dp))
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.Center)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, accent.copy(alpha = 0.62f), Color.Transparent)
                    ),
                    RoundedCornerShape(999.dp)
                )
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .align(Alignment.TopEnd)
                .background(accent.copy(alpha = 0.13f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("AI", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ScreenshotFeatureChip(text: String, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(34.dp)
            .background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
            .border(1.dp, accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun ScreenshotDraftCards(
    drafts: List<ScreenshotDraftItem>,
    selectedId: Long?,
    failedCount: Int,
    language: AppLanguage,
    accent: Color,
    onSelect: (ScreenshotDraftItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(language.text("截图草稿", "Screenshot Drafts"), color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(
                language.text(
                    "${drafts.size} 个可确认${if (failedCount > 0) " · ${failedCount} 个失败" else ""}",
                    "${drafts.size} ready${if (failedCount > 0) " · $failedCount failed" else ""}"
                ),
                color = AppSubtext,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            drafts.forEach { item ->
                val selected = item.id == selectedId
                val draft = item.draft
                Column(
                    modifier = Modifier
                        .size(width = 148.dp, height = 76.dp)
                        .background(Color.White.copy(alpha = if (selected) 0.94f else 0.72f), RoundedCornerShape(16.dp))
                        .border(1.dp, if (selected) accent else AppLine, RoundedCornerShape(16.dp))
                        .clickable { onSelect(item) }
                        .padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        language.text("第 ${item.sourceIndex} 张截图", "Image ${item.sourceIndex}"),
                        color = if (selected) accent else AppSubtext,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        draft.title?.takeIf { it.isNotBlank() } ?: language.text("待补充标题", "Title needed"),
                        color = AppText,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2
                    )
                    Text(
                        draft.deadlineDateTime?.let { DateTimeUtils.formatDateTime(it) }
                            ?: language.text("缺少截止时间", "Missing deadline"),
                        color = AppSubtext,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBlock(
    text: String,
    rawText: String,
    canRecover: Boolean,
    onCopy: () -> Unit,
    onManual: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(if (canRecover) AppRed.copy(alpha = 0.08f) else AppBlue.copy(alpha = 0.08f), RoundedCornerShape(18.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text, color = if (canRecover) AppRed else AppBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (canRecover) {
            val language = LocalAppLanguage.current
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RecoveryButton(language.text("复制文字", "Copy"), Modifier.weight(1f), enabled = rawText.isNotBlank(), onCopy)
                RecoveryButton(language.text("转手动", "Manual"), Modifier.weight(1f), enabled = true, onManual)
                RecoveryButton(language.text("重新识别", "Retry"), Modifier.weight(1f), enabled = true, onRetry)
            }
        }
    }
}

@Composable
private fun RecoveryButton(text: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp).background(Color.White.copy(alpha = 0.82f), RoundedCornerShape(14.dp)).border(1.dp, AppLine, RoundedCornerShape(14.dp)),
        colors = ButtonDefaults.textButtonColors(contentColor = AppText, disabledContentColor = AppSubtext)
    ) { Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Field(label: String, modifier: Modifier = Modifier, compact: Boolean = false, content: @Composable () -> Unit) {
    Column(modifier = modifier) {
        Text(
            label,
            color = AppSubtext,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 2.dp, bottom = if (compact) 2.dp else 4.dp)
        )
        content()
    }
}

@Composable
private fun StyledField(value: String, onValue: (String) -> Unit, placeholder: String, minLines: Int = 1) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        placeholder = { Text(placeholder, color = AppSubtext, fontSize = 13.sp, lineHeight = 17.sp) },
        modifier = Modifier.fillMaxWidth().height(if (minLines > 1) 64.dp else 50.dp),
        minLines = minLines,
        singleLine = minLines == 1,
        textStyle = TextStyle(fontSize = 13.sp, lineHeight = 17.sp),
        shape = RoundedCornerShape(15.dp),
        colors = fieldColors()
    )
}

@Composable
private fun ChoiceBox(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color.White.copy(alpha = 0.78f), RoundedCornerShape(15.dp))
            .border(1.dp, AppLine, RoundedCornerShape(15.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, color = AppText, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun DateSelectBox(value: String, placeholder: String, onSelected: (String) -> Unit) {
    val context = LocalContext.current
    ChoiceBox(if (value.isBlank()) placeholder else value) {
        val cal = pickerDateCalendar(value)
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onSelected("%04d-%02d-%02d".format(year, month + 1, day))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}

@Composable
private fun TimeSelectBox(value: String, placeholder: String, onSelected: (String) -> Unit) {
    val context = LocalContext.current
    ChoiceBox(if (value.isBlank()) placeholder else value) {
        val (hour, minute) = pickerTime(value)
        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                onSelected("%02d:%02d".format(selectedHour, selectedMinute))
            },
            hour,
            minute,
            true
        ).show()
    }
}

@Composable
private fun <T> SelectBox(value: String, options: List<Pair<String, T>>, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ChoiceBox(value) { expanded = true }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color.White
        ) {
            options.forEach { (label, optionValue) ->
                DropdownMenuItem(
                    text = { Text(label, color = AppText, fontWeight = FontWeight.Bold) },
                    onClick = {
                        expanded = false
                        onSelected(optionValue)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = AppText
                    )
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White.copy(alpha = 0.78f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.78f),
    focusedBorderColor = AppLine,
    unfocusedBorderColor = AppLine,
    focusedTextColor = AppText,
    unfocusedTextColor = AppText
)

private fun parseDeadline(date: String, time: String): Long {
    val parts = date.split("-").mapNotNull { it.toIntOrNull() }
    val clock = time.split(":").mapNotNull { it.toIntOrNull() }
    return DateTimeUtils.calendar().apply {
        if (parts.size == 3) set(parts[0], parts[1] - 1, parts[2])
        set(Calendar.HOUR_OF_DAY, clock.getOrNull(0) ?: 23)
        set(Calendar.MINUTE, clock.getOrNull(1) ?: 59)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun pickerDateCalendar(value: String): Calendar {
    val parts = value.split("-").mapNotNull { it.toIntOrNull() }
    return DateTimeUtils.calendar().apply {
        if (parts.size == 3) set(parts[0], parts[1] - 1, parts[2])
    }
}

private fun pickerTime(value: String): Pair<Int, Int> {
    val parts = value.split(":").mapNotNull { it.toIntOrNull() }
    return (parts.getOrNull(0) ?: 23) to (parts.getOrNull(1) ?: 59)
}

private fun modeTitle(mode: AddMode, language: AppLanguage): String = when (mode) {
    AddMode.Manual -> language.text("手动", "Manual")
    AddMode.Voice -> language.text("语音", "Voice")
    AddMode.Screenshot -> language.text("截图", "Screenshot")
}

private fun modeIcon(mode: AddMode, language: AppLanguage): String = when (mode) {
    AddMode.Manual -> language.text("手", "M")
    AddMode.Voice -> language.text("声", "V")
    AddMode.Screenshot -> language.text("图", "S")
}

private fun modeSub(mode: AddMode, language: AppLanguage): String = when (mode) {
    AddMode.Manual -> language.text("完整填写", "Fill details")
    AddMode.Voice -> language.text("按住说话", "Hold to speak")
    AddMode.Screenshot -> language.text("相册识别", "Pick image")
}

private fun labelImportance(value: ImportanceLevel, language: AppLanguage): String = when (value) {
    ImportanceLevel.HIGH -> language.text("高", "High")
    ImportanceLevel.MEDIUM -> language.text("中", "Medium")
    ImportanceLevel.LOW -> language.text("低", "Low")
}

private fun labelCategory(value: TaskCategory, language: AppLanguage): String = when (value) {
    TaskCategory.STUDY -> language.text("学习", "Study")
    TaskCategory.HOMEWORK -> language.text("作业", "Homework")
    TaskCategory.COMPETITION -> language.text("比赛", "Competition")
    TaskCategory.MEETING -> language.text("会议", "Meeting")
    TaskCategory.LIFE -> language.text("生活", "Life")
    TaskCategory.OTHER -> language.text("其他", "Other")
}

private fun labelRepeat(value: RepeatRule, language: AppLanguage): String = when (value) {
    RepeatRule.NONE -> language.text("不重复", "None")
    RepeatRule.DAILY -> language.text("每天", "Daily")
    RepeatRule.WEEKLY -> language.text("每周", "Weekly")
    RepeatRule.MONTHLY -> language.text("每月", "Monthly")
    RepeatRule.CUSTOM -> language.text("自定义", "Custom")
}

private fun labelReminder(value: Int?, language: AppLanguage): String = when (value) {
    1440 -> language.text("提前 1 天", "1 day before")
    180 -> language.text("提前 3 小时", "3 hours before")
    30 -> language.text("提前 30 分钟", "30 minutes before")
    null -> language.text("不提醒", "Off")
    else -> language.text("提前 ${value} 分钟", "$value minutes before")
}
