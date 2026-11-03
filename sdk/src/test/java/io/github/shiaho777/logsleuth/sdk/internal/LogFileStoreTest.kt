package io.github.shiaho777.logsleuth.sdk.internal

import io.github.shiaho777.logsleuth.sdk.SleuthConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogFileStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = tmp.newFolder("sleuth")
    }

    private fun store(config: SleuthConfig = SleuthConfig.Builder().build()) =
        LogFileStore(dir = dir, appLabel = { "pkg 1.0" }, config = config)

    @Test
    fun `rotation respects maxFiles and stays chronological`() {
        val s = store(SleuthConfig.Builder().maxFiles(3).maxFileBytes(64 * 1024).build())
        // Force four rotations: each file gets ~70KB, over the 64KB cap.
        val chunk = "x".repeat(70 * 1024) + "\n"
        repeat(4) { i ->
            s.append(chunk)
            // Ring slot actually written this round — wraps at maxFiles.
            File(dir, "log_${i % 3}.log").setLastModified(1_000_000L + i * 10_000)
        }
        val files = s.logFiles()
        assertTrue(files.size <= 3)
        // Chronological order = mtime ascending.
        assertEquals(files.sortedBy { it.lastModified() }, files)
    }

    @Test
    fun `logFiles order is chronological even after the ring wraps`() {
        val s = store(SleuthConfig.Builder().maxFiles(3).maxFileBytes(64 * 1024).build())
        val chunk = "x".repeat(70 * 1024) + "\n"
        // Slots written: 0,1,2,0,1 — pin mtimes so ordering is deterministic.
        repeat(5) { i ->
            s.append(chunk)
            File(dir, "log_${i % 3}.log").setLastModified(1_000_000L + i * 10_000)
        }
        // After the wrap, log_1 holds the newest content — chronological
        // order must place it last even though its index is not the largest.
        assertEquals("log_1.log", s.logFiles().last().name)
        assertEquals("log_2.log", s.logFiles().first().name)
    }

    @Test
    fun `append enforces the per-file size cap`() {
        val s = store(SleuthConfig.Builder().maxFileBytes(64 * 1024).build())
        s.append("y".repeat(64 * 1024) + "\n")
        s.append("more\n") // crosses the cap -> rotates to a second file
        assertEquals(2, s.logFiles().size)
    }

    @Test
    fun `redactions mask the whole match on write`() {
        val s = store(
            SleuthConfig.Builder().addRedaction(Regex("token=\\S+")).build(),
        )
        s.append("header token=secretvalue123 tail\n")
        val content = s.logFiles().single().readText()
        assertEquals("header *** tail\n", content)
    }

    @Test
    fun `short secrets are not written out in full`() {
        val s = store(SleuthConfig.Builder().addRedaction(Regex("\\d{4}")).build())
        s.append("pin 1234 ok\n")
        assertEquals("pin *** ok\n", s.logFiles().single().readText())
    }

    @Test
    fun `lookbehind keeps the key and masks only the value`() {
        val s = store(
            SleuthConfig.Builder().addRedaction(Regex("(?<=token=)\\S+")).build(),
        )
        s.append("token=abc def\n")
        assertEquals("token=*** def\n", s.logFiles().single().readText())
    }

    @Test
    fun `empty matches leave the text untouched`() {
        val s = store(SleuthConfig.Builder().addRedaction(Regex("x*")).build())
        assertEquals("abc", s.redact("abc"))
        assertEquals("a***c", s.redact("axxc"))
    }

    @Test
    fun `redact applies per line across multi-line text`() {
        val s = store(SleuthConfig.Builder().addRedaction(Regex("key=\\S+")).build())
        assertEquals("a ***\nb\n*** c", s.redact("a key=1\nb\nkey=2 c"))
    }

    @Test
    fun `block writes are redacted and keep their first line break`() {
        val s = store(SleuthConfig.Builder().addRedaction(Regex("key=\\S+")).build())
        s.appendBlock("\nhead key=1\ntail\n")
        assertEquals("\nhead ***\ntail\n", s.logFiles().single().readText())
    }

    @Test
    fun `snapshotInto copies logs in chronological order under sequential names`() {
        val s = store(SleuthConfig.Builder().maxFiles(3).maxFileBytes(64 * 1024).build())
        val chunk = "x".repeat(70 * 1024) + "\n"
        repeat(2) { s.append(chunk) }
        val dest = tmp.newFolder("staging")
        val (logs, _) = s.snapshotInto(dest)
        assertEquals(s.logFiles().size, logs.size)
        assertEquals("log_00.log", logs.first().name)
        assertEquals(
            s.logFiles().map { it.readText() },
            logs.map { it.readText() },
        )
    }
}
