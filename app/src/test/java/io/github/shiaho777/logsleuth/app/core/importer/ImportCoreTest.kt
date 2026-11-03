package io.github.shiaho777.logsleuth.app.core.importer

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
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

    /** Hands out at most one byte per read, like a slow pipe or provider stream. */
    private class TrickleStream(private val data: ByteArray) : InputStream() {
        private var pos = 0
        override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val c = read()
            if (c < 0) return -1
            b[off] = c.toByte()
            return 1
        }
    }

    @Test
    fun `readHead fills the header across short reads`() {
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)
        val head = ImportCore.readHead(TrickleStream(zip), 4)
        assertArrayEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04), head)
        assertTrue(ImportCore.isZip(head))
    }

    @Test
    fun `readHead returns empty for an empty stream`() {
        val head = ImportCore.readHead(ByteArrayInputStream(ByteArray(0)), 4)
        assertEquals(0, head.size)
        assertFalse(ImportCore.isZip(head))
    }

    @Test
    fun `readHead returns what exists when the stream is shorter than max`() {
        val head = ImportCore.readHead(TrickleStream(byteArrayOf(0x41, 0x42)), 4)
        assertArrayEquals(byteArrayOf(0x41, 0x42), head)
    }

    @Test
    fun `sdk bundle merges every ring file in order and appends crash`() {
        val names = listOf(
            "logs/log_00.log",
            "logs/log_01.log",
            "logs/log_02.log",
            "crash.txt",
            "device.txt",
            "app.txt",
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
