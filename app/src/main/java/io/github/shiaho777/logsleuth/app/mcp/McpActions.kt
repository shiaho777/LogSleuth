package io.github.shiaho777.logsleuth.app.mcp

import io.github.shiaho777.logsleuth.app.core.apps.InstalledApps
import io.github.shiaho777.logsleuth.app.core.automation.AutomationRule
import io.github.shiaho777.logsleuth.app.core.automation.CompiledAutomationRule
import io.github.shiaho777.logsleuth.app.core.crashes.CrashDeleter
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.core.importer.ExampleReportStore
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.core.sessions.SessionDeleter
import io.github.shiaho777.logsleuth.app.data.db.AutomationRuleDao
import io.github.shiaho777.logsleuth.app.data.db.BookmarkDao
import io.github.shiaho777.logsleuth.app.data.db.CrashEventDao
import io.github.shiaho777.logsleuth.app.data.db.CrashEventEntity
import io.github.shiaho777.logsleuth.app.data.db.FilterDao
import io.github.shiaho777.logsleuth.app.data.db.SessionDao
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
import io.github.shiaho777.logsleuth.app.data.db.toEntity
import io.github.shiaho777.logsleuth.app.data.db.toLogFilter
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import io.github.shiaho777.logsleuth.app.service.RecordingManager
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

