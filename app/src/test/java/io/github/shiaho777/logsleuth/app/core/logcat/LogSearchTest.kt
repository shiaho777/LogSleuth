package io.github.shiaho777.logsleuth.app.core.logcat

import org.junit.Assert.assertEquals
import org.junit.Test

class LogSearchTest {
    @Test
    fun retainsSelectedIdWhenEarlierMatchesAreTrimmed() {
        assertEquals(1, retainedSearchHitIndex(listOf(20L, 30L, 40L), selectedId = 30L))
        assertEquals(0, retainedSearchHitIndex(listOf(30L, 40L), selectedId = 30L))
    }

    @Test
    fun selectsNextSurvivingMatchWhenSelectedIdDisappears() {
        assertEquals(1, retainedSearchHitIndex(listOf(10L, 30L, 40L), selectedId = 20L))
    }

    @Test
    fun fallsBackAtTheEndsOfTheList() {
        assertEquals(1, retainedSearchHitIndex(listOf(10L, 30L), selectedId = 40L))
        assertEquals(0, retainedSearchHitIndex(listOf(10L, 30L), selectedId = null))
        assertEquals(-1, retainedSearchHitIndex(emptyList(), selectedId = 40L))
    }
}
