package com.pdgus86.voicenotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the widget button tap and flips the recording state. */
class WidgetToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RecordingController.ACTION_TOGGLE_RECORDING) {
            RecordingController.toggleRecording(context)
        }
    }
}
