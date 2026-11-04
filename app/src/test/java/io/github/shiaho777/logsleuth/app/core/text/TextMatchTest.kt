package io.github.shiaho777.logsleuth.app.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchTest {

    @Test
    fun `finds every non-overlapping match`() {
        assertEquals(
            listOf(0..1, 4..5),
            matchRanges("abxxab", "ab", ignoreCase = false),
        )
        // Consecutive matches do not overlap: "aa" in "aaa" is one match.
        assertEquals(listOf(0..1), matchRanges("aaa", "aa", ignoreCase = false))
    }

    @Test
    fun `ignore case matches without changing the reported range`() {
        assertEquals(listOf(3..7), matchRanges("at FATAL e", "fatal", ignoreCase = true))
    }

    @Test
    fun `a query that lowercases to more code units cannot push ranges past the text`() {
        // 'İ'.lowercase() is two code units, so a lowercased-index search would
        // report the 'x' at 4 in a 3-char string.
        val text = "İİx"
        val ranges = matchRanges(text, "x", ignoreCase = true)
        assertEquals(listOf(2..2), ranges)
        assertTrue(ranges.all { it.last < text.length })
    }

    @Test
    fun `empty input finds nothing`() {
        assertEquals(emptyList<IntRange>(), matchRanges("", "x", ignoreCase = false))
        assertEquals(emptyList<IntRange>(), matchRanges("abc", "", ignoreCase = false))
    }
}
