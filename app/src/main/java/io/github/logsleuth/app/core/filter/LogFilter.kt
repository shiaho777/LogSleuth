package io.github.logsleuth.app.core.filter

import io.github.logsleuth.app.core.logcat.LogLevel
import io.github.logsleuth.app.core.logcat.LogcatEntry

/** A user-facing filter preset. Mirrors the Room entity; keep it pure Kotlin. */
data class LogFilter(
    val id: Long = 0,
    val name: String = "",
    val minLevel: LogLevel = LogLevel.V,
    /** Include matcher: checked against tag and message. */
    val query: String = "",
    /** Exclusion matcher: any match drops the entry. */
    val excludeQuery: String = "",
    /** Tag-only matcher. */
    val tagQuery: String = "",
    val useRegex: Boolean = false,
    /** Per-app filter, applied server-side via `logcat --uid` (Shizuku only). */
    val packageName: String? = null,
) {
    val isDefault: Boolean
        get() = this == DEFAULT

    companion object {
        val DEFAULT = LogFilter(name = "Default")
    }
}

/** Pre-compiled, reusable matcher for a [LogFilter]. */
class CompiledFilter(rule: LogFilter, resolvedUid: Int? = null) {

    private val minPriority = rule.minLevel.priority

    // Per-app filter: compare the entry's uid column against the resolved uid.
    // null = no app filter; NO_UID = filter set but uid unresolvable (match nothing).
    private val requiredUid: Int? = when {
        rule.packageName == null -> null
        resolvedUid != null -> resolvedUid
        else -> NO_UID
    }

    private val queryRegex = rule.takeIf { it.useRegex && it.query.isNotBlank() }
        ?.let { runCatching { Regex(it.query, RegexOption.IGNORE_CASE) }.getOrNull() }
    private val queryPlain = rule.takeIf { !it.useRegex }?.query?.trim().orEmpty()

    private val excludeRegex = rule.takeIf { it.useRegex && it.excludeQuery.isNotBlank() }
        ?.let { runCatching { Regex(it.excludeQuery, RegexOption.IGNORE_CASE) }.getOrNull() }
    private val excludePlain = rule.takeIf { !it.useRegex }?.excludeQuery?.trim().orEmpty()

    private val tagRegex = rule.takeIf { it.useRegex && it.tagQuery.isNotBlank() }
        ?.let { runCatching { Regex(it.tagQuery, RegexOption.IGNORE_CASE) }.getOrNull() }
    private val tagPlain = rule.takeIf { !it.useRegex }?.tagQuery?.trim().orEmpty()

    /** True when [rule]'s regex strings are invalid (we fail open, matching everything). */
    val regexInvalid: Boolean =
        (rule.useRegex && rule.query.isNotBlank() && queryRegex == null) ||
            (rule.useRegex && rule.excludeQuery.isNotBlank() && excludeRegex == null) ||
            (rule.useRegex && rule.tagQuery.isNotBlank() && tagRegex == null)

    fun matches(entry: LogcatEntry): Boolean {
        if (entry.level.priority < minPriority) return false

        if (requiredUid != null) {
            if (requiredUid == NO_UID || entry.uid != requiredUid) return false
        }

        if (tagRegex != null) {
            if (!tagRegex.containsMatchIn(entry.tag)) return false
        } else if (tagPlain.isNotEmpty()) {
            if (!entry.tag.contains(tagPlain, ignoreCase = true)) return false
        }

        if (queryRegex != null) {
            if (!queryRegex.containsMatchIn(entry.tag) && !queryRegex.containsMatchIn(entry.message)) {
                return false
            }
        } else if (queryPlain.isNotEmpty()) {
            val hit = entry.tag.contains(queryPlain, ignoreCase = true) ||
                entry.message.contains(queryPlain, ignoreCase = true)
            if (!hit) return false
        }

        if (excludeRegex != null) {
            if (excludeRegex.containsMatchIn(entry.tag) || excludeRegex.containsMatchIn(entry.message)) {
                return false
            }
        } else if (excludePlain.isNotEmpty()) {
            val hit = entry.tag.contains(excludePlain, ignoreCase = true) ||
                entry.message.contains(excludePlain, ignoreCase = true)
            if (hit) return false
        }

        return true
    }

    companion object {
        private const val NO_UID = -1
    }
}
