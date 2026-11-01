package com.pdgus86.voicenotes.data

import android.content.Context
import com.pdgus86.voicenotes.model.JobStatus
import com.pdgus86.voicenotes.model.JobType
import com.pdgus86.voicenotes.model.TranscriptionJob
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent offline job queue, stored in SharedPreferences as JSON.
 * Survives app restarts and phone reboots. No network access.
 */
class JobQueueRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun listJobs(): List<TranscriptionJob> {
        val json = prefs.getString(KEY_JOBS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val out = mutableListOf<TranscriptionJob>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                out.add(
                    TranscriptionJob(
                        id = obj.optString("id"),
                        sessionId = obj.optString("sessionId"),
                        type = JobType.valueOf(
                            obj.optString("type", JobType.TRANSCRIBE.name)
                        ),
                        status = JobStatus.valueOf(
                            obj.optString("status", JobStatus.PENDING.name)
                        ),
                        providerId = obj.optString("providerId").ifBlank { null },
                        createdAtMs = obj.optLong("createdAtMs", 0L),
                        attempts = obj.optInt("attempts", 0),
                        lastError = obj.optString("lastError").ifBlank { null }
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun enqueue(job: TranscriptionJob) {
        val current = listJobs().toMutableList()
        current.add(job)
        persist(current)
    }

    fun updateJob(job: TranscriptionJob) {
        val current = listJobs().toMutableList()
        val index = current.indexOfFirst { it.id == job.id }
        if (index >= 0) {
            current[index] = job
            persist(current)
        }
    }

    fun deleteJob(job: TranscriptionJob) {
        val current = listJobs().filter { it.id != job.id }
        persist(current)
    }

    /** Jobs for one recording session that are not finished yet. */
    fun pendingJobsForSession(sessionId: String): List<TranscriptionJob> {
        return listJobs().filter {
            it.sessionId == sessionId &&
                (it.status == JobStatus.PENDING || it.status == JobStatus.FAILED)
        }
    }

    /** Session ids that have at least one unfinished job. */
    fun sessionsWithPendingJobs(): Set<String> {
        return listJobs()
            .filter { it.status == JobStatus.PENDING || it.status == JobStatus.FAILED }
            .map { it.sessionId }
            .toSet()
    }

    private fun persist(jobs: List<TranscriptionJob>) {
        val array = JSONArray()
        for (job in jobs) {
            array.put(
                JSONObject().apply {
                    put("id", job.id)
                    put("sessionId", job.sessionId)
                    put("type", job.type.name)
                    put("status", job.status.name)
                    put("providerId", job.providerId ?: "")
                    put("createdAtMs", job.createdAtMs)
                    put("attempts", job.attempts)
                    put("lastError", job.lastError ?: "")
                }
            )
        }
        prefs.edit().putString(KEY_JOBS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "voicenotes_job_queue"
        private const val KEY_JOBS = "jobs_json"
    }
}
