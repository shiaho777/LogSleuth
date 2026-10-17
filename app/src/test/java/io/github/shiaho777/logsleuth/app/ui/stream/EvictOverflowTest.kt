package io.github.shiaho777.logsleuth.app.ui.stream

import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class EvictOverflowTest {

    private fun entry(seq: Long) = UiLogEntry(
        seq,
        LogcatEntry(0L, 1, 1, null, LogLevel.I, "t", "m", "raw"),
    )

    @Test
    fun `evicted prefix drops matching prefix of visible`() {
        val all = (0L..9L).map(::entry).toMutableList()
        val visible = (0L..9L step 2).map(::entry).toMutableList()
        evictOverflow(all, visible, cap = 5)
        assertEquals((5L..9L).toList(), all.map { it.seq })
        assertEquals(listOf(6L, 8L), visible.map { it.seq })
    }

    @Test
    fun `no overflow leaves both lists untouched`() {
        val all = (0L..3L).map(::entry).toMutableList()
        val visible = all.toMutableList()
        evictOverflow(all, visible, cap = 5)
        assertEquals(4, all.size)
        assertEquals(4, visible.size)
    }

    @Test
    fun `prefix drop keeps the surviving tail of a fresh append`() {
        assertEquals(0 to 0, prefixDrop(sizeBefore = 100, appended = 5, dropped = 0))
        assertEquals(3 to 0, prefixDrop(sizeBefore = 100, appended = 5, dropped = 3))
        assertEquals(2 to 1, prefixDrop(sizeBefore = 2, appended = 3, dropped = 3))
        assertEquals(2 to 3, prefixDrop(sizeBefore = 2, appended = 3, dropped = 5))
        assertEquals(0 to 0, prefixDrop(sizeBefore = 0, appended = 0, dropped = 0))
    }

    @Test
    fun `overflow larger than visible evicts all of it`() {
        val all = (0L..9L).map(::entry).toMutableList()
        val visible = mutableListOf(entry(0), entry(3))
        evictOverflow(all, visible, cap = 5)
        assertEquals((5L..9L).toList(), all.map { it.seq })
        assertEquals(emptyList<Long>(), visible.map { it.seq })
    }

    @Test
    fun `parked index stays on the same line after a prefix drop`() {
        val entries = (5L..9L).map(::entry)
        assertEquals(2, parkedIndex(entries, seq = 7))
    }

    @Test
    fun `parked index falls forward onto the next surviving line`() {
        val entries = listOf(entry(5), entry(7), entry(9))
        assertEquals(1, parkedIndex(entries, seq = 6))
        assertEquals(0, parkedIndex(entries, seq = 4))
    }

    @Test
    fun `parked index is absent when every remaining line is older`() {
        assertEquals(-1, parkedIndex(emptyList(), seq = 1))
        assertEquals(-1, parkedIndex(listOf(entry(1), entry(2)), seq = 3))
    }
}
