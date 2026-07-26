package com.drynav.app.domain.model

/**
 * A single crowd-sourced flood report.
 *
 * Stored in Firestore collection [FIRESTORE_COLLECTION]. The [severity] field
 * is persisted as its enum name string so the document stays human-readable.
 */
data class FloodReport(
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val severity: FloodSeverity = FloodSeverity.PASSABLE,
    val timestamp: Long = System.currentTimeMillis(),
    val upvotes: Int = 0,
    val isCleared: Boolean = false,
    val description: String = "",
    /** As many photos as the reporter attached — no cap. */
    val photoUrls: List<String> = emptyList(),
    /**
     * Free-drawn affected area(s) — each inner list is ONE brush stroke's
     * points, kept separate. Rendering must draw each stroke as its own
     * disconnected line; flattening them into one list would draw a
     * straight "bridge" line between the end of one road and the start of
     * the next unrelated one.
     */
    val areaStrokes: List<List<AreaPoint>> = emptyList(),
    /** Moderation state — see [STATUS_PENDING]/[STATUS_APPROVED]/[STATUS_REJECTED]. */
    val status: String = STATUS_APPROVED,
    /**
     * Reverse-geocoded "Barangay, City" label, filled in at submission time
     * (best-effort — blank if the lookup failed). Lets the admin queue be
     * filtered/grouped by area instead of one flat list.
     */
    val areaLabel: String = "",
    /**
     * Where the REPORTER physically was when they submitted this, as opposed
     * to [latitude]/[longitude] which is where the flood itself was pinned/
     * painted — the two can be far apart (e.g. reporting a flood elsewhere
     * from home). Null on reports submitted before this was tracked, or if
     * the device's GPS fix wasn't available at submit time.
     */
    val reporterLatitude: Double? = null,
    val reporterLongitude: Double? = null,
    /** Reverse-geocoded "Barangay, City" for [reporterLatitude]/[reporterLongitude]. */
    val reporterAreaLabel: String = ""
) {
    /**
     * True once [timestamp] is older than [MAX_REPORT_AGE_MS]. Was defined
     * from the start with the intent that stale reports get ignored, but
     * nothing ever actually checked it — every report stayed "active"
     * forever regardless of age. After a lot of real-world/test usage this
     * let old, no-longer-relevant reports keep counting as live hazards
     * everywhere (heatmap, nearby-flood count, and — worst of all —
     * flood-avoidance routing, where stale points crowd the exclusion
     * budget and produce false "crosses a flooded road" warnings on routes
     * that don't actually cross anything current).
     */
    val isStale: Boolean
        get() = System.currentTimeMillis() - timestamp > MAX_REPORT_AGE_MS

    /** Approved-for-display state: not manually cleared, and not aged out. */
    val isLiveHazard: Boolean
        get() = !isCleared && !isStale

    val isActiveHazard: Boolean
        get() = isLiveHazard && severity == FloodSeverity.IMPASSABLE

    val isApproved: Boolean
        get() = status == STATUS_APPROVED

    val isPending: Boolean
        get() = status == STATUS_PENDING

    /** Every drawn point across all strokes, flattened — fine for anything that just needs "any point" (heatmap weighting, distance checks), never for drawing a connected line. */
    val areaPoints: List<AreaPoint>
        get() = areaStrokes.flatten()

    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "latitude" to latitude,
        "longitude" to longitude,
        "severity" to severity.name,
        "timestamp" to timestamp,
        "upvotes" to upvotes,
        "isCleared" to isCleared,
        "description" to description,
        "photoUrls" to photoUrls,
        // Firestore forbids an array directly containing another array, so
        // each stroke is wrapped in a map: [{"points": [...]}, {"points": [...]}].
        "areaStrokes" to areaStrokes.map { stroke ->
            mapOf("points" to stroke.map { mapOf("lat" to it.lat, "lng" to it.lng) })
        },
        "status" to status,
        "areaLabel" to areaLabel,
        "reporterAreaLabel" to reporterAreaLabel
    ) + buildMap {
        reporterLatitude?.let { put("reporterLatitude", it) }
        reporterLongitude?.let { put("reporterLongitude", it) }
    }

    companion object {
        const val FIRESTORE_COLLECTION = "flood_reports"

        /** Reports older than this are considered stale and ignored. */
        const val MAX_REPORT_AGE_MS: Long = 6L * 60L * 60L * 1000L // 6 hours

        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"

        fun fromFirestore(id: String, data: Map<String, Any?>): FloodReport = FloodReport(
            id = id,
            latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
            longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
            severity = FloodSeverity.fromString(data["severity"] as? String),
            timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
            upvotes = (data["upvotes"] as? Number)?.toInt() ?: 0,
            isCleared = (data["isCleared"] as? Boolean) ?: false,
            description = (data["description"] as? String).orEmpty(),
            // Back-compat: reports saved before multi-photo support used a
            // single "photoUrl" string instead of a "photoUrls" list.
            photoUrls = (data["photoUrls"] as? List<*>)?.filterIsInstance<String>()
                ?: (data["photoUrl"] as? String)?.takeIf { it.isNotBlank() }?.let { listOf(it) }
                ?: emptyList(),
            areaStrokes = parseAreaStrokes(data),
            // Reports saved before moderation existed had no status field —
            // treat them as approved so old data keeps displaying as before.
            status = (data["status"] as? String) ?: STATUS_APPROVED,
            areaLabel = (data["areaLabel"] as? String).orEmpty(),
            reporterLatitude = (data["reporterLatitude"] as? Number)?.toDouble(),
            reporterLongitude = (data["reporterLongitude"] as? Number)?.toDouble(),
            reporterAreaLabel = (data["reporterAreaLabel"] as? String).orEmpty()
        )

        private fun parseAreaStrokes(data: Map<String, Any?>): List<List<AreaPoint>> {
            // Current shape: "areaStrokes": [{"points": [{lat,lng}, ...]}, ...]
            (data["areaStrokes"] as? List<*>)?.let { strokesRaw ->
                return strokesRaw.mapNotNull { strokeEntry ->
                    val strokeMap = strokeEntry as? Map<*, *> ?: return@mapNotNull null
                    val points = (strokeMap["points"] as? List<*>).orEmpty().mapNotNull { parsePoint(it) }
                    points.takeIf { it.isNotEmpty() }
                }
            }
            // Back-compat: reports saved before strokes were kept separate
            // used one flat "area" array — treat the whole thing as a single
            // stroke rather than losing the data.
            val flat = (data["area"] as? List<*>).orEmpty().mapNotNull { parsePoint(it) }
            return if (flat.isNotEmpty()) listOf(flat) else emptyList()
        }

        private fun parsePoint(entry: Any?): AreaPoint? {
            val map = entry as? Map<*, *> ?: return null
            val lat = (map["lat"] as? Number)?.toDouble() ?: return null
            val lng = (map["lng"] as? Number)?.toDouble() ?: return null
            return AreaPoint(lat, lng)
        }
    }
}

/** One vertex of a drawn flood area. */
data class AreaPoint(val lat: Double, val lng: Double)
