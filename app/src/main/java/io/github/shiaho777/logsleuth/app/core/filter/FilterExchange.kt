package io.github.shiaho777.logsleuth.app.core.filter

import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel

/**
 * Text form of saved filter rules, so a stack can move between devices
 * without a JSON library. One rule per block:
 *
 * ```
 * logsleuth-filters 1
 * ---
 * name=OkHttp
 * including=true
 * enabled=true
 * minLevel=V
 * query=
 * exclude=
 * tag=OkHttp
 * regex=false
 * package=
 * pid=
 * tid=
 * uid=
 * ```
 */
object FilterExchange {

    const val HEADER = "logsleuth-filters 1"

    fun encode(filters: List<LogFilter>): String = buildString {
        appendLine(HEADER)
        for (f in filters) {
            appendLine("---")
            field("name", f.name)
            field("including", f.including.toString())
            field("enabled", f.enabled.toString())
            field("minLevel", f.minLevel.name)
            field("query", f.query)
            field("exclude", f.excludeQuery)
            field("tag", f.tagQuery)
            field("regex", f.useRegex.toString())
            field("package", f.packageName.orEmpty())
            field("pid", f.pid)
            field("tid", f.tid)
            field("uid", f.uid)
        }
    }

    fun decode(text: String): List<LogFilter>? {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lineSequence()
            .map { it.trimEnd() }
            .toList()
        if (lines.firstOrNull()?.trim() != HEADER) return null
        val rules = ArrayList<LogFilter>()
        var current = linkedMapOf<String, String>()
        fun flush() {
            if (current.isEmpty()) return
            rules += fromFields(current)
            current = linkedMapOf()
        }
        for (line in lines.drop(1)) {
            if (line == "---") {
                flush()
                continue
            }
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            current[line.substring(0, eq)] = unescape(line.substring(eq + 1))
        }
        flush()
        return rules
    }

    private fun StringBuilder.field(key: String, value: String) {
        append(key)
        append('=')
        appendLine(escape(value))
    }

    private fun fromFields(fields: Map<String, String>): LogFilter {
        val level = fields["minLevel"]?.let { runCatching { LogLevel.valueOf(it) }.getOrNull() }
            ?: LogLevel.V
        val pkg = fields["package"].orEmpty().ifBlank { null }
        return LogFilter(
            name = fields["name"].orEmpty().ifBlank { "Imported" },
            minLevel = level,
            query = fields["query"].orEmpty(),
            excludeQuery = fields["exclude"].orEmpty(),
            tagQuery = fields["tag"].orEmpty(),
            useRegex = fields["regex"] == "true",
            packageName = pkg,
            enabled = fields["enabled"] == "true",
            including = fields["including"] != "false",
            pid = fields["pid"].orEmpty(),
            tid = fields["tid"].orEmpty(),
            uid = fields["uid"].orEmpty(),
        )
    }

    private fun escape(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                else -> append(ch)
            }
        }
    }

    private fun unescape(value: String): String = buildString(value.length) {
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> append('\\')
                    'n' -> append('\n')
                    else -> append(value[i + 1])
                }
                i += 2
            } else {
                append(ch)
                i++
            }
        }
    }
}
