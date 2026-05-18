package com.deadlinemate.push

import android.content.Context
import com.deadlinemate.reminder.NotificationHelper
import com.xiaomi.mipush.sdk.ErrorCode
import com.xiaomi.mipush.sdk.MiPushClient
import com.xiaomi.mipush.sdk.MiPushCommandMessage
import com.xiaomi.mipush.sdk.MiPushMessage
import com.xiaomi.mipush.sdk.PushMessageReceiver

class DeadlineMiPushReceiver : PushMessageReceiver() {
    override fun onReceivePassThroughMessage(context: Context, message: MiPushMessage) {
        val text = message.title.takeIf { it.isNotBlank() }
            ?: message.description.takeIf { it.isNotBlank() }
            ?: message.content.takeIf { it.isNotBlank() }
            ?: "Deadline 提醒"
        NotificationHelper(context.applicationContext).showDeadlineReminder(text)
    }

    override fun onNotificationMessageArrived(context: Context, message: MiPushMessage) = Unit

    override fun onNotificationMessageClicked(context: Context, message: MiPushMessage) = Unit

    override fun onReceiveRegisterResult(context: Context, message: MiPushCommandMessage) {
        handleCommand(context, message)
    }

    override fun onCommandResult(context: Context, message: MiPushCommandMessage) {
        handleCommand(context, message)
    }

    private fun handleCommand(context: Context, message: MiPushCommandMessage) {
        if (message.command != MiPushClient.COMMAND_REGISTER) return
        val regId = message.commandArguments?.firstOrNull().orEmpty()
        MiPushInitializer.saveRegistered(
            context.applicationContext,
            registered = message.resultCode == ErrorCode.SUCCESS.toLong() && regId.isNotBlank(),
            regId = regId
        )
    }
}
