package com.deadlinemate.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadlinemate.domain.model.ImportanceLevel
import com.deadlinemate.domain.model.Task
import com.deadlinemate.domain.model.TaskStatus
import com.deadlinemate.ui.theme.AppBlue
import com.deadlinemate.ui.theme.AppCard
import com.deadlinemate.ui.theme.AppGreen
import com.deadlinemate.ui.theme.AppLine
import com.deadlinemate.ui.theme.AppOrange
import com.deadlinemate.ui.theme.AppRed
import com.deadlinemate.ui.theme.AppSubtext
import com.deadlinemate.ui.theme.AppText
import com.deadlinemate.ui.theme.AppYellow
import com.deadlinemate.ui.i18n.AppLanguage
import com.deadlinemate.ui.i18n.LocalAppLanguage
import com.deadlinemate.ui.i18n.text
import com.deadlinemate.util.DateTimeUtils
import java.util.Calendar
import java.util.concurrent.TimeUnit

@Composable
fun PageTopBar(
    eyebrow: String,
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(eyebrow, color = AppSubtext, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(title, color = AppText, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black)
        }
        if (action != null) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(10.dp, CircleShape, ambientColor = Color(0x12000000), spotColor = Color(0x12000000))
                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
                    .clickable(enabled = onAction != null) { onAction?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Text(action, color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    SectionTitle(title = title, action = action, onAction = onAction, accent = AppBlue)
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null, accent: Color = AppBlue) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp, start = 2.dp, end = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Black)
        if (action != null && onAction != null) {
            Text(action, color = accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable { onAction() })
        }
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(radius), ambientColor = Color(0x08000000), spotColor = Color(0x08000000))
            .background(AppCard, RoundedCornerShape(radius))
            .border(1.dp, AppLine, RoundedCornerShape(radius))
    ) {
        content()
    }
}

