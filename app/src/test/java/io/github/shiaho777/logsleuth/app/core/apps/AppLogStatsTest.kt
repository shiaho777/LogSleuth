package io.github.shiaho777.logsleuth.app.core.apps

import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLogStatsTest {

    private fun entry(
        uid: Int?,
        level: LogLevel = LogLevel.I,
        ts: Long = 1_000L,
    ) = LogcatEntry(ts, 100, 100, uid, level, "tag", "msg", "raw")

    private val resolver: (Int) -> Pair<String?, String>? = { uid ->
        when (uid) {
            1001 -> "com.alpha" to "Alpha"
            1002 -> "com.beta" to "Beta"
            else -> null to "UID $uid"
        }
    }

    @Test
    fun `groups by uid sorted by count desc`() {
        val groups = groupEntriesByApp(
            entries = listOf(
                entry(1001), entry(1002), entry(1001), entry(1002), entry(1002),
            ),
            unattributedLabel = "Unattributed",
            resolveApp = resolver,
        )
        assertEquals(2, groups.size)
        assertEquals(1002, groups[0].uid)
        assertEquals(3, groups[0].total)
        assertEquals("com.beta", groups[0].packageName)
        assertEquals("Beta", groups[0].label)
        assertEquals(1001, groups[1].uid)
        assertEquals(2, groups[1].total)
    }

    @Test
    fun `null uid forms one unattributed group`() {
        val groups = groupEntriesByApp(
            entries = listOf(entry(null), entry(null), entry(1001)),
            unattributedLabel = "Unattributed",
            resolveApp = resolver,
        )
        assertEquals(2, groups.size)
        val un = groups.first { it.uid == null }
        assertEquals(2, un.total)
        assertEquals("Unattributed", un.label)
        assertNull(un.packageName)
    }

    @Test
    fun `level counts and error warn aggregates`() {
        val groups = groupEntriesByApp(
            entries = listOf(
                entry(1001, LogLevel.E),
                entry(1001, LogLevel.F),
                entry(1001, LogLevel.A),
                entry(1001, LogLevel.W),
                entry(1001, LogLevel.D),
            ),
            unattributedLabel = "x",
            resolveApp = resolver,
        )
        val g = groups.single()
        assertEquals(3, g.errors)
        assertEquals(1, g.warns)
        assertEquals(1, g.levelCounts[LogLevel.D.ordinal])
        assertEquals(5, g.total)
    }

    @Test
    fun `first and last timestamps span the group`() {
        val groups = groupEntriesByApp(
            entries = listOf(entry(1001, ts = 500), entry(1001, ts = 100), entry(1001, ts = 900)),
            unattributedLabel = "x",
            resolveApp = resolver,
        )
        assertEquals(100L, groups[0].firstMillis)
        assertEquals(900L, groups[0].lastMillis)
    }

    @Test
    fun `unresolved uid falls back to label`() {
        val groups = groupEntriesByApp(
            entries = listOf(entry(9999)),
            unattributedLabel = "x",
            resolveApp = resolver,
        )
        assertEquals("UID 9999", groups[0].label)
        assertNull(groups[0].packageName)
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals(
            emptyList<AppLogGroup>(),
            groupEntriesByApp(emptyList(), "x", resolver),
        )
    }
}
