package io.github.shiaho777.logsleuth.app.service

import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingSequenceTest {
    private fun entry(seq: Long) = LogcatEntry(0L, 1, 1, null, LogLevel.I, "", "", "", seq)

    @Test
    fun `overrun repairs missing emissions before the next live entry`() {
        val writes = mutableListOf<String>()
        val retained = listOf(4L, 5L, 7L, 9L).map(::entry)
        val cursor = RecordingSequence(
            start = 1L,
            retained = { after, through -> retained.filter { it.seq in (after + 1)..through } },
            write = { writes.add("line ${it.seq}") },
            gap = { writes.add("gap $it") },
        )
        cursor.accept(entry(6))
        cursor.accept(entry(6))
        cursor.recoverThrough(10)
        assertEquals(
            listOf("gap 2", "line 4", "line 5", "line 6", "line 7", "gap 1", "line 9", "gap 1"),
            writes,
        )
    }

    @Test
    fun `a pause excludes skipped entries from the gap report`() {
        val writes = mutableListOf<String>()
        val cursor = RecordingSequence(
            start = 1L,
            retained = { _, _ -> emptyList() },
            write = { writes.add("line ${it.seq}") },
            gap = { writes.add("gap $it") },
        )
        cursor.discardThrough(9)
        cursor.accept(entry(9))
        cursor.accept(entry(10))
        assertEquals(listOf("line 10"), writes)
    }

    @Test
    fun `a failed write does not advance the recovery cursor`() {
        val writes = mutableListOf<Long>()
        var permit = false
        val cursor = RecordingSequence(
            start = 1L,
            retained = { _, _ -> listOf(entry(2)) },
            write = { if (permit) writes.add(it.seq) else false },
            gap = { error("Unexpected gap") },
        )
        cursor.accept(entry(3))
        permit = true
        cursor.accept(entry(3))
        assertEquals(listOf(2L, 3L), writes)
    }
}