internal class McpActions @Inject constructor(
    private val engine: LogcatEngine,
    private val recording: RecordingManager,
    private val apps: InstalledApps,
    private val capture: McpCapture,
    private val logs: McpLogs,
    private val sessions: SessionDao,
    private val bookmarks: BookmarkDao,
    private val crashes: CrashEventDao,
    private val filters: FilterDao,
    private val automation: AutomationRuleDao,
    private val example: ExampleReportStore,
    private val exporter: SessionExporter,
    private val sessionDeleter: SessionDeleter,
    private val crashDeleter: CrashDeleter,
    private val settingsRepository: SettingsRepository,
    private val settingsTools: McpSettings,
    private val transfers: McpFiles,
) {
    suspend fun execute(tool: String, args: JSONObject): JSONObject = when (tool) {
        "status" -> status()
        "list_apps" -> page(args, apps.loadForCapture(engine.access.value.remoteEndpoint?.let(engine::remotePackages))) {
            json("package" to it.packageName, "name" to it.label)
        }
        "search_logs" -> logs.search(args)
        "read_log_context" -> logs.context(args)
        "clear_logs" -> {
            logs.clear(args.getString("sourceId"))
            json("cleared" to true)
        }
        "save_snapshot" -> {
            val filter = args.optJSONObject("filter").toFilter()
            val entries = logs.snapshot(filter, filters.observeAll().first().map { it.toLogFilter() })
            if (entries.isEmpty()) refuse("NO_LOGS", "No retained logs match this filter.")
            receipt(recording.snapshotToSession(entries, args.string("name")).getOrThrow())
        }
        "start_recording" -> {
            val filter = args.optJSONObject("filter").toFilter()
            logs.compile(filter)
            receipt(capture.start(args.string("name"), filter, args.string("requestId"), null))
        }
        "pause_recording", "resume_recording", "stop_recording", "add_bookmark" -> changeRecording(tool, args)
        "list_sessions" -> page(args, sessions.observeAll().first(), row = ::sessionData)
        "get_session" -> sessionData(session(args.id("sessionId")))
        "list_bookmarks" -> listBookmarks(args)
        "open_example" -> receipt(example.open())
        "rename_session" -> {
            val id = args.id("sessionId")
            session(id)
            sessions.rename(id, name(args))
            sessionData(session(id))
        }
        "delete_session" -> {
            val id = args.id("sessionId")
            if (!sessionDeleter.delete(id)) refuse("SESSION_BUSY", "Stop the recording or check the session file, then retry.")
            json("deleted" to true)
        }
        "export_session" -> {
            val id = args.id("sessionId")
            if (session(id).endedAt == null) refuse("SESSION_BUSY", "Finish the recording before exporting.")
            val format = if (args.optString("format", "zip") == "txt") SessionExporter.Format.TXT else SessionExporter.Format.ZIP
            transfers.export(exporter.export(id, format).getOrThrow())
        }
        "import_report" -> refuse("USE_LOCAL_BRIDGE", "Use the Termux MCP bridge to import a local file.")
        "list_crashes" -> {
            val id = args.optionalId("sessionId")
            val packageName = args.string("package")
            val events = if (id == null) crashes.observeAll().first() else crashes.observeForSession(id).first()
            page(args, events.filter { packageName == null || it.packageName == packageName }, row = ::crashData)
        }
        "get_crash" -> {
            val event = crash(args.id("crashId"))
            val offset = offset(args)
            val context = event.contextPath?.let(::File)?.takeIf { it.isFile }?.useLines {
                it.drop(offset).take(limit(args) + 1).map { line -> line.take(1_024) }.toList()
            }.orEmpty()
            val extra = page(args, context, before = offset) { json("text" to it) }
            crashData(event).put("stack" , event.snippet.take(6_000)).put("stackTruncated", event.snippet.length > 6_000)
                .put("lines", extra.getJSONArray("items")).put("nextCursor", extra.get("nextCursor"))
        }
        "delete_crash" -> {
            crashDeleter.deleteIds(listOf(args.id("crashId")))
            json("deleted" to true)
        }
        "set_crash_ignored" -> {
            val pkg = args.getString("package")
            val ignored = args.getBoolean("ignored")
            if (ignored) settingsRepository.ignorePackage(pkg) else settingsRepository.unignorePackage(pkg)
            json("package" to pkg, "ignored" to ignored)
        }
        "export_crash" -> transfers.export(exporter.crashZip(crash(args.id("crashId"))).getOrThrow())
        "start_report" -> {
            val endpoint = engine.access.value.remoteEndpoint
            val target = args.getString("package")
            if (apps.loadForCapture(endpoint?.let(engine::remotePackages)).none { it.packageName == target }) {
                refuse("APP_NOT_FOUND", "Select an application returned by list_apps.")
            }
            receipt(capture.start(null, LogFilter(packageName = target), args.string("requestId"), target))
        }
        "get_report", "finish_report" -> {
            val id = args.id("sessionId")
            val report = capture.report(id) ?: refuse("REPORT_NOT_FOUND", "This report is not active in the current MCP service.")
            if (tool == "finish_report") recording.stop(id)
            sessionData(session(id)).put("targetPackage", report.target).put("crashCaught", report.crash ?: JSONObject.NULL)
        }
        "list_filters" -> page(args, filters.observeAll().first()) { filterData(it.toLogFilter()) }
        "save_filter" -> saveFilter(args)
        "delete_filter" -> {
            val id = args.id("filterId")
            filters.observeAll().first().firstOrNull { it.id == id }?.let { filters.delete(it) }
            json("deleted" to true)
        }
        "list_automation_rules" -> page(args, automation.observeAll().first()) {
            json("ruleId" to it.id.toString(), "name" to it.name, "enabled" to it.enabled, "package" to it.packageName,
                "query" to it.query, "regex" to it.useRegex, "tag" to it.tagQuery, "targetPackage" to it.targetPackage,
                "action" to it.action, "cooldownSeconds" to it.cooldownSeconds)
        }
        "save_automation_rule" -> saveAutomation(args)
        "delete_automation_rule" -> {
            val id = args.id("ruleId")
            automation.observeAll().first().firstOrNull { it.id == id }?.let { automation.delete(it) }
            json("deleted" to true)
        }
        else -> settingsTools.execute(tool, args)
    }

    private fun status(): JSONObject {
        val access = engine.access.value
        return json(
            "protocolVersion" to MCP_VERSION, "engine" to engine.state.value.name, "access" to access.kind.name,
            "granted" to access.granted, "viewerForeground" to engine.isInForeground(),
            "sourceId" to logs.sourceId(access.remoteEndpoint), "remoteEndpoint" to access.remoteEndpoint,
            "engineError" to engine.lastError.value?.take(256),
            "requiresViewer" to (access.kind.name == "READ_LOGS" &&
                engine.state.value != LogcatEngine.State.RUNNING && !engine.isInForeground()),
            "recording" to recordingData(),
        )
    }

    private fun recordingData(): JSONObject {
        val data = recording.state.value
        return json("sessionId" to data.sessionId?.toString(), "isRecording" to data.isRecording,
            "paused" to data.paused, "lineCount" to data.lineCount, "missedEntries" to data.missedEntries)
    }

    private suspend fun changeRecording(tool: String, args: JSONObject): JSONObject {
        val id = args.id("sessionId")
        if (recording.state.value.sessionId != id) {
            if (tool == "stop_recording" && sessions.getById(id)?.endedAt != null) return sessionData(session(id))
            refuse("RECORDING_CHANGED", "Check status and use the active recording's session ID.")
        }
        when (tool) {
            "pause_recording" -> recording.pause(id)
            "resume_recording" -> recording.resume(id)
            "add_bookmark" -> recording.addBookmark(args.optString("note"), id)
            "stop_recording" -> {
                recording.stop(id)
                return sessionData(session(id))
            }
        }
        if (recording.state.value.sessionId != id) {
            refuse("RECORDING_CHANGED", "This recording ended. Read the saved session for its final state.")
        }
        return recordingData()
    }

    private suspend fun listBookmarks(args: JSONObject): JSONObject {
        val session = session(args.id("sessionId"))
        val items = bookmarks.observeForSession(session.id).first()
        val start = offset(args).coerceAtMost(items.size)
        val selected = items.drop(start).take(limit(args) + 1)
        val selectedTimes = selected.take(limit(args)).map { it.time }
        val lines = logs.bookmarkLines(session, selectedTimes)
        val rows = selected.mapIndexed { index, bookmark ->
            json("bookmarkId" to bookmark.id.toString(), "timeMillis" to bookmark.time,
                "note" to bookmark.note.take(500), "line" to lines.getOrNull(index))
        }
        return page(args, rows, before = start) { it }
    }

    private suspend fun saveFilter(args: JSONObject): JSONObject {
        val id = args.optionalId("filterId")
        val existing = id?.let { value -> filters.observeAll().first().firstOrNull { it.id == value }
            ?: refuse("FILTER_NOT_FOUND", "The filter no longer exists.") }
        val filter = args.getJSONObject("filter").toFilter().copy(
            id = existing?.id ?: 0L, name = name(args), enabled = args.optBoolean("enabled", existing?.enabled ?: false),
            including = args.optBoolean("including", existing?.including ?: true),
        )
        logs.compile(filter)
        val entity = filter.toEntity().copy(createdAt = existing?.createdAt ?: System.currentTimeMillis())
        val saved = if (existing == null) filters.insert(entity) else entity.id.also { filters.update(entity) }
        return filterData(filter.copy(id = saved))
    }

    private suspend fun saveAutomation(args: JSONObject): JSONObject {
        val id = args.optionalId("ruleId")
        val existing = id?.let { value -> automation.observeAll().first().firstOrNull { it.id == value }
            ?: refuse("RULE_NOT_FOUND", "The automation rule no longer exists.") }
        val rule = AutomationRule(
            id = existing?.id ?: 0L, name = name(args), enabled = args.optBoolean("enabled", existing?.enabled ?: true),
            packageName = args.string("package"), query = args.optString("query"), useRegex = args.optBoolean("regex"),
            tagQuery = args.optString("tag"), targetPackage = args.getString("targetPackage"),
            action = args.getString("action"), cooldownSeconds = args.optInt("cooldownSeconds"),
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        if (!rule.hasConstraint || CompiledAutomationRule(rule).regexInvalid) {
            refuse("INVALID_RULE", "Provide a valid rule with at least one match condition.")
        }
        if (apps.identity(rule.targetPackage) == null) refuse("APP_NOT_FOUND", "The receiving app is unavailable.")
        val entity = rule.toEntity()
        val saved = if (existing == null) automation.insert(entity) else entity.id.also { automation.update(entity) }
        return json("ruleId" to saved.toString(), "enabled" to entity.enabled)
    }

    private suspend fun session(id: Long): SessionEntity =
        sessions.getById(id) ?: refuse("SESSION_NOT_FOUND", "The session no longer exists.")

    private suspend fun crash(id: Long): CrashEventEntity =
        crashes.observeById(id).first() ?: refuse("CRASH_NOT_FOUND", "The crash no longer exists.")

    private fun sessionData(session: SessionEntity): JSONObject {
        val active = recording.state.value.takeIf { it.sessionId == session.id }
        return json(
            "sessionId" to session.id.toString(), "name" to session.name.take(256), "startedAt" to session.startedAt,
            "endedAt" to session.endedAt, "lineCount" to (active?.lineCount ?: session.lineCount),
            "source" to session.accessKind, "package" to session.filterPackage, "imported" to session.imported,
            "remoteEndpoint" to session.remoteEndpoint, "recording" to (active != null),
        )
    }

    private fun crashData(crash: CrashEventEntity) = json(
        "crashId" to crash.id.toString(), "sessionId" to crash.sessionId?.toString(), "type" to crash.type,
        "timeMillis" to crash.time, "package" to crash.packageName, "pid" to crash.pid, "name" to crash.appName,
        "firstLine" to crash.firstLine.take(1_024), "version" to crash.versionName,
        "hasContext" to (crash.contextPath != null), "remoteEndpoint" to crash.remoteEndpoint,
    )

    private fun filterData(filter: LogFilter) = json(
        "filterId" to filter.id.toString(), "name" to filter.name, "minLevel" to filter.minLevel.name,
        "levels" to JSONArray(filter.allowedLevels.map { it.name }), "query" to filter.query, "excludeQuery" to filter.excludeQuery,
        "tag" to filter.tagQuery, "tagExact" to filter.tagExact, "regex" to filter.useRegex, "package" to filter.packageName,
        "enabled" to filter.enabled, "including" to filter.including, "pid" to filter.pid, "tid" to filter.tid, "uid" to filter.uid,
    )

    private fun receipt(id: Long) = json("sessionId" to id.toString())

    private fun name(args: JSONObject): String = args.getString("name").trim().ifEmpty {
        refuse("INVALID_ARGUMENT", "The name cannot be blank.")
    }

    private fun offset(args: JSONObject): Int = args.string("cursor")?.let {
        it.toIntOrNull()?.takeIf { value -> value in 0..Int.MAX_VALUE - 200 }
            ?: refuse("INVALID_CURSOR", "The cursor is invalid.")
    } ?: 0

    private fun limit(args: JSONObject): Int = args.optInt("limit", 25)

    private fun <T> page(args: JSONObject, all: List<T>, before: Int? = null, row: (T) -> JSONObject): JSONObject {
        val start = before ?: offset(args).coerceAtMost(all.size)
        val selected = if (before == null) all.drop(start) else all
        val rows = JSONArray()
        var bytes = 0
        for (item in selected.take(limit(args))) {
            val encoded = row(item)
            bytes += encoded.toString().toByteArray(Charsets.UTF_8).size + 1
            if (bytes > 48_000) break
            rows.put(encoded)
        }
        val next = (start + rows.length()).takeIf { rows.length() < selected.size }
        return json("items" to rows, "nextCursor" to next?.toString())
    }
}
