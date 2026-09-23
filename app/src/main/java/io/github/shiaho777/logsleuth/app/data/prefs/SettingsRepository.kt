package io.github.shiaho777.logsleuth.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    /** In-app language: "system" | "en" | "zh-CN" (see AppLocales). */
    val language: String = AppLocales.SYSTEM,
    /** Recordings auto-stop at this size so a long session can't fill storage. */
    val recordingMaxMb: Int = 64,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BUFFER_SIZE = intPreferencesKey("buffer_size")
        val CRASH_NOTIFICATIONS = booleanPreferencesKey("crash_notifications")
        val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        val THEME = stringPreferencesKey("theme")
        val LOG_TEXT_SCALE = intPreferencesKey("log_text_scale")
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        val LANGUAGE = stringPreferencesKey("language")
        val RECORDING_MAX_MB = intPreferencesKey("recording_max_mb")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            bufferSize = p[Keys.BUFFER_SIZE] ?: 20_000,
            crashNotifications = p[Keys.CRASH_NOTIFICATIONS] ?: true,
            bubbleEnabled = p[Keys.BUBBLE_ENABLED] ?: false,
            theme = p[Keys.THEME] ?: "system",
            logTextScale = p[Keys.LOG_TEXT_SCALE] ?: 1,
            setupCompleted = p[Keys.SETUP_COMPLETED] ?: false,
            language = p[Keys.LANGUAGE] ?: AppLocales.SYSTEM,
            recordingMaxMb = p[Keys.RECORDING_MAX_MB] ?: 64,
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

    suspend fun setRecordingMaxMb(value: Int) =
        context.dataStore.edit { it[Keys.RECORDING_MAX_MB] = value.coerceIn(8, 512) }

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
