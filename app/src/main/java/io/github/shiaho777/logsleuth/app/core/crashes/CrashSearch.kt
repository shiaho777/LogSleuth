package io.github.shiaho777.logsleuth.app.core.crashes

/** Highlight ranges into the stack snippet and the saved surrounding lines. */
data class CrashSearchHits(
    val snippetRanges: List<IntRange>,
    val contextRanges: List<IntRange>,
)

/** Every match of [query] in a crash stack, as ranges into the original text. */
object CrashSearch {
    fun search(
        snippet: String,
        context: String?,
        query: String,
        ignoreCase: Boolean,
    ): CrashSearchHits = CrashSearchHits(
        snippetRanges = matchRanges(snippet, query, ignoreCase),
        contextRanges = matchRanges(context.orEmpty(), query, ignoreCase),
    )

    fun matchRanges(
        haystack: String,
        query: String,
        ignoreCase: Boolean,
    ): List<IntRange> {
        if (query.isEmpty() || haystack.isEmpty()) return emptyList()
        val source = if (ignoreCase) haystack.lowercase() else haystack
        val needle = if (ignoreCase) query.lowercase() else query
        if (needle.isEmpty()) return emptyList()
        val ranges = ArrayList<IntRange>()
        var start = 0
        while (true) {
            val idx = source.indexOf(needle, start)
            if (idx < 0) break
            ranges += idx until (idx + needle.length)
            start = idx + needle.length
        }
        return ranges
    }
}
