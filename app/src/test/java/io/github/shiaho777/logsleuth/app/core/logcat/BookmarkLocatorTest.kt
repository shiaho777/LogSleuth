package io.github.shiaho777.logsleuth.app.core.logcat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarkLocatorTest {
    @Test
    fun choosesFirstExactMatchInFileOrder() {
        val times = listOf(9_100L, 8_000L, 8_000L, 6_700L)

        assertEquals(1, nearestTimestampIndex(times.size, 8_000L, times::get))
    }

    @Test
    fun choosesTheClosestVisibleLineEvenWhenTheFileIsOutOfOrder() {
        val times = listOf(9_100L, 6_700L, 7_900L)

        assertEquals(2, nearestTimestampIndex(times.size, 8_000L, times::get))
        assertEquals(0, nearestTimestampIndex(times.size, 9_500L, times::get))
    }

    @Test
    fun prefersTheFollowingLineOnATie() {
        val times = listOf(7_900L, 8_100L)

        assertEquals(1, nearestTimestampIndex(times.size, 8_000L, times::get))
    }

    @Test
    fun skipsUntimestampedLines() {
        val times = listOf(0L, -1L, 9_000L)

        assertEquals(2, nearestTimestampIndex(times.size, 8_000L, times::get))
        assertNull(nearestTimestampIndex(2, 8_000L, times::get))
        assertNull(nearestTimestampIndex(times.size, 0L, times::get))
    }
}
