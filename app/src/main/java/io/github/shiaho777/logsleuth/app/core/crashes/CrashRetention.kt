package io.github.shiaho777.logsleuth.app.core.crashes

/**
 * crash_events and their context files had no retention at all: a
 * crash-looping device grew the database and the files dir without bound.
 * Two caps, applied after every stored crash (and once at startup):
 *
 * - keep at most [maxEvents] rows, newest first;
 * - drop rows older than [maxAgeMs] (0 disables the age cap).
 *
 * Pure selection logic, unit-tested; the recorder executes the deletes and
 * removes the context files of everything selected.
 */
object CrashRetention {
    const val DEFAULT_MAX_EVENTS = 500
    const val DEFAULT_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000 // 30 days

    /** Minimal shape the policy needs; satisfied by the Room entity. */
    interface Row {
        val id: Long
        val time: Long
    }

    /**
     * Ids to delete from [rows] (any order). The newest [maxEvents] rows
     * within the age window survive; everything else goes.
     */
    fun selectForDeletion(
        rows: List<Row>,
        now: Long,
        maxEvents: Int = DEFAULT_MAX_EVENTS,
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    ): List<Long> {
        if (rows.isEmpty()) return emptyList()
        val sorted = rows.sortedByDescending { it.time }
        val doomed = LinkedHashSet<Long>()
        if (maxAgeMs > 0) {
            val cutoff = now - maxAgeMs
            doomed.addAll(sorted.filter { it.time < cutoff }.map { it.id })
        }
        if (maxEvents > 0) {
            doomed.addAll(sorted.drop(maxEvents).map { it.id })
        }
        return doomed.toList()
    }
}