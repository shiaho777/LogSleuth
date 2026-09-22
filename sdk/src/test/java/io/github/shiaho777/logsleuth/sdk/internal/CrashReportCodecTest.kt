package io.github.shiaho777.logsleuth.sdk.internal

import io.github.shiaho777.logsleuth.sdk.CrashReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrashReportCodecTest {

    @Test
    fun `round trip preserves all fields`() {
        val report = CrashReport(
            timeMillis = 1_700_000_000_000L,
            threadName = "main",
            exceptionClass = "java.lang.RuntimeException",
            message = "boom",
            stackTrace = "java.lang.RuntimeException: boom\n\tat a.b(C.java:1)",
        )
        assertEquals(report, CrashReportCodec.parse(CrashReportCodec.serialize(report)))
    }

    @Test
    fun `multi-line message does not corrupt the stack trace`() {
        val report = CrashReport(
            timeMillis = 42L,
            threadName = "worker-1",
            exceptionClass = "IllegalStateException",
            message = "first line\nsecond line\nthird",
            stackTrace = "trace\nmore trace",
        )
        val parsed = CrashReportCodec.parse(CrashReportCodec.serialize(report))
        assertEquals(report, parsed)
    }

    @Test
    fun `literal backslash-n survives the escape round trip`() {
        val report = CrashReport(
            timeMillis = 1L,
            threadName = "t",
            exceptionClass = "E",
            message = "literal \\n sequence and \\\\ backslash",
            stackTrace = "s",
        )
        assertEquals(report, CrashReportCodec.parse(CrashReportCodec.serialize(report)))
    }

    @Test
    fun `null message parses back as empty string`() {
        val report = CrashReport(1L, "t", "E", null, "trace")
        val parsed = CrashReportCodec.parse(CrashReportCodec.serialize(report))
        // null serializes to an empty escaped line, which parses back as ""
        assertEquals("", parsed.message)
    }

    @Test
    fun `legacy format with empty message parses`() {
        // Files written by the previous version stored the raw message line.
        val text = "123\nmain\njava.lang.Error\n\nstack here"
        val parsed = CrashReportCodec.parse(text)
        assertEquals(123L, parsed.timeMillis)
        assertEquals("", parsed.message)
        assertEquals("stack here", parsed.stackTrace)
    }

    @Test
    fun `missing fields degrade gracefully`() {
        val parsed = CrashReportCodec.parse("")
        assertEquals(0L, parsed.timeMillis)
        assertEquals("", parsed.threadName)
        assertNull(parsed.message)
    }
}
