package com.deadlinemate.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Rect
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadlinemate.BuildConfig
import com.deadlinemate.domain.model.Task
import com.deadlinemate.domain.model.TaskStatus
import com.deadlinemate.ui.UiSettings
import com.deadlinemate.ui.components.GlassPanel
import com.deadlinemate.ui.components.PageTopBar
import com.deadlinemate.ui.components.SectionTitle
import com.deadlinemate.ui.i18n.AppLanguage
import com.deadlinemate.ui.i18n.LocalAppLanguage
import com.deadlinemate.ui.i18n.text
import com.deadlinemate.ui.theme.AppBg
import com.deadlinemate.ui.theme.AppBlue
import com.deadlinemate.ui.theme.AppGreen
import com.deadlinemate.ui.theme.AppLine
import com.deadlinemate.ui.theme.AppSubtext
import com.deadlinemate.ui.theme.AppText
import com.deadlinemate.update.AppUpdateInfo
import com.deadlinemate.update.UpdateDownloadState
import com.deadlinemate.util.DateTimeUtils
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun ProfileScreen(
    tasks: List<Task>,
    settings: UiSettings,
    visualThemeStyle: String = settings.themeStyle,
    pageBg: Color = themeBackground(visualThemeStyle),
    onToggleNotifications: () -> Unit,
    onSetDefaultReminder: (Int?) -> Unit,
    onSetSpeechLanguage: (String) -> Unit,
    onSetTheme: (String) -> Unit,
    onSetProfileName: (String) -> Unit,
    onSetAvatarPath: (String?) -> Unit,
    deepSeekStatus: String,
    onOpenDeepSeekSettings: () -> Unit,
    onCheckUpdates: suspend () -> Result<AppUpdateInfo>,
    onDownloadUpdate: (String, String?) -> Result<Unit>,
    updateDownloadState: UpdateDownloadState,
    onOpenTestCenter: () -> Unit,
    onEnableDeveloperMode: () -> Unit
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val weekStart = weekStart(now)
    val weekEnd = DateTimeUtils.weekEnd(now)
    val weeklyTasks = tasks.filter { it.deadlineDateTime in weekStart until weekEnd }.distinctBy { it.id }
    val doneTasks = weeklyTasks.filter { it.status == TaskStatus.DONE }.sortedByDescending { it.completedAt ?: it.updatedAt }
    val done = doneTasks.size
    val total = weeklyTasks.size
    val streakStats = remember(tasks, now) { buildStreakStats(tasks, now) }
    val language = LocalAppLanguage.current
    var editProfile by remember { mutableStateOf(false) }
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cropSourceUri = uri
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(pageBg).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageTopBar("Profile", language.text("我的", "Me"), "⚙")
            ProfileCard(
                done = done,
                language = language,
                themeStyle = visualThemeStyle,
                profileName = settings.profileName,
                avatarPath = settings.avatarPath,
                onEdit = { editProfile = true }
            )
            SectionTitle(language.text("本周反馈", "This Week"), language.text("本周完成", "Weekly Done"), accent = themeAccent(visualThemeStyle))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ProgressCard(done = done, total = total, language = language, themeStyle = visualThemeStyle, modifier = Modifier.weight(1f).height(174.dp))
                StreakCard(stats = streakStats, language = language, modifier = Modifier.weight(1f).height(174.dp))
            }
        }
        item { HistoryList(doneTasks, language) }
        item {
            SettingList(
                settings = settings,
                onToggleNotifications = onToggleNotifications,
                onSetDefaultReminder = onSetDefaultReminder,
                onSetSpeechLanguage = onSetSpeechLanguage,
                onSetTheme = onSetTheme,
                deepSeekStatus = deepSeekStatus,
                onOpenDeepSeekSettings = onOpenDeepSeekSettings,
                onCheckUpdates = onCheckUpdates,
                onDownloadUpdate = onDownloadUpdate,
                updateDownloadState = updateDownloadState,
                versionName = BuildConfig.VERSION_NAME,
                onOpenTestCenter = onOpenTestCenter,
                onDeveloperUnlocked = {
                    onEnableDeveloperMode()
                    Toast.makeText(context, language.text("开发者模式被打开", "Developer mode enabled"), Toast.LENGTH_SHORT).show()
                    onOpenTestCenter()
                }
            )
        }
    }

    if (editProfile) {
        ProfileEditDialog(
            profileName = settings.profileName,
            avatarPath = settings.avatarPath,
            themeStyle = visualThemeStyle,
            onPickAvatar = {
                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onClearAvatar = { onSetAvatarPath(null) },
            onSaveName = {
                onSetProfileName(it)
                editProfile = false
            },
            onDismiss = { editProfile = false }
        )
    }

    cropSourceUri?.let { uri ->
        AvatarCropDialog(
            uri = uri,
            themeStyle = visualThemeStyle,
            onDismiss = { cropSourceUri = null },
            onSaved = { path ->
                onSetAvatarPath(path)
                cropSourceUri = null
                Toast.makeText(context, language.text("头像已保存", "Avatar saved"), Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun ProfileCard(
    done: Int,
    language: AppLanguage,
    themeStyle: String,
    profileName: String,
    avatarPath: String?,
    onEdit: () -> Unit
) {
    val accent = themeAccent(themeStyle)
    val displayName = profileDisplayName(profileName)
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24.dp) {
        Row(
            Modifier
                .clickable { onEdit() }
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarView(avatarPath = avatarPath, displayName = displayName, accent = accent, size = 54)
            Column(Modifier.weight(1f)) {
                Text(displayName, color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(language.text("本周已完成 $done 个任务，继续稳住。", "$done tasks completed this week. Keep going."), color = AppSubtext, fontSize = 12.sp)
            }
            Text(language.text("编辑", "Edit"), color = accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AvatarView(avatarPath: String?, displayName: String, accent: Color, size: Int) {
    val bitmap = remember(avatarPath) { avatarPath?.let { BitmapFactory.decodeFile(it) } }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape)
        )
    } else {
        Box(Modifier.size(size.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
            Text(avatarInitial(displayName), color = Color.White, fontSize = (size * 0.42f).sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ProfileEditDialog(
    profileName: String,
    avatarPath: String?,
    themeStyle: String,
    onPickAvatar: () -> Unit,
    onClearAvatar: () -> Unit,
    onSaveName: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val language = LocalAppLanguage.current
    val accent = themeAccent(themeStyle)
    val soft = themeAccentSoft(themeStyle)
    val container = themeBackground(themeStyle)
    var name by remember(profileName) { mutableStateOf(profileDisplayName(profileName)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(language.text("编辑个人资料", "Edit Profile"), color = AppText, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AvatarView(avatarPath = avatarPath, displayName = name, accent = accent, size = 76)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(language.text("名字", "Name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = AppLine,
                        cursorColor = accent,
                        focusedLabelColor = accent,
                        focusedContainerColor = Color.White.copy(alpha = 0.58f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.42f)
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onPickAvatar, modifier = Modifier.weight(1f)) {
                        Text(language.text("选择头像", "Choose Photo"), color = accent, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onClearAvatar, modifier = Modifier.weight(1f)) {
                        Text(language.text("清除头像", "Clear"), color = accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSaveName(name) }) {
                Text(language.text("保存", "Save"), color = accent, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(language.text("取消", "Cancel"), color = AppSubtext)
            }
        },
        containerColor = container,
        tonalElevation = 0.dp
    )
}

@Composable
private fun AvatarCropDialog(
    uri: Uri,
    themeStyle: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val accent = themeAccent(themeStyle)
    val container = themeBackground(themeStyle)
    val bitmap = remember(uri) { decodeBitmapFromUri(context, uri) }
    var zoom by remember(uri) { mutableStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var cropSize by remember(uri) { mutableStateOf(IntSize.Zero) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(language.text("裁剪头像", "Crop Avatar"), color = AppText, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(language.text("拖动或双指缩放图片，头像会保存为正方形。", "Drag or pinch to crop a square avatar."), color = AppSubtext, fontSize = 12.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(accent.copy(alpha = 0.92f))
                        .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                        .onSizeChanged { cropSize = it }
                        .pointerInput(bitmap) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                offset += pan
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                        )
                    } else {
                        Text(language.text("图片读取失败", "Failed to load image"), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = bitmap != null,
                onClick = {
                    val saved = bitmap?.let { saveCroppedAvatar(context, it, cropSize, zoom, offset) }
                    if (saved != null) onSaved(saved) else Toast.makeText(context, language.text("头像保存失败", "Failed to save avatar"), Toast.LENGTH_SHORT).show()
                }
            ) {
                Text(language.text("使用头像", "Use Avatar"), color = accent, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(language.text("取消", "Cancel"), color = AppSubtext)
            }
        },
        containerColor = container,
        tonalElevation = 0.dp
    )
}

@Composable
private fun ProgressCard(done: Int, total: Int, language: AppLanguage, themeStyle: String, modifier: Modifier) {
    val accent = themeAccent(themeStyle)
    GlassPanel(modifier = modifier, radius = 24.dp) {
        Column(
            Modifier.fillMaxSize().padding(15.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(language.text("本周完成度", "Weekly Progress"), color = AppText, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(58.dp)) {
                            drawCircle(Color(0x1F767680), style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
                            drawArc(
                                color = accent,
                                startAngle = -90f,
                                sweepAngle = if (total == 0) 0f else (done * 360f / total),
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text("${if (total == 0) 0 else done * 100 / total}%", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                    Text(language.text("完成 $done 个", "$done done"), color = AppSubtext, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StreakCard(stats: StreakStats, language: AppLanguage, modifier: Modifier) {
    GlassPanel(modifier = modifier, radius = 24.dp) {
        Column(
            Modifier.fillMaxSize().padding(15.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(language.text("连续完成 DDL", "DDL Streak"), color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stats.consecutiveDone.toString(), color = AppGreen, fontSize = 48.sp, lineHeight = 58.sp, fontWeight = FontWeight.Black)
                    Text(language.text("次 Deadline", "deadlines"), color = AppSubtext, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HistoryList(doneTasks: List<Task>, language: AppLanguage) {
    val consumeNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                return available
            }
        }
    }
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24.dp) {
        Column(modifier = Modifier.heightIn(max = if (doneTasks.size > 2) 152.dp else 220.dp)) {
            if (doneTasks.isEmpty()) {
                HistoryItem(language.text("本周暂无完成任务", "No tasks completed this week"), language.text("本周完成的 Deadline 会显示在这里。", "Deadlines completed this week will appear here."), "-")
            } else if (doneTasks.size <= 2) {
                doneTasks.forEachIndexed { index, task ->
                    if (index > 0) Divider()
                    HistoryItem(
                        task.title,
                        task.completedAt?.let { language.text("${DateTimeUtils.formatDateTime(it)} 完成", "${DateTimeUtils.formatDateTime(it, "English")} done") } ?: language.text("已完成", "Done"),
                        language.text("准时", "On time")
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .height(152.dp)
                        .nestedScroll(consumeNestedScroll)
                ) {
                    items(doneTasks, key = { it.id }) { task ->
                        HistoryItem(
                            task.title,
                            task.completedAt?.let { language.text("${DateTimeUtils.formatDateTime(it)} 完成", "${DateTimeUtils.formatDateTime(it, "English")} done") } ?: language.text("已完成", "Done"),
                            language.text("准时", "On time")
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(title: String, sub: String, score: String) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(30.dp).background(AppGreen.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Text("✓", color = AppGreen, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(sub, color = AppSubtext, fontSize = 12.sp, maxLines = 1)
        }
        Text(score, color = AppGreen, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SettingList(
    settings: UiSettings,
    onToggleNotifications: () -> Unit,
    onSetDefaultReminder: (Int?) -> Unit,
    onSetSpeechLanguage: (String) -> Unit,
    onSetTheme: (String) -> Unit,
    deepSeekStatus: String,
    onOpenDeepSeekSettings: () -> Unit,
    onCheckUpdates: suspend () -> Result<AppUpdateInfo>,
    onDownloadUpdate: (String, String?) -> Result<Unit>,
    updateDownloadState: UpdateDownloadState,
    versionName: String,
    onOpenTestCenter: () -> Unit,
    onDeveloperUnlocked: () -> Unit
) {
    val language = LocalAppLanguage.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expandedGroup by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }

    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 28.dp) {
        Column {
            if (settings.developerModeEnabled && settings.showDeveloperEntry) {
                Setting(language.text("测试中心", "Test Center"), language.text("进入 ›", "Open ›"), onOpenTestCenter)
                Divider()
            }
            Setting(language.text("DeepSeek API 设置", "DeepSeek API Settings"), "$deepSeekStatus ›", onOpenDeepSeekSettings)
            Divider()
            SettingGroup(
                title = language.text("提醒设置", "Reminder Settings"),
                value = if (settings.notificationEnabled) {
                    language.text("已开启 · ${reminderText(settings.defaultReminderMinutes, language)}", "On · ${reminderText(settings.defaultReminderMinutes, language)}")
                } else {
                    language.text("已关闭", "Off")
                },
                expanded = expandedGroup == "reminder",
                onClick = { expandedGroup = if (expandedGroup == "reminder") null else "reminder" }
            ) {
                Setting(language.text("通知提醒", "Notifications"), if (settings.notificationEnabled) language.text("已开启 ›", "On ›") else language.text("已关闭 ›", "Off ›"), onToggleNotifications)
                Divider()
                SelectSetting(
                    title = language.text("默认提醒时间", "Default Reminder"),
                    value = reminderText(settings.defaultReminderMinutes, language),
                    options = listOf(
                        language.text("提前 1 天", "1 day before") to 1440,
                        language.text("提前 3 小时", "3 hours before") to 180,
                        language.text("提前 30 分钟", "30 minutes before") to 30,
                        language.text("不提醒", "Off") to null
                    ),
                    onSelected = onSetDefaultReminder
                )
            }
            Divider()
            SettingGroup(
                title = language.text("显示与语言", "Display & Language"),
                value = "${languageText(settings.speechLanguage)} · ${themeText(settings.themeStyle, language)}",
                expanded = expandedGroup == "display",
                onClick = { expandedGroup = if (expandedGroup == "display") null else "display" }
            ) {
                SelectSetting(
                    title = language.text("语言", "Language"),
                    value = languageText(settings.speechLanguage),
                    options = listOf("中文" to "中文", "English" to "English"),
                    onSelected = onSetSpeechLanguage
                )
                Divider()
                SelectSetting(
                    title = language.text("主题样式", "Theme"),
                    value = themeText(settings.themeStyle, language),
                    options = listOf(
                        language.text("跟随系统", "System") to "跟随系统",
                        language.text("亮色", "Light") to "亮色",
                        language.text("暗色", "Dark") to "暗色"
                    ),
                    onSelected = onSetTheme
                )
            }
            Divider()
            SettingGroup(
                title = language.text("关于版本", "About"),
                value = "v$versionName",
                expanded = expandedGroup == "about",
                onClick = { expandedGroup = if (expandedGroup == "about") null else "about" }
            ) {
                Setting(
                    language.text("版本更新", "App Updates"),
                    if (checkingUpdate) language.text("检查中...", "Checking...") else language.text("检查更新 ›", "Check ›")
                ) {
                    if (!checkingUpdate) {
                        checkingUpdate = true
                        scope.launch {
                            val result = onCheckUpdates()
                            checkingUpdate = false
                            result
                                .onSuccess { updateInfo = it }
                                .onFailure { updateError = it.message ?: language.text("检查更新失败，请稍后重试。", "Failed to check for updates.") }
                        }
                    }
                }
                Divider()
                VersionSetting(versionName = versionName, onDeveloperUnlocked = onDeveloperUnlocked)
            }
        }
    }

    updateInfo?.let { info ->
        UpdateResultDialog(
            info = info,
            language = language,
            downloadState = updateDownloadState,
            onDismiss = { updateInfo = null },
            onOpenRelease = {
                updateInfo = null
                openUrl(context, info.releaseUrl)
            },
            onOpenApk = {
                val url = info.apkDownloadUrl ?: return@UpdateResultDialog
                onDownloadUpdate(url, info.apkName).onFailure {
                    updateError = it.message ?: language.text("更新下载失败，请稍后重试。", "Update download failed. Try again later.")
                }
                return@UpdateResultDialog
                var downloadingUpdate = false
                if (!downloadingUpdate) {
                    downloadingUpdate = true
                    scope.launch {
                        val result = onDownloadUpdate(url, info.apkName)
                        downloadingUpdate = false
                        result
                            .onSuccess {
                                updateInfo = null
                                Toast.makeText(context, language.text("下载完成，请在系统安装页面确认。", "Download complete. Confirm installation in the system installer."), Toast.LENGTH_LONG).show()
                            }
                            .onFailure {
                                updateError = it.message ?: language.text("更新下载失败，请稍后重试。", "Update download failed. Try again later.")
                            }
                    }
                }
            }
        )
    }

    updateError?.let { message ->
        AlertDialog(
            onDismissRequest = { updateError = null },
            title = { Text(language.text("检查更新失败", "Update Check Failed"), color = AppText, fontWeight = FontWeight.Black) },
            text = { Text(message, color = AppSubtext, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { updateError = null }) {
                    Text(language.text("知道了", "OK"), color = AppText, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(26.dp),
            tonalElevation = 0.dp
        )
    }
}

@Composable
private fun UpdateResultDialog(
    info: AppUpdateInfo,
    language: AppLanguage,
    downloadState: UpdateDownloadState,
    onDismiss: () -> Unit,
    onOpenRelease: () -> Unit,
    onOpenApk: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (info.hasUpdate) language.text("发现新版本", "Update Available") else language.text("已是最新版本", "Up to Date"),
                color = AppText,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (downloadState.running) {
                    val progress = downloadState.progress
                    LinearProgressIndicator(
                        progress = { progress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = AppBlue,
                        trackColor = AppLine
                    )
                    Text(progress?.let { "${(it * 100).toInt().coerceIn(0, 100)}%" } ?: language.text("正在连接下载...", "Connecting..."), color = AppSubtext, fontSize = 12.sp)
                }
                Text(
                    language.text(
                        "当前版本：${info.currentVersion}\n最新版本：${info.latestVersion}",
                        "Current: ${info.currentVersion}\nLatest: ${info.latestVersion}"
                    ),
                    color = AppText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (info.releaseNotes.isNotBlank()) {
                    Text(info.releaseNotes, color = AppSubtext, fontSize = 12.sp, lineHeight = 17.sp)
                }
                if (info.hasUpdate && info.apkName != null) {
                    Text(
                        language.text("将在应用内后台下载：${info.apkName}", "In-app background download: ${info.apkName}"),
                        color = AppSubtext,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            if (info.hasUpdate && info.apkDownloadUrl != null) {
                TextButton(onClick = onOpenApk, enabled = !downloadState.running) {
                    Text(
                        if (downloadState.running) language.text("下载中...", "Downloading...") else language.text("后台下载并安装", "Download and Install"),
                        color = if (downloadState.running) AppSubtext else AppText,
                        fontWeight = FontWeight.Black
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(language.text("知道了", "OK"), color = AppText, fontWeight = FontWeight.Black)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = if (info.releaseUrl.isNotBlank()) onOpenRelease else onDismiss) {
                Text(
                    if (info.releaseUrl.isNotBlank()) language.text("查看发布页", "View Release") else language.text("关闭", "Close"),
                    color = AppSubtext,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(26.dp),
        tonalElevation = 0.dp
    )
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingGroup(
    title: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(expanded) {
        if (expanded) {
            repeat(7) {
                delay(32)
                bringIntoViewRequester.bringIntoView()
            }
        }
    }
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(value, color = AppSubtext, fontSize = 12.sp, maxLines = 1)
            }
            Text(if (expanded) "收起" else "展开 ›", color = AppSubtext, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(240, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(120)),
            exit = shrinkVertically(
                animationSpec = tween(180, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(100))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 104.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(AppLine.copy(alpha = 0.55f))
                )
                Column(Modifier.weight(1f)) {
                    content()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .bringIntoViewRequester(bringIntoViewRequester)
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionSetting(
    versionName: String,
    onDeveloperUnlocked: () -> Unit
) {
    val language = LocalAppLanguage.current
    var tapCount by remember { mutableStateOf(0) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                tapCount += 1
                if (tapCount >= 5) {
                    tapCount = 0
                    onDeveloperUnlocked()
                }
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            language.text("版本号", "Version"),
            color = AppText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            versionName,
            color = AppSubtext,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun Setting(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(value, color = AppSubtext, fontSize = 14.sp)
    }
}

@Composable
private fun <T> SelectSetting(
    title: String,
    value: String,
    options: List<Pair<String, T>>,
    onSelected: (T) -> Unit
) {
    var showOptions by remember { mutableStateOf(false) }
    Setting(title, "$value ›") { showOptions = true }
    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text(title, color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    options.forEach { (label, optionValue) ->
                        OptionRow(
                            label = label,
                            selected = label == value,
                            onClick = {
                                showOptions = false
                                onSelected(optionValue)
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showOptions = false }) {
                    Text(LocalAppLanguage.current.text("取消", "Cancel"), color = AppSubtext)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 0.dp
        )
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) AppBlue.copy(alpha = 0.10f) else Color(0xFFF7F7F8))
            .border(1.dp, if (selected) AppBlue.copy(alpha = 0.26f) else AppLine, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(if (selected) "✓" else "", color = AppBlue, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).border(0.5.dp, AppLine))
}

private fun reminderText(minutes: Int?, language: AppLanguage): String = when (minutes) {
    1440 -> language.text("提前 1 天", "1 day before")
    180 -> language.text("提前 3 小时", "3 hours before")
    30 -> language.text("提前 30 分钟", "30 minutes before")
    null -> language.text("不提醒", "Off")
    else -> language.text("提前 $minutes 分钟", "$minutes minutes before")
}

fun resolveThemeStyle(theme: String, systemDark: Boolean): String = when (theme) {
    "暗色", "Dark" -> "暗色"
    "亮色", "Light" -> "亮色"
    "跟随系统", "System" -> if (systemDark) "暗色" else "亮色"
    else -> if (systemDark) "暗色" else "亮色"
}

fun themeBackground(theme: String): Color = when (theme) {
    "暗色", "Dark" -> Color(0xFFF1F1F1)
    else -> Color(0xFFFFF8ED)
}

fun themeAccent(theme: String): Color = when (theme) {
    "暗色", "Dark" -> Color(0xFF2F3437)
    else -> Color(0xFF8F765C)
}

fun themeAccentSoft(theme: String): Color = when (theme) {
    "暗色", "Dark" -> Color(0xFFE6E7E8)
    else -> Color(0xFFF1E5D6)
}

fun themeHeroGradient(theme: String): List<Color> {
    return when (theme) {
        "暗色", "Dark" -> listOf(Color(0xFF3A3F44), Color(0xFF73777C))
        else -> listOf(Color(0xFFC7A978), Color(0xFFE7D7BD))
    }
}

private fun profileDisplayName(name: String): String {
    return name.trim().ifBlank { "User" }
}

private fun avatarInitial(displayName: String): String {
    return displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U"
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > 2048) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }.getOrNull()
}

private fun saveCroppedAvatar(
    context: Context,
    bitmap: Bitmap,
    cropSize: IntSize,
    zoom: Float,
    offset: Offset
): String? {
    return runCatching {
        val box = min(cropSize.width, cropSize.height).takeIf { it > 0 }?.toFloat() ?: min(bitmap.width, bitmap.height).toFloat()
        val baseScale = max(box / bitmap.width, box / bitmap.height)
        val cropInBitmap = (box / (zoom.coerceAtLeast(1f) * baseScale)).coerceAtMost(min(bitmap.width, bitmap.height).toFloat())
        val localCenterX = ((box / 2f - box / 2f - offset.x) / zoom.coerceAtLeast(1f)) + box / 2f
        val localCenterY = ((box / 2f - box / 2f - offset.y) / zoom.coerceAtLeast(1f)) + box / 2f
        val displayedWidth = bitmap.width * baseScale
        val displayedHeight = bitmap.height * baseScale
        val baseLeft = (box - displayedWidth) / 2f
        val baseTop = (box - displayedHeight) / 2f
        val centerX = ((localCenterX - baseLeft) / baseScale).coerceIn(0f, bitmap.width.toFloat())
        val centerY = ((localCenterY - baseTop) / baseScale).coerceIn(0f, bitmap.height.toFloat())
        val left = (centerX - cropInBitmap / 2f).coerceIn(0f, bitmap.width - cropInBitmap)
        val top = (centerY - cropInBitmap / 2f).coerceIn(0f, bitmap.height - cropInBitmap)
        val source = Rect(
            left.roundToInt(),
            top.roundToInt(),
            (left + cropInBitmap).roundToInt().coerceAtMost(bitmap.width),
            (top + cropInBitmap).roundToInt().coerceAtMost(bitmap.height)
        )
        val output = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        AndroidCanvas(output).drawBitmap(bitmap, source, Rect(0, 0, 512, 512), null)
        val dir = File(context.filesDir, "avatar").apply { mkdirs() }
        val file = File(dir, "profile_avatar.png")
        FileOutputStream(file).use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
        output.recycle()
        file.absolutePath
    }.getOrNull()
}

private fun languageText(language: String): String {
    return if (language == "English") "English" else "中文"
}

private fun themeText(theme: String, language: AppLanguage): String = when (theme) {
    "亮色", "Light" -> language.text("亮色", "Light")
    "暗色", "Dark" -> language.text("暗色", "Dark")
    else -> language.text("跟随系统", "System")
}

private data class StreakStats(
    val consecutiveDone: Int,
    val noMissDays: Long
)

private fun buildStreakStats(tasks: List<Task>, now: Long): StreakStats {
    val completed = tasks
        .filter { it.status == TaskStatus.DONE }
        .sortedByDescending { it.completedAt ?: it.updatedAt }
    val consecutiveDone = completed.takeWhile { task ->
        val completedAt = task.completedAt ?: task.updatedAt
        completedAt <= task.deadlineDateTime
    }.size

    val latestMiss = tasks.mapNotNull { task ->
        val completedAt = task.completedAt
        when {
            task.status != TaskStatus.DONE && task.deadlineDateTime < now -> task.deadlineDateTime
            task.status == TaskStatus.DONE && completedAt != null && completedAt > task.deadlineDateTime -> completedAt
            else -> null
        }
    }.maxOrNull()
    val fallbackStart = tasks.minOfOrNull { it.createdAt } ?: now
    val since = latestMiss ?: fallbackStart
    val days = TimeUnit.MILLISECONDS.toDays(
        (DateTimeUtils.todayStart(now) - DateTimeUtils.todayStart(since)).coerceAtLeast(0L)
    )
    return StreakStats(consecutiveDone = consecutiveDone, noMissDays = days)
}

private fun weekStart(now: Long): Long {
    val cal = DateTimeUtils.calendar(now)
    val offset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    return DateTimeUtils.todayStart(now) - offset * TimeUnit.DAYS.toMillis(1)
}

private fun formatWeeksDays(totalDays: Long, language: AppLanguage): String {
    val weeks = totalDays / 7
    val days = totalDays % 7
    return language.text("${weeks}周${days}天", "${weeks}w ${days}d")
}

private fun formatMonthsWeeksDays(totalDays: Long, language: AppLanguage): String {
    val months = totalDays / 30
    val weeks = (totalDays % 30) / 7
    val days = (totalDays % 30) % 7
    return language.text("${months}月${weeks}周${days}天", "${months}m ${weeks}w ${days}d")
}

private fun formatYearsMonthsDays(totalDays: Long, language: AppLanguage): String {
    val years = totalDays / 365
    val months = (totalDays % 365) / 30
    val days = (totalDays % 365) % 30
    return language.text("${years}年${months}月${days}天", "${years}y ${months}m ${days}d")
}
