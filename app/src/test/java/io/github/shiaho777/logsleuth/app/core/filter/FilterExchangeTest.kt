package io.github.shiaho777.logsleuth.app.core.filter

import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterExchangeTest {

    @Test
    fun `round trip keeps escapes and stack fields`() {
        val original = listOf(
            LogFilter(
                name = "OkHttp",
                minLevel = LogLevel.W,
                allowedLevels = setOf(LogLevel.W, LogLevel.E),
                query = "line1\npath\\tmp",
                excludeQuery = "noise",
                tagQuery = "OkHttp",
                tagExact = true,
                useRegex = true,
                packageName = "com.example",
                enabled = true,
                including = false,
                pid = "42",
                tid = "7",
                uid = "u0a123",
            ),
        )
        val decoded = FilterExchange.decode(FilterExchange.encode(original))
        assertEquals(original.map { it.copy(id = 0) }, decoded)
    }

    @Test
    fun `a foreign header is rejected`() {
        assertNull(FilterExchange.decode("not-ours\n---\nname=x\n"))
    }

    @Test
    fun `legacy minLevel W document still decodes to warn and above`() {
        val text = """
            logsleuth-filters 1
            ---
            name=warn
            minLevel=W
            query=
            exclude=
            tag=
            regex=false
        """.trimIndent()
        val decoded = FilterExchange.decode(text) ?: error("decode failed")
        val rule = decoded.single()
        assertEquals(LogLevel.W, rule.minLevel)
        assertEquals(emptySet<LogLevel>(), rule.allowedLevels)
        assertEquals(LogLevel.W.andAbove(), rule.effectiveLevels)
        assertTrue(LogLevel.W in rule.effectiveLevels)
        assertTrue(LogLevel.F in rule.effectiveLevels)
        assertFalse(LogLevel.I in rule.effectiveLevels)
    }
}
