package com.deadlinemate.deepseek

import com.deadlinemate.util.DateTimeUtils
import java.util.Calendar

object DeadlineTextInterpreter {
    fun parse(text: String?): Long? {
        val raw = text?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val now = DateTimeUtils.calendar()
        val cal = when {
            raw.contains("后天") -> DateTimeUtils.calendar().apply { add(Calendar.DAY_OF_YEAR, 2) }
            raw.contains("明天") -> DateTimeUtils.calendar().apply { add(Calendar.DAY_OF_YEAR, 1) }
            raw.contains("今天") || raw.contains("今晚") -> DateTimeUtils.calendar()
            else -> parseExplicitDate(raw) ?: DateTimeUtils.calendar()
        }
        val explicit = parseExplicitDate(raw)
        if (explicit != null) {
            cal.timeInMillis = explicit.timeInMillis
        }
        val hourMinute = parseTime(raw)
        if (hourMinute == null && explicit == null && !raw.contains("今天") && !raw.contains("明天") && !raw.contains("后天")) {
            return null
        }
        val (hour, minute) = hourMinute ?: (23 to 59)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis.takeIf { it >= now.timeInMillis - 60_000 }
    }

    private fun parseExplicitDate(text: String): Calendar? {
        Regex("""(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})""").find(text)?.let {
            return DateTimeUtils.calendar().apply {
                set(it.groupValues[1].toInt(), it.groupValues[2].toInt() - 1, it.groupValues[3].toInt())
            }
        }
        Regex("""(\d{1,2})月(\d{1,2})[日号]?""").find(text)?.let {
            return DateTimeUtils.calendar().apply {
                set(Calendar.MONTH, it.groupValues[1].toInt() - 1)
                set(Calendar.DAY_OF_MONTH, it.groupValues[2].toInt())
            }
        }
        return null
    }

    private fun parseTime(text: String): Pair<Int, Int>? {
        Regex("""(\d{1,2})[:：](\d{1,2})""").find(text)?.let {
            return normalizeHour(text, it.groupValues[1].toInt()) to it.groupValues[2].toInt().coerceIn(0, 59)
        }
        Regex("""(\d{1,2})点(?:半|(\d{1,2})分?)?""").find(text)?.let {
            val minute = if (it.value.contains("半")) 30 else it.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            return normalizeHour(text, it.groupValues[1].toInt()) to minute.coerceIn(0, 59)
        }
        ChineseHour.entries.firstOrNull { text.contains(it.label) }?.let {
            return normalizeHour(text, it.hour) to 0
        }
        return when {
            text.contains("中午") -> 12 to 0
            text.contains("晚上") || text.contains("今晚") -> 20 to 0
            text.contains("下午") -> 15 to 0
            text.contains("上午") || text.contains("早上") -> 9 to 0
            else -> null
        }
    }

    private fun normalizeHour(text: String, hour: Int): Int {
        val base = hour.coerceIn(0, 23)
        return if ((text.contains("下午") || text.contains("晚上") || text.contains("今晚")) && base in 1..11) {
            base + 12
        } else {
            base
        }
    }

    private enum class ChineseHour(val label: String, val hour: Int) {
        ONE("一点", 1),
        TWO("两点", 2),
        THREE("三点", 3),
        FOUR("四点", 4),
        FIVE("五点", 5),
        SIX("六点", 6),
        SEVEN("七点", 7),
        EIGHT("八点", 8),
        NINE("九点", 9),
        TEN("十点", 10)
    }
}
