package com.deadlinemate.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "测试提醒：Deadline 即将截止"
        NotificationHelper(context).showDeadlineReminder(title)
    }

    companion object {
        const val ACTION_TEST_REMINDER = "com.deadlinemate.DEBUG_REMINDER"
        const val EXTRA_TITLE = "title"
    }
}
