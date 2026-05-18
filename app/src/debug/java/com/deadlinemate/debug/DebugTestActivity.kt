package com.deadlinemate.debug

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadlinemate.reminder.AlarmReceiver
import com.deadlinemate.reminder.NotificationHelper

class DebugTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DebugTestScreen() }
    }
}

@Composable
private fun DebugTestScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var title by remember { mutableStateOf("Deadline Mate 测试提醒") }
    var status by remember { mutableStateOf("待操作") }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = if (granted) "通知权限已允许" else "通知权限被拒绝"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEAF4FF))
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("测试中心", color = Color(0xFF1D1D1F), fontSize = 32.sp, fontWeight = FontWeight.Black)
        Text("用于检查通知、闹钟和权限链路的调试页面。", color = Color(0xFF7A7D85), fontSize = 13.sp)

        Panel {
            Text("提醒测试", color = Color(0xFF1D1D1F), fontSize = 18.sp, fontWeight = FontWeight.Black)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("通知内容") }
            )
            TestButton("直接发送通知") {
                NotificationHelper(context).showDeadlineReminder(title)
                status = "已调用 NotificationHelper"
            }
            TestButton("通过 AlarmReceiver 广播发送") {
                context.sendBroadcast(
                    Intent(context, AlarmReceiver::class.java)
                        .putExtra(AlarmReceiver.EXTRA_TASK_ID, 9001L)
                        .putExtra(AlarmReceiver.EXTRA_TITLE, title)
                )
                status = "已发送 AlarmReceiver 广播"
            }
            TestButton("10 秒后触发提醒") {
                runCatching { scheduleDebugReminder(context, title, 10_000L) }
                    .onSuccess { status = "已安排 10 秒后的提醒" }
                    .onFailure { status = "安排提醒失败：${it.javaClass.simpleName}" }
            }
        }

        Panel {
            Text("权限与系统", color = Color(0xFF1D1D1F), fontSize = 18.sp, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TestButton("申请权限", Modifier.weight(1f)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        status = "当前系统版本不需要运行时通知权限"
                    }
                }
                TestButton("通知设置", Modifier.weight(1f)) {
                    context.startActivity(notificationSettingsIntent(context))
                    status = "已打开系统通知设置"
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TestButton("清除通知", Modifier.weight(1f)) {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.cancelAll()
                    status = "已清除本 App 通知"
                }
                TestButton("复制 ADB 命令", Modifier.weight(1f)) {
                    clipboard.setText(AnnotatedString(ADB_REMINDER_COMMAND))
                    status = "ADB 命令已复制"
                }
            }
        }

        Panel {
            Text("状态", color = Color(0xFF1D1D1F), fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(status, color = Color(0xFF007AFF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(ADB_REMINDER_COMMAND, color = Color(0xFF7A7D85), fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.88f), RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFFE1E1E6), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun TestButton(text: String, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .background(Color(0xFF007AFF), RoundedCornerShape(16.dp)),
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

private fun scheduleDebugReminder(context: Context, title: String, delayMillis: Long) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java)
        .putExtra(AlarmReceiver.EXTRA_TASK_ID, 9002L)
        .putExtra(AlarmReceiver.EXTRA_TITLE, title)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        9002,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val triggerAt = System.currentTimeMillis() + delayMillis
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }.getOrElse {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }
}

private fun notificationSettingsIntent(context: Context): Intent {
    return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

private const val ADB_REMINDER_COMMAND =
    "adb shell am broadcast -a com.deadlinemate.DEBUG_REMINDER -n com.deadlinemate/.reminder.ReminderDebugReceiver --es title TestReminderFromDeadlineMate"