@Composable
fun LetterIcon(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    radius: Dp = 16.dp,
    fontSize: Int = 19
) {
    val light = lighten(color)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(listOf(light, color))),
        contentAlignment = Alignment.Center
    ) {
        Text(iconText(text), color = Color.White, fontSize = fontSize.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun StatCard(icon: String, label: String, value: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    GlassPanel(modifier = modifier, radius = 22.dp) {
        Column(
            Modifier
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            LetterIcon(icon, color, size = 32.dp, radius = 11.dp, fontSize = 14)
            Spacer(Modifier.height(12.dp))
            Text(value, color = AppText, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(label, color = AppSubtext, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TagPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskPreviewCard(
    task: Task,
    modifier: Modifier = Modifier,
    onCheckedChange: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        radius = 24.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .combinedClickable(
                    enabled = onLongClick != null,
                    onClick = {},
                    onLongClick = { onLongClick?.invoke() }
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LetterIcon(task.title, taskColor(task))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    color = AppText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    task.description ?: categoryText(task.category.name),
                    color = AppSubtext,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                TagPill(taskTag(task), tagColor(task))
                Spacer(Modifier.height(6.dp))
                Text(compactTime(task.deadlineDateTime), color = AppSubtext, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            TaskCheckBox(
                checked = task.status == TaskStatus.DONE,
                onClick = onCheckedChange
            )
        }
    }
}

@Composable
fun TaskCheckBox(
    checked: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    val shape = RoundedCornerShape(999.dp)
    val bg = if (checked) AppGreen else Color.White.copy(alpha = 0.88f)
    val border = if (checked) AppGreen.copy(alpha = 0.78f) else AppLine.copy(alpha = 0.62f)
    val textColor = if (checked) Color.White else AppSubtext.copy(alpha = 0.86f)
    Row(
        modifier = modifier
            .width(58.dp)
            .height(34.dp)
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, border, shape)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(15.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                .border(1.2.dp, if (checked) Color.White else AppLine.copy(alpha = 0.70f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text("✓", color = Color.White, fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(4.dp))
        if (checked) {
            Text(language.text("已完", "Done"), color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Black)
        } else {
            Text(language.text("完成", "Done"), color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun TimeCard(time: String, title: String, desc: String) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 24.dp) {
        Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(time, color = AppBlue, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.size(width = 58.dp, height = 20.dp))
            Column {
                Text(title, color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text(desc, color = AppSubtext, fontSize = 12.sp)
            }
        }
    }
}

fun taskColor(task: Task): Color {
    if (task.status == TaskStatus.DONE) return AppGreen
    val hours = TimeUnit.MILLISECONDS.toHours(task.deadlineDateTime - System.currentTimeMillis())
    return when {
        task.deadlineDateTime < System.currentTimeMillis() || hours <= 24 -> AppRed
        hours <= 72 -> AppOrange
        hours <= 168 -> AppYellow
        else -> AppBlue
    }
}

@Composable
fun importanceText(importance: ImportanceLevel): String {
    val language = LocalAppLanguage.current
    return when (importance) {
        ImportanceLevel.HIGH -> language.text("重要", "important")
        ImportanceLevel.MEDIUM -> language.text("普通", "normal")
        ImportanceLevel.LOW -> language.text("低压力", "low pressure")
    }
}

@Composable
fun taskTag(task: Task): String {
    val language = LocalAppLanguage.current
    val hours = TimeUnit.MILLISECONDS.toHours(task.deadlineDateTime - System.currentTimeMillis())
    return when {
        task.importance == ImportanceLevel.HIGH && hours <= 24 -> language.text("重要紧急", "Important")
        task.importance == ImportanceLevel.HIGH -> language.text("重要", "Important")
        task.importance == ImportanceLevel.MEDIUM -> language.text("普通", "Normal")
        else -> language.text("低压力", "Low")
    }
}

fun tagColor(task: Task): Color {
    val hours = TimeUnit.MILLISECONDS.toHours(task.deadlineDateTime - System.currentTimeMillis())
    return when {
        task.importance == ImportanceLevel.HIGH && hours <= 24 -> AppRed
        task.importance == ImportanceLevel.HIGH -> AppOrange
        task.importance == ImportanceLevel.MEDIUM -> Color(0xFF6E6E73)
        else -> AppBlue
    }
}

@Composable
fun compactTime(time: Long): String {
    val language = LocalAppLanguage.current
    val start = DateTimeUtils.todayStart()
    val tomorrow = start + TimeUnit.DAYS.toMillis(1)
    val clock = DateTimeUtils.formatTime(time)
    val period = dayPeriodText(time, language)
    return when (time) {
        in start until tomorrow -> language.text("$period $clock", "Today $period $clock")
        in tomorrow until tomorrow + TimeUnit.DAYS.toMillis(1) -> language.text("明天 $period $clock", "Tomorrow $period $clock")
        else -> DateTimeUtils.formatDay(time, if (language == AppLanguage.English) "English" else "中文")
    }
}

private fun dayPeriodText(time: Long, language: AppLanguage): String {
    val hour = DateTimeUtils.calendar(time).get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 6 -> language.text("凌晨", "early")
        hour < 12 -> language.text("早上", "morning")
        hour < 18 -> language.text("下午", "afternoon")
        else -> language.text("晚上", "evening")
    }
}

@Composable
fun weekdayLabel(day: Long): String {
    val language = LocalAppLanguage.current
    val cal = DateTimeUtils.calendar(day)
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> language.text("周一", "Mon")
        Calendar.TUESDAY -> language.text("周二", "Tue")
        Calendar.WEDNESDAY -> language.text("周三", "Wed")
        Calendar.THURSDAY -> language.text("周四", "Thu")
        Calendar.FRIDAY -> language.text("周五", "Fri")
        Calendar.SATURDAY -> language.text("周六", "Sat")
        else -> language.text("周日", "Sun")
    }
}

private fun iconText(text: String): String {
    val first = text.firstOrNull()?.toString() ?: "任"
    return when {
        text.startsWith("数据库") -> "数"
        text.startsWith("英语") -> "英"
        text.startsWith("算法") -> "算"
        else -> first
    }
}

private fun lighten(color: Color): Color {
    return Color(
        red = color.red + (1f - color.red) * 0.28f,
        green = color.green + (1f - color.green) * 0.28f,
        blue = color.blue + (1f - color.blue) * 0.28f,
        alpha = color.alpha
    )
}

private fun categoryText(raw: String) = raw.lowercase().replaceFirstChar { it.uppercase() }
