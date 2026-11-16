package io.github.shiaho777.logsleuth.app.core.logcat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The formatter cache must be invisible: same output as a fresh
 * SimpleDateFormat for any pattern, thread-safe, and correct across
 * repeated calls (the memoized path) and pattern edits.
 */
class DateTimePatternsTest {

    private val millis = 1_700_000_123_456L

    private fun reference(pattern: String): String =
        SimpleDateFormat(pattern, Locale.US).format(Date(millis))

    @Test
    fun `matches a fresh formatter for preset patterns`() {
        assertEquals(
            reference(DateTimePatterns.TIME_PRESET),
            DateTimePatterns.format(millis, DateTimePatterns.TIME_PRESET, DateTimePatterns.TIME_PRESET),
        )
        assertEquals(
            reference(DateTimePatterns.DATE_PRESET),
            DateTimePatterns.format(millis, DateTimePatterns.DATE_PRESET, DateTimePatterns.DATE_PRESET),
        )
    }

    @Test
    fun `repeated calls return the same cached value`() {
        val first = DateTimePatterns.format(millis, "yyyy-MM-dd HH:mm:ss", DateTimePatterns.TIME_PRESET)
        repeat(50) {
            assertEquals(first, DateTimePatterns.format(millis, "yyyy-MM-dd HH:mm:ss", DateTimePatterns.TIME_PRESET))
        }
        assertEquals(reference("yyyy-MM-dd HH:mm:ss"), first)
    }

    @Test
    fun `blank pattern uses the fallback`() {
        assertEquals(
            reference(DateTimePatterns.TIME_PRESET),
            DateTimePatterns.format(millis, "", DateTimePatterns.TIME_PRESET),
        )
        assertEquals(
            reference(DateTimePatterns.DATE_PRESET),
            DateTimePatterns.format(millis, "   ", DateTimePatterns.DATE_PRESET),
        )
    }

    @Test
    fun `invalid pattern falls back to the preset`() {
        assertEquals(
            reference(DateTimePatterns.TIME_PRESET),
            DateTimePatterns.format(millis, "iii", DateTimePatterns.TIME_PRESET),
        )
        assertFalse(DateTimePatterns.isValid("iii"))
        assertTrue(DateTimePatterns.isValid(DateTimePatterns.TIME_PRESET))
    }

    @Test
    fun `editing the pattern changes the output`() {
        val a = DateTimePatterns.format(millis, "HH:mm", DateTimePatterns.TIME_PRESET)
        val b = DateTimePatterns.format(millis, "HH:mm:ss", DateTimePatterns.TIME_PRESET)
        assertEquals(reference("HH:mm"), a)
        assertEquals(reference("HH:mm:ss"), b)
        assertFalse(a == b)
    }

    @Test
    fun `different millis are not conflated`() {
        val a = DateTimePatterns.format(millis, "HH:mm:ss.SSS", DateTimePatterns.TIME_PRESET)
        val b = DateTimePatterns.format(millis + 1, "HH:mm:ss.SSS", DateTimePatterns.TIME_PRESET)
        assertFalse(a == b)
    }

    @Test
    fun `thread time stamp keeps its exact shape`() {
        val stamp = UidNames.formatThreadTime(millis)
        assertEquals(reference("MM-dd HH:mm:ss.SSS"), stamp)
        assertTrue(Regex("""^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}$""").matches(stamp))
    }

    @Test
    fun `concurrent callers get correct values`() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val patterns = listOf("HH:mm:ss.SSS", "MM-dd", "yyyy/MM/dd HH:mm", "ss.SSS")
            val jobs = patterns.flatMap { pattern ->
                List(8) {
                    Callable {
                        repeat(100) {
                            assertEquals(
                                reference(pattern),
                                DateTimePatterns.format(millis, pattern, DateTimePatterns.TIME_PRESET),
                            )
                        }
                    }
                }
            }
            pool.invokeAll(jobs).forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }
    }
}
