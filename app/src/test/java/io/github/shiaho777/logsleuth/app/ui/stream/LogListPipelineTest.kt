package io.github.shiaho777.logsleuth.app.ui.stream

import io.github.shiaho777.logsleuth.app.core.filter.FilterStack
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pipeline owns ordering, trimming and the paused backlog. The harness
 * below applies its [ListEdit]s to a plain list the way the screen applies them
 * to the Compose list, so the assertions see exactly what the user would.
 */
class LogListPipelineTest {

    private class Harness(cap: Int = 20_000) {
        val shown = mutableListOf<UiLogEntry>()
        var pausedIncoming = 0
        var filteredOut = false
        var regexInvalid: Boolean? = null
        var edits = 0

        val pipeline = LogListPipeline(
            emit = { edit ->
                edits++
                when (edit) {
                    is ListEdit.Replace -> {
                        shown.clear()
                        shown.addAll(edit.items)
                    }
                    is ListEdit.Append -> {
                        val drop = edit.dropPrefix.coerceAtMost(shown.size)
                        if (drop > 0) shown.subList(0, drop).clear()
                        shown.addAll(edit.items)
                    }
                }
                filteredOut = edit.filteredOut
            },
            onPausedIncoming = { pausedIncoming = it },
            onRegexInvalid = { regexInvalid = it },
        ).also { it.setCap(cap) }

        /** Timestamps of the rows on screen — each fixture line has a unique one. */
        fun stamps(): List<Long> = shown.map { it.entry.timestampMillis }
    }

    private fun line(
        stamp: Long,
        tag: String = "Tag",
        message: String = "message $stamp",
        uid: Int? = 1000,
    ) = LogcatEntry(
        timestampMillis = stamp,
        pid = 1,
        tid = 1,
        uid = uid,
        level = LogLevel.I,
        tag = tag,
        message = message,
        raw = "raw $stamp",
        seq = stamp,
    )

    private fun rules(filter: LogFilter = LogFilter.DEFAULT) =
        FilterRules(filter, FilterStack.PASS)

    @Test
    fun `snapshot keeps matching rows and assigns increasing ids`() = runTest {
        val harness = Harness()
        val entries = (1L..4L).map { line(it) }

        harness.pipeline.snapshot(entries, { rules() })

        assertEquals(entries.map { it.timestampMillis }, harness.stamps())
        assertEquals(listOf(0L, 1L, 2L, 3L), harness.shown.map { it.seq })
    }

    @Test
    fun `append publishes only the rows the filter admits`() = runTest {
        val harness = Harness()
        val filter = LogFilter(query = "keep")

        val published = harness.pipeline.append(
            listOf(line(1, message = "keep one"), line(2, message = "drop"), line(3, message = "keep two")),
        ) { rules(filter) }

        assertTrue(published)
        assertEquals(listOf(1L, 3L), harness.stamps())
        assertFalse(harness.filteredOut)
    }

    @Test
    fun `overflow publishes the prefix the screen has to drop`() = runTest {
        val harness = Harness(cap = 3)
        harness.pipeline.snapshot((1L..3L).map { line(it) }, { rules() })

        val published = harness.pipeline.append(listOf(line(4), line(5))) { rules() }

        assertTrue(published)
        assertEquals(listOf(3L, 4L, 5L), harness.stamps())
    }

    @Test
    fun `a later cap applies on the next eviction`() = runTest {
        val harness = Harness(cap = 10)
        harness.pipeline.snapshot((1L..5L).map { line(it) }, { rules() })

        harness.pipeline.setCap(3)
        harness.pipeline.append(listOf(line(6))) { rules() }

        assertEquals(listOf(4L, 5L, 6L), harness.stamps())
    }

