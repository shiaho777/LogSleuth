package io.github.shiaho777.logsleuth.app.core.logcat

import java.util.Calendar
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
}
