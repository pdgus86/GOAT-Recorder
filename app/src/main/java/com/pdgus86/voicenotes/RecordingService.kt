package com.pdgus86.voicenotes

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.pdgus86.voicenotes.data.JobQueueRepository
import com.pdgus86.voicenotes.model.TranscriptionJob
import com.pdgus86.voicenotes.worker.JobQueueScheduler
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground recording service.
 *
 * Records to M4A files in chunks of [CHUNK_MS] so a crash or a dead battery
 * never loses more than one chunk of a long recording. Runs in the
 * foreground with a persistent notification, so Android keeps it alive
 * with the screen off and in the background.
 *
 * The microphone comes from the system audio source, so a connected
 * Bluetooth headset mic is used automatically when Bluetooth SCO audio
 * is active.
 */
class RecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "voicenotes_recording"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.pdgus86.voicenotes.ACTION_START_RECORDING"
        const val ACTION_STOP = "com.pdgus86.voicenotes.ACTION_STOP_RECORDING"

        /** New file every 15 minutes. */
        const val CHUNK_MS = 15L * 60L * 1000L
    }

    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var sessionId: String = ""
    private var sessionStartMs: Long = 0L
    private var partNumber = 1
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    private val rolloverRunnable = Runnable {
        if (isRecording) {
            rolloverToNewFile()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
            null -> {
                // System restarted the service after killing it. If we were
                // recording, pick the session back up with a fresh chunk.
                if (RecordingController.isRecording(this)) {
                    val savedId = RecordingController.currentSessionId(this)
                    if (savedId != null) {
                        sessionId = savedId
                        startRecording(resumeSession = true)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun startRecording(resumeSession: Boolean = false) {
        if (isRecording) return
        if (!resumeSession) {
            sessionId = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            sessionStartMs = System.currentTimeMillis()
            partNumber = 1
        }
        try {
            acquireWakeLock()
            startBluetoothSco()
            startNewChunk()
            isRecording = true
            RecordingController.setRecordingState(this, true, sessionId, sessionStartMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            handler.postDelayed(rolloverRunnable, CHUNK_MS)
            VoiceNotesWidget.updateAllWidgets(this)
        } catch (e: Exception) {
            releaseRecorderQuietly()
            releaseWakeLock()
            RecordingController.setRecordingState(this, false, null, 0L)
            stopSelf()
        }
    }

    private fun stopRecording() {
        if (!isRecording) {
            stopSelf()
            return
        }
        handler.removeCallbacks(rolloverRunnable)
        stopRecorderQuietly()
        isRecording = false
        // The recording is saved locally no matter what. Queue the
        // transcription job so it runs when a provider can be reached.
        // The worker only fires when the phone has a connection again.
        val jobQueue = JobQueueRepository(this)
        jobQueue.enqueue(TranscriptionJob(sessionId = sessionId))
        JobQueueScheduler.schedule(this)
        stopBluetoothSco()
        releaseWakeLock()
        RecordingController.setRecordingState(this, false, null, 0L)
        stopForeground(STOP_FOREGROUND_REMOVE)
        VoiceNotesWidget.updateAllWidgets(this)
        stopSelf()
    }

    private fun rolloverToNewFile() {
        stopRecorderQuietly()
        partNumber++
        try {
            startNewChunk()
            handler.postDelayed(rolloverRunnable, CHUNK_MS)
        } catch (e: Exception) {
            // If the new chunk fails, stop cleanly rather than spinning.
            stopRecording()
        }
    }

    private fun startNewChunk() {
        val file = chunkFile(partNumber)
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        this.recorder = recorder
    }

    private fun stopRecorderQuietly() {
        try {
            recorder?.stop()
        } catch (e: Exception) {
            // stop() throws if nothing was recorded yet. The file is
            // discarded in that case.
        }
        releaseRecorderQuietly()
    }

    private fun releaseRecorderQuietly() {
        try {
            recorder?.release()
        } catch (e: Exception) {
            // Already released, nothing to do.
        }
        recorder = null
    }

    private fun chunkFile(part: Int): File {
        val dir = recordingsDir()
        if (!dir.exists()) dir.mkdirs()
        val name = "rec_${sessionId}_part%02d.m4a".format(Locale.US, part)
        return File(dir, name)
    }

    private fun recordingsDir(): File {
        val external = getExternalFilesDir("VoiceNotes")
        return external ?: File(filesDir, "VoiceNotes")
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, RecordingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText(getString(R.string.notification_recording_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_stop,
                getString(R.string.notification_stop),
                stopIntent
            )
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VoiceNotes::RecordingWakeLock"
        ).apply {
            acquire(CHUNK_MS * 12) // 3 hours max, released on stop anyway.
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.release()
        } catch (e: Exception) {
            // Already released.
        }
        wakeLock = null
    }

    /** Best effort: ask the system to route audio through a Bluetooth mic. */
    private fun startBluetoothSco() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) return
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        } catch (e: Exception) {
            // No Bluetooth mic available or no permission. The phone mic
            // is used instead, so recording still works.
        }
    }

    private fun stopBluetoothSco() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isBluetoothScoOn = false
            audioManager.stopBluetoothSco()
        } catch (e: Exception) {
            // Nothing to clean up.
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(rolloverRunnable)
        stopRecorderQuietly()
        releaseWakeLock()
        super.onDestroy()
    }
}
