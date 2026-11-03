package io.github.shiaho777.logsleuth.sdk.internal

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatResumeCursorTest {

    private val today = LocalDate.of(2025, 6, 15)

    private fun line(stamp: String, msg: String) = "$stamp  1234  5678 I MyTag: $msg"

    @Test
    fun `first connection reads the whole buffer`() {
        val cursor = LogcatResumeCursor()
        assertEquals(
            listOf("logcat", "-v", "threadtime", "--pid=42"),
            cursor.command(42, today),
        )
        cursor.beginConnection()
        assertTrue(cursor.isNew("--------- beginning of main"))
        assertTrue(cursor.isNew(line("06-15 10:00:00.000", "a")))
    }

    @Test
    fun `reconnect resumes at the last delivered timestamp`() {
        val cursor = LogcatResumeCursor()
        cursor.beginConnection()
        cursor.isNew(line("06-15 10:00:00.000", "a"))
        cursor.isNew(line("06-15 10:00:01.500", "b"))
        assertEquals(
            listOf("logcat", "-v", "threadtime", "--pid=42", "-T", "2025-06-15 10:00:01.500"),
            cursor.command(42, today),
        )
    }

    @Test
    fun `replayed lines at the boundary are dropped once`() {
        val cursor = LogcatResumeCursor()
        cursor.beginConnection()
        assertTrue(cursor.isNew(line("06-15 10:00:00.000", "old")))
        assertTrue(cursor.isNew(line("06-15 10:00:01.500", "b1")))
        assertTrue(cursor.isNew(line("06-15 10:00:01.500", "b2")))

        cursor.beginConnection()
        assertFalse(cursor.isNew("--------- beginning of main"))
        assertFalse(cursor.isNew(line("06-15 10:00:01.500", "b1")))
        assertFalse(cursor.isNew(line("06-15 10:00:01.500", "b2")))
        assertTrue(cursor.isNew(line("06-15 10:00:01.500", "b3")))
        assertTrue(cursor.isNew(line("06-15 10:00:02.000", "c")))
    }

    @Test
    fun `a repeated message after the replay is new`() {
        val cursor = LogcatResumeCursor()
        cursor.beginConnection()
        cursor.isNew(line("06-15 10:00:01.500", "tick"))

        cursor.beginConnection()
        assertFalse(cursor.isNew(line("06-15 10:00:01.500", "tick")))
        assertTrue(cursor.isNew(line("06-15 10:00:02.000", "tick")))
        // Replay is over: an identical line is a genuine repeat, not a replay.
        assertTrue(cursor.isNew(line("06-15 10:00:02.000", "tick")))
    }

    @Test
    fun `boundary lines survive several reconnects`() {
        val cursor = LogcatResumeCursor()
        cursor.beginConnection()
        cursor.isNew(line("06-15 10:00:01.500", "b1"))

        cursor.beginConnection()
        assertFalse(cursor.isNew(line("06-15 10:00:01.500", "b1")))
        assertTrue(cursor.isNew(line("06-15 10:00:01.500", "b2")))

        cursor.beginConnection()
        assertFalse(cursor.isNew(line("06-15 10:00:01.500", "b1")))
        assertFalse(cursor.isNew(line("06-15 10:00:01.500", "b2")))
        assertTrue(cursor.isNew(line("06-15 10:00:03.000", "c")))
    }

    @Test
    fun `a month-day after today belongs to last year`() {
        val cursor = LogcatResumeCursor()
        cursor.beginConnection()
        cursor.isNew(line("12-31 23:59:59.999", "late"))
        assertEquals(
            "2024-12-31 23:59:59.999",
            cursor.command(1, LocalDate.of(2025, 1, 1)).last(),
        )
        assertEquals(
            "2025-12-31 23:59:59.999",
            cursor.command(1, LocalDate.of(2025, 12, 31)).last(),
        )
    }
}
