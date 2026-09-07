package io.github.logsleuth.app.data.prefs

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
    /** Show the setup wizard until the user has granted some access once. */
    val setupCompleted: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BUFFER_SIZE = intPreferencesKey("buffer_size")
        val CRASH_NOTIFICATIONS = booleanPreferencesKey("crash_notifications")
        val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        val THEME = stringPreferencesKey("theme")
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            bufferSize = p[Keys.BUFFER_SIZE] ?: 20_000,
            crashNotifications = p[Keys.CRASH_NOTIFICATIONS] ?: true,
            bubbleEnabled = p[Keys.BUBBLE_ENABLED] ?: false,
            theme = p[Keys.THEME] ?: "system",
            setupCompleted = p[Keys.SETUP_COMPLETED] ?: false,
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

    suspend fun setSetupCompleted(value: Boolean) =
        context.dataStore.edit { it[Keys.SETUP_COMPLETED] = value }
}
