package io.github.shiaho777.logsleuth.app.core.detect

import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashDetectorTest {

    private fun entry(
        tag: String = "AndroidRuntime",
        level: LogLevel = LogLevel.E,
        pid: Int = 777,
        message: String,
    ) = LogcatEntry(
        timestampMillis = 1000, pid = pid, tid = 777, uid = 10123,
        level = level, tag = tag, message = message, raw = "RAW: $message",
    )

    @Test
    fun `detects a crash block and extracts the package`() {
        val d = CrashDetector()
        assertNull(d.onEntry(entry(message = "FATAL EXCEPTION: main")))
        assertNull(d.onEntry(entry(message = "Process: com.example.app, PID: 777")))
        assertNull(d.onEntry(entry(message = "java.lang.NullPointerException: boom")))

        // A line outside the block completes the crash.
        val signal = d.onEntry(entry(tag = "OtherTag", level = LogLevel.D, message = "unrelated"))
        assertNotNull(signal)
        signal!!
        assertEquals(CrashType.CRASH, signal.type)
        assertEquals("com.example.app", signal.packageName)
        assertEquals(777, signal.pid)
        assertTrue(signal.snippet.contains("NullPointerException"))
        assertEquals(3, signal.snippet.lines().size)
    }

    @Test
    fun `flush completes an open crash block`() {
        val d = CrashDetector()
        d.onEntry(entry(message = "FATAL EXCEPTION: main"))
        val signal = d.flush()
        assertNotNull(signal)
        assertEquals(CrashType.CRASH, signal!!.type)
        assertNull(d.flush())
    }

    @Test
    fun `detects ANR with package name`() {
        val d = CrashDetector()
        val signal = d.onEntry(
            entry(
                tag = "ActivityManager",
                level = LogLevel.E,
                message = "ANR in com.example.slow (com.example.slow/.MainActivity)",
            ),
        )
        assertNotNull(signal)
        signal!!
        assertEquals(CrashType.ANR, signal.type)
        assertEquals("com.example.slow", signal.packageName)
    }

    @Test
    fun `detects am_anr event with pid and package from payload`() {
        val d = CrashDetector()
        // Events-buffer line: emitted by system_server (pid 1500); the app
        // being reported is inside the bracketed payload.
        val signal = d.onEntry(
            entry(
                tag = "am_anr",
                level = LogLevel.I,
                pid = 1500,
                message = "[0,4242,com.example.anr,-9527,Input dispatching timed out]",
            ),
        )
        assertNotNull(signal)
        signal!!
        assertEquals(CrashType.ANR, signal.type)
        assertEquals("com.example.anr", signal.packageName)
        assertEquals(4242, signal.pid)
    }

    @Test
    fun `detects a native fatal-signal block`() {
        val d = CrashDetector()
        // The logcat pid (9999) is crash_dump's; the payload names the
        // crashed process (pid 5678, com.example.native).
        assertNull(
            d.onEntry(
                entry(
                    tag = "DEBUG",
                    level = LogLevel.I,
                    pid = 9999,
                    message = "Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), " +
                        "fault addr 0x0 in tid 5670 (main), pid 5678 (com.example.native)",
                ),
            ),
        )
        // Dump lines ride at low levels — the block must not require E.
        assertNull(
            d.onEntry(
                entry(tag = "DEBUG", level = LogLevel.D, pid = 9999, message = "backtrace: #00 pc 0x1"),
            ),
        )
        val signal = d.onEntry(entry(tag = "OtherTag", message = "unrelated"))
        assertNotNull(signal)
        signal!!
        assertEquals(CrashType.NATIVE, signal.type)
        assertEquals("com.example.native", signal.packageName)
        assertEquals(5678, signal.pid)
        assertTrue(signal.snippet.contains("backtrace"))
    }

    @Test
    fun `a new fatal line flushes an open native block`() {
        val d = CrashDetector()
        d.onEntry(
            entry(
                tag = "DEBUG",
                pid = 9999,
                message = "Fatal signal 11 (SIGSEGV), code 1, fault addr 0x0, pid 5678 (com.a)",
            ),
        )
        val flushed = d.onEntry(entry(message = "FATAL EXCEPTION: main"))
        assertNotNull(flushed)
        assertEquals(CrashType.NATIVE, flushed!!.type)
        assertEquals("com.a", flushed.packageName)
    }

    @Test
    fun `ordinary lines produce nothing`() {
        val d = CrashDetector()
        assertNull(d.onEntry(entry(tag = "ActivityManager", level = LogLevel.I, message = "Start proc")))
        assertNull(d.onEntry(entry(tag = "AndroidRuntime", level = LogLevel.D, message = "VM shutting down")))
    }

    @Test
    fun `two consecutive crashes both surface`() {
        val d = CrashDetector()
        d.onEntry(entry(message = "FATAL EXCEPTION: main"))
        val first = d.onEntry(entry(message = "FATAL EXCEPTION: worker"))
        assertNotNull(first)
        val second = d.flush()
        assertNotNull(second)
    }
}
