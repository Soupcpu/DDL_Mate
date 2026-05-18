package com.deadlinemate.deepseek

data class DeepSeekConfig(
    val apiKey: String,
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val enabled: Boolean = false
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"
    }
}

fun DeepSeekConfig?.statusText(): String = when {
    this == null || apiKey.isBlank() -> "未配置"
    enabled -> "已启用"
    else -> "已配置，未启用"
}

fun DeepSeekConfig?.statusText(language: String): String = when {
    language == "English" && (this == null || apiKey.isBlank()) -> "Not configured"
    language == "English" && this?.enabled == true -> "Enabled"
    language == "English" -> "Configured, disabled"
    this == null || apiKey.isBlank() -> "未配置"
    enabled -> "已启用"
    else -> "已配置，未启用"
}
