package com.drynav.app.domain.repository

import com.drynav.app.domain.model.FloodReport
import kotlinx.coroutines.flow.Flow

interface FloodRepository {

    /** Persists a new flood report. Returns the generated document id. */
    suspend fun submitReport(report: FloodReport): Result<String>

    /**
     * Realtime stream of all non-cleared, non-stale flood reports.
     * Backed by a Firestore snapshot listener; emits on every change.
     */
    fun getLiveFloodReports(): Flow<List<FloodReport>>

    /** Atomically increments the upvote counter of a report. */
    suspend fun upvoteReport(reportId: String): Result<Unit>

    /**
     * Deletes a report, including its photo in Storage so no orphaned
     * data is left behind.
     */
    suspend fun deleteReport(report: FloodReport): Result<Unit>

    /** Admin moderation: approve, reject, or otherwise change a report's status. */
    suspend fun setReportStatus(reportId: String, status: String): Result<Unit>

    /** Admin-only: wipes every flood report (and their photos). Irreversible. */
    suspend fun deleteAllReports(): Result<Unit>
}
