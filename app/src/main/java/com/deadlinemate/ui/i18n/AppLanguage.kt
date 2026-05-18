package com.deadlinemate.ui.i18n

import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

enum class AppLanguage {
    Chinese,
    English
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.Chinese }

fun appLanguage(language: String): AppLanguage {
    return if (language == "English") AppLanguage.English else AppLanguage.Chinese
}

fun defaultAppLanguage(): String {
    return if (Locale.getDefault().language.equals("en", ignoreCase = true)) "English" else "中文"
}

fun AppLanguage.text(zh: String, en: String): String {
    return if (this == AppLanguage.English) en else zh
}
