package com.pdgus86.voicenotes.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Schedules the job queue worker to run when the phone has a network
 * connection again. Called whenever a job is queued.
 *
 * The network constraint is the whole trigger: WorkManager will not run
 * the worker until connectivity returns, so queued transcription and AI
 * jobs are sent automatically without Paul having to remember anything.
 * The local (on-device) provider needs no connection at all, so phase 2
 * can run its jobs straight away instead of waiting on this worker.
 */
object JobQueueScheduler {

    private const val UNIQUE_WORK_NAME = "voicenotes-job-queue"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<JobQueueWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
