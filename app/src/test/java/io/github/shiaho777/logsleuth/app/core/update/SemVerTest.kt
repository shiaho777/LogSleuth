package io.github.shiaho777.logsleuth.app.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun `parses tag and version spellings`() {
        assertTrue(SemVer.parse("v0.2.1")!!.contentEquals(intArrayOf(0, 2, 1)))
        assertTrue(SemVer.parse("0.2.1")!!.contentEquals(intArrayOf(0, 2, 1)))
        // Debug build's "-debug" suffix is stripped.
        assertTrue(SemVer.parse("0.2.1-debug")!!.contentEquals(intArrayOf(0, 2, 1)))
        // Short forms zero-pad.
        assertTrue(SemVer.parse("1.0")!!.contentEquals(intArrayOf(1, 0, 0)))
    }

    @Test
    fun `rejects non-semver input`() {
        assertNull(SemVer.parse("latest"))
        assertNull(SemVer.parse("v0.2.x"))
        assertNull(SemVer.parse(""))
    }

    @Test
    fun `compare orders versions`() {
        assertEquals(1, SemVer.compare("v0.3.0", "0.2.1"))
        assertEquals(-1, SemVer.compare("v0.2.1", "0.3.0"))
        assertEquals(0, SemVer.compare("v0.2.1", "0.2.1-debug"))
        assertEquals(1, SemVer.compare("1.0.0", "0.99.9"))
    }

    @Test
    fun `isNewer only fires on a strictly newer tag`() {
        assertTrue(SemVer.isNewer("v0.3.0", "0.2.1-debug"))
        assertFalse(SemVer.isNewer("v0.2.1", "0.2.1-debug"))
        assertFalse(SemVer.isNewer("v0.2.0", "0.2.1"))
        // Unparseable tags never claim an update exists.
        assertFalse(SemVer.isNewer("nightly", "0.2.1"))
    }
}
