package io.github.shiaho777.logsleuth.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.shiaho777.logsleuth.app.core.importer.LogImporter
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.data.prefs.AppLocales
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import io.github.shiaho777.logsleuth.app.ui.components.AppIconCache
import io.github.shiaho777.logsleuth.app.ui.components.LocalAppIconCache
import io.github.shiaho777.logsleuth.app.ui.components.LocalLogRowPrefs
import io.github.shiaho777.logsleuth.app.ui.components.LogRowPrefs
import io.github.shiaho777.logsleuth.app.ui.navigation.LogSleuthNavHost
import io.github.shiaho777.logsleuth.app.ui.navigation.Routes
import io.github.shiaho777.logsleuth.app.ui.theme.LocalLogTextScale
import io.github.shiaho777.logsleuth.app.ui.theme.LogSleuthTheme
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var logImporter: LogImporter
    @Inject lateinit var engine: LogcatEngine

    /** Session id produced by importing an externally shared log file. */
    private var importedSessionId by mutableStateOf<Long?>(null)

    /** Route requested by a later intent (singleTask → onNewIntent). */
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A configuration change or process restore re-delivers the launch
        // intent. Replaying it would import the shared file a second time and
        // push the notification's destination onto the restored back stack
        // again; onNewIntent handles genuinely new deliveries.
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = null)
            val preferences = settings ?: return@setContent
            val navController = rememberNavController()
            val startDestination = rememberSaveable {
                when {
                    intent?.getBooleanExtra(EXTRA_OPEN_CRASHES, false) == true -> Routes.CRASHES
                    !preferences.setupCompleted -> Routes.WELCOME
                    preferences.openCrashesOnStartup -> Routes.CRASHES
                    else -> Routes.STREAM
                }
            }

            LaunchedEffect(importedSessionId) {
                importedSessionId?.let {
                    navController.navigate(Routes.sessionDetail(it))
                    importedSessionId = null
                }
            }

            LaunchedEffect(pendingRoute) {
                pendingRoute?.let {
                    navController.navigate(it)
                    pendingRoute = null
                }
            }

            LogSleuthTheme(themeSetting = preferences.theme) {
                val lightSystemBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightSystemBars
                        isAppearanceLightNavigationBars = lightSystemBars
                    }
                }
                val logScale = preferences.logTextScale
                val iconScope = rememberCoroutineScope()
                val iconCache = remember {
                    AppIconCache(applicationContext, iconScope)
                }
                val rowPrefs = LogRowPrefs(
                    showTime = preferences.showLogTime,
                    showDate = preferences.showLogDate,
                    showUid = preferences.showLogUid,
                    showPid = preferences.showLogPid,
                    showTid = preferences.showLogTid,
                    showTag = preferences.showLogTag,
                    showPackage = preferences.showLogPackage,
                    timeFormat = preferences.logTimeFormat,
                    datePattern = preferences.datePattern,
                    timePattern = preferences.timePattern,
                    logsExpanded = preferences.logsExpanded,
                    searchCaseSensitive = preferences.searchCaseSensitive,
                )
                CompositionLocalProvider(
                    LocalLogTextScale provides logScale,
                    LocalAppIconCache provides iconCache,
                    LocalLogRowPrefs provides rowPrefs,
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        LogSleuthNavHost(
                            navController = navController,
                            startDestination = startDestination,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // PROCESS_STATE_TOP, which is what Android 13+ requires before it
        // will show the logcat approval dialog. onStart is too early.
        engine.setInForeground(true)
        // Grants toggled in the Shizuku app or via adb while we were away
        // fire no callback — re-evaluate on every return to foreground.
        engine.refreshAccess()
    }

    override fun onPause() {
        // A live logcat keeps running. This only stops a new READ_LOGS
        // start from being attempted while a dialog or another app is up.
        engine.setInForeground(false)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent, fromNewIntent = true)
    }

    private fun handleIntent(intent: Intent?, fromNewIntent: Boolean = false) {
        intent ?: return
        // Notification taps deep-link into a specific session or crash.
        val sessionId = intent.getLongExtra(EXTRA_OPEN_SESSION, -1L)
        if (sessionId > 0) pendingRoute = Routes.sessionDetail(sessionId)
        val crashId = intent.getLongExtra(EXTRA_OPEN_CRASH, -1L)
        if (crashId > 0) {
            pendingRoute = Routes.crashDetail(crashId)
        } else if (fromNewIntent && intent.getBooleanExtra(EXTRA_OPEN_CRASHES, false)) {
            pendingRoute = Routes.CRASHES
        }
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                stream?.takeIf { intent.type == "application/zip" && it.scheme == "content" }
            }
            else -> null
        } ?: return
        lifecycleScope.launch {
            logImporter.import(uri)
                .onSuccess { importedSessionId = it }
                .onFailure {
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        R.string.import_failed,
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    companion object {
        const val EXTRA_OPEN_CRASHES = "extra_open_crashes"
        const val EXTRA_OPEN_CRASH = "extra_open_crash"
        const val EXTRA_OPEN_SESSION = "extra_open_session"

        fun crashesIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_CRASHES, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        fun crashIntent(context: Context, eventId: Long): Intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_CRASH, eventId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        fun sessionIntent(context: Context, sessionId: Long?): Intent =
            Intent(context, MainActivity::class.java).apply {
                if (sessionId != null) putExtra(EXTRA_OPEN_SESSION, sessionId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}
