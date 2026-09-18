package com.drynav.app.domain.model

import com.mapbox.geojson.Point

/**
 * A user-pinned destination saved under a short name ("Home", "Work", or
 * anything custom) so it can be picked with one tap instead of typing a
 * search query every time.
 */
data class SavedPlace(
    val id: String = "",
    val userId: String = "",
    val label: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) {
    val point: Point get() = Point.fromLngLat(longitude, latitude)

    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "label" to label,
        "address" to address,
        "latitude" to latitude,
        "longitude" to longitude
    )

    companion object {
        const val FIRESTORE_COLLECTION = "saved_places"

        fun fromFirestore(id: String, data: Map<String, Any?>): SavedPlace = SavedPlace(
            id = id,
            userId = (data["userId"] as? String).orEmpty(),
            label = (data["label"] as? String).orEmpty(),
            address = (data["address"] as? String).orEmpty(),
            latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
            longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0
        )
    }
}
