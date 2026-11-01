package com.pdgus86.voicenotes

import android.content.Context
import android.content.Intent

/**
 * Single place that knows whether a recording is running.
 * The widget, the Quick Settings tile, and the main screen all read and
 * change recording state through here, so they can never disagree.
 */
object RecordingController {

    const val ACTION_TOGGLE_RECORDING = "com.pdgus86.voicenotes.ACTION_TOGGLE_RECORDING"
    const val ACTION_RECORDING_STATE_CHANGED =
        "com.pdgus86.voicenotes.ACTION_RECORDING_STATE_CHANGED"

    private const val PREFS = "voicenotes_state"
    private const val KEY_IS_RECORDING = "is_recording"
    private const val KEY_SESSION_ID = "session_id"
    private const val KEY_SESSION_START_MS = "session_start_ms"

    fun isRecording(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_RECORDING, false)
    }

    fun currentSessionId(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SESSION_ID, null)
    }

    fun startRecording(context: Context) {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    fun stopRecording(context: Context) {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun toggleRecording(context: Context) {
        if (isRecording(context)) {
            stopRecording(context)
        } else {
            startRecording(context)
        }
    }

    /** Called by the service when its real state changes. */
    fun setRecordingState(
        context: Context,
        recording: Boolean,
        sessionId: String?,
        sessionStartMs: Long
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_IS_RECORDING, recording)
            .putString(KEY_SESSION_ID, sessionId)
            .putLong(KEY_SESSION_START_MS, sessionStartMs)
            .apply()
        broadcastStateChanged(context)
    }

    fun broadcastStateChanged(context: Context) {
        val intent = Intent(ACTION_RECORDING_STATE_CHANGED)
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }
}
