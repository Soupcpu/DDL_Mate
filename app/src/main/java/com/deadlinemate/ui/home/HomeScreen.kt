package com.deadlinemate.ui.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadlinemate.domain.model.ImportanceLevel
import com.deadlinemate.domain.model.RepeatRule
import com.deadlinemate.domain.model.Task
import com.deadlinemate.domain.model.TaskCategory
import com.deadlinemate.domain.model.TaskStatus
import com.deadlinemate.ui.components.GlassPanel
import com.deadlinemate.ui.components.LetterIcon
import com.deadlinemate.ui.components.PageTopBar
import com.deadlinemate.ui.components.SectionTitle
import com.deadlinemate.ui.components.StatCard
import com.deadlinemate.ui.components.TaskPreviewCard
import com.deadlinemate.ui.components.importanceText
import com.deadlinemate.ui.components.taskColor
import com.deadlinemate.ui.i18n.AppLanguage
import com.deadlinemate.ui.i18n.LocalAppLanguage
import com.deadlinemate.ui.i18n.text
import com.deadlinemate.ui.profile.themeAccent
import com.deadlinemate.ui.profile.themeHeroGradient
import com.deadlinemate.ui.theme.AppBg
import com.deadlinemate.ui.theme.AppBlue
import com.deadlinemate.ui.theme.AppGreen
import com.deadlinemate.ui.theme.AppLine
import com.deadlinemate.ui.theme.AppOrange
import com.deadlinemate.ui.theme.AppRed
import com.deadlinemate.ui.theme.AppSubtext
import com.deadlinemate.ui.theme.AppText
import com.deadlinemate.util.DateTimeUtils
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(
    tasks: List<Task>,
    onAdd: () -> Unit,
    onPriority: () -> Unit,
    onToggleDone: (Task) -> Unit,
    onUpdateTask: (Task, String, String?, Long, ImportanceLevel, TaskCategory, RepeatRule, Int?) -> Unit,
    pageBg: Color = AppBg,
    themeStyle: String = "浅色"
) {
    val now = rememberCurrentTime()
    val todayStart = DateTimeUtils.todayStart(now)
    val tomorrowStart = todayStart + TimeUnit.DAYS.toMillis(1)
    val weekStart = weekStart(now)
    val weekEnd = DateTimeUtils.weekEnd(now)
    val soonEnd = now + TimeUnit.HOURS.toMillis(24)
    val active = tasks.filter { it.status != TaskStatus.DONE }
    val weeklyTasks = tasks.filter { it.deadlineDateTime in weekStart until weekEnd }
    val weeklyActive = weeklyTasks.filter { it.status != TaskStatus.DONE }
    val deadlineTasks = active
        .sortedWith(compareByDescending<Task> { it.deadlineDateTime < now }.thenBy { it.deadlineDateTime })
        .take(6)
    val weekCount = weeklyActive.size
    val soonCount = active.count { it.deadlineDateTime in now until soonEnd }
    val todayCount = active.count { it.deadlineDateTime in todayStart until tomorrowStart }
    val importantUrgent = active.count { it.importance == ImportanceLevel.HIGH && it.deadlineDateTime <= soonEnd }
    val doneCount = weeklyTasks.count { it.status == TaskStatus.DONE }
    val focus = active.maxByOrNull { priorityScore(it, now) }
    val language = LocalAppLanguage.current
    var editingTask by remember { mutableStateOf<Task?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(pageBg).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageTopBar(
                language.text("今天 · ${DateTimeUtils.formatDay(now)}", "Today · ${DateTimeUtils.formatDay(now, "English")}"),
                language.text("待办概览", "Tasks"),
                "＋",
                onAdd
            )

            HeroCard(weekCount, soonCount, language, themeStyle)

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(language.text("今", "D"), language.text("今日截止", "Due today"), todayCount.toString(), AppRed, Modifier.weight(1f))
                StatCard(language.text("急", "!"), language.text("重要紧急", "Urgent"), importantUrgent.toString(), AppOrange, Modifier.weight(1f))
                StatCard(language.text("完", "✓"), language.text("本周完成", "Done this week"), doneCount.toString(), AppGreen, Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))

            SectionTitle(language.text("今日状态", "Today"), language.text("优先级", "Priority"), onPriority, accent = themeAccent(themeStyle))
            FocusCard(focus, now, language)

            SectionTitle(language.text("临近 Deadline", "Upcoming Deadlines"), language.text("日历", "Calendar"))
        }

        if (deadlineTasks.isEmpty()) {
            item { EmptyState(language) }
        } else {
            items(deadlineTasks, key = { it.id }) { task ->
                TaskPreviewCard(
                    task = task,
                    onCheckedChange = { onToggleDone(task) },
                    onLongClick = { editingTask = task }
                )
            }
        }
    }

    editingTask?.let { task ->
        EditDeadlineDialog(
            task = task,
            language = language,
            accent = themeAccent(themeStyle),
            onDismiss = { editingTask = null },
            onSave = { title, desc, deadline, importance, category, repeat, reminder ->
                onUpdateTask(task, title, desc, deadline, importance, category, repeat, reminder)
                editingTask = null
            }
        )
    }
}

