package com.deadlinemate.ui.calendar

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadlinemate.domain.model.Task
import com.deadlinemate.domain.model.TaskStatus
import com.deadlinemate.ui.components.GlassPanel
import com.deadlinemate.ui.components.LetterIcon
import com.deadlinemate.ui.components.SectionTitle
import com.deadlinemate.ui.components.TagPill
import com.deadlinemate.ui.components.TaskCheckBox
import com.deadlinemate.ui.components.tagColor
import com.deadlinemate.ui.components.taskColor
import com.deadlinemate.ui.components.taskTag
import com.deadlinemate.ui.components.weekdayLabel
import com.deadlinemate.ui.i18n.AppLanguage
import com.deadlinemate.ui.i18n.LocalAppLanguage
import com.deadlinemate.ui.i18n.text
import com.deadlinemate.ui.profile.themeAccent
import com.deadlinemate.ui.theme.AppBg
import com.deadlinemate.ui.theme.AppBlue
import com.deadlinemate.ui.theme.AppGreen
import com.deadlinemate.ui.theme.AppLine
import com.deadlinemate.ui.theme.AppRed
import com.deadlinemate.ui.theme.AppSubtext
import com.deadlinemate.ui.theme.AppText
import com.deadlinemate.util.DateTimeUtils
import java.util.Calendar
import java.util.concurrent.TimeUnit

@Composable
fun CalendarScreen(
    tasks: List<Task>,
    onAdd: () -> Unit,
    onToggleDone: (Task) -> Unit,
    pageBg: Color = AppBg,
    themeStyle: String = "亮色"
) {
    var monthMode by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf(DateTimeUtils.todayStart()) }
    var visibleMonth by remember { mutableStateOf(DateTimeUtils.todayStart()) }
    val selectedStart = DateTimeUtils.todayStart(selectedDay)
    val selectedEnd = selectedStart + TimeUnit.DAYS.toMillis(1)
    val selectedTasks = tasks
        .filter { it.deadlineDateTime in selectedStart until selectedEnd }
        .sortedBy { it.deadlineDateTime }
    val language = LocalAppLanguage.current
    val accent = themeAccent(themeStyle)
    val selectedTitle = if (selectedStart == DateTimeUtils.todayStart()) {
        language.text("今天安排", "Today")
    } else {
        language.text("${DateTimeUtils.formatDay(selectedStart)}安排", "${DateTimeUtils.formatDay(selectedStart, "English")}")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(pageBg).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Calendar", color = AppSubtext, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (monthMode) language.text("月历", "Month") else language.text("本周日历", "Week"), color = AppText, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black)
                }
                ModeButton(if (monthMode) language.text("周历", "Week") else language.text("月历", "Month"), accent) {
                    monthMode = !monthMode
                    visibleMonth = selectedDay
                }
            }
            if (monthMode) {
                MonthGrid(
                    tasks = tasks,
                    selected = selectedDay,
                    visibleMonth = visibleMonth,
                    accent = accent,
                    onMonthChange = { visibleMonth = it },
                    onSelect = { selectedDay = it }
                )
            } else {
                WeekStrip(tasks, selectedDay, accent) { selectedDay = it }
            }
            SectionTitle(selectedTitle, language.text("添加", "Add"), onAdd, accent = accent)
        }

        if (selectedTasks.isEmpty()) {
            item { EmptyDayCard(selectedStart, language) }
        } else {
            items(selectedTasks, key = { it.id }) { task ->
                CalendarTaskCard(task = task, accent = accent, onToggleDone = { onToggleDone(task) })
            }
        }
    }
}

@Composable
private fun ModeButton(text: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(text, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CalendarTaskCard(task: Task, accent: Color, onToggleDone: () -> Unit) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        radius = 24.dp
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                DateTimeUtils.formatTime(task.deadlineDateTime),
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 2.dp)
            )
            LetterIcon(task.title, taskColor(task))
            Column(Modifier.weight(1f)) {
                Text(task.title, color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
                val language = LocalAppLanguage.current
                Text(task.description ?: language.text("无备注", "No notes"), color = AppSubtext, fontSize = 12.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (task.status == TaskStatus.DONE) {
                    val language = LocalAppLanguage.current
                    TagPill(language.text("已完成", "Done"), AppGreen)
                } else {
                    TagPill(taskTag(task), tagColor(task))
                }
            }
            TaskCheckBox(
                checked = task.status == TaskStatus.DONE,
                onClick = onToggleDone
            )
        }
    }
}

@Composable
private fun EmptyDayCard(day: Long, language: AppLanguage) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(language.text("${DateTimeUtils.formatDay(day)}暂无任务", "No tasks on ${DateTimeUtils.formatDay(day, "English")}"), color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(2.dp))
            Text(language.text("点击右上角添加任务后，会按截止日期显示在这里。", "Tasks you add will appear here by deadline date."), color = AppSubtext, fontSize = 12.sp)
        }
    }
}

private enum class DayTaskIndicator { None, Pending, Done }

