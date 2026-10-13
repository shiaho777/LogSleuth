package io.github.shiaho777.logsleuth.app.core.logcat

import io.github.shiaho777.logsleuth.app.core.crashes.CrashSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibleLineTest {

    private val raw =
        "10-13 12:00:00.123  1234  5678 I MyTag: hello from logcat"

    private fun entry() = LogcatEntry(
        timestampMillis = 1_700_000_000_000L,
        pid = 1234,
        tid = 5678,
        uid = 10123,
        level = LogLevel.I,
        tag = "MyTag",
        message = "hello from logcat",
        raw = raw,
    )

    @Test
    fun `time tag content omits uid and pid and is not the raw line`() {
        val formatted = formatVisible(
            entry(),
            VisibleColumns(
                showDate = false,
                showTime = true,
                showUid = false,
                showPid = false,
                showTid = false,
                showPackage = false,
                showTag = true,
            ),
        )
        assertFalse(formatted.contains("10123"))
        assertFalse(formatted.contains("1234"))
        assertTrue(formatted.contains("MyTag"))
        assertTrue(formatted.contains("hello from logcat"))
        assertTrue(formatted != raw)
    }

    @Test
    fun `all columns still differ from a threadtime raw line`() {
        val formatted = formatVisible(
            entry(),
            VisibleColumns(
                showDate = true,
                showTime = true,
                showUid = true,
                showPid = true,
                showTid = true,
                showPackage = true,
                showTag = true,
            ),
            appLabel = "Example",
        )
        assertTrue(formatted.contains("Example"))
        assertTrue(formatted.contains("MyTag"))
        assertTrue(formatted != raw)
        assertFalse(formatted.contains("  1234  5678 I "))
    }

    @Test
    fun `invalid date and time patterns fall back to the preset`() {
        val millis = 1_700_000_000_000L
        val date = DateTimePatterns.format(millis, "iii", DateTimePatterns.DATE_PRESET)
        val time = DateTimePatterns.format(millis, "iii", DateTimePatterns.TIME_PRESET)
        val dateOk = DateTimePatterns.format(millis, DateTimePatterns.DATE_PRESET, DateTimePatterns.DATE_PRESET)
        val timeOk = DateTimePatterns.format(millis, DateTimePatterns.TIME_PRESET, DateTimePatterns.TIME_PRESET)
        assertEquals(dateOk, date)
        assertEquals(timeOk, time)
        assertFalse(DateTimePatterns.isValid("iii"))
        assertTrue(DateTimePatterns.isValid(DateTimePatterns.TIME_PRESET))
    }

    @Test
    fun `package column copies the live row app label`() {
        val columns = VisibleColumns(
            showDate = false,
            showTime = false,
            showUid = false,
            showPid = false,
            showTid = false,
            showPackage = true,
            showTag = false,
        )
        val withLabel = formatVisible(entry(), columns, appLabel = "Maps")
        assertTrue(withLabel.startsWith("Maps "))
        val withoutLabel = formatVisible(entry(), columns, appLabel = null)
        assertFalse(withoutLabel.contains("Maps"))
        assertFalse(withoutLabel.startsWith("Maps"))
    }

    @Test
    fun `datetime and epoch copy the same time the row draws`() {
        val e = entry()
        val patternTime = DateTimePatterns.format(
            e.timestampMillis,
            DateTimePatterns.TIME_PRESET,
            DateTimePatterns.TIME_PRESET,
        )
        val timeOnly = VisibleColumns(
            showDate = false,
            showTime = true,
            showUid = false,
            showPid = false,
            showTid = false,
            showPackage = false,
            showTag = false,
        )
        val datetime = formatVisible(e, timeOnly.copy(timeFormat = "datetime"))
        assertTrue(datetime.startsWith(e.displayTime + " "))
        assertTrue(datetime.startsWith(formatLogTime(e, "datetime", DateTimePatterns.TIME_PRESET) + " "))
        assertFalse(datetime.startsWith(patternTime + " "))

        val epoch = formatVisible(e, timeOnly.copy(timeFormat = "epoch"))
        assertTrue(epoch.startsWith("${e.timestampMillis} "))
        assertEquals(
            formatLogTime(e, "epoch", DateTimePatterns.TIME_PRESET),
            e.timestampMillis.toString(),
        )

        val time = formatVisible(e, timeOnly.copy(timeFormat = "time"))
        assertTrue(time.startsWith(patternTime + " "))
        assertEquals(patternTime, formatLogTime(e, "time", DateTimePatterns.TIME_PRESET))
    }

    @Test
    fun `crash search finds every range case-insensitively and case-sensitively`() {
        val stack = "FATAL EXCEPTION: main\njava.lang.NullPointerException\nFATAL again"
        val insensitive = CrashSearch.matchRanges(stack, "fatal", ignoreCase = true)
        assertEquals(2, insensitive.size)
        assertEquals("FATAL", stack.substring(insensitive[0].first, insensitive[0].last + 1))
        assertEquals("FATAL", stack.substring(insensitive[1].first, insensitive[1].last + 1))

        val sensitive = CrashSearch.matchRanges(stack, "FATAL", ignoreCase = false)
        assertEquals(2, sensitive.size)
        val missed = CrashSearch.matchRanges(stack, "fatal", ignoreCase = false)
        assertTrue(missed.isEmpty())
    }

    @Test
    fun `crash search highlights snippet and surrounding context independently`() {
        val snippet = "FATAL EXCEPTION: main\njava.lang.NullPointerException"
        val context = "I ActivityManager: Process died\nE AndroidRuntime: FATAL extra"
        val fatalHits = CrashSearch.search(snippet, context, "FATAL", ignoreCase = false)
        assertEquals(1, fatalHits.snippetRanges.size)
        assertEquals(1, fatalHits.contextRanges.size)
        assertEquals(
            "FATAL",
            snippet.substring(fatalHits.snippetRanges[0].first, fatalHits.snippetRanges[0].last + 1),
        )
        assertEquals(
            "FATAL",
            context.substring(fatalHits.contextRanges[0].first, fatalHits.contextRanges[0].last + 1),
        )

        val snippetOnly = CrashSearch.search(snippet, context, "NullPointer", ignoreCase = false)
        assertEquals(1, snippetOnly.snippetRanges.size)
        assertTrue(snippetOnly.contextRanges.isEmpty())

        val contextOnly = CrashSearch.search(snippet, context, "Process died", ignoreCase = false)
        assertTrue(contextOnly.snippetRanges.isEmpty())
        assertEquals(1, contextOnly.contextRanges.size)
        assertEquals(
            "Process died",
            context.substring(contextOnly.contextRanges[0].first, contextOnly.contextRanges[0].last + 1),
        )
    }
}
