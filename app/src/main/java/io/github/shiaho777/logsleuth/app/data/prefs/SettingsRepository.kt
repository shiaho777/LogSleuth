package io.github.shiaho777.logsleuth.app.data.prefs

import android.content.Context
import io.github.shiaho777.logsleuth.app.core.logcat.AccessPolicy
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    /** Log row text scale preset: 0 compact, 1 default, 2 comfortable. */
    val logTextScale: Int = 1,
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
    val showLogPid: Boolean = true,
    val showLogTid: Boolean = true,
    val showLogTag: Boolean = true,
    val showLogPackage: Boolean = true,
    /** "time" | "datetime" | "epoch" */
    val logTimeFormat: String = "time",
    /**
     * Opt-in for a root shell. Default off: an unrooted phone never execs `su`.
     */
    val rootEnabled: Boolean = false,
    /** [AccessPolicy] value: auto, shizuku, root, or adb. */
    val accessPreference: String = AccessPolicy.AUTO,
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
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        val GUIDE_COMPLETED = booleanPreferencesKey("guide_completed")
        val LANGUAGE = stringPreferencesKey("language")
        val RECORDING_MAX_MB = intPreferencesKey("recording_max_mb")
        val RECORDING_MAX_HOURS = intPreferencesKey("recording_max_hours")
        val CRASH_BLACKLIST = stringSetPreferencesKey("crash_blacklist")
        val WATCH_ON_BOOT = booleanPreferencesKey("watch_on_boot")
        val SHOW_LOG_TIME = booleanPreferencesKey("show_log_time")
        val SHOW_LOG_PID = booleanPreferencesKey("show_log_pid")
        val SHOW_LOG_TID = booleanPreferencesKey("show_log_tid")
        val SHOW_LOG_TAG = booleanPreferencesKey("show_log_tag")
        val SHOW_LOG_PACKAGE = booleanPreferencesKey("show_log_package")
        val LOG_TIME_FORMAT = stringPreferencesKey("log_time_format")
        val ROOT_ENABLED = booleanPreferencesKey("root_enabled")
        val ACCESS_PREFERENCE = stringPreferencesKey("access_preference")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            bufferSize = p[Keys.BUFFER_SIZE] ?: 20_000,
            crashNotifications = p[Keys.CRASH_NOTIFICATIONS] ?: true,
            bubbleEnabled = p[Keys.BUBBLE_ENABLED] ?: false,
            theme = p[Keys.THEME] ?: "system",
            logTextScale = p[Keys.LOG_TEXT_SCALE] ?: 1,
            setupCompleted = p[Keys.SETUP_COMPLETED] ?: false,
            guideCompleted = p[Keys.GUIDE_COMPLETED] ?: false,
            language = p[Keys.LANGUAGE] ?: AppLocales.SYSTEM,
            recordingMaxMb = p[Keys.RECORDING_MAX_MB] ?: 64,
            recordingMaxHours = p[Keys.RECORDING_MAX_HOURS] ?: 0,
            crashBlacklist = p[Keys.CRASH_BLACKLIST] ?: emptySet(),
            watchOnBoot = p[Keys.WATCH_ON_BOOT] ?: false,
            showLogTime = p[Keys.SHOW_LOG_TIME] ?: true,
            showLogPid = p[Keys.SHOW_LOG_PID] ?: true,
            showLogTid = p[Keys.SHOW_LOG_TID] ?: true,
            showLogTag = p[Keys.SHOW_LOG_TAG] ?: true,
            showLogPackage = p[Keys.SHOW_LOG_PACKAGE] ?: true,
            logTimeFormat = p[Keys.LOG_TIME_FORMAT] ?: "time",
            rootEnabled = p[Keys.ROOT_ENABLED] ?: false,
            accessPreference = p[Keys.ACCESS_PREFERENCE] ?: AccessPolicy.AUTO,
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

    suspend fun setLogTextScale(value: Int) =
        context.dataStore.edit { it[Keys.LOG_TEXT_SCALE] = value.coerceIn(0, 2) }

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
