package io.github.shiaho777.logsleuth.app.core.crashes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashNotifyThrottleTest {

    @Test
    fun `first crash notifies immediately`() {
        val t = CrashNotifyThrottle(windowMs = 60_000)
        val d = t.onCrash("CRASH|com.a", 1_000)
        assertTrue(d.notify)
        assertEquals(1, d.count)
    }

    @Test
    fun `a crash loop is merged inside the window`() {
        val t = CrashNotifyThrottle(windowMs = 60_000)
        t.onCrash("CRASH|com.a", 1_000)
        // The system process dies every 20 s — no notification per crash.
        assertFalse(t.onCrash("CRASH|com.a", 21_000).notify)
        assertFalse(t.onCrash("CRASH|com.a", 41_000).notify)
        val after = t.onCrash("CRASH|com.a", 61_000)
        assertTrue(after.notify)
        // Two suppressed crashes plus this one.
        assertEquals(3, after.count)
    }

    @Test
    fun `count resets after an announcement`() {
        val t = CrashNotifyThrottle(windowMs = 60_000)
        t.onCrash("CRASH|com.a", 0)
        t.onCrash("CRASH|com.a", 10_000)
        assertEquals(2, t.onCrash("CRASH|com.a", 60_000).count)
        assertEquals(1, t.onCrash("CRASH|com.a", 120_000).count)
    }

    @Test
    fun `different apps and types do not share a window`() {
        val t = CrashNotifyThrottle(windowMs = 60_000)
        assertTrue(t.onCrash("CRASH|com.a", 1_000).notify)
        assertTrue(t.onCrash("CRASH|com.b", 1_100).notify)
        assertTrue(t.onCrash("ANR|com.a", 1_200).notify)
        assertFalse(t.onCrash("CRASH|com.a", 1_300).notify)
    }
}