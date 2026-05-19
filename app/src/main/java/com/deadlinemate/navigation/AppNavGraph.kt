package com.deadlinemate.navigation

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.deadlinemate.deepseek.statusText
import com.deadlinemate.domain.model.Task
import com.deadlinemate.domain.model.TaskStatus
import com.deadlinemate.ui.DeadlineMateViewModel
import com.deadlinemate.ui.add.AddTaskScreen
import com.deadlinemate.ui.calendar.CalendarScreen
import com.deadlinemate.ui.deepseek.DeepSeekSettingsScreen
import com.deadlinemate.ui.home.HomeScreen
import com.deadlinemate.ui.priority.PriorityScreen
import com.deadlinemate.ui.profile.ProfileScreen
import com.deadlinemate.ui.profile.themeAccent
import com.deadlinemate.ui.profile.themeAccentSoft
import com.deadlinemate.ui.profile.themeBackground
import com.deadlinemate.ui.profile.resolveThemeStyle
import com.deadlinemate.ui.test.TestCenterScreen
import com.deadlinemate.ui.i18n.LocalAppLanguage
import com.deadlinemate.ui.i18n.appLanguage
import com.deadlinemate.ui.i18n.text
import com.deadlinemate.ui.theme.AppLine
import com.deadlinemate.ui.theme.AppGreen
import com.deadlinemate.ui.theme.AppSubtext
import com.deadlinemate.ui.theme.AppText
import kotlinx.coroutines.delay

sealed class Screen(val route: String, val label: String, val icon: String) {
    data object Home : Screen("home", "首页", "🏠")
    data object Calendar : Screen("calendar", "日历", "📅")
    data object AddTask : Screen("add_task", "添加", "+")
    data object Priority : Screen("priority", "优先级", "🧭")
    data object Profile : Screen("profile", "我的", "👤")
    data object DeepSeekSettings : Screen("deepseek_settings", "DeepSeek", "")
    data object TestCenter : Screen("test_center", "测试", "")
}

private data class CompletionFeedback(
    val id: Long,
    val message: String
)

