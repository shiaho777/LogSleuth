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
    fun `overflow larger than visible evicts all of it`() {
        val all = (0L..9L).map(::entry).toMutableList()
        val visible = mutableListOf(entry(0), entry(3))
        evictOverflow(all, visible, cap = 5)
        assertEquals((5L..9L).toList(), all.map { it.seq })
        assertEquals(emptyList<Long>(), visible.map { it.seq })
    }
}
