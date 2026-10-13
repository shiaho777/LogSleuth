package io.github.shiaho777.logsleuth.app.core.crashes

import io.github.shiaho777.logsleuth.app.core.detect.CrashType
import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import io.github.shiaho777.logsleuth.app.core.logcat.AccessKind
import io.github.shiaho777.logsleuth.app.core.logcat.adbStartNeedsForeground
import io.github.shiaho777.logsleuth.app.core.logcat.streamSince
import io.github.shiaho777.logsleuth.app.data.db.CrashEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogicTest {

    @Test
    fun `context keeps the mixed tail and drops lines after the crash`() {
        val entries = (1..10).map { line(pid = it, time = it * 1_000L, raw = "l$it") } +
            line(pid = 3, time = 50_000L, raw = "later")
        val slice = CrashContext.lines(entries, timeMillis = 5_000L, maxLines = 3)
        assertEquals(listOf("l4", "l5", "l6"), slice.map { it.raw })
    }

    @Test
    fun `context is empty when nothing is old enough`() {
        val entries = listOf(line(pid = 1, time = 10_000L, raw = "future"))
        assertTrue(CrashContext.lines(entries, timeMillis = 1_000L).isEmpty())
    }

    @Test
    fun `notify and collect are independent`() {
        val silentJava = CrashCapture(notifyMaster = true, notifyJava = false, collectJava = true)
        assertTrue(CrashPolicy.stores(CrashType.CRASH, silentJava))
        assertFalse(CrashPolicy.notifies(CrashType.CRASH, silentJava))

        val pingOnly = CrashCapture(collectAnr = false, notifyAnr = true)
        assertFalse(CrashPolicy.stores(CrashType.ANR, pingOnly))
        assertTrue(CrashPolicy.notifies(CrashType.ANR, pingOnly))

        val muted = CrashCapture(notifyMaster = false)
        assertFalse(CrashPolicy.notifies(CrashType.NATIVE, muted))
        assertTrue(CrashPolicy.stores(CrashType.NATIVE, muted))
    }

    @Test
    fun `groups collapse by package and honor sort`() {
        val crashes = listOf(
            crash(1, "a.b", "Alpha", time = 10),
            crash(2, "a.b", "Alpha", time = 30, first = "second"),
            crash(3, "c.d", "Zed", time = 20),
            crash(4, null, null, time = 40, first = "mystery"),
        )
        val byCount = groupCrashes(crashes, query = "", sort = CrashSort.COUNT, reversed = false)
        assertEquals(listOf("a.b", null, "c.d"), byCount.map { it.packageName })
        assertEquals(2, byCount.first().count)
        assertEquals("second", byCount.first().events.first().firstLine)

        val byName = groupCrashes(crashes, query = "zed", sort = CrashSort.NAME, reversed = false)
        assertEquals(1, byName.size)
        assertEquals("c.d", byName.single().packageName)

        val newest = groupCrashes(crashes, query = "", sort = CrashSort.NEWEST, reversed = true)
        assertEquals("c.d", newest.first().packageName)
        assertEquals(20L, newest.first().latestTime)
    }

    @Test
    fun `adb logcat waits for the foreground only on android 13`() {
        assertTrue(adbStartNeedsForeground(33, AccessKind.READ_LOGS, inForeground = false))
        assertFalse(adbStartNeedsForeground(33, AccessKind.READ_LOGS, inForeground = true))
        assertFalse(adbStartNeedsForeground(32, AccessKind.READ_LOGS, inForeground = false))
        assertFalse(adbStartNeedsForeground(34, AccessKind.SHIZUKU, inForeground = false))
        assertFalse(adbStartNeedsForeground(34, AccessKind.ROOT, inForeground = false))
    }

    @Test
    fun `cold start dumps unless live-only is on`() {
        assertNull(streamSince(resumeSince = null, liveOnly = false, nowMillis = 0L))
        assertEquals("kept", streamSince("kept", liveOnly = true, nowMillis = 0L))
        val live = streamSince(null, liveOnly = true, nowMillis = 1_700_000_000_000L)
        assertTrue(live!!.contains(" "))
    }

    private fun line(pid: Int, time: Long, raw: String) = LogcatEntry(
        timestampMillis = time,
        pid = pid,
        tid = 1,
        uid = null,
        level = LogLevel.I,
        tag = "T",
        message = raw,
        raw = raw,
    )

    private fun crash(
        id: Long,
        pkg: String?,
        name: String?,
        time: Long,
        first: String = "boom",
    ) = CrashEventEntity(
        id = id,
        time = time,
        pid = 1,
        packageName = pkg,
        type = "CRASH",
        firstLine = first,
        snippet = first,
        appName = name,
    )
}
