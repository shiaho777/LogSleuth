package io.github.shiaho777.logsleuth.app.core.crashes

import org.junit.Assert.assertEquals
import org.junit.Test

class CrashSearchTest {

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

    @Test
    fun `a null context contributes no ranges`() {
        val hits = CrashSearch.search("boom", null, "boom", ignoreCase = true)
        assertEquals(listOf(0..3), hits.snippetRanges)
        assertEquals(emptyList<IntRange>(), hits.contextRanges)
    }
}
