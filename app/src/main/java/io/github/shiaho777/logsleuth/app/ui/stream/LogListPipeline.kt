package io.github.shiaho777.logsleuth.app.ui.stream

import io.github.shiaho777.logsleuth.app.core.filter.CompiledFilter
import io.github.shiaho777.logsleuth.app.core.filter.FilterStack
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One queued change to the on-screen list. */
internal sealed interface ListEdit {
    val filteredOut: Boolean

    data class Replace(
        val items: List<UiLogEntry>,
        override val filteredOut: Boolean,
    ) : ListEdit

    data class Append(
        val dropPrefix: Int,
        val items: List<UiLogEntry>,
        override val filteredOut: Boolean,
    ) : ListEdit
}

/**
 * The filter rules a rebuild should use, compiled once. Supplied by the caller
 * *inside* the pipeline lock, so the rules a rebuild applies are the newest
 * ones at the moment it runs.
 */
internal class FilterRules(
    filter: LogFilter,
    private val stack: FilterStack,
    resolvedUid: Int? = null,
) {
    private val compiled: CompiledFilter = CompiledFilter(filter, resolvedUid)

    /** True when either the quick filter or a saved rule has a bad regex. */
    val regexInvalid: Boolean = compiled.regexInvalid || stack.regexInvalid

    fun accepts(entry: LogcatEntry): Boolean =
        compiled.matches(entry) && stack.matches(entry)
}

/**
 * Owns the three lists behind the live stream: everything received, the rows
 * the active filter admits, and the rows that arrived while paused. Pure
 * Kotlin and serialized by its own lock, so ordering, trimming and the paused
 * backlog can be unit tested without a ViewModel.
 *
 * The caller mirrors [ListEdit]s onto the Compose list and the callbacks onto
 * its UI state; this class never touches either directly. Callbacks run while
 * the lock is held, so they must not suspend or call back into the pipeline.
 *
 * Pause/resume follows the chat-list model:
 * - pausing keeps every new line in `pending` and counts the ones the filter
 *   admits for the banner;
 * - resuming flushes that backlog; a batch drained while the backlog is still
 *   waiting is published after it, so lines never overtake older ones;
 * - a drain reads `paused` under the same lock as the flush, so a batch can
 *   never be stranded in `pending` after the flush emptied it.
 */
