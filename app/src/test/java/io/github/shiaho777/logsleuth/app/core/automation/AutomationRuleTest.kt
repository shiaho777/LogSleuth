package io.github.shiaho777.logsleuth.app.core.automation

import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRuleTest {

    private fun entry(
        tag: String = "MyTag",
        message: String = "hello world",
        uid: Int? = 10123,
    ) = LogcatEntry(
        timestampMillis = 1_000, pid = 42, tid = 42, uid = uid,
        level = LogLevel.E, tag = tag, message = message, raw = "RAW $message",
    )

    private fun compile(rule: AutomationRule) = CompiledAutomationRule(rule)

    @Test
    fun `plain query matches tag or message`() {
        val r = compile(AutomationRule(query = "world", targetPackage = "net.dinglisch.android.taskerm"))
        assertTrue(r.matches(entry(), null))
        assertTrue(r.matches(entry(tag = "worldly"), null))
        assertFalse(r.matches(entry(message = "nothing"), null))
    }

    @Test
    fun `regex query works and invalid regex fails closed`() {
        val ok = compile(AutomationRule(query = """w\w+rld""", useRegex = true, targetPackage = "t"))
        assertTrue(ok.matches(entry(), null))
        val bad = compile(AutomationRule(query = "[broken", useRegex = true, targetPackage = "t"))
        assertTrue(bad.regexInvalid)
        assertFalse(bad.matches(entry(), null))
    }

    @Test
    fun `package constraint requires the resolved package`() {
        val r = compile(AutomationRule(packageName = "com.example.a", targetPackage = "t"))
        assertTrue(r.matches(entry(), "com.example.a"))
        assertFalse(r.matches(entry(), "com.example.b"))
        assertFalse(r.matches(entry(), null))
    }

    @Test
    fun `tag constraint is a substring match`() {
        val r = compile(AutomationRule(tagQuery = "mytag", targetPackage = "t"))
        assertTrue(r.matches(entry(tag = "MyTag"), null))
        assertFalse(r.matches(entry(tag = "Other"), null))
    }

    @Test
    fun `empty rule matches everything`() {
        val r = compile(AutomationRule(targetPackage = "t"))
        assertTrue(r.matches(entry(), null))
    }

    @Test
    fun `payload carries identity fields and caps text`() {
        val rule = AutomationRule(name = "r1", targetPackage = "t")
        val long = "x".repeat(AutomationRule.MAX_TEXT + 500)
        val extras = AutomationPayload.extras(rule, entry(message = long), "com.example.a")
        assertEquals("r1", extras[AutomationRule.EXTRA_RULE_NAME])
        assertEquals("com.example.a", extras[AutomationRule.EXTRA_PACKAGE])
        assertEquals("E", extras[AutomationRule.EXTRA_LEVEL])
        assertEquals(42, extras[AutomationRule.EXTRA_PID])
        assertEquals(10123, extras[AutomationRule.EXTRA_UID])
        assertEquals(1_000L, extras[AutomationRule.EXTRA_TIME])
        assertEquals(AutomationRule.MAX_TEXT, (extras[AutomationRule.EXTRA_MESSAGE] as String).length)
        assertEquals(AutomationRule.MAX_TEXT, (extras[AutomationRule.EXTRA_RAW] as String).length)
    }

    @Test
    fun `missing uid maps to minus one`() {
        val extras = AutomationPayload.extras(AutomationRule(targetPackage = "t"), entry(uid = null), null)
        assertEquals(-1, extras[AutomationRule.EXTRA_UID])
        assertEquals("", extras[AutomationRule.EXTRA_PACKAGE])
    }
}