package com.deadlinemate.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.deadlinemate.domain.model.Task

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(task: Task) {
        val minutes = task.reminderMinutesBefore ?: return
        if (!task.reminderEnabled) return
        val triggerAt = task.deadlineDateTime - minutes * 60_000L
        if (triggerAt <= System.currentTimeMillis()) return
        scheduleAlarm(task.id, task.title, triggerAt)
    }

    fun scheduleTestReminder(title: String, delayMillis: Long = 10_000L): Long {
        val taskId = -(System.currentTimeMillis() % Int.MAX_VALUE)
        scheduleAlarm(taskId, title, System.currentTimeMillis() + delayMillis)
        return taskId
    }

    private fun scheduleAlarm(taskId: Long, title: String, triggerAt: Long) {
        val intent = pendingIntent(taskId, title)
        runCatching {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, openAppPendingIntent(taskId)),
                intent
            )
        }.getOrElse {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            }
        }
    }

    fun cancel(taskId: Long) {
        alarmManager.cancel(pendingIntent(taskId, ""))
    }

    private fun pendingIntent(taskId: Long, title: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction("${context.packageName}.DEADLINE_REMINDER.$taskId")
            .putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
            .putExtra(AlarmReceiver.EXTRA_TITLE, title)
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppPendingIntent(taskId: Long): PendingIntent {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            context,
            (taskId + 100_000L).toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
