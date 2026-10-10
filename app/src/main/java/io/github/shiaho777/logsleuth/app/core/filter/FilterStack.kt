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
) {
    val regexInvalid: Boolean =
        including.any { it.regexInvalid } || excluding.any { it.regexInvalid }

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
            return FilterStack(
                including = active.filter { it.including }.map { it.compile(resolveUid) },
                excluding = active.filter { !it.including }.map { it.compile(resolveUid) },
            )
        }

        private fun LogFilter.compile(resolveUid: (String?) -> Int?) =
            CompiledFilter(this, resolveUid(packageName))
    }
}
