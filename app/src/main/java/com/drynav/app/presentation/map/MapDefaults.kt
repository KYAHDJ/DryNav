package com.drynav.app.presentation.map

import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.PuckBearingSource
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.locationcomponent.location2
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar

/** Sensible camera start before the first GPS fix lands — Metro Manila. */
val LUZON_CENTER: Point = Point.fromLngLat(121.0437, 14.6510)

/**
 * One-stop map chrome: no Mapbox logo/attribution/scalebar. Panning/zooming
 * is unrestricted — the whole world is browsable and searchable, same as
 * any other map app.
 */
fun MapView.applyDryNavMapDefaults() {
    logo.updateSettings { enabled = false }
    attribution.updateSettings { enabled = false }
    scalebar.updateSettings { enabled = false }
    getMapboxMap().setCamera(
        CameraOptions.Builder().center(LUZON_CENTER).zoom(10.0).build()
    )
}

/**
 * Enables Mapbox's own GPS location puck — the stock Google-Maps-style dot
 * with a built-in bearing arrow. Bearing is driven by [PuckBearingSource.COURSE]
 * (GPS direction of travel), not HEADING (the device compass) — a phone's
 * magnetometer is unreliable inside a moving vehicle due to magnetic
 * interference from the car body. [PuckBearingSource] only lives on the
 * `location2` settings tier, not the plain `location` one. Scaled up ~30% —
 * the SDK default read as too small on-device.
 *
 * Mood characters are deliberately NOT rendered through this system (see
 * [MapScreen]'s own-mood `PointAnnotation`) — Mapbox's location-indicator
 * layer has no public rotation-alignment/pitch-billboard setting, so a full
 * standing character puck through it would tilt into the ground plane as
 * the map pitches and spin as it rotates, instead of always facing the
 * camera. A plain [com.mapbox.maps.plugin.annotation.generated.PointAnnotation]
 * with `iconRotationAlignment = VIEWPORT` doesn't have that problem.
 */
fun MapView.applyDryNavLocationPuck() {
    val puck = location.createDefault2DPuck(context = context, withBearing = true).apply {
        scaleExpression = "1.3"
    }
    location.updateSettings {
        enabled = true
        pulsingEnabled = true
        locationPuck = puck
    }
    location2.updateSettings2 {
        puckBearingEnabled = true
        puckBearingSource = PuckBearingSource.COURSE
    }
}

/** Toggles the default Mapbox puck on/off — off while the own-mood character puck is showing instead. */
fun MapView.setDefaultPuckEnabled(enabled: Boolean) {
    location.updateSettings { this.enabled = enabled }
}
