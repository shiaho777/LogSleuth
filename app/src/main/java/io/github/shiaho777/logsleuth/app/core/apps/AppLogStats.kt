package io.github.shiaho777.logsleuth.app.core.apps

import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry

/**
 * Per-app aggregation of a log buffer: the unit the clear/save scope
 * pickers group by. `uid == null` collects entries whose uid column the
 * parser could not read — one synthetic group, label supplied by caller.
 */
data class AppLogGroup(
    val uid: Int?,
    val packageName: String?,
    val label: String,
    val total: Int,
    /** Counts indexed by [LogLevel.ordinal]. */
    val levelCounts: IntArray,
    val firstMillis: Long,
    val lastMillis: Long,
) {
    val errors: Int get() =
        levelCounts[LogLevel.E.ordinal] + levelCounts[LogLevel.F.ordinal] +
            levelCounts[LogLevel.A.ordinal]
    val warns: Int get() = levelCounts[LogLevel.W.ordinal]

    override fun equals(other: Any?): Boolean =
        other is AppLogGroup && uid == other.uid && packageName == other.packageName &&
            label == other.label && total == other.total &&
            levelCounts.contentEquals(other.levelCounts) &&
            firstMillis == other.firstMillis && lastMillis == other.lastMillis

    override fun hashCode(): Int =
        (uid?.hashCode() ?: 0) * 31 + total
}

/**
 * Groups [entries] by uid, resolves each uid to a package/label via
 * [resolveApp] (returns package name + display label, or null when the uid
 * has no packages), and returns groups sorted by line count descending —
 * the noisiest app first.
 */
fun groupEntriesByApp(
    entries: List<LogcatEntry>,
    unattributedLabel: String,
    resolveApp: (Int) -> Pair<String?, String>?,
): List<AppLogGroup> {
    data class Acc(
        var total: Int = 0,
        val counts: IntArray = IntArray(LogLevel.entries.size),
        var first: Long = Long.MAX_VALUE,
        var last: Long = Long.MIN_VALUE,
    )
    val byUid = LinkedHashMap<Int?, Acc>()
    for (e in entries) {
        val acc = byUid.getOrPut(e.uid) { Acc() }
        acc.total++
        acc.counts[e.level.ordinal]++
        if (e.timestampMillis < acc.first) acc.first = e.timestampMillis
        if (e.timestampMillis > acc.last) acc.last = e.timestampMillis
    }
    return byUid.map { (uid, acc) ->
        val resolved = uid?.let(resolveApp)
        AppLogGroup(
            uid = uid,
            packageName = resolved?.first,
            label = resolved?.second ?: unattributedLabel,
            total = acc.total,
            levelCounts = acc.counts,
            firstMillis = acc.first,
            lastMillis = acc.last,
        )
    }.sortedByDescending { it.total }
}