    @Test
    fun `paused rows wait in the backlog and only admitted ones are counted`() = runTest {
        val harness = Harness()
        val filter = LogFilter(query = "keep")
        harness.pipeline.pause()

        val published = harness.pipeline.append(
            listOf(line(1, message = "keep"), line(2, message = "drop"), line(3, message = "keep")),
        ) { rules(filter) }

        assertFalse(published)
        assertEquals(emptyList<Long>(), harness.stamps())
        assertEquals(2, harness.pausedIncoming)
    }

    @Test
    fun `resume publishes the backlog before a newer batch`() = runTest {
        val harness = Harness()
        harness.pipeline.pause()
        harness.pipeline.append(listOf(line(1))) { rules() }

        harness.pipeline.markRunning()
        // The drain can beat the scheduled flush. The older backlog must not
        // be overtaken by this batch.
        harness.pipeline.append(listOf(line(2))) { rules() }

        assertEquals(listOf(1L, 2L), harness.stamps())
        assertFalse(harness.pipeline.flushPending { rules() })
    }

    @Test
    fun `flush publishes the backlog when the stream already ran`() = runTest {
        val harness = Harness()
        harness.pipeline.pause()
        harness.pipeline.append(listOf(line(1), line(2))) { rules() }

        harness.pipeline.markRunning()
        assertTrue(harness.pipeline.flushPending { rules() })

        assertEquals(listOf(1L, 2L), harness.stamps())
        assertEquals(0, harness.pausedIncoming)
    }

    @Test
    fun `pausing again keeps the backlog for the next resume`() = runTest {
        val harness = Harness()
        harness.pipeline.pause()
        harness.pipeline.append(listOf(line(1))) { rules() }

        harness.pipeline.markRunning()
        harness.pipeline.pause()
        assertFalse(harness.pipeline.flushPending { rules() })
        assertEquals(emptyList<Long>(), harness.stamps())
        assertEquals(0, harness.pausedIncoming)

        harness.pipeline.markRunning()
        assertTrue(harness.pipeline.flushPending { rules() })
        assertEquals(listOf(1L), harness.stamps())
    }

    @Test
    fun `pausing again restarts the banner count`() = runTest {
        val harness = Harness()
        harness.pipeline.pause()
        harness.pipeline.append(listOf(line(1))) { rules() }
        assertEquals(1, harness.pausedIncoming)

        harness.pipeline.pause()
        assertEquals(0, harness.pausedIncoming)

        harness.pipeline.append(listOf(line(2))) { rules() }
        assertEquals(1, harness.pausedIncoming)
    }

    @Test
    fun `clear drops rows and the paused backlog`() = runTest {
        val harness = Harness()
        harness.pipeline.snapshot(listOf(line(1)), { rules() })
        harness.pipeline.pause()
        harness.pipeline.append(listOf(line(2))) { rules() }

        harness.pipeline.clear()

        assertEquals(emptyList<Long>(), harness.stamps())
        assertEquals(0, harness.pausedIncoming)
        assertFalse(harness.filteredOut)
        // Ids stay monotonic across a clear so a stale key can never match.
        harness.pipeline.markRunning()
        harness.pipeline.snapshot(listOf(line(3)), { rules() })
        assertTrue(harness.shown.single().seq > 0)
    }

    @Test
    fun `removing apps drops rows and recounts the paused banner`() = runTest {
        val harness = Harness()
        harness.pipeline.snapshot(
            listOf(line(1, uid = 1000), line(2, uid = 2000), line(3, uid = 2000)),
            { rules() },
        )
        harness.pipeline.pause()
        harness.pipeline.append(listOf(line(4, uid = 1000), line(5, uid = 2000))) { rules() }
        assertEquals(2, harness.pausedIncoming)

        harness.pipeline.removeUids(setOf(2000)) { rules() }

        assertEquals(listOf(1L), harness.stamps())
        assertEquals(1, harness.pausedIncoming)
        assertEquals(listOf(1L, 4L), harness.pipeline.entriesForSave(null).map { it.timestampMillis })
    }

