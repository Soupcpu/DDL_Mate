package com.deadlinemate.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class AppUpdateInstaller(
    private val context: Context,
    private val httpClient: OkHttpClient
) {
    suspend fun downloadAndInstall(
        apkUrl: String,
        apkName: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (apkUrl.isBlank()) throw IllegalArgumentException("更新包地址为空")
            val target = downloadApk(apkUrl, apkName)
            withContext(Dispatchers.Main) {
                startInstall(target)
            }
        }
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
            target.outputStream().use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
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
            throw IllegalStateException("请先允许本应用安装未知来源应用，然后再次点击下载更新。")
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

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