@Composable
fun DeadlineMateApp(vm: DeadlineMateViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val settings by vm.uiSettings.collectAsStateWithLifecycle()
    val deepSeekConfig by vm.deepSeekConfig.collectAsStateWithLifecycle()
    val language = appLanguage(settings.speechLanguage)
    val visualThemeStyle = resolveThemeStyle(settings.themeStyle, isSystemInDarkTheme())
    val pageBg = themeBackground(visualThemeStyle)
    val visualSettings = settings.copy(themeStyle = visualThemeStyle)
    val items = listOf(Screen.Home, Screen.Calendar, Screen.AddTask, Screen.Priority, Screen.Profile)
    var pendingNotificationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var completionToast by remember { mutableStateOf<CompletionFeedback?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingNotificationAction?.invoke()
        pendingNotificationAction = null
    }

    fun runAfterNotificationPermission(action: () -> Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingNotificationAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    fun toggleDoneWithFeedback(task: Task) {
        val completing = task.status != TaskStatus.DONE
        vm.toggleDone(task)
        if (completing) {
            completionToast = CompletionFeedback(
                id = System.nanoTime(),
                message = completionFeedbackTexts(settings.speechLanguage).random()
            )
        }
    }

    CompositionLocalProvider(LocalAppLanguage provides language) {
    Box(Modifier.fillMaxSize().background(pageBg)) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 10.dp, bottom = 92.dp)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    tasks = tasks,
                    onAdd = { navController.navigate(Screen.AddTask.route) },
                    onPriority = { navController.navigate(Screen.Priority.route) },
                    onToggleDone = ::toggleDoneWithFeedback,
                    onUpdateTask = vm::updateTaskDetails,
                    pageBg = pageBg,
                    themeStyle = visualThemeStyle
                )
            }
            composable(Screen.Calendar.route) {
                CalendarScreen(
                    tasks = tasks,
                    onAdd = { navController.navigate(Screen.AddTask.route) },
                    onToggleDone = ::toggleDoneWithFeedback,
                    pageBg = pageBg,
                    themeStyle = visualThemeStyle
                )
            }
            composable(Screen.AddTask.route) {
                AddTaskScreen(
                    settings = visualSettings,
                    deepSeekEnabled = deepSeekConfig?.enabled == true && !deepSeekConfig?.apiKey.isNullOrBlank(),
                    pageBg = pageBg,
                    onGoConfigure = { navController.navigate(Screen.DeepSeekSettings.route) },
                    parseSmartText = vm::parseTaskText,
                    onSave = { title, desc, deadline, importance, category, repeat, reminder, finishAfterSave ->
                        val saveAction = {
                            vm.addTask(title, desc, deadline, importance, category, repeat, reminder)
                            if (finishAfterSave) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Home.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        }
                        if (reminder != null && settings.notificationEnabled) {
                            runAfterNotificationPermission(saveAction)
                        } else {
                            saveAction()
                        }
                    }
                )
            }
            composable(Screen.Priority.route) {
                PriorityScreen(tasks, onToggleDone = ::toggleDoneWithFeedback, pageBg = pageBg, themeStyle = visualThemeStyle)
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    tasks = tasks,
                    settings = settings,
                    visualThemeStyle = visualThemeStyle,
                    pageBg = pageBg,
                    onToggleNotifications = {
                        if (settings.notificationEnabled) {
                            vm.toggleNotifications()
                        } else {
                            runAfterNotificationPermission { vm.toggleNotifications() }
                        }
                    },
                    onSetDefaultReminder = vm::setDefaultReminder,
                    onSetSpeechLanguage = vm::setSpeechLanguage,
                    onSetTheme = vm::setThemeStyle,
                    onSetProfileName = vm::setProfileName,
                    onSetAvatarPath = vm::setAvatarPath,
                    deepSeekStatus = deepSeekConfig.statusText(settings.speechLanguage),
                    onOpenDeepSeekSettings = { navController.navigate(Screen.DeepSeekSettings.route) },
                    onCheckUpdates = { vm.checkAppUpdate(com.deadlinemate.BuildConfig.VERSION_NAME) },
                    onOpenTestCenter = { navController.navigate(Screen.TestCenter.route) },
                    onEnableDeveloperMode = vm::enableDeveloperMode
                )
            }
            composable(Screen.DeepSeekSettings.route) {
                DeepSeekSettingsScreen(
                    config = deepSeekConfig,
                    pageBg = pageBg,
                    onBack = { navController.popBackStack() },
                    onSave = vm::saveDeepSeekConfig,
                    onClear = vm::clearDeepSeekConfig,
                    onTest = vm::testDeepSeekConnection
                )
            }
            composable(Screen.TestCenter.route) {
                TestCenterScreen(
                    settings = settings,
                    deepSeekStatus = deepSeekConfig.statusText(settings.speechLanguage),
                    pageBg = pageBg,
                    parseDeepSeekDebug = vm::parseTaskTextForDebug,
                    showDeveloperEntry = settings.showDeveloperEntry,
                    onSetShowDeveloperEntry = vm::setShowDeveloperEntry,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        val entry by navController.currentBackStackEntryAsState()
        val currentRoute = entry?.destination?.route ?: Screen.Home.route
        FloatingBottomNav(
            items = items,
            currentRoute = currentRoute,
            themeStyle = visualThemeStyle,
            onNavigate = { screen ->
                if (screen.route != currentRoute) {
                    if (screen == Screen.Home) {
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    } else {
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        completionToast?.let { feedback ->
            CompletionCelebrationOverlay(
                feedback = feedback,
                themeStyle = visualThemeStyle,
                onFinish = { completionToast = null },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
    }
}

@Composable
private fun CompletionPill(
    feedback: CompletionFeedback,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(feedback.id) {
        delay(2200)
        onFinish()
    }
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 92.dp, start = 24.dp, end = 24.dp)
            .fillMaxWidth()
            .shadow(22.dp, RoundedCornerShape(26.dp), ambientColor = Color(0x22000000), spotColor = Color(0x1A000000))
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = 0.96f))
            .border(1.dp, Color.White.copy(alpha = 0.92f), RoundedCornerShape(26.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(AppGreen.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                .border(1.dp, AppGreen.copy(alpha = 0.22f), RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", color = AppGreen, fontSize = 20.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(feedback.message, color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(
                text = if (feedback.message.any { it.code > 127 }) "已记录到本周完成进度" else "Saved to this week's progress",
                color = AppSubtext,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(Color(0xFF34C759), Color(0xFFFFCC00), Color(0xFFFF8A65)).forEachIndexed { index, color ->
                Box(
                    Modifier
                        .size(if (index == 1) 7.dp else 5.dp)
                        .background(color.copy(alpha = 0.78f), RoundedCornerShape(999.dp))
                )
            }
        }
    }
}

@Composable
private fun CompletionCelebrationOverlay(
    feedback: CompletionFeedback,
    themeStyle: String,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember(feedback.id) { mutableStateOf(false) }
    val accent = themeAccent(themeStyle)
    val accentSoft = themeAccentSoft(themeStyle)

    LaunchedEffect(feedback.id) {
        visible = true
        delay(2500)
        visible = false
        delay(360)
        onFinish()
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(animationSpec = tween(260)) + scaleIn(
            initialScale = 0.98f,
            animationSpec = tween(420, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(animationSpec = tween(320)) + scaleOut(
            targetScale = 1.02f,
            animationSpec = tween(320, easing = FastOutSlowInEasing)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.96f),
                            accentSoft.copy(alpha = 0.94f),
                            Color.White.copy(alpha = 0.96f)
                        )
                    )
                )
                .clickable { visible = false }
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            CelebrationBubble(accent.copy(alpha = 0.10f), 130, Alignment.TopEnd, 34, 118)
            CelebrationBubble(AppGreen.copy(alpha = 0.13f), 82, Alignment.TopStart, 30, 165)
            CelebrationBubble(Color(0xFFFFCC66).copy(alpha = 0.18f), 62, Alignment.BottomEnd, -44, -158)
            CelebrationBubble(accent.copy(alpha = 0.12f), 96, Alignment.BottomStart, 36, -140)

            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 128.dp)
            ) {
                CelebrationSpark(Color(0xFFFFCC66), 8)
                CelebrationSpark(AppGreen, 12)
                CelebrationSpark(accent, 7)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .shadow(24.dp, RoundedCornerShape(999.dp), ambientColor = AppGreen.copy(alpha = 0.18f), spotColor = AppGreen.copy(alpha = 0.22f))
                        .clip(RoundedCornerShape(999.dp))
                        .background(Brush.linearGradient(listOf(AppGreen.copy(alpha = 0.92f), Color(0xFF6FE08D))))
                        .border(2.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = Color.White, fontSize = 66.sp, lineHeight = 66.sp, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (feedback.message.any { it.code > 127 }) "完成得很漂亮" else "Nicely done",
                        color = AppText,
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(feedback.message, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = if (feedback.message.any { it.code > 127 }) "这个 DDL 已经从待办里清掉了" else "This deadline has been cleared",
                        color = AppSubtext,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CelebrationBubble(color: Color, size: Int, alignment: Alignment, x: Int, y: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = alignment) {
        Box(
            Modifier
                .offset(x.dp, y.dp)
                .size(size.dp)
                .background(color, RoundedCornerShape(999.dp))
        )
    }
}

@Composable
private fun CelebrationSpark(color: Color, size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.84f))
    )
}

private fun completionFeedbackTexts(language: String): List<String> {
    return if (language == "English") {
        listOf(
            "DDL cleared.",
            "One deadline handled.",
            "Finished on track.",
            "Progress locked in."
        )
    } else {
        listOf(
            "DDL 已完成",
            "这一项处理好了",
            "今天又推进了一步",
            "完成进度已记录"
        )
    }
}

@Composable
private fun FloatingBottomNav(
    items: List<Screen>,
    currentRoute: String,
    themeStyle: String,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = themeAccent(themeStyle)
    val accentSoft = themeAccentSoft(themeStyle)
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(68.dp)
            .shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = Color(0x1F000000), spotColor = Color(0x1F000000))
            .background(Color.White.copy(alpha = 0.86f), RoundedCornerShape(28.dp))
            .border(1.dp, AppLine, RoundedCornerShape(28.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { screen ->
            val selected = currentRoute == screen.route
            val isAdd = screen == Screen.AddTask
            Column(
                modifier = Modifier
                    .weight(if (isAdd) 1.25f else 1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (selected && !isAdd) accentSoft else Color.Transparent,
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onNavigate(screen) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isAdd) {
                    Box(
                        modifier = Modifier
                            .background(accent, RoundedCornerShape(12.dp))
                            .padding(horizontal = 9.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = Color.White, fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black)
                    }
                } else {
                    Text(screen.icon, fontSize = 21.sp, lineHeight = 23.sp)
                }
                Text(
                    navLabel(screen),
                    color = if (selected) accent else AppSubtext,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun navLabel(screen: Screen): String {
    val language = LocalAppLanguage.current
    return when (screen) {
        Screen.Home -> language.text("首页", "Home")
        Screen.Calendar -> language.text("日历", "Calendar")
        Screen.AddTask -> language.text("添加", "Add")
        Screen.Priority -> language.text("优先级", "Priority")
        Screen.Profile -> language.text("我的", "Me")
        Screen.DeepSeekSettings -> "DeepSeek"
        Screen.TestCenter -> language.text("测试", "Test")
    }
}
