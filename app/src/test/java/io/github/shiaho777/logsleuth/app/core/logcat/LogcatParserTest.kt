package io.github.shiaho777.logsleuth.app.core.logcat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatParserTest {

    private val now = parseTime(2025, 6, 15, 12, 0, 0, 0)

    @Test
    fun `parses threadtime without uid`() {
        val line = "06-15 11:59:58.123  4567  4589 D MyTag: hello world"
        val parsed = LogcatParser.parse(line, now)
        assertTrue(parsed is ParsedLine.Entry)
        val e = (parsed as ParsedLine.Entry).entry
        assertEquals(4567, e.pid)
        assertEquals(4589, e.tid)
        assertNull(e.uid)
        assertEquals(LogLevel.D, e.level)
        assertEquals("MyTag", e.tag)
        assertEquals("hello world", e.message)
    }

    @Test
    fun `parses threadtime with uid column`() {
        val line = "06-15 11:59:58.123 10123  4567  4589 W MyTag: be careful"
        val parsed = LogcatParser.parse(line, now)
        assertTrue(parsed is ParsedLine.Entry)
        val e = (parsed as ParsedLine.Entry).entry
        assertEquals(10123, e.uid)
        assertEquals(4567, e.pid)
        assertEquals(4589, e.tid)
        assertEquals(LogLevel.W, e.level)
        assertEquals("be careful", e.message)
    }

    @Test
    fun `parses empty message`() {
        val line = "06-15 11:59:58.123  4567  4589 I Tag: "
        val parsed = LogcatParser.parse(line, now)
        assertTrue(parsed is ParsedLine.Entry)
        assertEquals("", (parsed as ParsedLine.Entry).entry.message)
    }

    @Test
    fun `keeps message with colons intact`() {
        val line = "06-15 11:59:58.123  4567  4589 E AndroidRuntime: java.lang.RuntimeException: boom: nested"
        val parsed = LogcatParser.parse(line, now) as ParsedLine.Entry
        assertEquals("java.lang.RuntimeException: boom: nested", parsed.entry.message)
    }

    @Test
    fun `non-matching lines are continuations`() {
        assertTrue(LogcatParser.parse("--------- beginning of main", now) is ParsedLine.Continuation)
        assertTrue(LogcatParser.parse("    at com.example.Foo.bar(Foo.kt:12)", now) is ParsedLine.Continuation)
        assertTrue(LogcatParser.parse("", now) is ParsedLine.Continuation)
    }

    @Test
    fun `year boundary moves future dates back one year`() {
        // "now" is Jan 1st 2025; a Dec-31 entry would be "in the future" without correction.
        val newYear = parseTime(2025, 1, 1, 0, 5, 0, 0)
        val line = "12-31 23:59:59.000  1111  1111 D Tag: last year line"
        val parsed = LogcatParser.parse(line, newYear) as ParsedLine.Entry
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = parsed.entry.timestampMillis }
        assertEquals(2024, cal.get(java.util.Calendar.YEAR))
        assertEquals(12, cal.get(java.util.Calendar.MONTH) + 1)
    }

    private fun parseTime(year: Int, month: Int, day: Int, h: Int, m: Int, s: Int, ms: Int): Long =
        java.util.Calendar.getInstance().apply {
            set(year, month - 1, day, h, m, s)
            set(java.util.Calendar.MILLISECOND, ms)
        }.timeInMillis
}
