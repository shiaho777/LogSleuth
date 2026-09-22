package io.github.shiaho777.logsleuth.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.data.prefs.AppLocales
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Floating control pill: start/stop recording, drop a timestamp bookmark into
 * the active recording, or hide itself. Dragging the pill moves it.
 * Needs the overlay permission; the in-app toggle clears it again.
 */
@AndroidEntryPoint
class BubbleService : Service() {

    @Inject lateinit var recordingManager: RecordingManager
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var bubble: BubbleView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        val wm = windowManager ?: return

        val view = BubbleView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 200
        }

        view.onToggleRecording = {
            val recording = recordingManager.state.value.isRecording
            val intent = Intent(this, RecordService::class.java).apply {
                action = if (recording) RecordService.ACTION_STOP else RecordService.ACTION_START
            }
            if (recording) startService(intent)
            else androidx.core.content.ContextCompat.startForegroundService(this, intent)
        }
        view.onBookmark = {
            scope.launch {
                if (recordingManager.state.value.isRecording) {
                    recordingManager.addBookmark()
                    Toast.makeText(this@BubbleService, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@BubbleService, R.string.not_recording, Toast.LENGTH_SHORT).show()
                }
            }
        }
        view.onHide = {
            scope.launch { settingsRepository.setBubbleEnabled(false) }
            stopSelf()
        }
        view.onDrag = { dx, dy ->
            lp.x += dx.toInt()
            lp.y += dy.toInt()
            // Clamp to the visible window — an off-screen pill cannot be
            // reached again without disabling the feature entirely.
            val bounds = displayBounds()
            val w = if (view.width > 0) view.width else view.measuredWidth
            val h = if (view.height > 0) view.height else view.measuredHeight
            lp.x = lp.x.coerceIn(bounds.left, (bounds.right - w).coerceAtLeast(bounds.left))
            lp.y = lp.y.coerceIn(bounds.top, (bounds.bottom - h).coerceAtLeast(bounds.top))
            runCatching { wm.updateViewLayout(view, lp) }
        }

        scope.launch {
            recordingManager.state.collect { state ->
                view.setRecording(state.isRecording)
            }
        }

        // If the permission was revoked while the service was starting,
        // addView throws — don't leave a headless service running.
        runCatching { wm.addView(view, lp) }
            .onFailure { stopSelf() }
        bubble = view
    }

    /** Visible window bounds for clamping the drag position. */
    private fun displayBounds(): android.graphics.Rect {
        val wm = windowManager ?: return android.graphics.Rect()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            android.graphics.Rect().also {
                val dm = android.util.DisplayMetrics()
                wm.defaultDisplay.getMetrics(dm)
                it.set(0, 0, dm.widthPixels, dm.heightPixels)
            }
        }
    }

    // Swiping the app away from recents removes the floating controls too —
    // they only live while the app is around.
    override fun onTaskRemoved(rootIntent: Intent?) {
        scope.launch { settingsRepository.setBubbleEnabled(false) }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        bubble?.let { runCatching { windowManager?.removeView(it) } }
        bubble = null
        super.onDestroy()
    }
}
