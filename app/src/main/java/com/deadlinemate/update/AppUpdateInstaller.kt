package com.deadlinemate.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class UpdateDownloadState(
    val running: Boolean = false,
    val progress: Float? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val apkName: String? = null,
    val message: String? = null
)

class AppUpdateInstaller(
    private val context: Context,
    private val httpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val _downloadState = MutableStateFlow(UpdateDownloadState())
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    fun downloadAndInstall(apkUrl: String, apkName: String?): Result<Unit> {
        if (_downloadState.value.running) {
            return Result.failure(IllegalStateException("更新包正在下载中"))
        }
        if (apkUrl.isBlank()) {
            return Result.failure(IllegalArgumentException("更新包地址为空"))
        }
        createNotificationChannel()
        val initialState = UpdateDownloadState(
            running = true,
            apkName = apkName,
            message = "正在准备下载更新包"
        )
        _downloadState.value = initialState
        showProgressNotification(initialState)
        scope.launch {
            runCatching {
                val target = downloadApk(apkUrl, apkName)
                val done = _downloadState.value.copy(
                    running = false,
                    progress = 1f,
                    message = "下载完成，正在打开安装页面"
                )
                _downloadState.value = done
                showCompleteNotification(target)
                withContext(Dispatchers.Main) { startInstall(target) }
            }.onFailure {
                val failed = UpdateDownloadState(
                    running = false,
                    apkName = apkName,
                    message = it.message ?: "更新下载失败"
                )
                _downloadState.value = failed
                showFailedNotification(failed.message ?: "更新下载失败")
            }
        }
        return Result.success(Unit)
    }

    private fun downloadApk(apkUrl: String, apkName: String?): File {
        val safeName = apkName
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: "ddl-manger-update.apk"
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, safeName)
        val request = Request.Builder()
            .url(apkUrl)
            .header("User-Agent", "DDL-Manger-Android")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("更新包下载失败")
            val body = response.body ?: throw IllegalStateException("更新包内容为空")
            val total = body.contentLength().takeIf { it > 0 }
            var downloaded = 0L
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            target.outputStream().use { output ->
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val state = UpdateDownloadState(
                            running = true,
                            progress = total?.let { downloaded.toFloat() / it.toFloat() },
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            apkName = safeName,
                            message = "正在下载更新包"
                        )
                        _downloadState.value = state
                        if (downloaded == read.toLong() || downloaded % NOTIFICATION_STEP_BYTES < read) {
                            showProgressNotification(state)
                        }
                    }
                }
            }
        }
        return target
    }

    private fun startInstall(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            throw IllegalStateException("请先允许本应用安装未知来源应用，然后再次点击安装。")
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "应用更新", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "显示更新包下载进度"
                }
            )
        }
    }

    private fun showProgressNotification(state: UpdateDownloadState) {
        val percent = state.progress?.let { (it * 100).toInt().coerceIn(0, 100) }
        val builder = baseNotification()
            .setContentTitle("正在下载更新")
            .setContentText(percent?.let { "$it% - ${state.apkName.orEmpty()}" } ?: state.apkName.orEmpty())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (percent == null) {
            builder.setProgress(100, 0, true)
        } else {
            builder.setProgress(100, percent, false)
        }
        runCatching { notificationManager.notify(NOTIFICATION_ID, builder.build()) }
    }

    private fun showCompleteNotification(apkFile: File) {
        runCatching {
            notificationManager.notify(
                NOTIFICATION_ID,
                baseNotification()
                    .setContentTitle("更新包下载完成")
                    .setContentText("请在系统安装页面确认安装")
                    .setOngoing(false)
                    .setProgress(0, 0, false)
                    .setContentIntent(installPendingIntent(apkFile))
                    .build()
            )
        }
    }

    private fun showFailedNotification(message: String) {
        runCatching {
            notificationManager.notify(
                NOTIFICATION_ID,
                baseNotification()
                    .setContentTitle("更新下载失败")
                    .setContentText(message)
                    .setOngoing(false)
                    .setProgress(0, 0, false)
                    .build()
            )
        }
    }

    private fun baseNotification(): NotificationCompat.Builder {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    private fun installPendingIntent(apkFile: File): PendingIntent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 3003
        const val NOTIFICATION_STEP_BYTES = 512 * 1024L
    }
}
