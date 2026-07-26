package com.drynav.app.domain.model

/**
 * A live snapshot of one signed-in user's position + chosen [Mood], broadcast
 * while they have a map screen open so other online users can see their
 * character moving around — the Waze-style "other drivers" feature. Stored
 * one document per user (doc id = uid) in Firestore, overwritten in place
 * rather than appended, so there's only ever one live row per user.
 */
data class Presence(
    val userId: String = "",
    val displayName: String = "",
    val mood: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    /** Millis (client clock, same convention as [FloodReport.timestamp]) of the last update. */
    val updatedAt: Long = 0L
) {
    /** Stale presence (app closed/killed without a clean [PresenceRepository.clearPresence] call) is hidden. */
    val isStale: Boolean
        get() = System.currentTimeMillis() - updatedAt > MAX_AGE_MS

    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "displayName" to displayName,
        "mood" to mood,
        "latitude" to latitude,
        "longitude" to longitude,
        "updatedAt" to updatedAt
    )

    companion object {
        const val FIRESTORE_COLLECTION = "presence"

        /** No heartbeat in this long means the user's session ended uncleanly. */
        const val MAX_AGE_MS = 90_000L

        fun fromFirestore(id: String, data: Map<String, Any?>): Presence = Presence(
            userId = id,
            displayName = (data["displayName"] as? String).orEmpty(),
            mood = (data["mood"] as? String).orEmpty(),
            latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
            longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }
}
