package io.github.shiaho777.logsleuth.app.core.logcat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogHistoryTest {
    private fun entry(time: Long, endpoint: String? = null) = LogcatEntry(
        time, 1, 1, 10123, LogLevel.I, "Test", "message", "raw", remoteEndpoint = endpoint,
    )

    @Test
    fun `local and remote sources keep independent cursors and snapshots`() {
        val history = LogHistory()
        val remote = "remote:5555"
        history.append(entry(1_000), 5)
        history.append(entry(42_000, remote), 5)
        history.append(entry(12_000), 5)
        assertEquals(listOf(1L, 2L), history.snapshot(null).entries.map { it.seq })
        assertEquals(listOf(1L), history.snapshot(remote).entries.map { it.seq })
        assertEquals(UidNames.formatSince(12_000), history.stamp(null))
        assertEquals(UidNames.formatSince(42_000), history.stamp(remote))
        assertNotEquals(history.stamp(null), history.stamp(remote))
        assertNull(history.stamp("other:5555"))
    }

    @Test
    fun `eviction and clear keep the original per-source watermark`() {
        val history = LogHistory()
        val remote = "remote:5555"
        history.append(entry(1_000, remote), 2)
        history.append(entry(2_000), 2)
        history.append(entry(3_000, remote), 2)
        assertEquals(listOf(2L), history.retained(remote, 0L, 5L).map { it.seq })
        history.clear(remote)
        assertEquals(emptyList<LogcatEntry>(), history.snapshot(remote).entries)
        assertEquals(2L, history.watermark(remote))
        assertEquals(1L, history.snapshot(null).maxSeq)
        assertEquals(3L, history.append(entry(4_000, remote), 2).seq)
    }
}
