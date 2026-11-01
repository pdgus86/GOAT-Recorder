package com.pdgus86.voicenotes

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * Quick Settings tile. Tap to start recording, tap again to stop.
 * The tile shows an active state while a recording is running.
 */
@RequiresApi(Build.VERSION_CODES.N)
class RecordingTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        RecordingController.toggleRecording(this)
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val recording = RecordingController.isRecording(this)
        tile.state = if (recording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (recording) {
                getString(R.string.tile_recording)
            } else {
                getString(R.string.tile_tap_to_record)
            }
        }
        tile.updateTile()
    }
}
