package io.github.shiaho777.logsleuth.app.core.logcat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Custom date/time pattern strings with a preset fallback when invalid. */
object DateTimePatterns {
    const val DATE_PRESET = "MM-dd"
    const val TIME_PRESET = "HH:mm:ss.SSS"

    fun format(millis: Long, pattern: String, fallback: String): String {
        val chosen = pattern.ifBlank { fallback }
        val formatter = formatterOrNull(chosen) ?: formatterOrNull(fallback)
            ?: return millis.toString()
        return formatter.format(Date(millis))
    }

    fun isValid(pattern: String): Boolean = formatterOrNull(pattern) != null

    private fun formatterOrNull(pattern: String): SimpleDateFormat? {
        if (pattern.isBlank()) return null
        return try {
            SimpleDateFormat(pattern, Locale.US)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