private enum class HomeDetailType { DueToday, Urgent, DoneThisWeek }

@Composable
private fun HomeStatDetailDialog(
    type: HomeDetailType,
    tasks: List<Task>,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onToggleDone: (Task) -> Unit,
    onEdit: (Task) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(detailTitle(type, language), color = AppText, fontWeight = FontWeight.Black)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(detailSubtitle(type, tasks.size, language), color = AppSubtext, fontSize = 12.sp)
                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(18.dp))
                            .border(1.dp, AppLine, RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Text(language.text("当前没有对应任务。", "No matching tasks."), color = AppSubtext, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            TaskPreviewCard(
                                task = task,
                                onCheckedChange = { onToggleDone(task) },
                                onLongClick = { onEdit(task) }
                            )
                        }
                    }
                    Text(language.text("点按切换完成状态，长按修改 DDL 细节。", "Tap to toggle done. Long press to edit details."), color = AppSubtext, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(language.text("关闭", "Close"), color = AppText, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color.White
    )
}

@Composable
private fun EditDeadlineDialog(
    task: Task,
    language: AppLanguage,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (String, String?, Long, ImportanceLevel, TaskCategory, RepeatRule, Int?) -> Unit
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var desc by remember(task.id) { mutableStateOf(task.description.orEmpty()) }
    var date by remember(task.id) { mutableStateOf(DateTimeUtils.formatInputDate(task.deadlineDateTime)) }
    var time by remember(task.id) { mutableStateOf(DateTimeUtils.formatTime(task.deadlineDateTime)) }
    var importance by remember(task.id) { mutableStateOf(task.importance) }
    var category by remember(task.id) { mutableStateOf(task.category) }
    var repeat by remember(task.id) { mutableStateOf(task.repeatRule) }
    var reminder by remember(task.id) { mutableStateOf(task.reminderMinutesBefore) }
    val deadline = parseEditDeadline(date, time)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(language.text("修改 DDL 细节", "Edit Deadline"), color = AppText, fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    EditField(language.text("任务名称", "Task name")) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = editFieldColors()
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        EditField(language.text("截止日期", "Date"), Modifier.weight(1f)) {
                            DateChoiceBox(date) { date = it }
                        }
                        EditField(language.text("截止时间", "Time"), Modifier.weight(1f)) {
                            TimeChoiceBox(time) { time = it }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        EditField(language.text("重要性", "Importance"), Modifier.weight(1f)) {
                            SelectEditBox(
                                importanceLabel(importance, language),
                                listOf(
                                    importanceLabel(ImportanceLevel.HIGH, language) to ImportanceLevel.HIGH,
                                    importanceLabel(ImportanceLevel.MEDIUM, language) to ImportanceLevel.MEDIUM,
                                    importanceLabel(ImportanceLevel.LOW, language) to ImportanceLevel.LOW
                                ),
                                onSelected = { importance = it }
                            )
                        }
                        EditField(language.text("提醒", "Reminder"), Modifier.weight(1f)) {
                            SelectEditBox(
                                reminderLabel(reminder, language),
                                listOf(
                                    language.text("提前 1 天", "1 day before") to 1440,
                                    language.text("提前 3 小时", "3 hours before") to 180,
                                    language.text("提前 30 分钟", "30 minutes before") to 30,
                                    language.text("不提醒", "Off") to null
                                ),
                                onSelected = { reminder = it }
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        EditField(language.text("重复", "Repeat"), Modifier.weight(1f)) {
                            SelectEditBox(
                                repeatLabel(repeat, language),
                                listOf(
                                    repeatLabel(RepeatRule.NONE, language) to RepeatRule.NONE,
                                    repeatLabel(RepeatRule.DAILY, language) to RepeatRule.DAILY,
                                    repeatLabel(RepeatRule.WEEKLY, language) to RepeatRule.WEEKLY,
                                    repeatLabel(RepeatRule.MONTHLY, language) to RepeatRule.MONTHLY,
                                    repeatLabel(RepeatRule.CUSTOM, language) to RepeatRule.CUSTOM
                                ),
                                onSelected = { repeat = it }
                            )
                        }
                        EditField(language.text("类型", "Category"), Modifier.weight(1f)) {
                            SelectEditBox(
                                categoryLabel(category, language),
                                listOf(
                                    categoryLabel(TaskCategory.OTHER, language) to TaskCategory.OTHER,
                                    categoryLabel(TaskCategory.STUDY, language) to TaskCategory.STUDY,
                                    categoryLabel(TaskCategory.HOMEWORK, language) to TaskCategory.HOMEWORK,
                                    categoryLabel(TaskCategory.COMPETITION, language) to TaskCategory.COMPETITION,
                                    categoryLabel(TaskCategory.MEETING, language) to TaskCategory.MEETING,
                                    categoryLabel(TaskCategory.LIFE, language) to TaskCategory.LIFE
                                ),
                                onSelected = { category = it }
                            )
                        }
                    }
                }
                item {
                    EditField(language.text("备注", "Notes")) {
                        OutlinedTextField(
                            value = desc,
                            onValueChange = { desc = it },
                            modifier = Modifier.fillMaxWidth().height(86.dp),
                            minLines = 2,
                            shape = RoundedCornerShape(16.dp),
                            colors = editFieldColors()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title.trim(), desc.trim().ifBlank { null }, deadline, importance, category, repeat, reminder) },
                enabled = title.isNotBlank() && date.isNotBlank(),
                colors = ButtonDefaults.textButtonColors(contentColor = accent)
            ) {
                Text(language.text("保存", "Save"), fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(language.text("取消", "Cancel"), color = AppSubtext)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun EditField(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier) {
        Text(label, color = AppSubtext, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
        content()
    }
}

@Composable
private fun DateChoiceBox(value: String, onSelected: (String) -> Unit) {
    val context = LocalContext.current
    EditChoiceBox(value) {
        val parts = value.split("-").mapNotNull { it.toIntOrNull() }
        val cal = DateTimeUtils.calendar().apply {
            if (parts.size == 3) set(parts[0], parts[1] - 1, parts[2])
        }
        DatePickerDialog(
            context,
            { _, year, month, day -> onSelected("%04d-%02d-%02d".format(year, month + 1, day)) },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}

@Composable
private fun TimeChoiceBox(value: String, onSelected: (String) -> Unit) {
    val context = LocalContext.current
    EditChoiceBox(value) {
        val parts = value.split(":").mapNotNull { it.toIntOrNull() }
        TimePickerDialog(
            context,
            { _, hour, minute -> onSelected("%02d:%02d".format(hour, minute)) },
            parts.getOrNull(0) ?: 23,
            parts.getOrNull(1) ?: 59,
            true
        ).show()
    }
}

@Composable
private fun EditChoiceBox(value: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Color.White.copy(alpha = 0.86f), RoundedCornerShape(15.dp))
            .border(1.dp, AppLine, RoundedCornerShape(15.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(value, color = AppText, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun <T> SelectEditBox(value: String, options: List<Pair<String, T>>, onSelected: (T) -> Unit) {
    var showOptions by remember { mutableStateOf(false) }
    EditChoiceBox(value) { showOptions = true }
    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text(LocalAppLanguage.current.text("选择选项", "Choose Option"), color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    options.forEach { (label, item) ->
                        EditOptionRow(
                            label = label,
                            selected = label == value,
                            onClick = {
                                showOptions = false
                                onSelected(item)
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
            shape = RoundedCornerShape(28.dp)
        )
    }
}

@Composable
private fun EditOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White.copy(alpha = 0.86f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.86f),
    focusedBorderColor = AppLine,
    unfocusedBorderColor = AppLine,
    focusedTextColor = AppText,
    unfocusedTextColor = AppText
)

private fun parseEditDeadline(date: String, time: String): Long {
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

private fun detailTitle(type: HomeDetailType, language: AppLanguage): String = when (type) {
    HomeDetailType.DueToday -> language.text("今日截止详情", "Due Today")
    HomeDetailType.Urgent -> language.text("重要紧急详情", "Important & Urgent")
    HomeDetailType.DoneThisWeek -> language.text("本周完成详情", "Done This Week")
}

private fun detailSubtitle(type: HomeDetailType, count: Int, language: AppLanguage): String = when (type) {
    HomeDetailType.DueToday -> language.text("这里展示今天 00:00 到 23:59 仍未完成的 DDL，共 $count 个。", "$count unfinished deadlines due today.")
    HomeDetailType.Urgent -> language.text("这里展示高重要性且 24 小时内截止或已逾期的 DDL，共 $count 个。", "$count high-importance deadlines due within 24 hours or overdue.")
    HomeDetailType.DoneThisWeek -> language.text("这里展示本周已完成的 DDL，共 $count 个。", "$count deadlines completed this week.")
}

private fun importanceLabel(value: ImportanceLevel, language: AppLanguage): String = when (value) {
    ImportanceLevel.HIGH -> language.text("高", "High")
    ImportanceLevel.MEDIUM -> language.text("中", "Medium")
    ImportanceLevel.LOW -> language.text("低", "Low")
}

private fun repeatLabel(value: RepeatRule, language: AppLanguage): String = when (value) {
    RepeatRule.NONE -> language.text("不重复", "None")
    RepeatRule.DAILY -> language.text("每天", "Daily")
    RepeatRule.WEEKLY -> language.text("每周", "Weekly")
    RepeatRule.MONTHLY -> language.text("每月", "Monthly")
    RepeatRule.CUSTOM -> language.text("自定义", "Custom")
}

private fun categoryLabel(value: TaskCategory, language: AppLanguage): String = when (value) {
    TaskCategory.STUDY -> language.text("学习", "Study")
    TaskCategory.HOMEWORK -> language.text("作业", "Homework")
    TaskCategory.COMPETITION -> language.text("比赛", "Competition")
    TaskCategory.MEETING -> language.text("会议", "Meeting")
    TaskCategory.LIFE -> language.text("生活", "Life")
    TaskCategory.OTHER -> language.text("其他", "Other")
}

private fun reminderLabel(value: Int?, language: AppLanguage): String = when (value) {
    1440 -> language.text("提前 1 天", "1 day before")
    180 -> language.text("提前 3 小时", "3 hours before")
    30 -> language.text("提前 30 分钟", "30 minutes before")
    null -> language.text("不提醒", "Off")
    else -> language.text("提前 $value 分钟", "$value minutes before")
}

@Composable
private fun rememberCurrentTime(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    return now
}

@Composable
private fun HeroCard(weekCount: Int, soonCount: Int, language: AppLanguage, themeStyle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(themeHeroGradient(themeStyle)))
            .padding(22.dp)
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .offset(x = 255.dp, y = (-58).dp)
                .background(Color.White.copy(alpha = 0.09f), CircleShape)
        )
        Column {
            Text(language.text("本周剩余任务", "Remaining this week"), color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(weekCount.toString(), color = Color.White, fontSize = 52.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black)
                    Text(language.text("个", ""), color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 3.dp))
                }
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(language.text("$soonCount 个即将截止", "$soonCount due soon"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun FocusCard(task: Task?, now: Long, language: AppLanguage) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24.dp) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            LetterIcon(task?.title ?: language.text("任", "T"), task?.let { taskColor(it) } ?: AppBlue)
            Column {
                if (task == null) {
                    Text(language.text("暂无待办任务", "No pending tasks"), color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text(language.text("点击右上角添加新的 Deadline。", "Tap the plus button to add a new deadline."), color = AppSubtext, fontSize = 12.sp, lineHeight = 17.sp)
                } else {
                    Text(language.text("建议先处理：${task.title}", "Handle first: ${task.title}"), color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text(
                        language.text(
                            "距离截止${remainingText(task.deadlineDateTime, now, language)}，${importanceText(task.importance)}任务。",
                            "Deadline ${remainingText(task.deadlineDateTime, now, language)}, ${importanceText(task.importance)} task."
                        ),
                        color = AppSubtext,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(language: AppLanguage) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(language.text("还没有临近任务", "No upcoming tasks"), color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(language.text("新增 DDL 后，这里会按截止时间自动整理。", "New deadlines will be sorted by due time."), color = AppSubtext, fontSize = 12.sp)
        }
    }
}

private fun priorityScore(task: Task, now: Long): Int {
    val hours = TimeUnit.MILLISECONDS.toHours(task.deadlineDateTime - now)
    val deadlineScore = when {
        task.deadlineDateTime < now -> 100
        hours <= 24 -> 80
        hours <= 72 -> 50
        hours <= 168 -> 30
        else -> 0
    }
    val importanceScore = when (task.importance) {
        ImportanceLevel.HIGH -> 40
        ImportanceLevel.MEDIUM -> 20
        ImportanceLevel.LOW -> 5
    }
    val repeatScore = if (task.repeatRule != RepeatRule.NONE) 10 else 0
    return deadlineScore + importanceScore + repeatScore
}

private fun weekStart(now: Long): Long {
    val cal = DateTimeUtils.calendar(now)
    val offset = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
    return DateTimeUtils.todayStart(now) - offset * TimeUnit.DAYS.toMillis(1)
}

private fun remainingText(deadline: Long, now: Long, language: AppLanguage): String {
    val diff = deadline - now
    if (diff < 0) return language.text("已逾期", "overdue")
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff).coerceAtLeast(1)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        days >= 1 -> language.text("还有 $days 天", "in $days days")
        hours >= 1 -> language.text("还有 $hours 小时", "in $hours hours")
        else -> language.text("还有 $minutes 分钟", "in $minutes minutes")
    }
}
