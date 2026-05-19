package com.deadlinemate.update

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val tagName: String,
    val releaseUrl: String,
    val releaseNotes: String,
    val apkName: String?,
    val apkDownloadUrl: String?,
    val hasUpdate: Boolean
)

class GithubUpdateChecker(
    private val httpClient: OkHttpClient
) {
    suspend fun checkLatest(currentVersion: String): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DDL-Manger-Android")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("GitHub release check failed")
                }
                val root = JSONObject(body)
                val tagName = root.optString("tag_name")
                val latestVersion = normalizeVersion(tagName)
                val asset = selectBestApkAsset(root)
                AppUpdateInfo(
                    currentVersion = currentVersion,
                    latestVersion = latestVersion,
                    tagName = tagName,
                    releaseUrl = root.optString("html_url"),
                    releaseNotes = root.optString("body"),
                    apkName = asset?.first,
                    apkDownloadUrl = asset?.second,
                    hasUpdate = compareVersions(latestVersion, currentVersion) > 0
                )
            }
        }
    }

    private fun selectBestApkAsset(root: JSONObject): Pair<String, String>? {
        val assets = root.optJSONArray("assets") ?: return null
        val apks = buildList {
            for (index in 0 until assets.length()) {
                val item = assets.optJSONObject(index) ?: continue
                val name = item.optString("name")
                val url = item.optString("browser_download_url")
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    add(name to url)
                }
            }
        }
        if (apks.isEmpty()) return null
        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return apks.firstOrNull { it.first.contains(preferredAbi, ignoreCase = true) }
            ?: apks.firstOrNull { preferredAbi.contains("arm64") && it.first.contains("arm64", ignoreCase = true) }
            ?: apks.firstOrNull { preferredAbi.contains("x86_64") && it.first.contains("x86_64", ignoreCase = true) }
            ?: apks.first()
    }

    private fun normalizeVersion(value: String): String {
        return value.trim().removePrefix("v").removePrefix("V")
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split(".", "-", "_").map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split(".", "-", "_").map { it.toIntOrNull() ?: 0 }
        val size = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val diff = (leftParts.getOrNull(index) ?: 0) - (rightParts.getOrNull(index) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/Soupcpu/DDL_Mate/releases/latest"
    }
}
