package io.github.shiaho777.logsleuth.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Starts the crash watch after reboot when the user left that switch on. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (settingsRepository.settings.first().watchOnBoot) {
                    CrashWatchService.start(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
