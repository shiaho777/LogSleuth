package io.github.shiaho777.logsleuth.app.core.filter

import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilterExchangeTest {

    @Test
    fun `round trip keeps escapes and stack fields`() {
        val original = listOf(
            LogFilter(
                name = "OkHttp",
                minLevel = LogLevel.W,
                query = "line1\npath\\tmp",
                excludeQuery = "noise",
                tagQuery = "OkHttp",
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
}
