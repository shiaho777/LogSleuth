package io.github.shiaho777.logsleuth.app.core.logcat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogcatCommandTest {

    private val source = LocalLogcatSource()

    @Test
    fun `first connection dumps the buffers`() {
        val cmd = source.buildCommand(null)
        assertFalse(cmd.contains("-T"))
        assertEquals(
            listOf(
                "logcat",
                "-b", "main", "-b", "crash", "-b", "events",
                "-v", "threadtime", "-v", "uid",
            ),
            cmd,
        )
    }

    @Test
    fun `reconnect passes the last stamp as one -T argument`() {
        val stamp = "10-10 08:07:06.009"
        val cmd = source.buildCommand(stamp)
        assertEquals(stamp, cmd[cmd.indexOf("-T") + 1])
        assertFalse(cmd.contains("10-10"))
    }
}
