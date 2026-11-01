package com.pdgus86.voicenotes.model

import java.util.UUID

/** What the job asks the AI to do with a finished recording. */
enum class JobType {
    /** Turn the audio into text. Runs first. */
    TRANSCRIBE,

    /** Summarize the transcript. Runs after transcription, phase 2. */
    SUMMARIZE,

    /** Pull action items out of the transcript. Runs after transcription, phase 2. */
    ACTION_ITEMS
}

/** Where a job sits in its life. */
enum class JobStatus {
    /** Waiting for a connection, or for its turn. */
    PENDING,

    /** Being sent to a provider right now. */
    RUNNING,

    /** Finished. */
    DONE,

    /** Failed. Stays in the queue so it can be retried. */
    FAILED
}

/**
 * One unit of transcription or AI work for one recording session.
 *
 * The offline queue, phase 1 data model. The app always records locally
 * no matter what, even if no provider can be reached. When a recording
 * finishes, a job is queued here. When connectivity returns, phase 2
 * sends the queued jobs to whichever provider is currently selected,
 * whether that is OpenRouter or the PC. The provider is deliberately
 * NOT fixed on the job: a null providerId means "use the provider that
 * is selected when the job runs", so Paul never has to remember to
 * switch to the local model before going offline.
 *
 * Jobs live in SharedPreferences as JSON (see JobQueueRepository), so
 * they survive app restarts and phone reboots.
 */
data class TranscriptionJob(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val type: JobType = JobType.TRANSCRIBE,
    val status: JobStatus = JobStatus.PENDING,
    val providerId: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastError: String? = null
)
