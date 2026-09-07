package io.github.logsleuth.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.logsleuth.app.core.shizuku.ShizukuManager
import javax.inject.Inject

@HiltAndroidApp
class LogSleuthApp : Application() {

    @Inject lateinit var shizukuManager: ShizukuManager

    override fun onCreate() {
        super.onCreate()
        shizukuManager.attach()
    }
}
