package io.github.shiaho777.logsleuth.sdk.internal

import io.github.shiaho777.logsleuth.sdk.CrashReport
import io.github.shiaho777.logsleuth.sdk.SleuthConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SleuthCrashHandlerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val raw = CrashReport(
        timeMillis = 1L,
        threadName = "main",
        exceptionClass = "java.lang.IllegalStateException",
        message = "bad token=s3cret",
        stackTrace = "java.lang.IllegalStateException: bad token=s3cret\n\tat a.b(C.java:1)",
    )

    private fun config(immediate: Boolean = false) = SleuthConfig.Builder()
        .addRedaction(Regex("(?<=token=)\\S+"))
        .onCrashInvokedOnNextStart(!immediate)
        .build()

    @Test
    fun `stored crash report is redacted`() {
        val cfg = config()
        val store = LogFileStore(tmp.newFolder("sleuth"), { "pkg 1.0" }, cfg)
        SleuthCrashHandler(cfg, store) {}.record(raw)

        val stored = CrashReportCodec.parse(store.crashFile().readText())
        assertEquals("bad token=***", stored.message)
        assertFalse(stored.stackTrace.contains("s3cret"))
        assertTrue(store.pendingCrashFlag().exists())
        assertFalse(store.logFiles().single().readText().contains("s3cret"))
    }

    @Test
    fun `immediate listener receives the redacted report`() {
        val cfg = config(immediate = true)
        val store = LogFileStore(tmp.newFolder("sleuth"), { "pkg 1.0" }, cfg)
        var delivered: CrashReport? = null
        SleuthCrashHandler(cfg, store) { delivered = it }.record(raw)

        assertEquals("bad token=***", delivered?.message)
        assertFalse(delivered!!.stackTrace.contains("s3cret"))
    }
}
