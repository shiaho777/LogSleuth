package io.github.shiaho777.logsleuth.app.core.crashes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashRetentionTest {

    private data class R(override val id: Long, override val time: Long) : CrashRetention.Row

    @Test
    fun `count cap keeps the newest rows`() {
        val rows = (1L..10L).map { R(id = it, time = it * 1_000) }
        val doomed = CrashRetention.selectForDeletion(rows, now = 100_000, maxEvents = 4, maxAgeMs = 0)
        // Rows 7..10 are the newest four; 1..6 go.
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), doomed.sorted())
    }

    @Test
    fun `age cap drops rows older than the window`() {
        val now = 100_000L
        // Cutoff is 40_000: only the 10_000 row is older than the window.
        val rows = listOf(R(1, 10_000), R(2, 50_000), R(3, 99_000))
        val doomed = CrashRetention.selectForDeletion(rows, now, maxEvents = 0, maxAgeMs = 60_000)
        assertEquals(listOf(1L), doomed.sorted())
    }

    @Test
    fun `both caps combine without duplicates`() {
        val now = 100_000L
        val rows = listOf(R(1, 1_000), R(2, 2_000), R(3, 90_000), R(4, 95_000), R(5, 99_000))
        val doomed = CrashRetention.selectForDeletion(rows, now, maxEvents = 2, maxAgeMs = 60_000)
        // 1,2 are too old; 3 is past the count cap; 4,5 survive.
        assertEquals(listOf(1L, 2L, 3L), doomed.sorted())
    }

    @Test
    fun `empty history selects nothing`() {
        assertTrue(CrashRetention.selectForDeletion(emptyList(), now = 0).isEmpty())
    }

    @Test
    fun `unsorted input is handled`() {
        val rows = listOf(R(3, 3_000), R(1, 1_000), R(2, 2_000))
        val doomed = CrashRetention.selectForDeletion(rows, now = 10_000, maxEvents = 1, maxAgeMs = 0)
        assertEquals(listOf(1L, 2L), doomed.sorted())
    }
}