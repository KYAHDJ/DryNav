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



}
