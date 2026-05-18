package com.deadlinemate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AppBlue = Color(0xFF007AFF)
val AppRed = Color(0xFFFF3B30)
val AppOrange = Color(0xFFFF9500)
val AppYellow = Color(0xFFF7B500)
val AppGreen = Color(0xFF34C759)
val AppPurple = Color(0xFFAF52DE)
val AppBg = Color(0xFFF5F5F7)
val AppText = Color(0xFF1D1D1F)
val AppSubtext = Color(0xFF86868B)
val AppLine = Color(0x1F3C3C43)
val AppCard = Color(0xDBFFFFFF)

private val Scheme = lightColorScheme(
    primary = AppBlue,
    background = AppBg,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = AppText,
    onSurface = AppText
)

@Composable
fun DeadlineMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
