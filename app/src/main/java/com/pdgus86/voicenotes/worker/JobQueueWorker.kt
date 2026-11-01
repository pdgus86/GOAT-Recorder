package com.pdgus86.voicenotes.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Runs when the phone has a network connection again.
 *
 * Phase 1: this worker exists only to prove the trigger works. It reads
 * the pending jobs and returns success. It makes no network calls.
 *
 * Phase 2: this is where queued jobs get sent to the currently selected
 * provider (OpenRouter, the PC, or the on-device model). A job that
 * fails stays in the queue with status FAILED so it can be retried, and
 * the worker re-enqueues itself while unfinished jobs remain.
 */
class JobQueueWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        // Phase 1: trigger only. Job processing is phase 2.
        return Result.success()
    }
}
