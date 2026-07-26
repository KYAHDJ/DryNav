package com.drynav.app.routing

import android.util.Log
import com.drynav.app.domain.model.FloodReport
import com.drynav.app.domain.model.FloodSeverity
import com.drynav.app.domain.repository.FloodRepository
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import com.mapbox.turf.TurfMisc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Wraps [MapboxNavigation] route requests with flood avoidance.
 *
 * History worth knowing before touching this again: a version with NO
 * `exclude=point(...)` hint sent to Mapbox was tried — it was clean and
 * simple, but on-screen diagnostics proved Mapbox then only ever returns
 * ONE route with no real alternative, because nothing told its routing
 * engine there was any reason to consider a different path. A version with
 * a very DENSE hint (many closely-packed points) was also tried — that
 * over-constrains the routing graph and produces nonsensical zig-zags for
 * short trips. A version that force-injects an artificial waypoint to one
 * side of the flood was tried too — worse still (unrealistic detours, extra
 * stop-circles on the map for the injected leg).
 *
 * Confirmed via live Directions API testing (not guessed): `exclude=point(..)`
 * genuinely forces Mapbox off that road and onto a real alternate corridor
 * when the point sits on the route, and sending many points (30+, including
 * irrelevant ones) doesn't error or get silently truncated. The real bug
 * found in production use wasn't the exclude mechanism itself — it was that
 * [buildExclusions] pooled every active flood's points together, sorted by
 * raw distance from the trip origin, and applied ONE global cap. With more
 * than a couple of active flood reports around (very normal after this app
 * has been used/tested for a while), a flood that mattered for THIS trip
 * could be silently crowded out of the point budget by other floods that
 * happened to be closer to the origin but irrelevant to this route. Fixed
 * by guaranteeing every active flood gets a fair round-robin share of the
 * budget instead of a first-come-first-served pool. [requestFloodAwareRoute]
 * also now auto-escalates from the light hint straight to the dense one
 * in the same call, silently, before ever telling the user "no alternative"
 * — that escalation used to require the user to notice a warning and tap
 * "Find Alternative" manually, which read as the system not even trying.
 *
 * The one thing this class actually trusts regardless of what Mapbox does
 * with the hint is [floodIntersectsRoute]: plain local geometry math, no
 * API involved, used to verify and pick among whatever routes come back.
 */
