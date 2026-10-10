package io.github.shiaho777.logsleuth.app.core.logcat

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessPolicyTest {

    @Test
    fun `auto keeps Shizuku ahead of root and the adb grant`() {
        assertEquals(
            AccessKind.SHIZUKU,
            AccessPolicy.choose("auto", shizukuReady = true, rootReady = true, readLogs = true),
        )
    }

    @Test
    fun `auto uses root when Shizuku is down`() {
        assertEquals(
            AccessKind.ROOT,
            AccessPolicy.choose("auto", shizukuReady = false, rootReady = true, readLogs = true),
        )
    }

    @Test
    fun `auto falls through to the adb grant`() {
        assertEquals(
            AccessKind.READ_LOGS,
            AccessPolicy.choose("auto", shizukuReady = false, rootReady = false, readLogs = true),
        )
        assertEquals(
            AccessKind.NONE,
            AccessPolicy.choose("auto", shizukuReady = false, rootReady = false, readLogs = false),
        )
    }

    @Test
    fun `an unknown preference behaves as auto`() {
        assertEquals(
            AccessKind.ROOT,
            AccessPolicy.choose("nope", shizukuReady = false, rootReady = true, readLogs = true),
        )
    }

    @Test
    fun `root preference wins when the shell is ready`() {
        assertEquals(
            AccessKind.ROOT,
            AccessPolicy.choose("root", shizukuReady = true, rootReady = true, readLogs = true),
        )
    }

    @Test
    fun `root preference falls through when root is not granted`() {
        assertEquals(
            AccessKind.SHIZUKU,
            AccessPolicy.choose("root", shizukuReady = true, rootReady = false, readLogs = true),
        )
    }

    @Test
    fun `adb preference keeps the adb grant ahead of the shells`() {
        assertEquals(
            AccessKind.READ_LOGS,
            AccessPolicy.choose("adb", shizukuReady = true, rootReady = true, readLogs = true),
        )
        assertEquals(
            AccessKind.SHIZUKU,
            AccessPolicy.choose("adb", shizukuReady = true, rootReady = true, readLogs = false),
        )
    }

    @Test
    fun `shizuku preference falls through to root`() {
        assertEquals(
            AccessKind.ROOT,
            AccessPolicy.choose("shizuku", shizukuReady = false, rootReady = true, readLogs = true),
        )
    }
}
