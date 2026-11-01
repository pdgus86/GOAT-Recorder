package com.pdgus86.voicenotes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.pdgus86.voicenotes.data.JobQueueRepository

/**
 * Main screen: a big record button up top and a plain list of recordings.
 * Tap a recording to play it back. Rename and delete from the buttons on
 * each row. Big tap targets throughout.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var recordButton: MaterialButton
    private lateinit var settingsButton: MaterialButton
    private lateinit var emptyText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RecordingAdapter
    private lateinit var jobQueue: JobQueueRepository

    private var sessions: List<RecordingSession> = emptyList()
    private var mediaPlayer: MediaPlayer? = null
    private var playingSessionId: String? = null
    private var playingPartIndex: Int = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.RECORD_AUDIO] == false) {
            Toast.makeText(
                this,
                getString(R.string.permission_mic_needed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recordButton = findViewById(R.id.record_button)
        settingsButton = findViewById(R.id.settings_button)
        emptyText = findViewById(R.id.empty_text)
        recyclerView = findViewById(R.id.recordings_list)
        jobQueue = JobQueueRepository(this)

        adapter = RecordingAdapter(
            onPlayClicked = { session -> togglePlayback(session) },
            onRenameClicked = { session -> showRenameDialog(session) },
            onDeleteClicked = { session -> showDeleteDialog(session) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        recordButton.setOnClickListener {
            if (!hasMicPermission()) {
                requestPermissions()
                return@setOnClickListener
            }
            RecordingController.toggleRecording(this)
            refreshUi()
        }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        requestPermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    private fun refreshUi() {
        val recording = RecordingController.isRecording(this)
        recordButton.text = if (recording) {
            getString(R.string.stop_recording)
        } else {
            getString(R.string.start_recording)
        }
        recordButton.setIconResource(
            if (recording) R.drawable.ic_stop else R.drawable.ic_mic
        )
        sessions = RecordingRepository.listSessions(this)
        adapter.submitSessions(sessions, playingSessionId, jobQueue.sessionsWithPendingJobs())
        emptyText.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val wanted = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun togglePlayback(session: RecordingSession) {
        if (playingSessionId == session.id) {
            stopPlayback()
            refreshUi()
            return
        }
        stopPlayback()
        playingSessionId = session.id
        playingPartIndex = 0
        playCurrentPart(session)
        refreshUi()
    }

    private fun playCurrentPart(session: RecordingSession) {
        val file = session.files.getOrNull(playingPartIndex) ?: run {
            stopPlayback()
            refreshUi()
            return
        }
        val player = MediaPlayer()
        try {
            player.setDataSource(file.absolutePath)
            player.prepare()
            player.setOnCompletionListener {
                playingPartIndex++
                if (playingPartIndex < session.files.size) {
                    playCurrentPart(session)
                } else {
                    stopPlayback()
                    refreshUi()
                }
            }
            player.setOnErrorListener { _, _, _ ->
                stopPlayback()
                refreshUi()
                true
            }
            player.start()
            mediaPlayer = player
        } catch (e: Exception) {
            try {
                player.release()
            } catch (ignored: Exception) {
            }
            stopPlayback()
            Toast.makeText(this, getString(R.string.playback_failed), Toast.LENGTH_SHORT).show()
            refreshUi()
        }
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            // Was not playing.
        }
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Already released.
        }
        mediaPlayer = null
        playingSessionId = null
    }

    private fun showRenameDialog(session: RecordingSession) {
        val input = EditText(this).apply {
            setText(RecordingRepository.displayName(this@MainActivity, session))
            setSelection(text.length)
            textSize = 20f
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.rename_title))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    RecordingRepository.rename(this, session, name)
                    refreshUi()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDeleteDialog(session: RecordingSession) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_title))
            .setMessage(
                getString(
                    R.string.delete_message,
                    RecordingRepository.displayName(this, session)
                )
            )
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                stopPlaybackIf(session)
                RecordingRepository.delete(this, session)
                refreshUi()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun stopPlaybackIf(session: RecordingSession) {
        if (playingSessionId == session.id) {
            stopPlayback()
        }
    }

    private class RecordingAdapter(
        private val onPlayClicked: (RecordingSession) -> Unit,
        private val onRenameClicked: (RecordingSession) -> Unit,
        private val onDeleteClicked: (RecordingSession) -> Unit
    ) : RecyclerView.Adapter<RecordingAdapter.ViewHolder>() {

        private var sessions: List<RecordingSession> = emptyList()
        private var playingId: String? = null
        private var pendingIds: Set<String> = emptySet()

        fun submitSessions(
            sessions: List<RecordingSession>,
            playingId: String?,
            pendingIds: Set<String> = emptySet()
        ) {
            this.sessions = sessions
            this.playingId = playingId
            this.pendingIds = pendingIds
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recording, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val session = sessions[position]
            val context = holder.itemView.context
            holder.nameText.text = RecordingRepository.displayName(context, session)
            val parts = if (session.partCount > 1) {
                context.getString(R.string.parts_count, session.partCount)
            } else {
                ""
            }
            val pending = if (pendingIds.contains(session.id)) {
                context.getString(R.string.pending_transcription)
            } else {
                ""
            }
            holder.detailText.text = listOf(
                RecordingRepository.formatDuration(session.durationMs),
                parts,
                pending
            ).filter { it.isNotEmpty() }.joinToString("  -  ")
            holder.playButton.setImageResource(
                if (playingId == session.id) R.drawable.ic_stop else R.drawable.ic_play
            )
            holder.playButton.contentDescription = if (playingId == session.id) {
                context.getString(R.string.stop_playback)
            } else {
                context.getString(R.string.play_recording)
            }
            holder.playButton.setOnClickListener { onPlayClicked(session) }
            holder.itemView.setOnClickListener { onPlayClicked(session) }
            holder.renameButton.setOnClickListener { onRenameClicked(session) }
            holder.deleteButton.setOnClickListener { onDeleteClicked(session) }
        }

        override fun getItemCount(): Int = sessions.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.recording_name)
            val detailText: TextView = view.findViewById(R.id.recording_detail)
            val playButton: ImageButton = view.findViewById(R.id.play_button)
            val renameButton: ImageButton = view.findViewById(R.id.rename_button)
            val deleteButton: ImageButton = view.findViewById(R.id.delete_button)
        }
    }
}