@Composable
private fun WeekStrip(tasks: List<Task>, selected: Long, accent: Color, onSelect: (Long) -> Unit) {
    val cal = DateTimeUtils.calendar()
    val offset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val start = DateTimeUtils.todayStart() - offset * TimeUnit.DAYS.toMillis(1)
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) { weekIndex ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(7) { dayIndex ->
                    val day = start + (weekIndex * 7 + dayIndex) * TimeUnit.DAYS.toMillis(1)
                    DayCard(
                        day = day,
                        active = DateTimeUtils.todayStart(selected) == day,
                        indicator = dayTaskIndicator(tasks, day),
                        accent = accent,
                        modifier = Modifier.weight(1f)
                    ) { onSelect(day) }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    tasks: List<Task>,
    selected: Long,
    visibleMonth: Long,
    accent: Color,
    onMonthChange: (Long) -> Unit,
    onSelect: (Long) -> Unit
) {
    val monthStart = DateTimeUtils.calendar(visibleMonth).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val year = monthStart.get(Calendar.YEAR)
    val month = monthStart.get(Calendar.MONTH)
    val firstOffset = (monthStart.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val gridStart = monthStart.timeInMillis - firstOffset * TimeUnit.DAYS.toMillis(1)
    val previousMonth = DateTimeUtils.calendar(monthStart.timeInMillis).apply { add(Calendar.MONTH, -1) }.timeInMillis
    val nextMonth = DateTimeUtils.calendar(monthStart.timeInMillis).apply { add(Calendar.MONTH, 1) }.timeInMillis
    val language = LocalAppLanguage.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonthArrow("‹") { onMonthChange(previousMonth) }
        Text(language.text("${year}年${month + 1}月", "${DateTimeUtils.formatDay(monthStart.timeInMillis, "English").substringBefore(" ")} $year"), color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Black)
        MonthArrow("›") { onMonthChange(nextMonth) }
    }
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 28.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val weekdays = if (language == AppLanguage.English) listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") else listOf("一", "二", "三", "四", "五", "六", "日")
                weekdays.forEach {
                    Text(it, color = AppSubtext, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
            repeat(6) { weekIndex ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(7) { dayIndex ->
                        val day = gridStart + (weekIndex * 7 + dayIndex) * TimeUnit.DAYS.toMillis(1)
                        val dayCal = DateTimeUtils.calendar(day)
                        val muted = dayCal.get(Calendar.MONTH) != month
                        val dayStart = DateTimeUtils.todayStart(day)
                        MonthDay(
                            dayNum = dayCal.get(Calendar.DAY_OF_MONTH),
                            muted = muted,
                            active = DateTimeUtils.todayStart(selected) == dayStart,
                            indicator = dayTaskIndicator(tasks, dayStart),
                            accent = accent,
                            modifier = Modifier.weight(1f)
                        ) {
                            onSelect(dayStart)
                            if (muted) onMonthChange(dayStart)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x0D000000), spotColor = Color(0x0D000000))
            .background(Color.White.copy(alpha = 0.86f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DayCard(day: Long, active: Boolean, indicator: DayTaskIndicator, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (active) accent else Color.White.copy(alpha = 0.86f)
    Column(
        modifier = modifier
            .background(bg, RoundedCornerShape(18.dp))
            .border(if (active) 0.dp else 1.dp, if (active) Color.Transparent else AppLine, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(weekdayLabel(day), color = if (active) Color.White.copy(alpha = 0.72f) else AppSubtext, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Text(DateTimeUtils.calendar(day).get(Calendar.DAY_OF_MONTH).toString(), color = if (active) Color.White else AppText, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .background(indicatorColor(indicator), CircleShape)
                .padding(3.dp)
        )
    }
}

@Composable
private fun MonthDay(dayNum: Int, muted: Boolean, active: Boolean, indicator: DayTaskIndicator, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(if (active) accent else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            dayNum.toString(),
            color = when {
                active -> Color.White
                muted -> Color(0x473C3C43)
                else -> AppText
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold
        )
        if (indicator != DayTaskIndicator.None) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(indicatorColor(indicator), CircleShape)
                    .padding(2.5.dp)
            )
        }
    }
}

private fun dayTaskIndicator(tasks: List<Task>, day: Long): DayTaskIndicator {
    val start = DateTimeUtils.todayStart(day)
    val dayTasks = tasks.filter { it.deadlineDateTime in start until start + TimeUnit.DAYS.toMillis(1) }
    return when {
        dayTasks.isEmpty() -> DayTaskIndicator.None
        dayTasks.all { it.status == TaskStatus.DONE } -> DayTaskIndicator.Done
        else -> DayTaskIndicator.Pending
    }
}

private fun indicatorColor(indicator: DayTaskIndicator): Color = when (indicator) {
    DayTaskIndicator.Done -> AppGreen
    DayTaskIndicator.Pending -> AppRed
    DayTaskIndicator.None -> Color.Transparent
}
