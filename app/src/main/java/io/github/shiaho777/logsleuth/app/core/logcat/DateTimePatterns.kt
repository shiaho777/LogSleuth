package io.github.shiaho777.logsleuth.app.core.logcat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Custom date/time pattern strings with a preset fallback when invalid. */
object DateTimePatterns {
    const val DATE_PRESET = "MM-dd"
    const val TIME_PRESET = "HH:mm:ss.SSS"

    /**
     * Live rows re-format their stamp on every recomposition, and a hot
     * stream recomposes the viewport dozens of times per second. Parsing a
     * pattern into a [SimpleDateFormat] each time dominated that cost, so
     * formatters are cached per thread (they are not thread-safe) and whole
     * results are memoized — a buffer of immutable entries hits the same
     * (millis, pattern) pairs over and over.
     */
    private const val CACHE_LIMIT = 8_192

    private val formatters = object : ThreadLocal<MutableMap<String, SimpleDateFormat?>>() {
        override fun initialValue(): MutableMap<String, SimpleDateFormat?> = HashMap()
    }

    private val cache = ConcurrentHashMap<String, String>()

    fun format(millis: Long, pattern: String, fallback: String): String {
        val key = "$millis|${pattern.ifBlank { fallback }}|$fallback"
        cache[key]?.let { return it }
        val chosen = pattern.ifBlank { fallback }
        val formatter = cachedFormatter(chosen) ?: cachedFormatter(fallback)
            ?: return millis.toString()
        val out = formatter.format(Date(millis))
        // Patterns are user-editable; stop caching once the key space runs
        // away instead of evicting hot entries for cold custom patterns.
        if (cache.size < CACHE_LIMIT) cache[key] = out
        return out
    }

    fun isValid(pattern: String): Boolean = formatterOrNull(pattern) != null

    private fun cachedFormatter(pattern: String): SimpleDateFormat? {
        if (pattern.isBlank()) return null
        val map = formatters.get()!!
        if (map.containsKey(pattern)) return map[pattern]
        val parsed = formatterOrNull(pattern)
        map[pattern] = parsed
        return parsed
    }

    private fun formatterOrNull(pattern: String): SimpleDateFormat? {
        if (pattern.isBlank()) return null
        return try {
            SimpleDateFormat(pattern, Locale.US)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
