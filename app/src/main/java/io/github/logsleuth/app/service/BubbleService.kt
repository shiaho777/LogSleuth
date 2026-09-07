package io.github.logsleuth.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import io.github.logsleuth.app.R
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Floating bubble overlay: drag to move, tap to drop a timestamp bookmark
 * into the active recording, long-press to start/stop recording.
 * Disabled by default; needs the overlay permission.
 */
@AndroidEntryPoint
class BubbleService : Service() {

    @Inject lateinit var recordingManager: RecordingManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var bubble: BubbleView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
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
        view.onToggleRecording = {
            val recording = recordingManager.state.value.isRecording
            val intent = Intent(this, RecordService::class.java).apply {
                action = if (recording) RecordService.ACTION_STOP else RecordService.ACTION_START
            }
            if (recording) startService(intent)
            else androidx.core.content.ContextCompat.startForegroundService(this, intent)
        }
        view.onDrag = { dx, dy ->
            lp.x += dx.toInt()
            lp.y += dy.toInt()
            runCatching { wm.updateViewLayout(view, lp) }
        }

        scope.launch {
            recordingManager.state.collect { state ->
                view.setRecording(state.isRecording)
            }
        }

        wm.addView(view, lp)
        bubble = view
        params = lp
    }

    override fun onDestroy() {
        scope.cancel()
        bubble?.let { runCatching { windowManager?.removeView(it) } }
        bubble = null
        super.onDestroy()
    }
}
