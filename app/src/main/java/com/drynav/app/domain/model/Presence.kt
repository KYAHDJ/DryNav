package com.drynav.app.domain.model

/** Live location of another signed-in user while the app is in the foreground. */
data class Presence(
    val userId: String = "",
    val displayName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val updatedAt: Long = 0L
) {
    val isStale: Boolean
        get() = System.currentTimeMillis() - updatedAt > MAX_AGE_MS

    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "displayName" to displayName,
        "latitude" to latitude,
        "longitude" to longitude,
        "updatedAt" to updatedAt
    )

    companion object {
        const val FIRESTORE_COLLECTION = "presence"
        const val MAX_AGE_MS = 90_000L

        fun fromFirestore(id: String, data: Map<String, Any?>): Presence = Presence(
            userId = id,
            displayName = (data["displayName"] as? String).orEmpty(),
            latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
            longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }
}
