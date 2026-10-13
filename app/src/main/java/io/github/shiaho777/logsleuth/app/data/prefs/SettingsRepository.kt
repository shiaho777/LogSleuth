package io.github.shiaho777.logsleuth.app.data.prefs

import android.content.Context
import io.github.shiaho777.logsleuth.app.core.logcat.AccessPolicy
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    /** Max entries kept in the on-screen ring buffer. */
    val bufferSize: Int = 20_000,
    val crashNotifications: Boolean = true,
    val bubbleEnabled: Boolean = false,
    /** "system" | "light" | "dark" */
    val theme: String = "system",
    /** Log row text scale, 0.75–1.50. Older installs mapped 0/1/2 presets. */
    val logTextScale: Float = 1f,
    /** Show the setup wizard until the user has granted some access once. */
    val setupCompleted: Boolean = false,
    /** Feature tour overlay has been seen once (skip and finish both count). */
    val guideCompleted: Boolean = false,
    /** In-app language: "system" | "en" | "zh-CN" (see AppLocales). */
    val language: String = AppLocales.SYSTEM,
    /** Recordings auto-stop at this size so a long session can't fill storage. */
    val recordingMaxMb: Int = 64,
    /** Recordings auto-stop after this many hours; 0 = unlimited. */
    val recordingMaxHours: Int = 0,
    /** Package names whose crashes are dropped instead of stored or notified. */
    val crashBlacklist: Set<String> = emptySet(),
    /** Keep a foreground watch alive across reboot so crashes are not missed. */
    val watchOnBoot: Boolean = false,
    val showLogTime: Boolean = true,
    val showLogDate: Boolean = false,
    val showLogUid: Boolean = false,
    val showLogPid: Boolean = true,
    val showLogTid: Boolean = true,
    val showLogTag: Boolean = true,
    val showLogPackage: Boolean = true,
    /** "time" | "datetime" | "epoch" */
    val logTimeFormat: String = "time",
    val datePattern: String = io.github.shiaho777.logsleuth.app.core.logcat.DateTimePatterns.DATE_PRESET,
    val timePattern: String = io.github.shiaho777.logsleuth.app.core.logcat.DateTimePatterns.TIME_PRESET,
    /**
     * Opt-in for a root shell. Default off: an unrooted phone never execs `su`.
     */
    val rootEnabled: Boolean = false,
    /** [AccessPolicy] value: auto, shizuku, root, or adb. */
    val accessPreference: String = AccessPolicy.AUTO,
    /** Cold start follows new lines instead of dumping the ring buffer. */
    val liveOnlyOnStart: Boolean = false,
    val collectJava: Boolean = true,
    val collectNative: Boolean = true,
    val collectAnr: Boolean = true,
    val notifyJava: Boolean = true,
    val notifyNative: Boolean = true,
    val notifyAnr: Boolean = true,
    /** One notification channel per crash type and package. */
    val separateCrashChannels: Boolean = true,
    /** Draw full log lines in the stream instead of clamping to 8. */
    val logsExpanded: Boolean = false,
    /** Search matches case. Default off, which is the previous behavior. */
    val searchCaseSensitive: Boolean = false,
    val crashSort: String = "NEWEST",
    val crashSortReversed: Boolean = false,
    val wrapCrashLogLines: Boolean = true,
    val openCrashesOnStartup: Boolean = false,
    val includeDeviceInfoInZip: Boolean = true,
    val includeAppInfoInZip: Boolean = true,
)

class SettingsRepository(private val context: Context) {

