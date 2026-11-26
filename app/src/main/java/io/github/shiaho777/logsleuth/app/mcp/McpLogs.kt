package io.github.shiaho777.logsleuth.app.mcp

import io.github.shiaho777.logsleuth.app.core.apps.InstalledApps
import io.github.shiaho777.logsleuth.app.core.filter.CompiledFilter
import io.github.shiaho777.logsleuth.app.core.filter.FilterStack
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatParser
import io.github.shiaho777.logsleuth.app.core.logcat.ParsedLine
import io.github.shiaho777.logsleuth.app.data.db.SessionDao
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

@Singleton
internal class McpLogs @Inject constructor(
    private val engine: LogcatEngine,
    private val sessions: SessionDao,
    private val apps: InstalledApps,
) {
    private val generation = UUID.randomUUID().toString()

    fun sourceId(endpoint: String? = engine.access.value.remoteEndpoint) = "$generation/${endpoint.orEmpty()}"

    fun clear(source: String) {
        val endpoint = engine.access.value.remoteEndpoint
        checkSource(source, endpoint)
        engine.clearBuffer(endpoint)
    }

    fun compile(filter: LogFilter, endpoint: String? = engine.access.value.remoteEndpoint): CompiledFilter =
        CompiledFilter(filter, resolveUid(filter.packageName, endpoint)).also {
            if (it.regexInvalid) refuse("INVALID_FILTER", "A filter expression is invalid.")
        }

    fun snapshot(filter: LogFilter, rules: List<LogFilter>): List<LogcatEntry> {
        val endpoint = engine.access.value.remoteEndpoint
        val compiled = compile(filter, endpoint)
        val stack = FilterStack.compile(rules) { resolveUid(it, endpoint) }
        return engine.snapshot(endpoint).entries.filter { compiled.matches(it) && stack.matches(it) }
    }

    suspend fun search(args: JSONObject): JSONObject {
        val sessionId = args.optionalId("sessionId")
        val filter = args.optJSONObject("filter").toFilter()
        val raw = args.optBoolean("raw")
        val limit = args.optInt("limit", 50)
        if (sessionId == null) {
            val endpoint = engine.access.value.remoteEndpoint
            val source = sourceId(endpoint)
            val snap = engine.snapshot(endpoint)
            val cursor = args.string("cursor")?.let { parseCursor(it, source) }
            if (cursor != null && (snap.entries.isEmpty() ||
                    cursor !in snap.entries.first().seq..snap.maxSeq)) {
                refuse("CURSOR_EXPIRED", "The requested logs are no longer retained. Run a new search.")
            }
            val compiled = compile(filter, endpoint)
            val rows = snap.entries.asReversed().asSequence()
                .filter { (cursor == null || it.seq < cursor) && compiled.matches(it) }
                .map { it.seq to row(it.seq, it, it.raw, raw, endpoint) }
            return page(source, rows, limit).put("latestSequence", snap.maxSeq.toString())
        }
        val session = session(sessionId)
        val source = "session/$sessionId"
        val after = args.string("cursor")?.let { parseCursor(it, source) } ?: 0L
        val compiled = compile(filter, session.remoteEndpoint)
        val coroutine = currentCoroutineContext()
        return file(session).useLines { lines ->
            val matches = lines.withIndex().mapNotNull { (index, line) ->
                coroutine.ensureActive()
                val number = index.toLong() + 1
                if (number <= after || line.startsWith('#') || line.isBlank()) return@mapNotNull null
                val entry = parse(line, session)
                val searchable = entry ?: LogcatEntry(0, 0, 0, null, LogLevel.V, "", line, line)
                if (!compiled.matches(searchable)) return@mapNotNull null
                number to row(number, entry, line, raw, session.remoteEndpoint)
            }
            page(source, matches, limit)
        }
    }

    suspend fun context(args: JSONObject): JSONObject {
        val anchor = args.id("anchor")
        val before = args.optInt("before", 10)
        val after = args.optInt("after", 10)
        val raw = args.optBoolean("raw")
        val sid = args.optionalId("sessionId")
        if (sid == null) {
            val endpoint = engine.access.value.remoteEndpoint
            checkSource(args.string("sourceId"), endpoint)
            val entries = engine.snapshot(endpoint).entries
            val index = entries.binarySearchBy(anchor) { it.seq }
            if (index < 0) refuse("LOG_NOT_RETAINED", "This live log entry is no longer retained.")
            val first = (index - before).coerceAtLeast(0)
            val end = (index + after + 1).coerceAtMost(entries.size)
            val rows = entries.subList(first, end).map { row(it.seq, it, it.raw, raw, endpoint) }
            return contextPage(sourceId(endpoint), rows, index - first, anchor)
        }

        if (anchor > Int.MAX_VALUE - 100L) refuse("LOG_NOT_FOUND", "This line number is outside the session.")
        val session = session(sid)
        val first = (anchor - before).coerceAtLeast(1)
        val size = (anchor + after - first + 1).toInt()
        val coroutine = currentCoroutineContext()
        return file(session).useLines { lines ->
            val rows = lines.withIndex().dropWhile { (index, _) ->
                coroutine.ensureActive()
                index.toLong() + 1 < first
            }.take(size).map { (index, line) ->
                val number = index.toLong() + 1
                row(number, parse(line, session), line, raw, session.remoteEndpoint)
            }.toList()
            val index = (anchor - first).toInt()
            if (index >= rows.size) refuse("LOG_NOT_FOUND", "This line number is outside the session.")
            contextPage("session/$sid", rows, index, anchor)
        }
    }

    private fun contextPage(source: String, rows: List<JSONObject>, index: Int, anchor: Long): JSONObject {
        fun size(row: JSONObject) = row.toString().toByteArray(Charsets.UTF_8).size + 1
        var bytes = size(rows[index])
        var first = index
        var end = index + 1
        while (first > 0 || end < rows.size) {
            val left = rows.getOrNull(first - 1)?.let(::size) ?: Int.MAX_VALUE
            val right = rows.getOrNull(end)?.let(::size) ?: Int.MAX_VALUE
            val canAddLeft = left <= 58_000 - bytes
            val canAddRight = right <= 58_000 - bytes
            if (!canAddLeft && !canAddRight) break
            if (canAddLeft && (!canAddRight || index - first <= end - index - 1)) {
                bytes += left
                first--
            } else {
                bytes += right
                end++
            }
        }
        return json("sourceId" to source, "lines" to JSONArray(rows.subList(first, end)),
            "anchor" to anchor.toString(), "omittedBefore" to first, "omittedAfter" to (rows.size - end))
    }

    suspend fun bookmarkLines(session: SessionEntity, times: List<Long>): List<String?> {
        val file = File(session.filePath)
        if (times.isEmpty() || !file.isFile) return emptyList()
        val nearest = arrayOfNulls<String>(times.size)
        val distances = LongArray(times.size) { Long.MAX_VALUE }
        val coroutine = currentCoroutineContext()
        file.useLines { lines ->
            for ((index, text) in lines.withIndex()) {
                coroutine.ensureActive()
                val entry = parse(text, session) ?: continue
                times.forEachIndexed { slot, time ->
                    val distance = kotlin.math.abs(entry.timestampMillis - time)
                    if (distance < distances[slot]) {
                        nearest[slot] = (index + 1).toString()
                        distances[slot] = distance
                    }
                }
            }
        }
        return nearest.toList()
    }

    private fun parse(line: String, session: SessionEntity): LogcatEntry? =
        (LogcatParser.parse(line.take(16_384), session.startedAt) as? ParsedLine.Entry)?.entry

    private suspend fun session(id: Long): SessionEntity =
        sessions.getById(id) ?: refuse("SESSION_NOT_FOUND", "The session no longer exists.")

    private fun file(session: SessionEntity): File = File(session.filePath).takeIf { it.isFile }
        ?: refuse("FILE_NOT_FOUND", "The session log file is unavailable.")

    private fun resolveUid(name: String?, endpoint: String?): Int? = when {
        name == null -> null
        endpoint == null -> apps.resolveUid(name)
        else -> engine.remotePackages(endpoint).entries.firstOrNull { it.value == name }?.key
    }

    private fun checkSource(source: String?, endpoint: String?) {
        if (source != sourceId(endpoint)) refuse("SOURCE_CHANGED", "Refresh the live source and search again.")
    }

    private fun parseCursor(cursor: String, source: String): Long {
        if (cursor.substringBeforeLast('|') != source) refuse("INVALID_CURSOR", "This cursor belongs to a different source.")
        return cursor.substringAfterLast('|').toLongOrNull()?.takeIf { it > 0 }
            ?: refuse("INVALID_CURSOR", "The cursor is invalid.")
    }

    private fun row(id: Long, entry: LogcatEntry?, text: String, raw: Boolean, endpoint: String?): JSONObject {
        val message = entry?.message ?: text
        val truncated = message.length > 2_048 || (raw && text.length > 2_048)
        return json(
            "ref" to id.toString(), "timeMillis" to entry?.timestampMillis, "level" to entry?.level?.name,
            "tag" to entry?.tag?.take(256), "pid" to entry?.pid, "tid" to entry?.tid, "uid" to entry?.uid,
            "package" to entry?.let {
                it.remotePackage ?: it.uid?.let { uid ->
                    if (endpoint == null) apps.packageForUid(uid) else engine.remotePackages(endpoint)[uid]
                }
            },
            "message" to message.take(2_048), "truncated" to truncated,
        ).apply { if (raw) put("raw", text.take(2_048)) }
    }

    private fun page(source: String, rows: Sequence<Pair<Long, JSONObject>>, limit: Int): JSONObject {
        val items = JSONArray()
        var bytes = 0
        var last = 0L
        var more = false
        for ((id, item) in rows) {
            val size = item.toString().toByteArray(Charsets.UTF_8).size + 1
            if (items.length() == limit || bytes + size > 58_000) {
                more = true
                break
            }
            items.put(item)
            bytes += size
            last = id
        }
        return json("sourceId" to source, "lines" to items, "nextCursor" to if (more) "$source|$last" else null)
    }
}

internal fun JSONObject?.toFilter(): LogFilter {
    val value = this ?: return LogFilter()
    val allowed = value.optJSONArray("levels")
    return LogFilter(
        minLevel = LogLevel.valueOf(value.optString("minLevel", "V")),
        allowedLevels = if (allowed == null) emptySet() else (0 until allowed.length()).map { LogLevel.valueOf(allowed.getString(it)) }.toSet(),
        query = value.optString("query"),
        excludeQuery = value.optString("excludeQuery"),
        tagQuery = value.optString("tag"),
        tagExact = value.optBoolean("tagExact"),
        useRegex = value.optBoolean("regex"),
        packageName = value.string("package"),
        pid = value.optString("pid"),
        tid = value.optString("tid"),
        uid = value.optString("uid"),
    )
}
