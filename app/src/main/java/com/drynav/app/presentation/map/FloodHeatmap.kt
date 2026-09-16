package com.drynav.app.presentation.map

import com.drynav.app.domain.model.FloodReport
import com.drynav.app.domain.model.FloodSeverity
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.fillLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import kotlin.math.cos
import kotlin.math.sin

private const val BLOCKED_SOURCE = "drynav-flood-zone-source-blocked"
private const val BLOCKED_LAYER = "drynav-flood-zone-layer-blocked"
private const val SLOW_SOURCE = "drynav-flood-zone-source-slow"
private const val SLOW_LAYER = "drynav-flood-zone-layer-slow"
private const val CIRCLE_SEGMENTS = 64

/**
 * Draws each flood report as one solid circular affected zone. The radius is
 * taken directly from FloodReport.floodRadiusMeters, so changing the report
 * slider and redrawing the source updates the visible zone immediately.
 */
fun updateFloodHeatmap(style: Style, reports: List<FloodReport>, enabled: Boolean) {
    if (!enabled) {
        removeZoneLayer(style, BLOCKED_LAYER, BLOCKED_SOURCE)
        removeZoneLayer(style, SLOW_LAYER, SLOW_SOURCE)
        return
    }

    val active = reports.filter { !it.isCleared }
    val (blocked, slow) = active.partition { it.severity == FloodSeverity.IMPASSABLE }

    updateZoneSource(style, BLOCKED_SOURCE, blocked)
    updateZoneSource(style, SLOW_SOURCE, slow)
    ensureZoneLayer(style, BLOCKED_LAYER, BLOCKED_SOURCE, blocked = true)
    ensureZoneLayer(style, SLOW_LAYER, SLOW_SOURCE, blocked = false)
}

private fun removeZoneLayer(style: Style, layerId: String, sourceId: String) {
    if (style.styleLayerExists(layerId)) style.removeStyleLayer(layerId)
    if (style.styleSourceExists(sourceId)) style.removeStyleSource(sourceId)
}

private fun updateZoneSource(style: Style, sourceId: String, reports: List<FloodReport>) {
    val features = reports.map { report ->
        Feature.fromGeometry(makeCircle(report.longitude, report.latitude, report.floodRadiusMeters.coerceAtLeast(1.0)))
    }
    val collection = FeatureCollection.fromFeatures(features)
    val existing = style.getSourceAs<GeoJsonSource>(sourceId)
    if (existing != null) {
        existing.featureCollection(collection)
    } else {
        style.addSource(geoJsonSource(sourceId) { featureCollection(collection) })
    }
}

private fun ensureZoneLayer(style: Style, layerId: String, sourceId: String, blocked: Boolean) {
    if (style.styleLayerExists(layerId)) return
    style.addLayer(
        fillLayer(layerId, sourceId) {
            fillColor(if (blocked) "#E53935" else "#FFB300")
            fillOpacity(0.30)
            fillOutlineColor(if (blocked) "#C62828" else "#F57C00")
        }
    )
}

/** Creates a geodesic-ish circle polygon in WGS84 coordinates. */
private fun makeCircle(longitude: Double, latitude: Double, radiusMeters: Double): Polygon {
    val latScale = 111_320.0
    val lonScale = 111_320.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.1)
    val ring = ArrayList<Point>(CIRCLE_SEGMENTS + 1)
    for (i in 0..CIRCLE_SEGMENTS) {
        val angle = 2.0 * Math.PI * i / CIRCLE_SEGMENTS
        ring += Point.fromLngLat(
            longitude + cos(angle) * radiusMeters / lonScale,
            latitude + sin(angle) * radiusMeters / latScale
        )
    }
    return Polygon.fromLngLats(listOf(ring))
}
