package com.deadlinemate.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object DateTimeUtils {
    private val appTimeZone = TimeZone.getTimeZone("Asia/Shanghai")
    private val dayFormat = formatter("M月d日", Locale.CHINA)
    private val dayFormatEn = formatter("MMM d", Locale.US)
    private val inputDateFormat = formatter("yyyy-MM-dd")
    private val dateTimeFormat = formatter("M月d日 HH:mm", Locale.CHINA)
    private val dateTimeFormatEn = formatter("MMM d HH:mm", Locale.US)
    private val timeFormat = formatter("HH:mm")

    fun formatDay(time: Long): String = dayFormat.format(time)
    fun formatDay(time: Long, language: String): String = if (language == "English") dayFormatEn.format(time) else dayFormat.format(time)
    fun formatInputDate(time: Long): String = inputDateFormat.format(time)
    fun formatDateTime(time: Long): String = dateTimeFormat.format(time)
    fun formatDateTime(time: Long, language: String): String = if (language == "English") dateTimeFormatEn.format(time) else dateTimeFormat.format(time)
    fun formatTime(time: Long): String = timeFormat.format(time)

    fun calendar(now: Long = System.currentTimeMillis()): Calendar = Calendar.getInstance(appTimeZone, Locale.CHINA).apply {
        timeInMillis = now
    }

    fun todayStart(now: Long = System.currentTimeMillis()): Long = calendar(now).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun tomorrowAt(hour: Int, minute: Int): Long = calendar().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun combine(dateMillis: Long, hour: Int, minute: Int): Long = calendar(dateMillis).apply {
        timeInMillis = dateMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun weekEnd(now: Long = System.currentTimeMillis()): Long = calendar(todayStart(now)).apply {
        timeInMillis = todayStart(now)
        val day = get(Calendar.DAY_OF_WEEK)
        val daysUntilSunday = if (day == Calendar.SUNDAY) 0 else Calendar.SATURDAY - day + 1
        add(Calendar.DAY_OF_YEAR, daysUntilSunday + 1)
    }.timeInMillis

    private fun formatter(pattern: String, locale: Locale = Locale.CHINA): SimpleDateFormat = SimpleDateFormat(pattern, locale).apply {
        timeZone = appTimeZone
    }
}
