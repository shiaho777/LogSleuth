package io.github.logsleuth.app.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import io.github.logsleuth.app.R
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick-settings tile: tap to start/stop a recording session. */
@AndroidEntryPoint
class RecordingTileService : TileService() {

    @Inject lateinit var recordingManager: RecordingManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            recordingManager.state.collect { state ->
                qsTile?.apply {
                    this.state = if (state.isRecording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    label = if (state.isRecording) {
                        getString(R.string.tile_recording, state.lineCount)
                    } else {
                        getString(R.string.tile_record)
                    }
                    updateTile()
                }
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        scope.cancel()
    }

    override fun onClick() {
        super.onClick()
        val recording = recordingManager.state.value.isRecording
        val intent = Intent(this, RecordService::class.java).apply {
            action = if (recording) RecordService.ACTION_STOP else RecordService.ACTION_START
        }
        if (recording) startService(intent)
        else androidx.core.content.ContextCompat.startForegroundService(this, intent)
    }
}
