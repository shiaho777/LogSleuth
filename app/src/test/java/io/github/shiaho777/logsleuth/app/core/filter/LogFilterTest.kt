package io.github.shiaho777.logsleuth.app.core.filter

import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFilterTest {

    private fun entry(
        level: LogLevel = LogLevel.D,
        tag: String = "MyTag",
        message: String = "hello",
        uid: Int? = 10123,
        pid: Int = 1,
        tid: Int = 1,
    ) = LogcatEntry(
        timestampMillis = 0, pid = pid, tid = tid, uid = uid,
        level = level, tag = tag, message = message, raw = "",
    )

    @Test
    fun `default filter matches everything`() {
        val f = CompiledFilter(LogFilter.DEFAULT)
        LogLevel.entries.forEach { assertTrue(f.matches(entry(level = it))) }
    }

    @Test
    fun `min level filters out lower priorities`() {
        val f = CompiledFilter(LogFilter(minLevel = LogLevel.W))
        assertFalse(f.matches(entry(level = LogLevel.D)))
        assertFalse(f.matches(entry(level = LogLevel.I)))
        assertTrue(f.matches(entry(level = LogLevel.W)))
        assertTrue(f.matches(entry(level = LogLevel.E)))
    }

    @Test
    fun `plain query matches tag or message case-insensitively`() {
        val f = CompiledFilter(LogFilter(query = "network"))
        assertTrue(f.matches(entry(message = "network request failed")))
        assertTrue(f.matches(entry(tag = "NetworkManager")))
        assertFalse(f.matches(entry(message = "unrelated")))
    }

    @Test
    fun `exclude query drops matching entries`() {
        val f = CompiledFilter(LogFilter(excludeQuery = "spam"))
        assertFalse(f.matches(entry(message = "spam spam")))
        assertTrue(f.matches(entry(message = "useful info")))
    }

    @Test
    fun `tag query only matches the tag`() {
        val f = CompiledFilter(LogFilter(tagQuery = "Activity"))
        assertTrue(f.matches(entry(tag = "ActivityManager", message = "nothing")))
        assertFalse(f.matches(entry(tag = "Other", message = "Activity happened")))
    }

    @Test
    fun `regex mode matches patterns`() {
        val f = CompiledFilter(LogFilter(query = """code=\d{4}""", useRegex = true))
        assertTrue(f.matches(entry(message = "login failed code=4291")))
        assertFalse(f.matches(entry(message = "login failed code=abc")))
    }

    @Test
    fun `invalid regex fails open`() {
        val f = CompiledFilter(LogFilter(query = "[broken", useRegex = true))
        assertTrue(f.regexInvalid)
        assertTrue(f.matches(entry(message = "anything")))
    }

    @Test
    fun `uid filter matches only the resolved uid`() {
        val f = CompiledFilter(LogFilter(packageName = "com.example"), resolvedUid = 10123)
        assertTrue(f.matches(entry(uid = 10123)))
        assertFalse(f.matches(entry(uid = 10456)))
        assertFalse(f.matches(entry(uid = null)))
    }

    @Test
    fun `unresolvable package matches nothing`() {
        val f = CompiledFilter(LogFilter(packageName = "com.missing"), resolvedUid = null)
        assertFalse(f.matches(entry(uid = 10123)))
    }

    @Test
    fun `pid tid and uid tokens match exactly`() {
        val byPid = CompiledFilter(LogFilter(pid = "42"))
        assertTrue(byPid.matches(entry(pid = 42)))
        assertFalse(byPid.matches(entry(pid = 7)))

        val byTid = CompiledFilter(LogFilter(tid = "9"))
        assertTrue(byTid.matches(entry(tid = 9)))
        assertFalse(byTid.matches(entry(tid = 8)))

        val byName = CompiledFilter(LogFilter(uid = "system"))
        assertTrue(byName.matches(entry(uid = 1000)))
        assertFalse(byName.matches(entry(uid = 10123)))

        val byToken = CompiledFilter(LogFilter(uid = "u0a123"))
        assertTrue(byToken.matches(entry(uid = 10123)))

        val unknown = CompiledFilter(LogFilter(uid = "not-a-uid"))
        assertFalse(unknown.matches(entry(uid = 10123)))
        assertFalse(unknown.matches(entry(uid = null)))
    }
}
