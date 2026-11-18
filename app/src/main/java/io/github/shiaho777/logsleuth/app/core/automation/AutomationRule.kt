package io.github.shiaho777.logsleuth.app.core.automation

import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry

/**
 * One automation trigger: when a log line matches, broadcast it to the
 * user-chosen automation app (Tasker, MacroDroid, …). The target package is
 * mandatory — broadcasts are explicit, never implicit, so no other app can
 * receive the log content.
 */
data class AutomationRule(
    val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    /** Empty = any app. */
    val packageName: String? = null,
    /** Substring or regex over tag + message. Empty = any text. */
    val query: String = "",
    val useRegex: Boolean = false,
    /** Empty = any tag. */
    val tagQuery: String = "",
    /** Package that receives the broadcast. Required. */
    val targetPackage: String = "",
    /** Broadcast action; the automation app listens for it. */
    val action: String = DEFAULT_ACTION,
    /** Minimum seconds between two broadcasts of this rule (0 = no limit). */
    val cooldownSeconds: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val hasConstraint: Boolean
        get() = packageName != null || query.isNotBlank() || tagQuery.isNotBlank()

    companion object {
        const val DEFAULT_ACTION = "io.github.shiaho777.logsleuth.app.LOG_MATCH"

        const val EXTRA_RULE_NAME = "rule_name"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_LEVEL = "level"
        const val EXTRA_TAG = "tag"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_RAW = "raw"
        const val EXTRA_PID = "pid"
        const val EXTRA_TID = "tid"
        const val EXTRA_UID = "uid"
        const val EXTRA_TIME = "time"

        /** Cap on the text extras — Binder transactions are ~1 MiB shared. */
        const val MAX_TEXT = 8_000
    }
}

/** A rule compiled once; invalid regex fails closed, like the filter stack. */
class CompiledAutomationRule(val rule: AutomationRule) {
    private val queryRegex = rule.takeIf { it.useRegex && it.query.isNotBlank() }
        ?.let { runCatching { Regex(it.query, RegexOption.IGNORE_CASE) }.getOrNull() }
    private val queryPlain = rule.takeIf { !it.useRegex }?.query?.trim().orEmpty()
    private val tagPlain = rule.tagQuery.trim()

    val regexInvalid: Boolean =
        rule.useRegex && rule.query.isNotBlank() && queryRegex == null

    fun matches(entry: LogcatEntry, entryPackage: String?): Boolean {
        if (regexInvalid) return false
        if (rule.packageName != null && rule.packageName != entryPackage) return false
        if (tagPlain.isNotEmpty() && !entry.tag.contains(tagPlain, ignoreCase = true)) return false
        if (queryRegex != null) {
            return queryRegex.containsMatchIn(entry.tag) || queryRegex.containsMatchIn(entry.message)
        }
        if (queryPlain.isNotEmpty()) {
            return entry.tag.contains(queryPlain, ignoreCase = true) ||
                entry.message.contains(queryPlain, ignoreCase = true)
        }
        return true
    }
}

/** Pure broadcast-payload assembly, unit-tested without Android. */
object AutomationPayload {
    fun extras(rule: AutomationRule, entry: LogcatEntry, entryPackage: String?): Map<String, Any> =
        linkedMapOf(
            AutomationRule.EXTRA_RULE_NAME to rule.name,
            AutomationRule.EXTRA_PACKAGE to entryPackage.orEmpty(),
            AutomationRule.EXTRA_LEVEL to entry.level.name,
            AutomationRule.EXTRA_TAG to entry.tag,
            AutomationRule.EXTRA_MESSAGE to entry.message.take(AutomationRule.MAX_TEXT),
            AutomationRule.EXTRA_RAW to entry.raw.take(AutomationRule.MAX_TEXT),
            AutomationRule.EXTRA_PID to entry.pid,
            AutomationRule.EXTRA_TID to entry.tid,
            AutomationRule.EXTRA_UID to (entry.uid ?: -1),
            AutomationRule.EXTRA_TIME to entry.timestampMillis,
        )
}