internal class LogListPipeline(
    private val emit: (ListEdit) -> Unit,
    private val onPausedIncoming: (Int) -> Unit,
    private val onRegexInvalid: (Boolean) -> Unit,
) {
    private val mutex = Mutex()

    private val all = ArrayList<UiLogEntry>()
    private var visible = ArrayList<UiLogEntry>()
    private val pending = ArrayList<LogcatEntry>()

    /** Monotonic across clears; the on-screen list uses it as its key. */
    private var seq = 0L

    /**
     * Rows kept while paused. Read under [mutex] by [append]; written by
     * [pause], which runs on the caller's thread so the flag flips before the
     * flush that follows a resume is scheduled.
     */
    @Volatile
    var paused: Boolean = false
        private set

    @Volatile
    var cap: Int = 20_000
        private set

    private var pausedIncoming = 0
    private var lastFilteredOut = false

    fun setCap(value: Int) {
        cap = value
    }

    /** Starts the pause. The count resets; the banner starts from zero. */
    fun pause() {
        paused = true
        pausedIncoming = 0
        onPausedIncoming(0)
    }

    /** Ends the pause immediately; [flushPending] moves the backlog afterwards. */
    fun markRunning() {
        paused = false
        pausedIncoming = 0
        onPausedIncoming(0)
    }

    /** Rebuilds both lists from the engine snapshot (cold start / warm attach). */
    suspend fun snapshot(entries: List<LogcatEntry>, rules: () -> FilterRules) = mutex.withLock {
        val filters = rules()
        all.clear()
        visible.clear()
        for (e in entries) {
            val row = UiLogEntry(seq++, e)
            all.add(row)
            if (filters.accepts(e)) visible.add(row)
        }
        evictOverflow(all, visible, cap)
        publishReplaceLocked()
    }

    /**
     * Adds one drained batch. Returns true when an edit was published (the
     * caller re-indexes search hits then).
     */
    suspend fun append(batch: List<LogcatEntry>, rules: () -> FilterRules): Boolean = mutex.withLock {
        var matchedWhilePaused = 0
        // Evaluated under the lock: a refilter that ran between reading the
        // rules and locking would otherwise append this batch under the rule
        // it replaced.
        val filters = rules()
        // Read under the lock: resume flips `paused` first and then flushes
        // `pending` under this lock, so a batch either lands before that flush
        // (and is flushed with it) or sees the stream running.
        val pausedNow = paused
        // Resumed, but the backlog flush has not run yet: everything in
        // `pending` is older than this batch, so publish it first rather than
        // letting the new lines overtake it.
        val incoming = if (!pausedNow && pending.isNotEmpty()) {
            ArrayList<LogcatEntry>(pending.size + batch.size).apply {
                addAll(pending)
                addAll(batch)
            }.also { pending.clear() }
        } else {
            batch
        }
        val sizeBefore = visible.size
        val appended = ArrayList<UiLogEntry>()
        for (e in incoming) {
            if (pausedNow) {
                // Keep every line — the filter can change while paused and must
                // still be able to reveal them — but only the ones the active
                // filter admits are "new lines" to the user, so only those
                // count toward the banner.
                if (filters.accepts(e)) matchedWhilePaused++
                pending.add(e)
                continue
            }
            val row = UiLogEntry(seq++, e)
            all.add(row)
            if (filters.accepts(e)) {
                visible.add(row)
                appended.add(row)
            }
        }
        // Paused entries are bounded too: without this, pausing during a log
        // storm grows `pending` without limit.
        var pendingTrimmed = false
        if (pending.size > cap) {
            pending.subList(0, pending.size - cap).clear()
            pendingTrimmed = true
        }
        if (pausedNow) {
            // Trimmed lines are gone, so the running total is no longer
            // trustworthy — recount then. The `paused` guard covers a pause
            // that was undone while this batch was being processed.
            val recount = if (pendingTrimmed) pending.count { filters.accepts(it) } else -1
            if (paused) {
                pausedIncoming = if (recount >= 0) recount else pausedIncoming + matchedWhilePaused
                onPausedIncoming(pausedIncoming)
            }
            return@withLock false
        }
        val dropped = evictAndCountLocked()
        val (dropOld, dropNew) = prefixDrop(sizeBefore, appended.size, dropped)
        if (dropNew > 0) appended.subList(0, dropNew).clear()
        val filtered = all.isNotEmpty() && visible.isEmpty()
        val changed = dropOld > 0 || appended.isNotEmpty()
        if (!changed && filtered == lastFilteredOut) return@withLock false
        lastFilteredOut = filtered
        emit(ListEdit.Append(dropOld, ArrayList(appended), filtered))
        true
    }

    /**
     * Moves the paused backlog into the lists. False when the user paused again
     * before this ran: the backlog stays for the next resume.
     */
    suspend fun flushPending(rules: () -> FilterRules): Boolean = mutex.withLock {
        if (paused || pending.isEmpty()) return@withLock false
        val filters = rules()
        for (e in pending) {
            val row = UiLogEntry(seq++, e)
            all.add(row)
            if (filters.accepts(e)) visible.add(row)
        }
        pending.clear()
        evictOverflow(all, visible, cap)
        publishReplaceLocked()
        true
    }

    suspend fun clear() = mutex.withLock {
        all.clear()
        visible.clear()
        pending.clear()
        pausedIncoming = 0
        onPausedIncoming(0)
        publishReplaceLocked()
    }

    /** Removes every row (and pending line) belonging to [uids]. */
    suspend fun removeUids(uids: Set<Int?>, rules: () -> FilterRules) = mutex.withLock {
        all.removeAll { it.entry.uid in uids }
        visible.removeAll { it.entry.uid in uids }
        pending.removeAll { it.uid in uids }
        if (paused) {
            val filters = rules()
            pausedIncoming = pending.count { filters.accepts(it) }
            onPausedIncoming(pausedIncoming)
        }
        publishReplaceLocked()
    }

    /**
     * Re-applies the newest rules to everything. The invalid-regex flag is
     * published under the lock too: two refilters can race after their lock
     * turns, and the older one must not overwrite the newer result.
     */
    suspend fun refilter(rules: () -> FilterRules) = mutex.withLock {
        val filters = rules()
        visible = all.filterTo(ArrayList()) { filters.accepts(it.entry) }
        publishReplaceLocked()
        if (paused) {
            pausedIncoming = pending.count { filters.accepts(it) }
            onPausedIncoming(pausedIncoming)
        }
        onRegexInvalid(filters.regexInvalid)
    }

    /** Current buffer (or only the [uids] subset; null = all) for a snapshot. */
    suspend fun entriesForSave(uids: Set<Int?>?): List<LogcatEntry> = mutex.withLock {
        // Include `pending` — paused lines are still part of the stream the
        // user sees (they surface on resume) and belong in a "save everything"
        // snapshot. Staged lines are a <120 ms window and are left out.
        (all.filter { uids == null || it.entry.uid in uids }.map { it.entry } +
            pending.filter { uids == null || it.uid in uids })
            .sortedBy { it.seq }
    }

    /** Everything buffered plus the paused backlog, for per-app aggregation. */
    suspend fun allEntries(): List<LogcatEntry> = mutex.withLock {
        all.map { it.entry } + pending
    }

    /** Stable row ids matching [query] in tag or message. */
    suspend fun hits(query: String, ignoreCase: Boolean): List<Long> = mutex.withLock {
        if (query.isBlank()) return@withLock emptyList()
        visible.mapNotNull { row ->
            val e = row.entry
            if (e.tag.contains(query, ignoreCase) || e.message.contains(query, ignoreCase)) row.seq else null
        }
    }

    private fun evictAndCountLocked(): Int {
        val before = visible.size
        evictOverflow(all, visible, cap)
        return before - visible.size
    }

    private fun publishReplaceLocked() {
        lastFilteredOut = all.isNotEmpty() && visible.isEmpty()
        emit(ListEdit.Replace(ArrayList(visible), lastFilteredOut))
    }
}

