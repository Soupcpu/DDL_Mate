package com.deadlinemate.push

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import com.deadlinemate.BuildConfig
import com.xiaomi.channel.commonutils.logger.LoggerInterface
import com.xiaomi.mipush.sdk.Logger
import com.xiaomi.mipush.sdk.MiPushClient

object MiPushInitializer {
    private const val PREFS = "deadline_mate_mipush"
    private const val KEY_REGISTERED = "registered"
    private const val KEY_REG_ID = "reg_id"

    fun init(context: Context) {
        if (!context.isMainProcess()) return
        val appId = BuildConfig.MIPUSH_APP_ID.trim()
        val appKey = BuildConfig.MIPUSH_APP_KEY.trim()
        if (appId.isBlank() || appKey.isBlank()) {
            saveRegistered(context, false, "")
            return
        }
        MiPushClient.registerPush(context.applicationContext, appId, appKey)
        Logger.setLogger(context.applicationContext, object : LoggerInterface {
            override fun setTag(tag: String?) = Unit
            override fun log(content: String?) = Unit
            override fun log(content: String?, t: Throwable?) = Unit
        })
    }

    fun saveRegistered(context: Context, registered: Boolean, regId: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REGISTERED, registered)
            .putString(KEY_REG_ID, regId.orEmpty())
            .apply()
    }

    private fun Context.isMainProcess(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pid = Process.myPid()
        val packageName = packageName
        return manager.runningAppProcesses?.any { it.pid == pid && it.processName == packageName } == true
    }
}
