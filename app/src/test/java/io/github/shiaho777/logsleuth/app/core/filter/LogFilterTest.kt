package io.github.shiaho777.logsleuth.app.core.filter

import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import org.junit.Assert.assertEquals
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
        assertTrue(f.matches(entry(level = LogLevel.F)))
    }

    @Test
    fun `error-only keeps E and drops W and F`() {
        val f = CompiledFilter(LogFilter(allowedLevels = setOf(LogLevel.E)))
        assertFalse(f.matches(entry(level = LogLevel.W)))
        assertTrue(f.matches(entry(level = LogLevel.E)))
        assertFalse(f.matches(entry(level = LogLevel.F)))
    }

    @Test
    fun `warn plus error without fatal keeps W and E and drops F`() {
        val f = CompiledFilter(LogFilter(allowedLevels = setOf(LogLevel.W, LogLevel.E)))
        assertTrue(f.matches(entry(level = LogLevel.W)))
        assertTrue(f.matches(entry(level = LogLevel.E)))
        assertFalse(f.matches(entry(level = LogLevel.F)))
        assertFalse(f.matches(entry(level = LogLevel.I)))
    }

    @Test
    fun `legacy minLevel W still admits W and above`() {
        val rule = LogFilter(minLevel = LogLevel.W, allowedLevels = emptySet())
        assertTrue(rule.effectiveLevels.isNotEmpty())
        val f = CompiledFilter(rule)
        assertFalse(f.matches(entry(level = LogLevel.I)))
        assertTrue(f.matches(entry(level = LogLevel.W)))
        assertTrue(f.matches(entry(level = LogLevel.E)))
        assertTrue(f.matches(entry(level = LogLevel.F)))
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
    fun `exact tag keeps AndroidRuntime and drops a superstring`() {
        val f = CompiledFilter(
            LogFilter(tagQuery = "AndroidRuntime", tagExact = true),
        )
        assertTrue(f.matches(entry(tag = "AndroidRuntime")))
        assertTrue(f.matches(entry(tag = "androidruntime")))
        assertFalse(f.matches(entry(tag = "AndroidRuntimeSomething")))
    }

    @Test
    fun `contains tag still matches a superstring`() {
        val f = CompiledFilter(
            LogFilter(tagQuery = "AndroidRuntime", tagExact = false),
        )
        assertTrue(f.matches(entry(tag = "AndroidRuntimeSomething")))
    }

    @Test
    fun `regex mode matches patterns`() {
        val f = CompiledFilter(LogFilter(query = """code=\d{4}""", useRegex = true))
        assertTrue(f.matches(entry(message = "login failed code=4291")))
        assertFalse(f.matches(entry(message = "login failed code=abc")))
    }

    @Test
    fun `invalid regex fails closed`() {
        val f = CompiledFilter(LogFilter(query = "[broken", useRegex = true))
        assertTrue(f.regexInvalid)
        assertFalse(f.matches(entry(message = "anything")))
    }

    @Test
    fun `invalid exclude regex does not silently pass everything`() {
        val f = CompiledFilter(LogFilter(excludeQuery = "[broken", useRegex = true))
        assertTrue(f.regexInvalid)
        assertFalse(f.matches(entry(message = "anything")))
    }

    @Test
    fun `invalid tag regex fails closed`() {
        val f = CompiledFilter(LogFilter(tagQuery = "[broken", useRegex = true))
        assertTrue(f.regexInvalid)
        assertFalse(f.matches(entry(tag = "anything")))
    }

    @Test
    fun `regex validity helper accepts blank and rejects broken patterns`() {
        assertFalse(CompiledFilter.isInvalidRegex(""))
        assertFalse(CompiledFilter.isInvalidRegex("  "))
        assertFalse(CompiledFilter.isInvalidRegex("""code=\d+"""))
        assertTrue(CompiledFilter.isInvalidRegex("[broken"))
    }

    @Test
    fun `multi-word query requires every word`() {
        val f = CompiledFilter(LogFilter(query = "timeout retry"))
        assertTrue(f.matches(entry(message = "retry after timeout")))
        assertTrue(f.matches(entry(tag = "RetryManager", message = "timeout")))
        assertFalse(f.matches(entry(message = "timeout only")))
        assertFalse(f.matches(entry(message = "retry only")))
    }

    @Test
    fun `single-word query keeps substring behavior`() {
        val f = CompiledFilter(LogFilter(query = "network"))
        assertTrue(f.matches(entry(message = "the network is down")))
    }

    @Test
    fun `multi-word exclude drops only lines carrying every word`() {
        val f = CompiledFilter(LogFilter(excludeQuery = "spam bot"))
        assertFalse(f.matches(entry(message = "spam from bot")))
        assertTrue(f.matches(entry(message = "spam only")))
        assertTrue(f.matches(entry(message = "bot only")))
    }

    @Test
    fun `empty tag token selects only empty-tag lines`() {
        val f = CompiledFilter(LogFilter(tagQuery = CompiledFilter.EMPTY_TAG_TOKEN))
        assertTrue(f.matches(entry(tag = "")))
        assertFalse(f.matches(entry(tag = "MyTag")))
        assertFalse(f.matches(entry(tag = "(empty)")))
    }

    @Test
    fun `blank tag still means no constraint`() {
        val f = CompiledFilter(LogFilter(tagQuery = ""))
        assertTrue(f.matches(entry(tag = "")))
        assertTrue(f.matches(entry(tag = "MyTag")))
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

    @Test
    fun `fromLine prefills tag pid tid uid package and a single level`() {
        val longMsg = "x".repeat(200)
        val rule = LogFilter.fromLine(
            tag = "AndroidRuntime",
            message = longMsg,
            pid = 42,
            tid = 7,
            uid = 10123,
            level = LogLevel.E,
            packageName = "com.example.app",
        )
        assertEquals("AndroidRuntime", rule.name)
        assertEquals("AndroidRuntime", rule.tagQuery)
        assertTrue(rule.tagExact)
        assertEquals("42", rule.pid)
        assertEquals("7", rule.tid)
        assertEquals("10123", rule.uid)
        assertEquals("com.example.app", rule.packageName)
        assertEquals(setOf(LogLevel.E), rule.effectiveLevels)
        assertEquals(120, rule.query.length)
        assertTrue(rule.enabled)
        assertTrue(rule.including)
        val compiled = CompiledFilter(rule, resolvedUid = 10123)
        val matching = entry(
            level = LogLevel.E,
            tag = "AndroidRuntime",
            message = longMsg,
            pid = 42,
            tid = 7,
            uid = 10123,
        )
        assertTrue(compiled.matches(matching))
        assertFalse(compiled.matches(matching.copy(level = LogLevel.W)))
        assertFalse(compiled.matches(matching.copy(tag = "Other")))
        assertFalse(compiled.matches(matching.copy(tid = 8)))
    }
}
