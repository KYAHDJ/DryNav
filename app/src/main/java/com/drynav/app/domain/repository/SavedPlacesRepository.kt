package com.drynav.app.domain.repository

import com.drynav.app.domain.model.SavedPlace
import com.mapbox.geojson.Point
import kotlinx.coroutines.flow.Flow

interface SavedPlacesRepository {

    /** Realtime stream of the signed-in user's own saved places. */
    fun getSavedPlaces(): Flow<List<SavedPlace>>

    suspend fun savePlace(label: String, address: String, point: Point): Result<Unit>

    suspend fun deletePlace(placeId: String): Result<Unit>
}
