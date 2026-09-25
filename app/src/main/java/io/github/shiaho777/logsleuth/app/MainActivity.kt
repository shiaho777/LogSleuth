package io.github.shiaho777.logsleuth.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.shiaho777.logsleuth.app.core.importer.LogImporter
import io.github.shiaho777.logsleuth.app.data.prefs.AppLocales
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
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
        handleIntent(intent)

        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = null)
            val navController = rememberNavController()

            NotificationPermissionEffect()

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

            LogSleuthTheme(themeSetting = settings?.theme ?: "system") {
                val logScale = when (settings?.logTextScale ?: 1) {
                    0 -> 0.85f
                    2 -> 1.15f
                    else -> 1f
                }
                CompositionLocalProvider(LocalLogTextScale provides logScale) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val start = when {
                            settings?.setupCompleted != true -> Routes.SETUP
                            intent?.getBooleanExtra(EXTRA_OPEN_CRASHES, false) == true -> Routes.CRASHES
                            else -> Routes.STREAM
                        }
                        LogSleuthNavHost(navController = navController, startDestination = start)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_CRASHES, false)) {
            pendingRoute = Routes.CRASHES
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        // Notification taps deep-link into a specific session.
        val sessionId = intent.getLongExtra(EXTRA_OPEN_SESSION, -1L)
        if (sessionId > 0) pendingRoute = Routes.sessionDetail(sessionId)
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
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

    @Composable
    private fun NotificationPermissionEffect() {
        if (Build.VERSION.SDK_INT < 33) return
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* result ignored: recording still works, just quieter */ }
        LaunchedEffect(Unit) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_OPEN_CRASHES = "extra_open_crashes"
        const val EXTRA_OPEN_SESSION = "extra_open_session"

        fun crashesIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_CRASHES, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        fun sessionIntent(context: Context, sessionId: Long?): Intent =
            Intent(context, MainActivity::class.java).apply {
                if (sessionId != null) putExtra(EXTRA_OPEN_SESSION, sessionId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}