/**
 * Split a prefix eviction across the rows that were already on screen and the
 * ones just appended. [dropped] is how many visible rows the trim removed,
 * counted from the front.
 */
internal fun prefixDrop(sizeBefore: Int, appended: Int, dropped: Int): Pair<Int, Int> {
    val dropOld = minOf(dropped.coerceAtLeast(0), sizeBefore.coerceAtLeast(0))
    val dropNew = (dropped - dropOld).coerceAtLeast(0).coerceAtMost(appended.coerceAtLeast(0))
    return dropOld to dropNew
}

/**
 * Drops the oldest `all.size - cap` entries. `visible` is a seq-ordered
 * subsequence of `all`, so evicted entries form a prefix of it — O(overflow)
 * instead of a per-entry indexOfFirst scan.
 */
internal fun evictOverflow(
    all: MutableList<UiLogEntry>,
    visible: MutableList<UiLogEntry>,
    cap: Int,
) {
    val overflow = all.size - cap
    if (overflow <= 0) return
    val lastEvictedSeq = all[overflow - 1].seq
    // subList().clear() is a single arraycopy — removeAt(0) in a loop is
    // O(n·overflow) and stalls the drain under sustained streams.
    all.subList(0, overflow).clear()
    var drop = 0
    while (drop < visible.size && visible[drop].seq <= lastEvictedSeq) drop++
    if (drop > 0) visible.subList(0, drop).clear()
}
