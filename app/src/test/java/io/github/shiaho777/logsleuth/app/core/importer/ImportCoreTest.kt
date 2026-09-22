package io.github.shiaho777.logsleuth.app.core.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportCoreTest {

    @Test
    fun `isZip detects the PK magic`() {
        assertTrue(ImportCore.isZip(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertFalse(ImportCore.isZip(byteArrayOf(0x50)))
        assertFalse(ImportCore.isZip(byteArrayOf(0x41, 0x42, 0x43)))
        assertFalse(ImportCore.isZip(ByteArray(0)))
    }

    @Test
    fun `sdk bundle merges every ring file in order and appends crash`() {
        val names = listOf(
            "logs/log_00.log",
            "logs/log_01.log",
            "logs/log_02.log",
            "crash.txt",
            "device.txt",
            "meta.txt",
        )
        val plan = ImportCore.planZip(names)
        assertEquals(
            listOf("logs/log_00.log", "logs/log_01.log", "logs/log_02.log"),
            plan.logEntries,
        )
        assertEquals("crash.txt", plan.crashEntry)
    }

    @Test
    fun `viewer bundle picks logs dir over loose files`() {
        val names = listOf("logs/session.log", "meta.txt", "device.txt")
        val plan = ImportCore.planZip(names)
        assertEquals(listOf("logs/session.log"), plan.logEntries)
        assertNull(plan.crashEntry)
    }

    @Test
    fun `foreign zip falls back to any log or txt entry`() {
        val names = listOf("b_log.txt", "a_log.log", "readme.md", "meta.txt")
        val plan = ImportCore.planZip(names)
        assertEquals(listOf("a_log.log", "b_log.txt"), plan.logEntries)
    }

    @Test
    fun `entry names never strip directory prefixes for matching`() {
        // meta.txt nested in a subdir is still metadata, not a log
        val plan = ImportCore.planZip(listOf("x/meta.txt", "logs/a.log"))
        assertEquals(listOf("logs/a.log"), plan.logEntries)
    }

    @Test
    fun `parseMeta reads viewer-style keys`() {
        val meta = ImportCore.parseMeta(
            "name=Session 2026-01-01 10:00\n" +
                "startedAt=1735000000000\n" +
                "endedAt=1735000060000\n" +
                "lineCount=42\n" +
                "access=SHIZUKU\n",
        )
        assertEquals("Session 2026-01-01 10:00", meta.name)
        assertEquals(1_735_000_000_000L, meta.startedAt)
        assertFalse(meta.fromSdk)
    }

    @Test
    fun `parseMeta recognizes sdk bundles`() {
        val meta = ImportCore.parseMeta("source=sdk\napp=com.example\nlogFiles=3\n")
        assertTrue(meta.fromSdk)
        assertNull(meta.name)
    }

    @Test
    fun `parseMeta tolerates junk lines`() {
        val meta = ImportCore.parseMeta("no separator\n=x\nkey=value\n")
        assertNull(meta.name)
    }
}
