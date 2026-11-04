package io.github.shiaho777.logsleuth.app.core.crashes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashSearchTest {

    @Test
    fun `finds every non-overlapping match`() {
        assertEquals(
            listOf(0..1, 4..5),
            CrashSearch.matchRanges("abxxab", "ab", ignoreCase = false),
        )
        // Consecutive matches do not overlap: "aa" in "aaa" is one match.
        assertEquals(listOf(0..1), CrashSearch.matchRanges("aaa", "aa", ignoreCase = false))
    }

    @Test
    fun `ignore case matches without changing the reported range`() {
        assertEquals(listOf(3..7), CrashSearch.matchRanges("at FATAL e", "fatal", ignoreCase = true))
    }

    @Test
    fun `a query that lowercases to more code units cannot push ranges past the text`() {
        // 'İ'.lowercase() is two code units, so a lowercased-index search would
        // report the 'x' at 4 in a 3-char string.
        val text = "İİx"
        val ranges = CrashSearch.matchRanges(text, "x", ignoreCase = true)
        assertEquals(listOf(2..2), ranges)
        assertTrue(ranges.all { it.last < text.length })
    }

    @Test
    fun `empty input finds nothing`() {
        assertEquals(emptyList<IntRange>(), CrashSearch.matchRanges("", "x", ignoreCase = false))
        assertEquals(emptyList<IntRange>(), CrashSearch.matchRanges("abc", "", ignoreCase = false))
    }

    @Test
    fun `search splits snippet and context ranges`() {
        val hits = CrashSearch.search(
            snippet = "boom at line 1",
            context = "line 1: ok\nline 2: boom",
            query = "line",
            ignoreCase = false,
        )
        assertEquals(listOf(8..11), hits.snippetRanges)
        assertEquals(listOf(0..3, 11..14), hits.contextRanges)
        assertEquals(CrashSearchSection.SNIPPET, hits.hits.first().section)
        assertEquals(CrashSearchSection.CONTEXT, hits.hits[1].section)
    }
}
