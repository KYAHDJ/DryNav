package com.drynav.app.presentation.map

import com.drynav.app.domain.model.FloodReport
import com.drynav.app.domain.model.FloodSeverity
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.get
import com.mapbox.maps.extension.style.expressions.dsl.generated.heatmapDensity
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.extension.style.expressions.dsl.generated.rgb
import com.mapbox.maps.extension.style.expressions.dsl.generated.rgba
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.heatmapLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement

private const val BLOCKED_SOURCE = "drynav-flood-heat-source-blocked"
private const val BLOCKED_LAYER = "drynav-flood-heat-layer-blocked"
private const val SLOW_SOURCE = "drynav-flood-heat-source-slow"
private const val SLOW_LAYER = "drynav-flood-heat-layer-slow"
private const val HEAT_SAMPLE_SPACING_METERS = 8.0

/**
 * Renders flood reports as pure heat-vision, no marker icons or hard-edged
 * lines — see the history in the two layers below. Two separate heatmap
 * layers (not one shared ramp) so severity reads as an actual color
 * difference: blocked roads glow red, passable/slow ones glow yellow. A
 * single shared layer can't do this because a heatmap's color always comes
 * from its own aggregated density, not per-feature properties.
 */
fun updateFloodHeatmap(style: Style, reports: List<FloodReport>, enabled: Boolean) {
    if (!enabled) {
        removeHeatLayer(style, BLOCKED_LAYER, BLOCKED_SOURCE)
        removeHeatLayer(style, SLOW_LAYER, SLOW_SOURCE)
        return
    }

    val active = reports.filter { !it.isCleared }
    val (blocked, slow) = active.partition { it.severity == FloodSeverity.IMPASSABLE }

    updateHeatSource(style, BLOCKED_SOURCE, blocked)
    updateHeatSource(style, SLOW_SOURCE, slow)
    ensureLayer(style, BLOCKED_LAYER, BLOCKED_SOURCE, blocked = true)
    ensureLayer(style, SLOW_LAYER, SLOW_SOURCE, blocked = false)
}

private fun removeHeatLayer(style: Style, layerId: String, sourceId: String) {
    if (style.styleLayerExists(layerId)) style.removeStyleLayer(layerId)
    if (style.styleSourceExists(sourceId)) style.removeStyleSource(sourceId)
}

private fun updateHeatSource(style: Style, sourceId: String, reports: List<FloodReport>) {
    val heatFeatures = reports.flatMap { report ->
        val tracePoints = report.areaStrokes.flatMap { stroke ->
            resampleDense(stroke.map { Point.fromLngLat(it.lng, it.lat) }, HEAT_SAMPLE_SPACING_METERS)
        }
        val points = tracePoints.ifEmpty {
            listOf(Point.fromLngLat(report.longitude, report.latitude))
        }
        points.map { point -> Feature.fromGeometry(point).apply { addNumberProperty("weight", 1.0) } }
    }
    val collection = FeatureCollection.fromFeatures(heatFeatures)
    val existing = style.getSourceAs<GeoJsonSource>(sourceId)
    if (existing != null) {
        existing.featureCollection(collection)
    } else {
        style.addSource(geoJsonSource(sourceId) { featureCollection(collection) })
    }
}

private fun ensureLayer(style: Style, layerId: String, sourceId: String, blocked: Boolean) {
    if (style.styleLayerExists(layerId)) return
    style.addLayer(
        heatmapLayer(layerId, sourceId) {
            heatmapColor(if (blocked) redRamp() else yellowRamp())
            heatmapWeight(get { literal("weight") })
            heatmapIntensity(1.0)
            heatmapRadius(
                interpolate {
                    linear()
                    zoom()
                    stop { literal(8); literal(6.0) }
                    stop { literal(13); literal(14.0) }
                    stop { literal(16); literal(24.0) }
                }
            )
            heatmapOpacity(0.75)
        }
    )
}

/** Blocked / impassable roads — red glow. */
private fun redRamp() = interpolate {
    linear()
    heatmapDensity()
    stop { literal(0.0); rgba(255.0, 87.0, 34.0, 0.0) }
    stop { literal(0.2); rgba(255.0, 138.0, 101.0, 0.5) }
    stop { literal(0.4); rgb(255.0, 87.0, 34.0) }
    stop { literal(0.6); rgb(244.0, 67.0, 54.0) }
    stop { literal(0.8); rgb(211.0, 47.0, 47.0) }
    stop { literal(1.0); rgb(183.0, 28.0, 28.0) }
}

/** Passable / slow roads — yellow glow, reads as "caution" not "blocked". */
private fun yellowRamp() = interpolate {
    linear()
    heatmapDensity()
    stop { literal(0.0); rgba(255.0, 235.0, 59.0, 0.0) }
    stop { literal(0.2); rgba(255.0, 241.0, 118.0, 0.5) }
    stop { literal(0.4); rgb(255.0, 235.0, 59.0) }
    stop { literal(0.6); rgb(255.0, 213.0, 0.0) }
    stop { literal(0.8); rgb(255.0, 179.0, 0.0) }
    stop { literal(1.0); rgb(255.0, 143.0, 0.0) }
}

/**
 * Linearly interpolates extra points along the polyline so the gaps between
 * decimated/stored points don't read as separate blobs — a long road becomes
 * one continuous band of closely-spaced points instead of a sparse trail.
 */
private fun resampleDense(points: List<Point>, spacingMeters: Double): List<Point> {
    if (points.size < 2) return points
    val out = mutableListOf(points.first())
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val segMeters = TurfMeasurement.distance(a, b, TurfConstants.UNIT_METERS)
        val steps = (segMeters / spacingMeters).toInt().coerceAtMost(200)
        for (s in 1..steps) {
            val f = s.toDouble() / (steps + 1)
            out.add(
                Point.fromLngLat(
                    a.longitude() + (b.longitude() - a.longitude()) * f,
                    a.latitude() + (b.latitude() - a.latitude()) * f
                )
            )
        }
        out.add(b)
    }
    return out
}
