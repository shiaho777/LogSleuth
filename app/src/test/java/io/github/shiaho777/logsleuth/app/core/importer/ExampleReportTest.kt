package io.github.shiaho777.logsleuth.app.core.importer

import io.github.shiaho777.logsleuth.app.core.detect.CrashDetector
import io.github.shiaho777.logsleuth.app.core.detect.CrashType
import io.github.shiaho777.logsleuth.app.core.logcat.EntryAssembler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleReportTest {
    @Test
    fun `the sample replays the bookmark and detects a single checkout crash`() {
        val start = 1_790_000_000_000L
        val lines = exampleReportLines(start)
        val assembler = EntryAssembler { start + 8_000L }
        val entries = lines.flatMap(assembler::onLine) + listOfNotNull(assembler.flush())
        assertEquals(lines.size, entries.size)
        assertEquals(start + 3_000L, entries.single { it.message == "User tapped Pay" }.timestampMillis)

        val detector = CrashDetector()
        val crash = (entries.mapNotNull(detector::onEntry) + detector.drain()).single()
        assertEquals(CrashType.CRASH, crash.type)
        assertEquals("com.example.checkout", crash.packageName)
        assertTrue("Checkout total is missing" in crash.snippet)
    }
}
