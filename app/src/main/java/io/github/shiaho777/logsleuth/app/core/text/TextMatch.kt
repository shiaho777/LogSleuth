package io.github.shiaho777.logsleuth.app.core.text

/**
 * Every non-overlapping match of [query] in [haystack], as ranges into
 * [haystack].
 *
 * Matching is done in place with [ignoreCase] instead of on a lowercased copy:
 * lowercasing can change the length (for example `'İ'` becomes two code units),
 * which would shift the later ranges off the original string and can point past
 * its end.
 */
fun matchRanges(haystack: String, query: String, ignoreCase: Boolean): List<IntRange> {
    if (query.isEmpty() || haystack.isEmpty()) return emptyList()
    val ranges = ArrayList<IntRange>()
    var start = 0
    while (true) {
        val idx = haystack.indexOf(query, start, ignoreCase = ignoreCase)
        if (idx < 0) break
        ranges += idx until (idx + query.length)
        start = idx + query.length
    }
    return ranges
}