    @Test
    fun `refilter rebuilds the visible rows from the newest rules`() = runTest {
        val harness = Harness()
        harness.pipeline.snapshot((1L..3L).map { line(it, message = "keep $it") }, { rules() })

        harness.pipeline.refilter { rules(LogFilter(query = "keep 2")) }

        assertEquals(listOf(2L), harness.stamps())
        assertEquals(false, harness.regexInvalid)

        harness.pipeline.refilter { rules(LogFilter(query = "[", useRegex = true)) }

        // A bad regex fails open, so every row stays visible, and the flag
        // reaches the caller so the bar can warn.
        assertEquals(listOf(1L, 2L, 3L), harness.stamps())
        assertEquals(true, harness.regexInvalid)
    }

    @Test
    fun `refilter recounts what is waiting while paused`() = runTest {
        val harness = Harness()
        harness.pipeline.pause()
        harness.pipeline.append(
            listOf(
                line(1, message = "keep one"),
                line(2, message = "keep two"),
                line(3, message = "drop"),
            ),
        ) { rules(LogFilter(query = "keep")) }
        assertEquals(2, harness.pausedIncoming)

        harness.pipeline.refilter { rules(LogFilter(query = "drop")) }

        assertEquals(1, harness.pausedIncoming)
        assertEquals(emptyList<Long>(), harness.stamps())
    }

    @Test
    fun `entries for save include the backlog and honour the uid subset`() = runTest {
        val harness = Harness()
        harness.pipeline.append(listOf(line(1, uid = 1000), line(2, uid = 2000))) { rules() }
        harness.pipeline.pause()
        harness.pipeline.append(listOf(line(3, uid = 1000), line(4, uid = 2000))) { rules() }

        assertEquals(
            listOf(1L, 2L, 3L, 4L),
            harness.pipeline.entriesForSave(null).map { it.timestampMillis },
        )
        assertEquals(
            listOf(1L, 3L),
            harness.pipeline.entriesForSave(setOf(1000)).map { it.timestampMillis },
        )
    }

    @Test
    fun `hits are row ids and survive an eviction`() = runTest {
        val harness = Harness(cap = 4)
        harness.pipeline.snapshot(
            listOf(
                line(1, tag = "Needle"),
                line(2, message = "needle"),
                line(3, message = "other"),
            ),
            { rules() },
        )
        val before = harness.pipeline.hits("needle", ignoreCase = true)
        assertEquals(harness.shown.take(2).map { it.seq }, before)

        // Evict the first row; the surviving hit's id is unchanged.
        harness.pipeline.append(listOf(line(4), line(5, message = "needle"))) { rules() }

        val after = harness.pipeline.hits("needle", ignoreCase = true)
        assertEquals(listOf(before[1]) + harness.shown.last().seq, after)
        assertEquals(emptyList<Long>(), harness.pipeline.hits("   ", ignoreCase = true))
    }

    @Test
    fun `filtered-out flips only when the state changes`() = runTest {
        val harness = Harness()
        val hideAll = LogFilter(query = "none")

        assertTrue(harness.pipeline.append(listOf(line(1))) { rules(hideAll) })
        assertTrue(harness.filteredOut)
        assertTrue(harness.shown.isEmpty())

        // Still nothing visible: no new edit, so no needless recompose.
        assertFalse(harness.pipeline.append(listOf(line(2))) { rules(hideAll) })

        assertTrue(harness.pipeline.append(listOf(line(3, message = "none"))) { rules(hideAll) })
        assertFalse(harness.filteredOut)
        assertEquals(listOf(3L), harness.stamps())
    }

    @Test
    fun `an empty snapshot with a filter is not filtered out`() = runTest {
        val harness = Harness()

        harness.pipeline.snapshot(emptyList()) { rules(LogFilter(query = "none")) }

        assertFalse(harness.filteredOut)
        assertEquals(emptyList<Long>(), harness.stamps())
    }
}
