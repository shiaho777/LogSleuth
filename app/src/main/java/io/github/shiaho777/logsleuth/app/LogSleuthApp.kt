package io.github.shiaho777.logsleuth.app

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuManager
import io.github.shiaho777.logsleuth.app.data.prefs.AppLocales
import javax.inject.Inject

@HiltAndroidApp
class LogSleuthApp : Application() {

    @Inject lateinit var shizukuManager: ShizukuManager

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocales.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        shizukuManager.attach()
    }
}
