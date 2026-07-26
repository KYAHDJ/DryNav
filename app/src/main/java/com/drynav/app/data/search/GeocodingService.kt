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
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A destination suggestion (from [GeocodingService.search], [point] is null until
 * resolved via [GeocodingService.retrieve]) or a fully-resolved place.
 */
data class PlaceResult(
    val id: String,
    val name: String,
    val address: String,
    val point: Point? = null
)

/**
 * Thin HTTP client for Mapbox's Search Box API (suggest -> retrieve).
 *
 * Migrated off the legacy Geocoding v5 API, which was confirmed via live testing to
 * have real data gaps for Philippine neighborhoods (e.g. no usable "Cubao" result
 * under any query/parameter combination); Search Box correctly finds it.
 */
@Singleton
class GeocodingService @Inject constructor(
    @ApplicationContext context: Context
) {
    private val accessToken = context.getString(R.string.mapbox_access_token)

    // Search Box billing is session-based: one token per suggest->retrieve interaction.
    @Volatile
    private var sessionToken = UUID.randomUUID().toString()

    /** Call when a search interaction ends (place picked, or search dismissed). */
    fun resetSession() {
        sessionToken = UUID.randomUUID().toString()
    }

    suspend fun search(query: String, proximity: Point?): List<PlaceResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                val proximityParam = proximity
                    ?.let { "&proximity=${it.longitude()},${it.latitude()}" }
                    .orEmpty()
                val url = URL(
                    "https://api.mapbox.com/search/searchbox/v1/suggest?q=$encoded" +
                        "&access_token=$accessToken&session_token=$sessionToken" +
                        "&language=en&limit=8$proximityParam"
                )
                parseSuggestions(get(url))
            }.getOrDefault(emptyList())
        }

    /**
     * Resolves [point] to a human-readable "Barangay, City" label for grouping
     * flood reports by area (admin moderation filter). Falls back to just the
     * city, or null if the lookup fails entirely. No session token — reverse
     * lookups are a standalone billable request, not part of a suggest/retrieve
     * session.
     */
    suspend fun reverseGeocode(point: Point): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(
                    "https://api.mapbox.com/search/searchbox/v1/reverse" +
                        "?longitude=${point.longitude()}&latitude=${point.latitude()}" +
                        "&access_token=$accessToken&language=en&limit=1" +
                        "&types=place,locality,neighborhood,district"
                )
                parseReverseLabel(get(url))
            }.getOrNull()
        }

    private fun parseReverseLabel(json: String): String? {
        val feature = JSONObject(json).optJSONArray("features")?.optJSONObject(0) ?: return null
        val props = feature.optJSONObject("properties") ?: return null
        val context = props.optJSONObject("context")
        val city = context?.optJSONObject("place")?.optString("name")?.takeIf { it.isNotBlank() }
        val barangay = context?.optJSONObject("locality")?.optString("name")?.takeIf { it.isNotBlank() }
        val combined = listOfNotNull(barangay, city).joinToString(", ")
        if (combined.isNotBlank()) return combined
        return props.optString("place_formatted").takeIf { it.isNotBlank() }
    }

    /** Resolves a suggestion's coordinates. Returns null if the lookup fails. */
    suspend fun retrieve(place: PlaceResult): PlaceResult? =
        withContext(Dispatchers.IO) {
            if (place.point != null) return@withContext place
            runCatching {
                val url = URL(
                    "https://api.mapbox.com/search/searchbox/v1/retrieve/${place.id}" +
                        "?access_token=$accessToken&session_token=$sessionToken"
                )
                parseRetrieve(get(url), place)
            }.getOrNull()
        }

    private fun get(url: URL): String {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSuggestions(json: String): List<PlaceResult> {
        val suggestions = JSONObject(json).optJSONArray("suggestions") ?: return emptyList()
        return buildList {
            for (i in 0 until suggestions.length()) {
                val s = suggestions.getJSONObject(i)
                val mapboxId = s.optString("mapbox_id")
                if (mapboxId.isEmpty()) continue
                add(
                    PlaceResult(
                        id = mapboxId,
                        name = s.optString("name"),
                        address = s.optString("place_formatted"),
                        point = null
                    )
                )
            }
        }
    }

    private fun parseRetrieve(json: String, fallback: PlaceResult): PlaceResult? {
        val feature = JSONObject(json).optJSONArray("features")?.optJSONObject(0) ?: return null
        val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        val props = feature.optJSONObject("properties")
        return fallback.copy(
            name = props?.optString("name")?.takeIf { it.isNotEmpty() } ?: fallback.name,
            address = props?.optString("place_formatted")?.takeIf { it.isNotEmpty() }
                ?: fallback.address,
            point = Point.fromLngLat(coords.getDouble(0), coords.getDouble(1))
        )
    }
}
