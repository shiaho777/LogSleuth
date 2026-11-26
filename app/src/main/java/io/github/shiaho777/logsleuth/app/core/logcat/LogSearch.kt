package io.github.shiaho777.logsleuth.app.core.logcat

internal fun LogcatEntry.matchesLogSearch(query: String, ignoreCase: Boolean): Boolean =
    query.isNotBlank() && (tag.contains(query, ignoreCase) || message.contains(query, ignoreCase))

internal fun retainedSearchHitIndex(matches: List<Long>, selectedId: Long?): Int {
    if (matches.isEmpty()) return -1
    if (selectedId == null) return 0
    val index = matches.binarySearch(selectedId)
    return if (index >= 0) index else (-index - 1).coerceAtMost(matches.lastIndex)
}
