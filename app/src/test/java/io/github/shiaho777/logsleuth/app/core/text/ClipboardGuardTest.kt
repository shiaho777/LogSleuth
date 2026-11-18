package io.github.shiaho777.logsleuth.app.core.text

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardGuardTest {

    @Test
    fun `text at the cap fits and one char past it does not`() {
        assertTrue(ClipboardGuard.fits("x".repeat(ClipboardGuard.MAX_CLIPBOARD_CHARS)))
        assertFalse(ClipboardGuard.fits("x".repeat(ClipboardGuard.MAX_CLIPBOARD_CHARS + 1)))
    }

    @Test
    fun `empty and small text fit`() {
        assertTrue(ClipboardGuard.fits(""))
        assertTrue(ClipboardGuard.fits("one log line"))
    }
}