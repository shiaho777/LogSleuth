package io.github.shiaho777.logsleuth.app.core.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShellCommandTest {

    @Test
    fun `a stamp with a space stays one shell word`() {
        val stamp = "10-10 08:07:06.009"
        val cmd = toShellCommand(listOf("logcat", "-T", stamp))
        assertEquals("'logcat' '-T' '10-10 08:07:06.009'", cmd)
        assertFalse(cmd.contains("'10-10'"))
    }

    @Test
    fun `a single quote inside an argument is escaped`() {
        assertEquals("'a'\\''b'", shellQuote("a'b"))
    }
}
