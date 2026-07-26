package com.drynav.app.domain.repository

import com.drynav.app.domain.model.Presence
import com.mapbox.geojson.Point
import kotlinx.coroutines.flow.Flow

interface PresenceRepository {

    /** Upserts the signed-in user's own live position/mood. Call on a heartbeat while online. */
    suspend fun publishPresence(mood: String, point: Point): Result<Unit>

    /** Removes the signed-in user's presence doc — call when leaving the map/signing out. */
    suspend fun clearPresence(): Result<Unit>

    /** Realtime stream of every OTHER online user's presence (self excluded, stale entries filtered). */
    fun observeOthers(): Flow<List<Presence>>
}
