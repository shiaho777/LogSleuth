package io.github.shiaho777.logsleuth.app.mcp

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.shiaho777.logsleuth.app.core.logcat.AccessPolicy
import io.github.shiaho777.logsleuth.app.core.logcat.DateTimePatterns
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.core.remoteadb.RemoteAdbManager
import io.github.shiaho777.logsleuth.app.core.update.UpdateManager
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import io.github.shiaho777.logsleuth.app.service.AutomationService
import io.github.shiaho777.logsleuth.app.service.BubbleService
import io.github.shiaho777.logsleuth.app.service.CrashWatchService
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class McpSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val engine: LogcatEngine,
    private val remote: RemoteAdbManager,
    private val updates: UpdateManager,
) {
    suspend fun execute(tool: String, args: JSONObject): JSONObject = when (tool) {
        "get_settings" -> current()
        "change_settings" -> {
            change(args.getJSONObject("settings"))
            current()
        }
        "reset_settings" -> {
            settings.resetToDefaults()
            engine.refreshAccess()
            current()
        }
        "set_automation_enabled" -> {
            val enabled = args.getBoolean("enabled")
            toggleService(AutomationService::class.java, enabled, settings.settings.first().automationEnabled,
                settings::setAutomationEnabled, { AutomationService.start(context) })
            json("enabled" to enabled)
        }
        "set_crash_watch" -> {
            val enabled = args.getBoolean("enabled")
            toggleService(CrashWatchService::class.java, enabled, settings.settings.first().watchOnBoot,
                settings::setWatchOnBoot, {
                    settings.clearWatchBlocked()
                    CrashWatchService.start(context)
                })
            json("enabled" to enabled)
        }
        "set_floating_controls" -> {
            val enabled = args.getBoolean("enabled")
            if (enabled && !AndroidSettings.canDrawOverlays(context)) {
                refuse("USER_ACTION_REQUIRED", "Grant overlay permission in LogSleuth settings.")
            }
            val intent = Intent(context, BubbleService::class.java)
            toggleService(BubbleService::class.java, enabled, settings.settings.first().bubbleEnabled,
                settings::setBubbleEnabled, {
                    if (context.startService(intent) == null) refuse("SERVICE_FAILED", "Floating controls could not start.")
                })
            json("enabled" to enabled)
        }
        "set_log_source" -> {
            val mode = args.getString("mode")
            val state = settings.settings.first()
            if (mode == AccessPolicy.ROOT && !state.rootEnabled) {
                refuse("USER_ACTION_REQUIRED", "Enable Root in LogSleuth settings before selecting it.")
            }
            if (mode == "remote_adb" && state.remoteAdbHost.isBlank()) {
                refuse("USER_ACTION_REQUIRED", "Configure the remote device in LogSleuth settings first.")
            }
            settings.setAccessPreference(if (mode == "remote_adb") AccessPolicy.REMOTE else mode)
            engine.refreshAccess()
            source()
        }
        "refresh_log_source" -> {
            engine.refreshAccess()
            source()
        }
        "restart_log_source" -> {
            engine.restart()
            source()
        }
        "test_remote_source" -> {
            if (settings.settings.first().remoteAdbHost.isBlank()) {
                refuse("USER_ACTION_REQUIRED", "Configure the remote device in LogSleuth settings first.")
            }
            remote.testConnection()
            source()
        }
        "disconnect_remote_source" -> {
            remote.disconnect()
            source()
        }
        "get_update_status" -> version()
        "check_updates" -> { updates.checkNow(); version() }
        "download_update" -> {
            val release = updates.state.value.releases.firstOrNull { it.tag == args.getString("tag") && it.apkUrl != null }
                ?: refuse("RELEASE_NOT_FOUND", "Check updates first and select an available APK release.")
            updates.start(release)
            version()
        }
        "pause_update_download" -> { updates.pause(); version() }
        "resume_update_download" -> { updates.resume(); version() }
        "cancel_update_download" -> { updates.cancelDownload(); version() }
        else -> refuse("UNKNOWN_TOOL", "Unknown LogSleuth tool.")
    }

    private suspend fun toggleService(
        component: Class<*>,
        enabled: Boolean,
        previous: Boolean,
        persist: suspend (Boolean) -> Any?,
        start: suspend () -> Unit,
    ) {
        if (enabled && !engine.isInForeground()) {
            refuse("FOREGROUND_REQUIRED", "Open LogSleuth before enabling this Android service.")
        }
        persist(enabled)
        try {
            if (enabled) start() else context.stopService(Intent(context, component))
        } catch (failure: IllegalStateException) {
            persist(previous)
            refuse("FOREGROUND_REQUIRED", "Open LogSleuth and retry the operation.")
        } catch (failure: SecurityException) {
            persist(previous)
            refuse("USER_ACTION_REQUIRED", "Check the Android permission for this feature.")
        }
    }

    private fun source() = json(
        "source" to engine.access.value.kind.name, "engine" to engine.state.value.name,
        "remote" to remote.state.value.status.name, "endpoint" to engine.access.value.remoteEndpoint,
    )

    private fun version(): JSONObject {
        val s = updates.state.value
        return json(
            "checking" to s.checking, "checkError" to s.checkError?.take(256),
            "latestTag" to s.latest?.tag, "updateAvailable" to s.updateAvailable,
            "download" to s.dlStatus.name, "downloadTag" to s.dlRelease?.tag,
            "bytes" to s.dlDoneBytes, "totalBytes" to s.dlTotalBytes, "downloadError" to s.dlError?.take(256),
        )
    }

    private suspend fun current(): JSONObject {
        val s = settings.settings.first()
        return json(
            "theme" to s.theme,
            "language" to s.language,
            "bufferSize" to s.bufferSize,
            "recordingMaxMb" to s.recordingMaxMb,
            "recordingMaxHours" to s.recordingMaxHours,
            "logTextScale" to s.logTextScale,
            "logTimeFormat" to s.logTimeFormat,
            "datePattern" to s.datePattern,
            "timePattern" to s.timePattern,
            "crashSort" to s.crashSort,
            "crashNotifications" to s.crashNotifications,
            "liveOnlyOnStart" to s.liveOnlyOnStart,
            "collectJava" to s.collectJava,
            "collectNative" to s.collectNative,
            "collectAnr" to s.collectAnr,
            "notifyJava" to s.notifyJava,
            "notifyNative" to s.notifyNative,
            "notifyAnr" to s.notifyAnr,
            "separateCrashChannels" to s.separateCrashChannels,
            "logsExpanded" to s.logsExpanded,
            "searchCaseSensitive" to s.searchCaseSensitive,
            "openCrashesOnStartup" to s.openCrashesOnStartup,
            "includeDeviceInfoInZip" to s.includeDeviceInfoInZip,
            "includeAppInfoInZip" to s.includeAppInfoInZip,
            "showLogTime" to s.showLogTime,
            "showLogDate" to s.showLogDate,
            "showLogUid" to s.showLogUid,
            "showLogPid" to s.showLogPid,
            "showLogTid" to s.showLogTid,
            "showLogTag" to s.showLogTag,
            "showLogPackage" to s.showLogPackage,
            "crashSortReversed" to s.crashSortReversed,
            "wrapCrashLogLines" to s.wrapCrashLogLines,
            "watchOnBoot" to s.watchOnBoot,
            "automationEnabled" to s.automationEnabled,
            "bubbleEnabled" to s.bubbleEnabled,
            "rootEnabled" to s.rootEnabled,
            "accessPreference" to s.accessPreference,
            "remoteAdbHost" to s.remoteAdbHost,
            "remoteAdbPort" to s.remoteAdbPort,
        )
    }

    private suspend fun change(patch: JSONObject) {
        for (key in listOf("datePattern", "timePattern")) {
            val pattern = patch.string(key) ?: continue
            if (pattern.isNotBlank() && !DateTimePatterns.isValid(pattern)) {
                refuse("INVALID_ARGUMENT", "Invalid date or time pattern.")
            }
        }
        for (key in patch.keys()) {
            when (key) {
                "theme" -> settings.setTheme(patch.getString(key))
                "language" -> withContext(Dispatchers.Main) { settings.setLanguage(patch.getString(key)) }
                "bufferSize" -> settings.setBufferSize(patch.getInt(key))
                "recordingMaxMb" -> settings.setRecordingMaxMb(patch.getInt(key))
                "recordingMaxHours" -> settings.setRecordingMaxHours(patch.getInt(key))
                "logTextScale" -> settings.setLogTextScale(patch.getDouble(key).toFloat())
                "logTimeFormat" -> settings.setLogTimeFormat(patch.getString(key))
                "datePattern" -> settings.setDatePattern(patch.getString(key))
                "timePattern" -> settings.setTimePattern(patch.getString(key))
                "crashSort" -> settings.setCrashSort(patch.getString(key))
                "crashNotifications" -> settings.setCrashNotifications(patch.getBoolean(key))
                "liveOnlyOnStart" -> settings.setLiveOnlyOnStart(patch.getBoolean(key))
                "collectJava" -> settings.setCollectJava(patch.getBoolean(key))
                "collectNative" -> settings.setCollectNative(patch.getBoolean(key))
                "collectAnr" -> settings.setCollectAnr(patch.getBoolean(key))
                "notifyJava" -> settings.setNotifyJava(patch.getBoolean(key))
                "notifyNative" -> settings.setNotifyNative(patch.getBoolean(key))
                "notifyAnr" -> settings.setNotifyAnr(patch.getBoolean(key))
                "separateCrashChannels" -> settings.setSeparateCrashChannels(patch.getBoolean(key))
                "logsExpanded" -> settings.setLogsExpanded(patch.getBoolean(key))
                "searchCaseSensitive" -> settings.setSearchCaseSensitive(patch.getBoolean(key))
                "openCrashesOnStartup" -> settings.setOpenCrashesOnStartup(patch.getBoolean(key))
                "includeDeviceInfoInZip" -> settings.setIncludeDeviceInfoInZip(patch.getBoolean(key))
                "includeAppInfoInZip" -> settings.setIncludeAppInfoInZip(patch.getBoolean(key))
                "showLogTime" -> settings.setShowLogTime(patch.getBoolean(key))
                "showLogDate" -> settings.setShowLogDate(patch.getBoolean(key))
                "showLogUid" -> settings.setShowLogUid(patch.getBoolean(key))
                "showLogPid" -> settings.setShowLogPid(patch.getBoolean(key))
                "showLogTid" -> settings.setShowLogTid(patch.getBoolean(key))
                "showLogTag" -> settings.setShowLogTag(patch.getBoolean(key))
                "showLogPackage" -> settings.setShowLogPackage(patch.getBoolean(key))
                "crashSortReversed" -> settings.setCrashSortReversed(patch.getBoolean(key))
                "wrapCrashLogLines" -> settings.setWrapCrashLogLines(patch.getBoolean(key))
                else -> refuse("INVALID_ARGUMENT", "Unknown setting.")
            }
        }
    }
}
