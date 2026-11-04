package io.github.shiaho777.logsleuth.app.core.crashes

import io.github.shiaho777.logsleuth.app.core.text.matchRanges

/** Which block a crash-details search hit sits in. */
enum class CrashSearchSection { SNIPPET, CONTEXT }

/** One match, in document order: snippet first, then surrounding logs. */
data class CrashSearchHit(
    val section: CrashSearchSection,
    val range: IntRange,
)

/** Highlight ranges into the stack snippet and the saved surrounding lines. */
data class CrashSearchHits(
    val snippetRanges: List<IntRange>,
    val contextRanges: List<IntRange>,
) {
    val hits: List<CrashSearchHit>
        get() = buildList(snippetRanges.size + contextRanges.size) {
            snippetRanges.forEach { add(CrashSearchHit(CrashSearchSection.SNIPPET, it)) }
            contextRanges.forEach { add(CrashSearchHit(CrashSearchSection.CONTEXT, it)) }
        }
}

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
}
