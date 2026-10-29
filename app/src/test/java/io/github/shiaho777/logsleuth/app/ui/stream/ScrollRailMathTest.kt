package io.github.shiaho777.logsleuth.app.ui.stream

import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollRailMathTest {

    private fun entry(seq: Long, level: LogLevel) = UiLogEntry(
        seq = seq,
        entry = LogcatEntry(
            timestampMillis = seq,
            pid = 1,
            tid = 1,
            uid = null,
            level = level,
            tag = "T",
            message = "m$seq",
            raw = "raw$seq",
        ),
    )

    @Test
    fun `thumb is proportional to the visible share`() {
        val rail = 1000f
        // 100 of 1000 rows visible -> a tenth of the rail.
        assertEquals(100f, ScrollRailMath.thumbHeight(rail, viewport = 100, total = 1000, minPx = 40f), 0.001f)
    }

    @Test
    fun `thumb never shrinks below the grab size`() {
        val height = ScrollRailMath.thumbHeight(1000f, viewport = 5, total = 200_000, minPx = 40f)
        assertEquals(40f, height, 0.001f)
    }

    @Test
    fun `thumb cannot exceed the rail when everything fits`() {
        assertEquals(200f, ScrollRailMath.thumbHeight(200f, viewport = 30, total = 30, minPx = 40f), 0.001f)
    }

    @Test
    fun `degenerate inputs draw nothing`() {
        assertEquals(0f, ScrollRailMath.thumbHeight(0f, 10, 100, 40f), 0f)
        assertEquals(0f, ScrollRailMath.thumbHeight(600f, 0, 100, 40f), 0f)
        assertEquals(0f, ScrollRailMath.thumbHeight(600f, 10, 0, 40f), 0f)
    }

    @Test
    fun `thumb travel spans the free space`() {
        assertEquals(0f, ScrollRailMath.thumbTop(1000f, 100f, 0f), 0.001f)
        assertEquals(900f, ScrollRailMath.thumbTop(1000f, 100f, 1f), 0.001f)
        assertEquals(450f, ScrollRailMath.thumbTop(1000f, 100f, 0.5f), 0.001f)
        // Out-of-range progress is clamped, not extrapolated off the rail.
        assertEquals(900f, ScrollRailMath.thumbTop(1000f, 100f, 3f), 0.001f)
    }

    @Test
    fun `drag centres the thumb on the finger`() {
        val rail = 800f
        val thumb = 80f
        // Finger at the very top/bottom puts the thumb edge there.
        assertEquals(0f, ScrollRailMath.progressForDrag(0f, rail, thumb), 0.001f)
        assertEquals(1f, ScrollRailMath.progressForDrag(rail, rail, thumb), 0.001f)
        // Finger at the thumb's centre is exactly the thumb's position.
        val mid = 400f
        val progress = ScrollRailMath.progressForDrag(mid, rail, thumb)
        assertEquals(mid, ScrollRailMath.thumbTop(rail, thumb, progress) + thumb / 2f, 0.001f)
    }

    @Test
    fun `drag round-trips every progress value`() {
        val rail = 960f
        val thumb = 64f
        var p = 0f
        while (p <= 1f) {
            val y = ScrollRailMath.thumbTop(rail, thumb, p) + thumb / 2f
            assertEquals(p, ScrollRailMath.progressForDrag(y, rail, thumb), 0.0001f)
            p += 0.05f
        }
    }

    @Test
    fun `drag outside the rail clamps to the ends`() {
        assertEquals(0f, ScrollRailMath.progressForDrag(-500f, 800f, 80f), 0.001f)
        assertEquals(1f, ScrollRailMath.progressForDrag(5000f, 800f, 80f), 0.001f)
    }

    @Test
    fun `progress maps onto the first and last row`() {
        assertEquals(0, ScrollRailMath.indexForProgress(0f, 1000))
        assertEquals(999, ScrollRailMath.indexForProgress(1f, 1000))
        // 0.5 * 999 = 499.5: the midpoint rounds to the nearer row, which is
        // 500 — the rail never lands between two rows.
        assertEquals(500, ScrollRailMath.indexForProgress(0.5f, 1000))
        assertEquals(0, ScrollRailMath.indexForProgress(0.9f, 1))
        assertEquals(0, ScrollRailMath.indexForProgress(1f, 0))
    }

    @Test
    fun `index never leaves the list`() {
        for (i in 0..100) {
            val index = ScrollRailMath.indexForProgress(i / 100f, 37)
            assertTrue("index $index out of range", index in 0 until 37)
        }
    }

    @Test
    fun `buckets report the worst level in each slice`() {
        val entries = listOf(
            entry(0, LogLevel.V),
            entry(1, LogLevel.V),
            entry(2, LogLevel.E),
            entry(3, LogLevel.D),
            entry(4, LogLevel.W),
            entry(5, LogLevel.I),
        )
        val buckets = ScrollRailMath.bucketLevels(entries, 2)
        assertEquals(2, buckets.size)
        assertEquals(LogLevel.E.priority, buckets[0])
        assertEquals(LogLevel.W.priority, buckets[1])
    }

    @Test
    fun `a single error survives a slice full of chatter`() {
        val entries = (0 until 100).map { entry(it.toLong(), LogLevel.I) }
            .toMutableList()
        entries[57] = entry(57, LogLevel.F)
        val buckets = ScrollRailMath.bucketLevels(entries, 10)
        assertEquals(10, buckets.size)
        // 57 falls in bucket 5 (50..59).
        assertEquals(LogLevel.F.priority, buckets[5])
        assertEquals(LogLevel.I.priority, buckets[0])
    }

    @Test
    fun `buckets cover the whole buffer`() {
        val entries = (0 until 1000).map { entry(it.toLong(), LogLevel.D) }
        val buckets = ScrollRailMath.bucketLevels(entries, 37)
        assertEquals(37, buckets.size)
        assertTrue(buckets.all { it == LogLevel.D.priority })
    }

    @Test
    fun `empty input produces no buckets`() {
        assertEquals(0, ScrollRailMath.bucketLevels(emptyList(), 20).size)
        assertEquals(0, ScrollRailMath.bucketLevels(listOf(entry(0, LogLevel.I)), 0).size)
    }

    @Test
    fun `more buckets than rows does not crash or drop the tail`() {
        val entries = listOf(entry(0, LogLevel.V), entry(1, LogLevel.E))
        val buckets = ScrollRailMath.bucketLevels(entries, 8)
        assertEquals(8, buckets.size)
        assertTrue(buckets.any { it == LogLevel.E.priority })
    }
}
