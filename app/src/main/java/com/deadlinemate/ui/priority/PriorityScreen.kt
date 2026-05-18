package com.deadlinemate.ui.priority

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadlinemate.domain.PriorityCalculator
import com.deadlinemate.domain.model.ImportanceLevel
import com.deadlinemate.domain.model.Task
import com.deadlinemate.domain.model.TaskStatus
import com.deadlinemate.ui.components.GlassPanel
import com.deadlinemate.ui.components.PageTopBar
import com.deadlinemate.ui.components.TagPill
import com.deadlinemate.ui.components.TaskCheckBox
import com.deadlinemate.ui.components.compactTime
import com.deadlinemate.ui.components.tagColor
import com.deadlinemate.ui.i18n.AppLanguage
import com.deadlinemate.ui.i18n.LocalAppLanguage
import com.deadlinemate.ui.i18n.text
import com.deadlinemate.ui.profile.themeHeroGradient
import com.deadlinemate.ui.theme.AppBg
import com.deadlinemate.ui.theme.AppBlue
import com.deadlinemate.ui.theme.AppRed
import com.deadlinemate.ui.theme.AppSubtext
import com.deadlinemate.ui.theme.AppText
import com.deadlinemate.util.DateTimeUtils
import java.util.concurrent.TimeUnit

@Composable
fun PriorityScreen(
    tasks: List<Task>,
    onToggleDone: (Task) -> Unit,
    pageBg: Color = AppBg,
    themeStyle: String = "亮色"
) {
    val active = tasks.filter { it.status != TaskStatus.DONE }.distinctBy { it.title }
    val now = System.currentTimeMillis()
    val calculator = PriorityCalculator()
    val urgent = active
        .filter { it.deadlineDateTime <= now + TimeUnit.DAYS.toMillis(1) || it.importance == ImportanceLevel.HIGH }
        .sortedByDescending { calculator.calculate(it) }
    val planned = active
        .filter { it !in urgent && it.deadlineDateTime <= now + TimeUnit.DAYS.toMillis(14) }
        .sortedByDescending { calculator.calculate(it) }
    val later = active.filter { it !in urgent && it !in planned }.sortedByDescending { calculator.calculate(it) }
    val language = LocalAppLanguage.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(pageBg).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageTopBar("Priority", language.text("优先级", "Priority"), language.text("序", "#"))
            SummaryCard(language, themeStyle)
        }
        item {
            PriorityGroup(language.text("马上处理", "Do Now"), language.text("高优先级", "High"), AppRed, urgent, onToggleDone)
        }
        item {
            PriorityGroup(language.text("计划推进", "Planned"), language.text("保持进度", "Keep Moving"), AppBlue, planned, onToggleDone)
        }
        item {
            PriorityGroup(language.text("可延后", "Later"), language.text("低压力", "Low"), Color(0xFF6E6E73), later, onToggleDone)
        }
    }
}

@Composable
private fun SummaryCard(language: AppLanguage, themeStyle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(themeHeroGradient(themeStyle)))
            .padding(20.dp)
    ) {
        Column {
            Text(language.text("根据截止时间、重要性和重复频率排序", "Sorted by deadline, importance, and repeat frequency"), color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
            Text(language.text("先完成最容易拖慢节奏的任务", "Finish the tasks that block your pace first"), color = Color.White, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun PriorityGroup(
    title: String,
    badge: String,
    badgeColor: Color,
    tasks: List<Task>,
    onToggleDone: (Task) -> Unit
) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 26.dp) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Black)
                TagPill(badge, badgeColor)
            }
            if (tasks.isNotEmpty()) {
                tasks.forEachIndexed { index, task ->
                    PriorityTaskRow(
                        number = index + 1,
                        title = task.title,
                        desc = task.description ?: priorityDesc(task),
                        time = compactTime(task.deadlineDateTime),
                        color = tagColor(task),
                        done = task.status == TaskStatus.DONE,
                        onToggleDone = { onToggleDone(task) }
                    )
                }
            } else {
                EmptyPriorityGroup()
            }
        }
    }
}

@Composable
private fun PriorityTaskRow(number: Int, title: String, desc: String, time: String, color: Color, done: Boolean, onToggleDone: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(AppBlue.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number.toString(), color = AppBlue, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(desc, color = AppSubtext, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(time, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black)
        TaskCheckBox(checked = done, onClick = onToggleDone)
    }
}

@Composable
private fun EmptyPriorityGroup() {
    val language = LocalAppLanguage.current
    Text(
        language.text("暂无真实 Deadline", "No real deadlines here"),
        color = AppSubtext,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun priorityDesc(task: Task): String {
    val language = LocalAppLanguage.current
    val now = System.currentTimeMillis()
    val diff = task.deadlineDateTime - now
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    return when {
        diff < 0 -> language.text("已逾期，请尽快处理", "Overdue. Handle as soon as possible.")
        hours <= 24 -> language.text("24 小时内截止", "Due within 24 hours")
        else -> language.text("截止时间：${DateTimeUtils.formatDateTime(task.deadlineDateTime)}", "Deadline: ${DateTimeUtils.formatDateTime(task.deadlineDateTime, "English")}")
    }
}