    // Fire-and-forget writes that must survive the caller's teardown —
    // e.g. marking the guide done while its overlay is being torn down.
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val BUFFER_SIZE = intPreferencesKey("buffer_size")
        val CRASH_NOTIFICATIONS = booleanPreferencesKey("crash_notifications")
        val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        val THEME = stringPreferencesKey("theme")
        val LOG_TEXT_SCALE = intPreferencesKey("log_text_scale")
        val LOG_TEXT_SCALE_F = floatPreferencesKey("log_text_scale_f")
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        val GUIDE_COMPLETED = booleanPreferencesKey("guide_completed")
        val LANGUAGE = stringPreferencesKey("language")
        val RECORDING_MAX_MB = intPreferencesKey("recording_max_mb")
        val RECORDING_MAX_HOURS = intPreferencesKey("recording_max_hours")
        val CRASH_BLACKLIST = stringSetPreferencesKey("crash_blacklist")
        val WATCH_ON_BOOT = booleanPreferencesKey("watch_on_boot")
        val SHOW_LOG_TIME = booleanPreferencesKey("show_log_time")
        val SHOW_LOG_DATE = booleanPreferencesKey("show_log_date")
        val SHOW_LOG_UID = booleanPreferencesKey("show_log_uid")
        val SHOW_LOG_PID = booleanPreferencesKey("show_log_pid")
        val SHOW_LOG_TID = booleanPreferencesKey("show_log_tid")
        val SHOW_LOG_TAG = booleanPreferencesKey("show_log_tag")
        val SHOW_LOG_PACKAGE = booleanPreferencesKey("show_log_package")
        val LOG_TIME_FORMAT = stringPreferencesKey("log_time_format")
        val DATE_PATTERN = stringPreferencesKey("date_pattern")
        val TIME_PATTERN = stringPreferencesKey("time_pattern")
        val CRASH_SORT = stringPreferencesKey("crash_sort")
        val CRASH_SORT_REVERSED = booleanPreferencesKey("crash_sort_reversed")
        val WRAP_CRASH_LOG = booleanPreferencesKey("wrap_crash_log")
        val OPEN_CRASHES_ON_STARTUP = booleanPreferencesKey("open_crashes_on_startup")
        val INCLUDE_DEVICE_ZIP = booleanPreferencesKey("include_device_zip")
        val INCLUDE_APP_ZIP = booleanPreferencesKey("include_app_zip")
        val ROOT_ENABLED = booleanPreferencesKey("root_enabled")
        val ACCESS_PREFERENCE = stringPreferencesKey("access_preference")
        val LIVE_ONLY_ON_START = booleanPreferencesKey("live_only_on_start")
        val COLLECT_JAVA = booleanPreferencesKey("collect_java")
        val COLLECT_NATIVE = booleanPreferencesKey("collect_native")
        val COLLECT_ANR = booleanPreferencesKey("collect_anr")
        val NOTIFY_JAVA = booleanPreferencesKey("notify_java")
        val NOTIFY_NATIVE = booleanPreferencesKey("notify_native")
        val NOTIFY_ANR = booleanPreferencesKey("notify_anr")
        val SEPARATE_CRASH_CHANNELS = booleanPreferencesKey("separate_crash_channels")
        val LOGS_EXPANDED = booleanPreferencesKey("logs_expanded")
        val SEARCH_CASE_SENSITIVE = booleanPreferencesKey("search_case_sensitive")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            bufferSize = p[Keys.BUFFER_SIZE] ?: 20_000,
            crashNotifications = p[Keys.CRASH_NOTIFICATIONS] ?: true,
            bubbleEnabled = p[Keys.BUBBLE_ENABLED] ?: false,
            theme = p[Keys.THEME] ?: "system",
            logTextScale = p[Keys.LOG_TEXT_SCALE_F] ?: when (p[Keys.LOG_TEXT_SCALE]) {
                0 -> 0.85f
                2 -> 1.15f
                else -> 1f
            },
            setupCompleted = p[Keys.SETUP_COMPLETED] ?: false,
            guideCompleted = p[Keys.GUIDE_COMPLETED] ?: false,
            language = p[Keys.LANGUAGE] ?: AppLocales.SYSTEM,
            recordingMaxMb = p[Keys.RECORDING_MAX_MB] ?: 64,
            recordingMaxHours = p[Keys.RECORDING_MAX_HOURS] ?: 0,
            crashBlacklist = p[Keys.CRASH_BLACKLIST] ?: emptySet(),
            watchOnBoot = p[Keys.WATCH_ON_BOOT] ?: false,
            showLogTime = p[Keys.SHOW_LOG_TIME] ?: true,
            showLogDate = p[Keys.SHOW_LOG_DATE] ?: false,
            showLogUid = p[Keys.SHOW_LOG_UID] ?: false,
            showLogPid = p[Keys.SHOW_LOG_PID] ?: true,
            showLogTid = p[Keys.SHOW_LOG_TID] ?: true,
            showLogTag = p[Keys.SHOW_LOG_TAG] ?: true,
            showLogPackage = p[Keys.SHOW_LOG_PACKAGE] ?: true,
            logTimeFormat = p[Keys.LOG_TIME_FORMAT] ?: "time",
            datePattern = p[Keys.DATE_PATTERN]
                ?: io.github.shiaho777.logsleuth.app.core.logcat.DateTimePatterns.DATE_PRESET,
            timePattern = p[Keys.TIME_PATTERN]
                ?: io.github.shiaho777.logsleuth.app.core.logcat.DateTimePatterns.TIME_PRESET,
            rootEnabled = p[Keys.ROOT_ENABLED] ?: false,
            accessPreference = p[Keys.ACCESS_PREFERENCE] ?: AccessPolicy.AUTO,
            liveOnlyOnStart = p[Keys.LIVE_ONLY_ON_START] ?: false,
            collectJava = p[Keys.COLLECT_JAVA] ?: true,
            collectNative = p[Keys.COLLECT_NATIVE] ?: true,
            collectAnr = p[Keys.COLLECT_ANR] ?: true,
            notifyJava = p[Keys.NOTIFY_JAVA] ?: true,
            notifyNative = p[Keys.NOTIFY_NATIVE] ?: true,
            notifyAnr = p[Keys.NOTIFY_ANR] ?: true,
            separateCrashChannels = p[Keys.SEPARATE_CRASH_CHANNELS] ?: true,
            logsExpanded = p[Keys.LOGS_EXPANDED] ?: false,
            searchCaseSensitive = p[Keys.SEARCH_CASE_SENSITIVE] ?: false,
            crashSort = p[Keys.CRASH_SORT] ?: "NEWEST",
            crashSortReversed = p[Keys.CRASH_SORT_REVERSED] ?: false,
            wrapCrashLogLines = p[Keys.WRAP_CRASH_LOG] ?: true,
            openCrashesOnStartup = p[Keys.OPEN_CRASHES_ON_STARTUP] ?: false,
            includeDeviceInfoInZip = p[Keys.INCLUDE_DEVICE_ZIP] ?: true,
            includeAppInfoInZip = p[Keys.INCLUDE_APP_ZIP] ?: true,
        )
    }

    suspend fun setBufferSize(value: Int) =
        context.dataStore.edit { it[Keys.BUFFER_SIZE] = value.coerceIn(1_000, 200_000) }

    suspend fun setCrashNotifications(value: Boolean) =
        context.dataStore.edit { it[Keys.CRASH_NOTIFICATIONS] = value }

    suspend fun setBubbleEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.BUBBLE_ENABLED] = value }

    suspend fun setTheme(value: String) =
        context.dataStore.edit { it[Keys.THEME] = value }

    suspend fun setLogTextScale(value: Float) =
        context.dataStore.edit { it[Keys.LOG_TEXT_SCALE_F] = value.coerceIn(0.75f, 1.50f) }

    suspend fun setSetupCompleted(value: Boolean) =
        context.dataStore.edit { it[Keys.SETUP_COMPLETED] = value }

    /**
     * Non-suspending variants running on this repo's own scope — a call
     * right before a ViewModel/composition teardown cannot be cancelled.
     */
    fun setSetupCompletedAsync(value: Boolean) {
        writeScope.launch { setSetupCompleted(value) }
    }

    suspend fun setGuideCompleted(value: Boolean) =
        context.dataStore.edit { it[Keys.GUIDE_COMPLETED] = value }

    fun setGuideCompletedAsync(value: Boolean) {
        writeScope.launch { setGuideCompleted(value) }
    }

    suspend fun setRecordingMaxMb(value: Int) =
        context.dataStore.edit { it[Keys.RECORDING_MAX_MB] = value.coerceIn(8, 512) }

    suspend fun setRecordingMaxHours(value: Int) =
        context.dataStore.edit { it[Keys.RECORDING_MAX_HOURS] = value.coerceIn(0, 24) }

    suspend fun setWatchOnBoot(value: Boolean) =
        context.dataStore.edit { it[Keys.WATCH_ON_BOOT] = value }

    suspend fun ignorePackage(packageName: String) {
        if (packageName.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.CRASH_BLACKLIST] ?: emptySet()
            prefs[Keys.CRASH_BLACKLIST] = current + packageName
        }
    }

    suspend fun unignorePackage(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.CRASH_BLACKLIST] ?: emptySet()
            prefs[Keys.CRASH_BLACKLIST] = current - packageName
        }
    }

    suspend fun setShowLogTime(value: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_LOG_TIME] = value }

    suspend fun setShowLogDate(value: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_LOG_DATE] = value }

    suspend fun setShowLogUid(value: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_LOG_UID] = value }

    suspend fun setShowLogPid(value: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_LOG_PID] = value }

    suspend fun setShowLogTid(value: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_LOG_TID] = value }

    suspend fun setShowLogTag(value: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_LOG_TAG] = value }

    suspend fun setShowLogPackage(value: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_LOG_PACKAGE] = value }

    suspend fun setLogTimeFormat(value: String) {
        val allowed = setOf("time", "datetime", "epoch")
        context.dataStore.edit { it[Keys.LOG_TIME_FORMAT] = if (value in allowed) value else "time" }
    }

    suspend fun setRootEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.ROOT_ENABLED] = value }

    suspend fun setLiveOnlyOnStart(value: Boolean) =
        context.dataStore.edit { it[Keys.LIVE_ONLY_ON_START] = value }

    suspend fun setCollectJava(value: Boolean) =
        context.dataStore.edit { it[Keys.COLLECT_JAVA] = value }

    suspend fun setCollectNative(value: Boolean) =
        context.dataStore.edit { it[Keys.COLLECT_NATIVE] = value }

    suspend fun setCollectAnr(value: Boolean) =
        context.dataStore.edit { it[Keys.COLLECT_ANR] = value }

    suspend fun setNotifyJava(value: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFY_JAVA] = value }

    suspend fun setNotifyNative(value: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFY_NATIVE] = value }

    suspend fun setNotifyAnr(value: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFY_ANR] = value }

    suspend fun setSeparateCrashChannels(value: Boolean) =
        context.dataStore.edit { it[Keys.SEPARATE_CRASH_CHANNELS] = value }

    suspend fun setLogsExpanded(value: Boolean) =
        context.dataStore.edit { it[Keys.LOGS_EXPANDED] = value }

    suspend fun setSearchCaseSensitive(value: Boolean) =
        context.dataStore.edit { it[Keys.SEARCH_CASE_SENSITIVE] = value }

    suspend fun setDatePattern(value: String) =
        context.dataStore.edit { it[Keys.DATE_PATTERN] = value }

    suspend fun setTimePattern(value: String) =
        context.dataStore.edit { it[Keys.TIME_PATTERN] = value }

    suspend fun setCrashSort(value: String) =
        context.dataStore.edit { it[Keys.CRASH_SORT] = value }

    suspend fun setCrashSortReversed(value: Boolean) =
        context.dataStore.edit { it[Keys.CRASH_SORT_REVERSED] = value }

    suspend fun setWrapCrashLogLines(value: Boolean) =
        context.dataStore.edit { it[Keys.WRAP_CRASH_LOG] = value }

    suspend fun setOpenCrashesOnStartup(value: Boolean) =
        context.dataStore.edit { it[Keys.OPEN_CRASHES_ON_STARTUP] = value }

    suspend fun setIncludeDeviceInfoInZip(value: Boolean) =
        context.dataStore.edit { it[Keys.INCLUDE_DEVICE_ZIP] = value }

    suspend fun setIncludeAppInfoInZip(value: Boolean) =
        context.dataStore.edit { it[Keys.INCLUDE_APP_ZIP] = value }

    suspend fun setAccessPreference(value: String) {
        val allowed = setOf(
            AccessPolicy.AUTO,
            AccessPolicy.SHIZUKU,
            AccessPolicy.ROOT,
            AccessPolicy.ADB,
        )
        context.dataStore.edit {
            it[Keys.ACCESS_PREFERENCE] = if (value in allowed) value else AccessPolicy.AUTO
        }
    }

    /**
     * Mirrors the choice into DataStore (for UI state) and applies the
     * override synchronously via [AppLocales] — the caller recreates the
     * activity on <33, the framework restarts it on 33+.
     */
    suspend fun setLanguage(value: String) {
        AppLocales.setOverride(context, value)
        context.dataStore.edit { it[Keys.LANGUAGE] = AppLocales.overrideTag(context) }
    }
}
