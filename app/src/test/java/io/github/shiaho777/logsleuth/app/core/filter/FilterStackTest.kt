package io.github.shiaho777.logsleuth.app.core.filter

import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterStackTest {

    private fun entry(
        tag: String = "Tag",
        message: String = "hello",
        uid: Int? = 10123,
    ) = LogcatEntry(
        timestampMillis = 0,
        pid = 1,
        tid = 2,
        uid = uid,
        level = LogLevel.I,
        tag = tag,
        message = message,
        raw = "",
    )

    @Test
    fun `no enabled rules pass every line`() {
        val stack = FilterStack.compile(
            listOf(LogFilter(name = "off", tagQuery = "Alpha")),
        ) { null }
        assertTrue(stack.matches(entry(tag = "Other")))
    }

    @Test
    fun `including rules are OR`() {
        val stack = FilterStack.compile(
            listOf(
                LogFilter(enabled = true, including = true, tagQuery = "Alpha"),
                LogFilter(enabled = true, including = true, tagQuery = "Beta"),
            ),
        ) { null }
        assertTrue(stack.matches(entry(tag = "Alpha")))
        assertTrue(stack.matches(entry(tag = "Beta")))
        assertFalse(stack.matches(entry(tag = "Gamma")))
    }

    @Test
    fun `an exclude drops a line an include would keep`() {
        val stack = FilterStack.compile(
            listOf(
                LogFilter(enabled = true, including = true, tagQuery = "Alpha"),
                LogFilter(enabled = true, including = false, query = "spam"),
            ),
        ) { null }
        assertFalse(stack.matches(entry(tag = "Alpha", message = "spam here")))
        assertTrue(stack.matches(entry(tag = "Alpha", message = "useful")))
    }

    @Test
    fun `a blank exclude cannot hide the stream`() {
        val stack = FilterStack.compile(
            listOf(LogFilter(enabled = true, including = false)),
        ) { null }
        assertTrue(stack.matches(entry()))
    }

    @Test
    fun `a broken include rule is parked without dragging healthy rules down`() {
        val stack = FilterStack.compile(
            listOf(
                LogFilter(enabled = true, including = true, query = "[broken", useRegex = true),
                LogFilter(enabled = true, including = true, tagQuery = "Alpha"),
            ),
        ) { null }
        assertTrue(stack.regexInvalid)
        assertTrue(stack.matches(entry(tag = "Alpha")))
        assertFalse(stack.matches(entry(tag = "Gamma")))
    }

    @Test
    fun `a broken exclude rule is parked and flagged`() {
        val stack = FilterStack.compile(
            listOf(LogFilter(enabled = true, including = false, query = "[broken", useRegex = true)),
        ) { null }
        assertTrue(stack.regexInvalid)
        assertTrue(stack.matches(entry()))
    }
}
