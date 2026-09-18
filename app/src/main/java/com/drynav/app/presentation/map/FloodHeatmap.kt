package com.drynav.app.presentation.map

import com.drynav.app.domain.model.FloodReport
import com.drynav.app.domain.model.FloodSeverity
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.fillLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import kotlin.math.cos
import kotlin.math.sin

private const val BLOCKED_SOURCE = "drynav-flood-zone-source-blocked"
private const val BLOCKED_LAYER = "drynav-flood-zone-layer-blocked"
private const val BLOCKED_INNER_SOURCE = "drynav-flood-zone-source-blocked-inner"
private const val BLOCKED_INNER_LAYER = "drynav-flood-zone-layer-blocked-inner"
private const val SLOW_SOURCE = "drynav-flood-zone-source-slow"
private const val SLOW_LAYER = "drynav-flood-zone-layer-slow"
private const val SLOW_INNER_SOURCE = "drynav-flood-zone-source-slow-inner"
private const val SLOW_INNER_LAYER = "drynav-flood-zone-layer-slow-inner"
private const val CENTER_SOURCE = "drynav-flood-zone-source-centers"
private const val CENTER_LAYER = "drynav-flood-zone-layer-centers"
private const val CIRCLE_SEGMENTS = 64

/**
 * Draws flood affected areas as two concentric circles: a large outer zone
 * underneath and a smaller ~80% inner zone on top. [pulseScale] is used only
 * by the public map screens for a subtle breathing/pulsing effect. Report
 * placement passes 1.0 so the preview remains completely static.
 *
 * [showCenterDots] is intended for the report-placement map, where the user
 * needs an unmistakable point showing the exact location being pinned.
 */
fun updateFloodHeatmap(
    style: Style,
    reports: List<FloodReport>,
    enabled: Boolean,
    pulseScale: Double = 1.0,
    showCenterDots: Boolean = false
) {
    if (!enabled) {
        removeZoneLayer(style, BLOCKED_LAYER, BLOCKED_SOURCE)
        removeZoneLayer(style, BLOCKED_INNER_LAYER, BLOCKED_INNER_SOURCE)
        removeZoneLayer(style, SLOW_LAYER, SLOW_SOURCE)
        removeZoneLayer(style, SLOW_INNER_LAYER, SLOW_INNER_SOURCE)
        removeZoneLayer(style, CENTER_LAYER, CENTER_SOURCE)
        return
    }

    val active = reports.filter { !it.isCleared }
    val (blocked, slow) = active.partition { it.severity == FloodSeverity.IMPASSABLE }
    val safePulse = pulseScale.coerceIn(0.88, 1.12)

    updateZoneSource(style, BLOCKED_SOURCE, blocked, safePulse)
    updateZoneSource(style, BLOCKED_INNER_SOURCE, blocked, safePulse * 0.80)
    updateZoneSource(style, SLOW_SOURCE, slow, safePulse)
    updateZoneSource(style, SLOW_INNER_SOURCE, slow, safePulse * 0.80)

    ensureZoneLayer(style, BLOCKED_LAYER, BLOCKED_SOURCE, blocked = true, inner = false)
    ensureZoneLayer(style, BLOCKED_INNER_LAYER, BLOCKED_INNER_SOURCE, blocked = true, inner = true)
    ensureZoneLayer(style, SLOW_LAYER, SLOW_SOURCE, blocked = false, inner = false)
    ensureZoneLayer(style, SLOW_INNER_LAYER, SLOW_INNER_SOURCE, blocked = false, inner = true)

    if (showCenterDots) {
        updateCenterSource(style, reports.filter { !it.isCleared })
        ensureCenterLayer(style)
    } else {
        removeZoneLayer(style, CENTER_LAYER, CENTER_SOURCE)
    }
}

private fun removeZoneLayer(style: Style, layerId: String, sourceId: String) {
    if (style.styleLayerExists(layerId)) style.removeStyleLayer(layerId)
    if (style.styleSourceExists(sourceId)) style.removeStyleSource(sourceId)
}

private fun updateZoneSource(
    style: Style,
    sourceId: String,
    reports: List<FloodReport>,
    scale: Double
) {
    val features = reports.map { report ->
        Feature.fromGeometry(
            makeCircle(
                report.longitude,
                report.latitude,
                report.floodRadiusMeters.coerceAtLeast(1.0) * scale
            )
        )
    }
    val collection = FeatureCollection.fromFeatures(features)
    val existing = style.getSourceAs<GeoJsonSource>(sourceId)
    if (existing != null) {
        existing.featureCollection(collection)
    } else {
        style.addSource(geoJsonSource(sourceId) { featureCollection(collection) })
    }
}

private fun ensureZoneLayer(
    style: Style,
    layerId: String,
    sourceId: String,
    blocked: Boolean,
    inner: Boolean
) {
    if (style.styleLayerExists(layerId)) return
    style.addLayer(
        fillLayer(layerId, sourceId) {
            fillColor(if (blocked) "#E53935" else "#FFB300")
            fillOpacity(if (inner) 0.38 else 0.22)
            // High-contrast white edge so the affected area remains visible
            // against satellite, dark, and light map backgrounds.
            fillOutlineColor("#FFFFFF")
        }
    )
}

private fun updateCenterSource(style: Style, reports: List<FloodReport>) {
    val features = reports.map { report ->
        Feature.fromGeometry(Point.fromLngLat(report.longitude, report.latitude))
    }
    val collection = FeatureCollection.fromFeatures(features)
    val existing = style.getSourceAs<GeoJsonSource>(CENTER_SOURCE)
    if (existing != null) {
        existing.featureCollection(collection)
    } else {
        style.addSource(geoJsonSource(CENTER_SOURCE) { featureCollection(collection) })
    }
}

private fun ensureCenterLayer(style: Style) {
    if (style.styleLayerExists(CENTER_LAYER)) return
    style.addLayer(
        circleLayer(CENTER_LAYER, CENTER_SOURCE) {
            circleRadius(5.5)
            circleColor("#FFFFFF")
            circleStrokeColor("#263238")
            circleStrokeWidth(2.0)
            circleOpacity(1.0)
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
