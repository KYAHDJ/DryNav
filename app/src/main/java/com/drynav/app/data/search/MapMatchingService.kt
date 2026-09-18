package com.drynav.app.data.search

import android.content.Context
import com.drynav.app.R
import com.mapbox.geojson.Point
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snaps a raw finger-drawn trail onto the actual road network using the
 * Mapbox Map Matching API, so a flood "brush" stroke becomes the road
 * itself rather than a free-hand scribble.
 */
@Singleton
class MapMatchingService @Inject constructor(
    @ApplicationContext context: Context
) {
    private val accessToken = context.getString(R.string.mapbox_access_token)

    /**
     * Returns the matched road geometry, or null when nothing matched at
     * all. Tries the driving network first (the most precise match for an
     * actual road), then falls back to the walking network — which covers
     * far more alleys, footpaths, and minor service roads that aren't
     * tagged for driving in Mapbox's data — so any real road a person can
     * move through, big or small, can be marked.
     */
    suspend fun snapToRoads(rawPoints: List<Point>): List<Point>? =
        withContext(Dispatchers.IO) {
            val capped = decimate(rawPoints, MAX_INPUT_POINTS)
            if (capped.size < 2) return@withContext null
            matchAgainst(capped, "driving") ?: matchAgainst(capped, "walking")
        }

    private fun matchAgainst(points: List<Point>, profile: String): List<Point>? = runCatching {
        val coords = points.joinToString(";") { "${it.longitude()},${it.latitude()}" }
        val radiuses = points.joinToString(";") { SEARCH_RADIUS_METERS }
        val url = URL(
            "https://api.mapbox.com/matching/v5/mapbox/$profile/$coords" +
                "?geometries=geojson&overview=full&radiuses=$radiuses" +
                "&access_token=$accessToken"
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseMatching(body)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun parseMatching(json: String): List<Point>? {
        val root = JSONObject(json)
        if (root.optString("code") != "Ok") return null
        val matching = root.optJSONArray("matchings")?.optJSONObject(0) ?: return null
        val coords = matching.optJSONObject("geometry")
            ?.optJSONArray("coordinates") ?: return null
        val points = buildList {
            for (i in 0 until coords.length()) {
                val pair = coords.getJSONArray(i)
                add(Point.fromLngLat(pair.getDouble(0), pair.getDouble(1)))
            }
        }
        return points.takeIf { it.size >= 2 }
    }

    private fun decimate(points: List<Point>, max: Int): List<Point> {
        if (points.size <= max) return points
        val step = (points.size + max - 1) / max
        return points.filterIndexed { i, _ -> i % step == 0 || i == points.lastIndex }
    }

    private companion object {
        const val MAX_INPUT_POINTS = 60 // API caps at 100 coordinates
        // Wider than before (was 35m) — forgiving of a finger stroke that
        // drifts a bit from the exact center of a narrow street.
        const val SEARCH_RADIUS_METERS = "50"
    }
}
