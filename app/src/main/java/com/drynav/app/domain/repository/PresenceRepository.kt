package com.drynav.app.domain.repository

import com.drynav.app.domain.model.Presence
import com.mapbox.geojson.Point
import kotlinx.coroutines.flow.Flow

interface PresenceRepository {
    suspend fun publishPresence(point: Point): Result<Unit>
    suspend fun clearPresence(): Result<Unit>
    fun observeOthers(): Flow<List<Presence>>
}
