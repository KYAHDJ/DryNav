package com.drynav.app.domain.repository

import com.drynav.app.domain.model.FloodReport
import kotlinx.coroutines.flow.Flow

interface FloodRepository {

    /** Persists a new flood report. Returns the generated document id. */
    suspend fun submitReport(report: FloodReport): Result<String>

    /** Rejects a report when this reporter has a recent nearby report. */
    suspend fun checkReportRateLimit(report: FloodReport): Result<Unit>

    /**
     * Realtime stream of all non-cleared, non-stale flood reports.
     * Backed by a Firestore snapshot listener; emits on every change.
     */
    fun getLiveFloodReports(): Flow<List<FloodReport>>

    /** Atomically increments the upvote counter of a report. */
    suspend fun upvoteReport(reportId: String): Result<Unit>

    /** Deletes a report, but only when the authenticated user owns it. */
    suspend fun deleteReport(reportId: String, reporterId: String): Result<Unit>



}
