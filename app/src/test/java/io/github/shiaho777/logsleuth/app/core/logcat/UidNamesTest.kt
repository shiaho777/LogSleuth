package io.github.shiaho777.logsleuth.app.core.logcat

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UidNamesTest {

    @Test
    fun `resolves decimals names and multi-user tokens`() {
        assertEquals(10123, UidNames.resolve("10123"))
        assertEquals(10123, UidNames.resolve("u0a123"))
        assertEquals(10123, UidNames.resolve("u0_a123"))
        assertEquals(1_010_005, UidNames.resolve("u10_a5"))
        assertEquals(99015, UidNames.resolve("u0i15"))
        assertEquals(199015, UidNames.resolve("u1_i15"))
        assertEquals(1000, UidNames.resolve("system"))
        assertEquals(2000, UidNames.resolve("shell"))
        assertNull(UidNames.resolve("not-a-uid"))
        assertNull(UidNames.resolve(""))
    }

    @Test
    fun `well known name round trips`() {
        assertEquals("system", UidNames.wellKnownName(1000))
        assertEquals("root", UidNames.wellKnownName(0))
        assertNull(UidNames.wellKnownName(10123))
    }

    @Test
    fun `threadtime stamp matches logcat -T`() {
        val millis = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 8, 7, 6)
            set(Calendar.MILLISECOND, 9)
        }.timeInMillis
        val stamp = UidNames.formatThreadTime(millis)
        assertEquals("10-10 08:07:06.009", stamp)
        assertTrue(stamp.matches(Regex("""\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}""")))
    }

    private fun millisOf(year: Int, month: Int, day: Int, h: Int, m: Int, s: Int, ms: Int): Long =
        GregorianCalendar().apply {
            set(year, month, day, h, m, s)
            set(Calendar.MILLISECOND, ms)
        }.timeInMillis

    @Test
    fun `reconnect stamp carries the year so a resume survives New Year`() {
        val stamp = UidNames.formatSince(millisOf(2026, Calendar.JANUARY, 1, 0, 0, 1, 42))
        assertEquals("2026-01-01 00:00:01.042", stamp)
        assertTrue(stamp.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}""")))
    }

    @Test
    fun `reconnect stamp stays Gregorian and ASCII under other locales`() {
        val millis = millisOf(2026, Calendar.JANUARY, 1, 0, 0, 1, 42)
        val original = Locale.getDefault()
        try {
            // th-TH defaults to the Buddhist calendar, ar-EG to its own digits.
            Locale.setDefault(Locale.forLanguageTag("th-TH"))
            assertEquals("2026-01-01 00:00:01.042", UidNames.formatSince(millis))
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("2026-01-01 00:00:01.042", UidNames.formatSince(millis))
        } finally {
            Locale.setDefault(original)
        }
    }
}