class FloodAwareRouter(
    private val mapboxNavigation: MapboxNavigation,
    private val floodRepository: FloodRepository,
    private val scope: CoroutineScope
) {

    sealed interface RoutingState {
        data object Idle : RoutingState
        data object Calculating : RoutingState
        data class RouteReady(
            val routes: List<NavigationRoute>,
            /** True when even the best route still crosses a blocked flood. */
            val crossesFlood: Boolean = false,
            /** True when the chosen route passes a passable (slow) flood — not blocking, just worth a heads-up. */
            val crossesSlowFlood: Boolean = false,
            /** Human-readable summary of this decision, for on-screen diagnostics. */
            val diagnostics: String = ""
        ) : RoutingState
        data class Failed(val reason: String) : RoutingState
    }

    private val _routingState = MutableStateFlow<RoutingState>(RoutingState.Idle)
    val routingState: StateFlow<RoutingState> = _routingState

    /** Emits the flood report that most recently forced a reroute (for UI toasts). */
    private val _rerouteTrigger = MutableStateFlow<FloodReport?>(null)
    val rerouteTrigger: StateFlow<FloodReport?> = _rerouteTrigger

    private var floodWatchJob: Job? = null
    private var lastOrigin: Point? = null
    private var lastDestination: Point? = null
    private var lastProfile: String = DirectionsCriteria.PROFILE_DRIVING_TRAFFIC

    /**
     * Requests routes from [origin] to [destination], preferring whichever
     * alternative Mapbox returns doesn't cross any active IMPASSABLE
     * [FloodReport] in [activeFloods]. Tries the light exclusion hint first;
     * if the result still crosses a blocked flood, automatically escalates
     * to the denser hint in the same call — silently, before ever bothering
     * the user — since requiring a manual tap to "try harder" read as the
     * system giving up too easily.
     */
    suspend fun requestFloodAwareRoute(
        origin: Point,
        destination: Point,
        activeFloods: List<FloodReport>,
        profile: String = DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
    ): List<NavigationRoute> {
        val light = performRequest(origin, destination, activeFloods, profile, ExclusionTier.LIGHT)
        val lightState = _routingState.value as? RoutingState.RouteReady
        if (light.isEmpty() || lightState?.crossesFlood != true) return light
        Log.i(TAG, "Light exclusion hint still crosses a flood — auto-escalating to dense")
        return performRequest(origin, destination, activeFloods, profile, ExclusionTier.DENSE)
    }

    /**
     * Last-resort manual retry — only reachable from the "Find Alternative"
     * button, which by the time it's shown means [requestFloodAwareRoute]
     * already tried both the light AND dense tiers automatically. Uses an
     * even heavier exclusion hint than either of those.
     */
    suspend fun requestDenserAlternative(
        origin: Point,
        destination: Point,
        activeFloods: List<FloodReport>,
        profile: String = DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
    ): List<NavigationRoute> =
        performRequest(origin, destination, activeFloods, profile, ExclusionTier.EXTRA_DENSE)

    private suspend fun performRequest(
        origin: Point,
        destination: Point,
        activeFloods: List<FloodReport>,
        profile: String,
        tier: ExclusionTier
    ): List<NavigationRoute> {
        lastOrigin = origin
        lastDestination = destination
        lastProfile = profile

        val impassable = activeFloods.filter { it.isActiveHazard }
        val slowFloods = activeFloods.filter { it.isLiveHazard && it.severity == FloodSeverity.PASSABLE }
        Log.i(TAG, "Requesting route (tier=$tier), ${impassable.size} active flood(s) to check against")

        _routingState.value = RoutingState.Calculating
        val builder = RouteOptions.builder()
            .applyDefaultNavigationOptions(profile)
            .coordinatesList(listOf(origin, destination))
            .alternatives(true)
            .steps(true)
            .voiceInstructions(true)
            .bannerInstructions(true)
        // Exclude points are rejected outright on the walking profile.
        if (profile != DirectionsCriteria.PROFILE_WALKING) {
            val exclusions = buildExclusions(impassable, tier)
            Log.i(TAG, "Sending ${exclusions.size} exclusion point(s) (tier=$tier)")
            builder.excludeList(exclusions)
        }
        val routeOptions = builder.build()

        return suspendCoroutine { cont ->
            mapboxNavigation.requestRoutes(
                routeOptions,
                object : NavigationRouterCallback {
                    override fun onRoutesReady(
                        routes: List<NavigationRoute>,
                        routerOrigin: RouterOrigin
                    ) {
                        // Prefer whichever of Mapbox's own alternatives is
                        // actually clean — never distort the route ourselves.
                        val (clean, dirty) = routes.partition { route ->
                            impassable.none { floodIntersectsRoute(it, route) }
                        }
                        val diagnostics = "${impassable.size} flood(s) active · " +
                            "${routes.size} route(s) back · ${clean.size} clean / ${dirty.size} crossing"
                        Log.i(TAG, diagnostics)
                        val ordered = clean + dirty
                        val chosen = ordered.firstOrNull()
                        _routingState.value = RoutingState.RouteReady(
                            routes = ordered,
                            crossesFlood = clean.isEmpty() && impassable.isNotEmpty(),
                            crossesSlowFlood = chosen != null &&
                                slowFloods.any { floodIntersectsRoute(it, chosen) },
                            diagnostics = diagnostics
                        )
                        mapboxNavigation.setNavigationRoutes(ordered)
                        cont.resume(ordered)
                    }

                    override fun onFailure(
                        reasons: List<RouterFailure>,
                        routeOptions: RouteOptions
                    ) {
                        val msg = reasons.joinToString { it.message }
                        Log.e(TAG, "Route request failed: $msg")
                        _routingState.value = RoutingState.Failed(msg)
                        cont.resume(emptyList())
                    }

                    override fun onCanceled(
                        routeOptions: RouteOptions,
                        routerOrigin: RouterOrigin
                    ) {
                        _routingState.value = RoutingState.Idle
                        cont.resume(emptyList())
                    }
                }
            )
        }
    }

    /**
     * Starts watching the realtime flood stream and reroutes automatically
     * whenever a new impassable flood intersects the active route. Also runs
     * a periodic recheck (see [periodicRecheckWhileCrossing]) so a route
     * that couldn't dodge a flood from the original starting point isn't
     * stuck that way for the rest of the trip — as the driver gets closer,
     * a clean corridor that wasn't reachable/preferred from the old origin
     * can become the obvious choice, but nothing re-asks Mapbox for it
     * unless something prompts a new request. Call when guidance starts;
     * call [stop] when guidance ends.
     */
    fun watchFloodsAndReroute() {
        floodWatchJob?.cancel()
        floodWatchJob = scope.launch {
            launch {
                floodRepository.getLiveFloodReports().collect { reports ->
                    val state = _routingState.value as? RoutingState.RouteReady
                        ?: return@collect
                    val primary = state.routes.firstOrNull() ?: return@collect

                    // Pending (unapproved) reports must never affect live routing —
                    // otherwise anyone could block a road just by claiming it's
                    // flooded. Every active flood is re-checked against the
                    // CURRENT primary route on every flood-data change — no
                    // "already tried this one, never check again" exemption.
                    val approved = reports.filter { it.isApproved }
                    val newThreats = approved.filter { report ->
                        report.isActiveHazard && floodIntersectsRoute(report, primary)
                    }

                    if (newThreats.isNotEmpty()) {
                        Log.i(TAG, "New flood(s) on route: ${newThreats.map { it.id }} — rerouting")
                        _rerouteTrigger.value = newThreats.first()
                        val origin = currentPuckPosition() ?: lastOrigin ?: return@collect
                        val destination = lastDestination ?: return@collect
                        requestFloodAwareRoute(origin, destination, approved, lastProfile)
                    }
                }
            }
            launch { periodicRecheckWhileCrossing() }
        }
    }

    /**
     * While the active route still crosses a blocked flood, keep asking
     * Mapbox again every [RECHECK_INTERVAL_MS] from wherever the driver
     * currently is — confirmed by direct user testing that a route
     * recalculated from a later position on the same trip can dodge a flood
     * that the original starting point's route couldn't, even with nothing
     * else (flood data, profile) having changed. Without this, the app just
     * keeps silently warning about the same crossing for the rest of the
     * trip instead of ever trying again.
     */
    private suspend fun periodicRecheckWhileCrossing() {
        while (true) {
            delay(RECHECK_INTERVAL_MS)
            val state = _routingState.value as? RoutingState.RouteReady ?: continue
            if (!state.crossesFlood) continue
            val origin = currentPuckPosition() ?: lastOrigin ?: continue
            val destination = lastDestination ?: continue
            val approved = floodRepository.getLiveFloodReports().first().filter { it.isApproved }
            Log.i(TAG, "Route still crosses a flood — periodic recheck from current position")
            requestFloodAwareRoute(origin, destination, approved, lastProfile)
        }
    }

    fun stop() {
        floodWatchJob?.cancel()
        floodWatchJob = null
        mapboxNavigation.setNavigationRoutes(emptyList())
        _routingState.value = RoutingState.Idle
    }

    fun consumeRerouteTrigger() {
        _rerouteTrigger.value = null
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    /**
     * Builds the Directions `exclude` entries for [tier]. Every active flood
     * gets its own points round-robin'd into the final list instead of one
     * shared pool sorted by distance-from-origin — the earlier version could
     * let a flood irrelevant to this trip (just happening to be closer to
     * the origin) silently crowd out the one actually on the route, which
     * was the real cause of routes still crossing a flood that had an
     * obvious clean alternative.
     */
    private fun buildExclusions(impassable: List<FloodReport>, tier: ExclusionTier): List<String> {
        val perReport = impassable.map { it.exclusionPoints(tier) }
        val combined = perReport.sumOf { it.size }
        val points = if (combined <= tier.totalCap) {
            perReport.flatten()
        } else {
            roundRobinTake(perReport, tier.totalCap)
        }
        return points.map { "point(${it.longitude()} ${it.latitude()})" }
    }

    /** Takes from each group in turn (round-robin) until [cap] points are collected. */
    private fun roundRobinTake(groups: List<List<Point>>, cap: Int): List<Point> {
        val result = mutableListOf<Point>()
        var index = 0
        while (result.size < cap && groups.any { index < it.size }) {
            for (group in groups) {
                if (index < group.size) {
                    result.add(group[index])
                    if (result.size >= cap) break
                }
            }
            index++
        }
        return result
    }

    /** Center point + an evenly-spaced trace of the drawn area. */
    private fun FloodReport.exclusionPoints(tier: ExclusionTier): List<Point> =
        allGeometryPoints().resampleByDistance(tier.spacingMeters).take(tier.perReportCap)

    /**
     * The full drawn road (undecimated) when one was painted; the single
     * center point only for a point-only report. Deliberately NOT both —
     * [FloodReport.latitude]/[FloodReport.longitude] is a centroid of the
     * drawn strokes (see [com.drynav.app.presentation.report.ReportViewModel.submit]),
     * which for a bent/curved road can land off the actual pavement (e.g.
     * across a median or a nearby block), which would otherwise get checked
     * against routes as if it were a real point on the flooded road and
     * produce a "crosses a flood" false positive against a DIFFERENT nearby
     * road that was never actually flooded.
     */
    private fun FloodReport.allGeometryPoints(): List<Point> =
        if (areaPoints.isNotEmpty()) {
            areaPoints.map { Point.fromLngLat(it.lng, it.lat) }
        } else {
            listOf(Point.fromLngLat(longitude, latitude))
        }

    private fun List<Point>.resampleByDistance(spacingMeters: Double): List<Point> {
        if (size <= 1) return this
        val kept = mutableListOf(first())
        var lastKept = first()
        for (point in drop(1)) {
            if (TurfMeasurement.distance(lastKept, point, TurfConstants.UNIT_METERS) >= spacingMeters) {
                kept.add(point)
                lastKept = point
            }
        }
        kept.add(last()) // always include the final point of the trace
        return kept.distinct()
    }

    /**
     * True if any part of [report] (center or drawn area) is near [route].
     * Uses the FULL, undecimated geometry — this is local math, not an API
     * call — and is the one check this whole class actually trusts.
     */
    private fun floodIntersectsRoute(report: FloodReport, route: NavigationRoute): Boolean {
        val geometry = route.directionsRoute.geometry() ?: return false
        val line = LineString.fromPolyline(geometry, PRECISION_6)
        return report.allGeometryPoints().any { floodPoint ->
            val snapped = TurfMisc.nearestPointOnLine(floodPoint, line.coordinates())
            val snappedPoint = snapped.geometry() as? Point ?: return@any false
            TurfMeasurement.distance(
                floodPoint, snappedPoint, TurfConstants.UNIT_METERS
            ) <= FLOOD_INTERSECTION_THRESHOLD_METERS
        }
    }

    private fun currentPuckPosition(): Point? = lastEnhancedLocation

    /**
     * Updated externally (by the ViewModel's LocationObserver) with the latest
     * map-matched position during guidance, so reroutes start from where the
     * driver actually is rather than the original trip origin.
     */
    var lastEnhancedLocation: Point? = null

    /**
     * Live Directions API testing confirmed Mapbox happily accepts 30+
     * exclude points with no error and no silent truncation, so these caps
     * are sized for "cover every active flood generously," not for staying
     * under some API ceiling.
     */
    private enum class ExclusionTier(
        val spacingMeters: Double,
        val perReportCap: Int,
        val totalCap: Int
    ) {
        /** First, automatic attempt. */
        LIGHT(spacingMeters = 45.0, perReportCap = 10, totalCap = 60),
        /** Second, automatic attempt — tried in the same call if LIGHT still crosses a flood. */
        DENSE(spacingMeters = 18.0, perReportCap = 20, totalCap = 100),
        /** Manual last resort, only from the "Find Alternative" button. */
        EXTRA_DENSE(spacingMeters = 10.0, perReportCap = 35, totalCap = 160)
    }

    companion object {
        private const val TAG = "FloodAwareRouter"
        private const val PRECISION_6 = 6
        // How often to retry finding a clean route while the active one
        // still crosses a flood — frequent enough to catch real progress,
        // not so often it hammers the Directions API.
        private const val RECHECK_INTERVAL_MS = 20_000L
        // Was 75m to account for wide arterials (service road + median +
        // carriageways). Direct user testing showed that was too loose in
        // practice — routes that were merely nearby a flood, not actually on
        // it, were triggering the crossing warning. Tightened back down; a
        // real flooded-road hit should be within normal lane/shoulder slack.
        const val FLOOD_INTERSECTION_THRESHOLD_METERS = 30.0
    }
}
