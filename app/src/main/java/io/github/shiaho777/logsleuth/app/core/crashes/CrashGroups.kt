package io.github.shiaho777.logsleuth.app.core.crashes

import io.github.shiaho777.logsleuth.app.data.db.CrashEventEntity

enum class CrashSort { NEWEST, COUNT, NAME }

data class CrashGroup(
    val packageName: String?,
    val label: String,
    val count: Int,
    val latestTime: Long,
    /** Newest first. */
    val events: List<CrashEventEntity>,
)

/**
 * One row per package. [query] matches the name, package, type, or stack.
 * [reversed] flips whichever sort is selected.
 */
fun groupCrashes(
    crashes: List<CrashEventEntity>,
    query: String,
    sort: CrashSort,
    reversed: Boolean,
): List<CrashGroup> {
    val q = query.trim()
    val matched = if (q.isEmpty()) {
        crashes
    } else {
        crashes.filter { crash ->
            crash.packageName?.contains(q, ignoreCase = true) == true ||
                crash.appName?.contains(q, ignoreCase = true) == true ||
                crash.type.contains(q, ignoreCase = true) ||
                crash.firstLine.contains(q, ignoreCase = true) ||
                crash.snippet.contains(q, ignoreCase = true)
        }
    }
    val groups = matched.groupBy { it.packageName }.map { (pkg, events) ->
        val ordered = events.sortedByDescending { it.time }
        val latest = ordered.first()
        val label = ordered.firstNotNullOfOrNull { it.appName?.takeIf { name -> name.isNotBlank() } }
            ?: pkg
            ?: ""
        CrashGroup(
            packageName = pkg,
            label = label,
            count = ordered.size,
            latestTime = latest.time,
            events = ordered,
        )
    }
    val sorted = when (sort) {
        CrashSort.NEWEST -> groups.sortedByDescending { it.latestTime }
        CrashSort.COUNT -> groups.sortedWith(
            compareByDescending<CrashGroup> { it.count }.thenByDescending { it.latestTime },
        )
        CrashSort.NAME -> groups.sortedBy { it.label.lowercase() }
    }
    return if (reversed) sorted.asReversed() else sorted
}
