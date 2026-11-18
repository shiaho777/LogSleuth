package io.github.shiaho777.logsleuth.app.core.filter

import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry

/**
 * Several saved rules at once.
 *
 * Excluding rules run first: a line that suits any of them is dropped.
 * If any including rules are enabled, the line must suit at least one.
 * Rules with no constraint are ignored so a blank exclude cannot hide
 * the entire stream. No enabled rules means every line passes — the quick
 * filter on the bar is applied separately.
 */
class FilterStack(
    private val including: List<CompiledFilter>,
    private val excluding: List<CompiledFilter>,
    /** A compiled rule was parked for a broken pattern; the UI flags it. */
    val regexInvalid: Boolean = false,
) {

    fun matches(entry: LogcatEntry): Boolean {
        if (excluding.any { it.matches(entry) }) return false
        if (including.isEmpty()) return true
        return including.any { it.matches(entry) }
    }

    companion object {
        val PASS = FilterStack(emptyList(), emptyList())

        fun compile(
            rules: List<LogFilter>,
            resolveUid: (String?) -> Int?,
        ): FilterStack {
            val active = rules.filter { it.enabled && it.hasConstraint }
            val compiled = active.map { it to it.compile(resolveUid) }
            // A rule with a broken pattern is parked, not applied: it can
            // neither include nor exclude anything, and it must not drag
            // the healthy rules in its group down with it. The UI still
            // surfaces [regexInvalid] so the user can fix the pattern.
            val usable = compiled.filterNot { it.second.regexInvalid }
            return FilterStack(
                including = usable.filter { it.first.including }.map { it.second },
                excluding = usable.filterNot { it.first.including }.map { it.second },
                regexInvalid = usable.size != compiled.size,
            )
        }

        private fun LogFilter.compile(resolveUid: (String?) -> Int?) =
            CompiledFilter(this, resolveUid(packageName))
    }
}
