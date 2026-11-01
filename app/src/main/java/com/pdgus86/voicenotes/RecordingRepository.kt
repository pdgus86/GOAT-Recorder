package com.pdgus86.voicenotes

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One recording session. A long recording is stored as several 15-minute
 * chunk files; they are grouped back into a single session here.
 */
data class RecordingSession(
    val id: String,
    val startTimeMs: Long,
    val files: List<File>,
    val durationMs: Long
) {
    val partCount: Int get() = files.size
}

/**
 * Reads the recordings folder and groups chunk files into sessions.
 * Display names are kept in SharedPreferences, so renaming never has to
 * touch the files on disk.
 */
object RecordingRepository {

    private const val NAME_PREFS = "voicenotes_names"

    fun recordingsDir(context: Context): File {
        val external = context.getExternalFilesDir("VoiceNotes")
        return external ?: File(context.filesDir, "VoiceNotes")
    }

    fun listSessions(context: Context): List<RecordingSession> {
        val dir = recordingsDir(context)
        if (!dir.exists()) return emptyList()
        val files = dir.listFiles { f ->
            f.isFile && f.name.startsWith("rec_") && f.name.endsWith(".m4a")
        }?.toList() ?: return emptyList()

        val grouped = files.groupBy { file ->
            // rec_20261101_134022_part01.m4a -> 20261101_134022
            file.name.removePrefix("rec_").substringBefore("_part")
        }
        val sessions = grouped.mapNotNull { (id, parts) ->
            val sorted = parts.sortedBy { it.name }
            val startMs = parseSessionStart(id) ?: return@mapNotNull null
            val duration = sorted.sumOf { fileDurationMs(it) }
            RecordingSession(id, startMs, sorted, duration)
        }
        return sessions.sortedByDescending { it.startTimeMs }
    }

    fun displayName(context: Context, session: RecordingSession): String {
        val saved = context.getSharedPreferences(NAME_PREFS, Context.MODE_PRIVATE)
            .getString(session.id, null)
        if (!saved.isNullOrBlank()) return saved
        return SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US)
            .format(Date(session.startTimeMs))
    }

    fun rename(context: Context, session: RecordingSession, name: String) {
        context.getSharedPreferences(NAME_PREFS, Context.MODE_PRIVATE).edit()
            .putString(session.id, name.trim())
            .apply()
    }

    fun delete(context: Context, session: RecordingSession) {
        session.files.forEach { it.delete() }
        context.getSharedPreferences(NAME_PREFS, Context.MODE_PRIVATE).edit()
            .remove(session.id)
            .apply()
    }

    private fun parseSessionStart(id: String): Long? {
        return try {
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(id)?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun fileDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Already released.
            }
        }
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
        } else {
            "%d:%02d".format(Locale.US, minutes, seconds)
        }
    }
}
