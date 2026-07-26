package com.drynav.app.presentation.map

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drynav.app.R
import com.drynav.app.data.auth.AuthRepository
import com.drynav.app.data.location.LocationProvider
import com.drynav.app.data.prefs.UserPreferences
import com.drynav.app.data.search.GeocodingService
import com.drynav.app.data.search.PlaceResult
import com.drynav.app.domain.model.FloodReport
import com.drynav.app.domain.model.FloodSeverity
import com.drynav.app.domain.model.Mood
import com.drynav.app.domain.model.SavedPlace
import com.drynav.app.data.presence.PresenceManager
import com.drynav.app.domain.repository.FloodRepository
import com.drynav.app.domain.repository.SavedPlacesRepository
import com.drynav.app.routing.FloodAwareRouter
import com.mapbox.bindgen.Expected
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.plugin.animation.CameraAnimationsPlugin
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.OffRouteObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraStateChangedObserver
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.voice.api.MapboxSpeechApi
import com.mapbox.navigation.ui.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.ui.voice.model.SpeechAnnouncement
import com.mapbox.navigation.ui.voice.model.SpeechError
import com.mapbox.navigation.ui.voice.model.SpeechValue
import com.mapbox.navigation.ui.voice.model.SpeechVolume
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

// ~3.6 km/h — below this, GPS/map-matched bearing is unreliable noise.
private const val MIN_BEARING_SPEED_MPS = 1.0f
// "Nearby" for the Waze-style toast — a few city blocks, not "anywhere online".
private const val NEARBY_RADIUS_METERS = 3_000.0
private const val NEARBY_MESSAGE_DURATION_MS = 3_800L

@HiltViewModel
class MapViewModel @Inject constructor(
    private val floodRepository: FloodRepository,
    private val locationProvider: LocationProvider,
    private val geocodingService: GeocodingService,
    private val prefs: UserPreferences,
    private val authRepository: AuthRepository,
    private val presenceManager: PresenceManager,
    private val savedPlacesRepository: SavedPlacesRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState(isAdmin = authRepository.isAdmin))
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    /**
     * The router is created lazily once the screen hands us the
     * [MapboxNavigation] instance (it needs an Activity-scoped Mapbox setup).
     */
    private var router: FloodAwareRouter? = null
    private var mapboxNavigation: MapboxNavigation? = null
    private var routerStateJob: Job? = null
    private var reroutingBannerJob: Job? = null
    private var searchJob: Job? = null

    /**
     * Feeds the map's location puck during active navigation. Previously the
     * puck was driven by Mapbox's generic GPS location component — a totally
     * separate, slower, unsmoothed feed from the one actually powering
     * rerouting/ETA/turn instructions below. That mismatch was the real
     * cause of the puck looking laggy and facing the wrong way: it was
     * showing a different, worse location feed than the one the app was
     * actually navigating with. Feeding it from the same enhanced location
     * observed here keeps the puck perfectly in sync.
     */
    val navigationLocationProvider = NavigationLocationProvider()

    // ------------------------------------------------------------------
    // Waze-style follow camera (NavigationCamera + ViewportDataSource)
    // ------------------------------------------------------------------

    private var viewportDataSource: MapboxNavigationViewportDataSource? = null
    private var navigationCamera: NavigationCamera? = null
    private var gesturesHandler: NavigationBasicGesturesHandler? = null
    private var cameraPlugin: CameraAnimationsPlugin? = null

    private val cameraStateObserver = NavigationCameraStateChangedObserver { state ->
        _uiState.update { it.copy(isFollowingCamera = state == NavigationCameraState.FOLLOWING) }
    }

    // ------------------------------------------------------------------
    // Turn-by-turn voice guidance
    // ------------------------------------------------------------------

    // Instantiated eagerly (not lazily) per MapboxVoiceInstructionsPlayer's own
    // kdoc warning, so there's no first-instruction delay once a trip starts.
    private val speechApi = MapboxSpeechApi(
        appContext,
        appContext.getString(R.string.mapbox_access_token),
        Locale.getDefault().language
    )
    private val voiceInstructionsPlayer = MapboxVoiceInstructionsPlayer(
        appContext,
        Locale.getDefault().language
    )

    private val voicePlayerCallback =
        MapboxNavigationConsumer<SpeechAnnouncement> { announcement -> speechApi.clean(announcement) }

    private val speechCallback =
        MapboxNavigationConsumer<Expected<SpeechError, SpeechValue>> { expected ->
            expected.fold(
                { error -> voiceInstructionsPlayer.play(error.fallback, voicePlayerCallback) },
                { value -> voiceInstructionsPlayer.play(value.announcement, voicePlayerCallback) }
            )
        }

    private val voiceInstructionsObserver = VoiceInstructionsObserver { voiceInstructions ->
        speechApi.generate(voiceInstructions, speechCallback)
    }

    // Below this speed, GPS/map-matched course-over-ground is noise, not a
    // real heading — feeding it straight to the puck/camera is what made
    // both look like they were "spinning" instead of calmly following,
    // unlike Google Maps/Waze which hold the last known heading while
    // stationary or crawling. Freezing bearing below the threshold (and
    // feeding that same frozen value to both the puck AND the follow
    // camera, since they read it off the same Location object here) fixes
    // both at once.
    private var stableBearing = 0f

    /** Freezes bearing below [MIN_BEARING_SPEED_MPS] instead of passing through GPS noise. */
    private fun stabilizeBearing(loc: android.location.Location, current: Float): Float =
        if (loc.hasSpeed() && loc.speed >= MIN_BEARING_SPEED_MPS && loc.hasBearing()) loc.bearing else current

    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: android.location.Location) = Unit

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val raw = locationMatcherResult.enhancedLocation
            stableBearing = stabilizeBearing(raw, stableBearing)
            val loc: android.location.Location = if (raw.hasSpeed() && raw.speed >= MIN_BEARING_SPEED_MPS) {
                raw
            } else {
                android.location.Location(raw).apply { bearing = stableBearing }
            }

            val point = Point.fromLngLat(loc.longitude, loc.latitude)
            router?.lastEnhancedLocation = point
            _uiState.update { it.copy(userLocation = point) }

            val transition: (android.animation.ValueAnimator.() -> Unit) =
                if (locationMatcherResult.isTeleport) {
                    { duration = 0 }
                } else {
                    { duration = 1000 }
                }
            navigationLocationProvider.changePosition(
                location = loc,
                keyPoints = locationMatcherResult.keyPoints,
                latLngTransitionOptions = transition,
                bearingTransitionOptions = transition
            )

            viewportDataSource?.onLocationChanged(loc)
            viewportDataSource?.evaluate()
        }
    }

    private val routeProgressObserver = RouteProgressObserver { progress: RouteProgress ->
        _uiState.update {
            it.copy(
                currentInstruction = progress.bannerInstructions
                    ?.primary()?.text()
                    ?: it.currentInstruction,
                maneuverModifier = progress.bannerInstructions?.primary()?.modifier()
                    ?: it.maneuverModifier,
                remainingDistanceMeters = progress.distanceRemaining.toDouble(),
                remainingDurationSeconds = progress.durationRemaining
            )
        }
        viewportDataSource?.onRouteProgressChanged(progress)
        viewportDataSource?.evaluate()
    }

    /**
     * Silent off-route correction: the driver left the calculated route (wrong
     * turn, different street, etc. — nothing to do with a flood). Mapbox's own
     * default [MapboxNavigation] reroute controller would normally handle this
     * automatically, but it requests a plain route with no flood exclusions —
     * so it's turned off ([attachNavigation]'s `setRerouteEnabled(false)`) and
     * replaced with this, which re-requests through the same flood-aware path
     * as everything else. Deliberately never touches [MapUiState.showFloodCrossingModal]
     * or any snackbar — recalculating quietly while driving is expected
     * behavior, not something to interrupt the driver about.
     */
    private var offRouteJob: Job? = null
    private val offRouteObserver = OffRouteObserver { offRoute ->
        if (!offRoute) return@OffRouteObserver
        if (offRouteJob?.isActive == true) return@OffRouteObserver
        val activeRouter = router ?: return@OffRouteObserver
        val origin = router?.lastEnhancedLocation ?: _uiState.value.userLocation
        val destination = _uiState.value.destination
        if (origin == null || destination == null) return@OffRouteObserver
        offRouteJob = viewModelScope.launch {
            val routes = activeRouter.requestFloodAwareRoute(
                origin = origin,
                destination = destination,
                activeFloods = _uiState.value.floodReports,
                profile = _uiState.value.travelMode.profile
            )
            if (routes.isNotEmpty()) {
                _uiState.update { it.copy(routes = routes) }
                updateViewportRoute(routes)
            }
        }
    }

    init {
        observeFloodReports()
        fetchInitialLocation()
        loadRecentSearches()
        observeSavedPlaces()
        observePresence()
        viewModelScope.launch {
            prefs.mood.collect { name ->
                _uiState.update { it.copy(mood = Mood.fromName(name) ?: Mood.HAPPY) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Wiring from the UI layer
    // ------------------------------------------------------------------

    fun attachNavigation(navigation: MapboxNavigation) {
        if (mapboxNavigation === navigation) return
        mapboxNavigation = navigation
        router = FloodAwareRouter(navigation, floodRepository, viewModelScope)
        // Mapbox's own default reroute controller is turned off — it would
        // otherwise race our flood-aware one and, worse, hand back a plain
        // route with no flood exclusions at all. offRouteObserver below is
        // the sole path for "driver left the route" recalculation now.
        navigation.setRerouteEnabled(false)
        navigation.registerLocationObserver(locationObserver)
        navigation.registerRouteProgressObserver(routeProgressObserver)
        navigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)
        navigation.registerOffRouteObserver(offRouteObserver)
        observeRouterState()
    }

    fun detachNavigation() {
        mapboxNavigation?.let {
            it.unregisterLocationObserver(locationObserver)
            it.unregisterRouteProgressObserver(routeProgressObserver)
            it.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
            it.unregisterOffRouteObserver(offRouteObserver)
        }
        voiceInstructionsPlayer.clear()
        routerStateJob?.cancel()
        offRouteJob?.cancel()
        router?.stop()
        router = null
        mapboxNavigation = null
    }

    /** Builds the Waze-style follow camera once the screen's MapView is ready. */
    fun attachMap(mapboxMap: MapboxMap, cameraPlugin: CameraAnimationsPlugin) {
        if (this.cameraPlugin === cameraPlugin) return
        this.cameraPlugin = cameraPlugin
        val density = appContext.resources.displayMetrics.density
        val source = MapboxNavigationViewportDataSource(mapboxMap).apply {
            // Symmetric top/bottom insets keep the puck centered on screen
            // and continuously tracked as it moves — like Grab/Angkas and
            // other riding apps, rather than anchored low.
            followingPadding = EdgeInsets(
                160.0 * density,
                40.0 * density,
                160.0 * density,
                40.0 * density
            )
        }
        val camera = NavigationCamera(mapboxMap, cameraPlugin, source)
        gesturesHandler = NavigationBasicGesturesHandler(camera).also {
            cameraPlugin.addCameraAnimationsLifecycleListener(it)
        }
        camera.registerNavigationCameraStateChangeObserver(cameraStateObserver)
        viewportDataSource = source
        navigationCamera = camera
    }

    fun detachMap() {
        navigationCamera?.unregisterNavigationCameraStateChangeObserver(cameraStateObserver)
        gesturesHandler?.let { cameraPlugin?.removeCameraAnimationsLifecycleListener(it) }
        gesturesHandler = null
        navigationCamera = null
        viewportDataSource = null
        cameraPlugin = null
    }

    /**
     * Waze-style "other drivers": [PresenceManager] itself broadcasts the
     * signed-in user's position/[Mood] and streams everyone else's, for as
     * long as the app is in the foreground (see [DryNavGraph][com.drynav.app.presentation.navigation.DryNavGraph]) —
     * not tied to this screen. This just mirrors that shared stream into
     * [MapUiState.otherPresences] for rendering.
     */
    private fun observePresence() {
        viewModelScope.launch {
            presenceManager.otherPresences.collect { others ->
                _uiState.update { it.copy(otherPresences = others) }
                maybeShowNearbyMessage()
            }
        }
    }

    /**
     * Waze-style: a brief "N people nearby" toast, shown once per visit to
     * this screen the first time there's actually someone nearby to report
     * — never for zero. Tried again on every presence/location update until
     * it finds a nonzero count (or the screen closes), then never again.
     */
    private var hasShownNearbyMessage = false

    private fun maybeShowNearbyMessage() {
        if (hasShownNearbyMessage) return
        val origin = _uiState.value.userLocation ?: return
        val nearby = _uiState.value.otherPresences.count { presence ->
            TurfMeasurement.distance(
                origin,
                Point.fromLngLat(presence.longitude, presence.latitude),
                TurfConstants.UNIT_METERS
            ) <= NEARBY_RADIUS_METERS
        }
        if (nearby <= 0) return
        hasShownNearbyMessage = true
        _uiState.update { it.copy(nearbyPeopleCount = nearby) }
        viewModelScope.launch {
            delay(NEARBY_MESSAGE_DURATION_MS)
            _uiState.update { it.copy(nearbyPeopleCount = null) }
        }
    }

    /** "Resume following" button — snaps the camera back onto the puck. */
    fun resumeFollowingCamera() {
        navigationCamera?.requestNavigationCameraToFollowing()
    }

    fun toggleVoiceMute() {
        val muted = !_uiState.value.isVoiceMuted
        voiceInstructionsPlayer.volume(SpeechVolume(if (muted) 0f else 1f))
        _uiState.update { it.copy(isVoiceMuted = muted) }
    }

    private fun updateViewportRoute(routes: List<NavigationRoute>) {
        val primary = routes.firstOrNull()
        if (primary != null) {
            viewportDataSource?.onRouteChanged(primary)
        } else {
            viewportDataSource?.clearRouteData()
        }
        viewportDataSource?.evaluate()
    }

    // ------------------------------------------------------------------
    // Destination search
    // ------------------------------------------------------------------

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.length < 3) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // debounce typing
            _uiState.update { it.copy(isSearching = true) }
            val results = geocodingService.search(query, _uiState.value.userLocation)
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    fun selectPlace(place: PlaceResult) {
        _uiState.update {
            it.copy(
                searchQuery = place.name,
                searchResults = emptyList(),
                destinationName = place.name
            )
        }
        viewModelScope.launch {
            val resolved = geocodingService.retrieve(place) ?: return@launch
            val point = resolved.point ?: return@launch
            geocodingService.resetSession()
            rememberRecentSearch(resolved)
            navigateTo(point)
        }
    }

    /** A Saved Place already has a resolved point — no search/retrieve round trip needed. */
    fun selectSavedPlace(place: SavedPlace) {
        _uiState.update {
            it.copy(searchQuery = place.label, searchResults = emptyList(), destinationName = place.label)
        }
        navigateTo(place.point)
    }

    /** Persists the latest searched destinations (most recent first, unbounded — the list scrolls). */
    private fun rememberRecentSearch(place: PlaceResult) {
        val updated = listOf(place) + _uiState.value.recentSearches
            .filterNot { it.name == place.name && it.point == place.point }
        _uiState.update { it.copy(recentSearches = updated) }
        viewModelScope.launch { prefs.setRecentSearches(updated) }
    }

    private fun loadRecentSearches() {
        viewModelScope.launch {
            val recents = prefs.getRecentSearches()
            _uiState.update { it.copy(recentSearches = recents) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        geocodingService.resetSession()
        _uiState.update {
            it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false)
        }
    }

    // ------------------------------------------------------------------
    // Map layers
    // ------------------------------------------------------------------

    fun setMapType(type: MapType) = _uiState.update { it.copy(mapType = type) }

    fun toggleTraffic() = _uiState.update { it.copy(trafficEnabled = !it.trafficEnabled) }

    fun toggleFloodOverlay() =
        _uiState.update { it.copy(floodOverlayEnabled = !it.floodOverlayEnabled) }

    /** Switches Car / Motor / Walk and recalculates the active route if any. */
    fun setTravelMode(mode: TravelMode) {
        if (_uiState.value.travelMode == mode) return
        _uiState.update { it.copy(travelMode = mode) }
        _uiState.value.destination?.let { navigateTo(it) }
    }

    // ------------------------------------------------------------------
    // Tap-to-pin: navigate here or mark this spot as flooded
    // ------------------------------------------------------------------

    fun onMapTapped(point: Point) {
        if (_uiState.value.isNavigating) return
        if (_uiState.value.isPinningSavedPlace) {
            _uiState.update { it.copy(pendingSavedPlacePoint = point) }
            return
        }
        _uiState.update { it.copy(pendingPin = point, searchResults = emptyList()) }
    }

    fun dismissPin() = _uiState.update { it.copy(pendingPin = null) }

    // ------------------------------------------------------------------
    // Saved Places (Home/Work/custom-named pinned destinations)
    // ------------------------------------------------------------------

    private fun observeSavedPlaces() {
        viewModelScope.launch {
            savedPlacesRepository.getSavedPlaces().collect { places ->
                _uiState.update { it.copy(savedPlaces = places) }
            }
        }
    }

    /** Enters "tap the map to drop a saved place" mode. */
    fun startPinningSavedPlace() =
        _uiState.update { it.copy(isPinningSavedPlace = true, pendingPin = null) }

    fun cancelPinningSavedPlace() =
        _uiState.update { it.copy(isPinningSavedPlace = false, pendingSavedPlacePoint = null) }

    /** Confirms the pin dropped while [MapUiState.isPinningSavedPlace] under [label]. */
    fun confirmSavedPlace(label: String) {
        val point = _uiState.value.pendingSavedPlacePoint ?: return
        val trimmed = label.trim().ifBlank { return }
        _uiState.update { it.copy(isPinningSavedPlace = false, pendingSavedPlacePoint = null) }
        viewModelScope.launch {
            val address = geocodingService.reverseGeocode(point).orEmpty()
            savedPlacesRepository.savePlace(trimmed, address, point)
                .onFailure { e ->
                    _uiState.update { it.copy(snackbarMessage = "Couldn't save place: ${e.message}") }
                }
        }
    }

    fun deleteSavedPlace(placeId: String) {
        viewModelScope.launch {
            savedPlacesRepository.deletePlace(placeId).onFailure { e ->
                _uiState.update { it.copy(snackbarMessage = "Couldn't delete place: ${e.message}") }
            }
        }
    }

    fun navigateToPin() {
        val pin = _uiState.value.pendingPin ?: return
        _uiState.update { it.copy(pendingPin = null, destinationName = "Pinned location") }
        navigateTo(pin)
    }

    // ------------------------------------------------------------------
    // Flood reports
    // ------------------------------------------------------------------

    private fun observeFloodReports() {
        viewModelScope.launch {
            floodRepository.getLiveFloodReports()
                .catch { e ->
                    _uiState.update {
                        it.copy(snackbarMessage = "Live flood sync failed: ${e.message}")
                    }
                }
                .collect { reports ->
                    // Pending reports stay invisible to the public map/routing
                    // until an admin approves them — only the Admin screen
                    // sees the raw, unfiltered stream. Stale reports (older
                    // than FloodReport.MAX_REPORT_AGE_MS) are dropped here too
                    // so they stop showing on the heatmap and stop factoring
                    // into routing/avoidance, not just in isActiveHazard checks
                    // downstream.
                    _uiState.update {
                        it.copy(
                            floodReports = reports.filter { r -> r.isApproved && r.isLiveHazard },
                            floodsLoaded = true
                        )
                    }
                }
        }
    }

    fun showReportSheet(show: Boolean) {
        _uiState.update { it.copy(showReportSheet = show) }
    }

    /** Captures current GPS position and submits a flood report. */
    @SuppressLint("MissingPermission")
    fun submitFloodReport(severity: FloodSeverity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingReport = true) }
            val location = locationProvider.getCurrentLocation()
            if (location == null) {
                _uiState.update {
                    it.copy(
                        isSubmittingReport = false,
                        snackbarMessage = "Couldn't get GPS fix — check location permission."
                    )
                }
                return@launch
            }
            val isAdmin = authRepository.isAdmin
            val areaLabel = geocodingService.reverseGeocode(
                Point.fromLngLat(location.longitude, location.latitude)
            ).orEmpty()
            val report = FloodReport(
                latitude = location.latitude,
                longitude = location.longitude,
                severity = severity,
                timestamp = System.currentTimeMillis(),
                status = if (isAdmin) FloodReport.STATUS_APPROVED else FloodReport.STATUS_PENDING,
                areaLabel = areaLabel,
                // This flow reports the reporter's own GPS fix directly —
                // flood location and reporter location are the same point.
                reporterLatitude = location.latitude,
                reporterLongitude = location.longitude,
                reporterAreaLabel = areaLabel
            )
            floodRepository.submitReport(report)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSubmittingReport = false,
                            showReportSheet = false,
                            snackbarMessage = if (isAdmin) {
                                "Flood report submitted. Stay safe!"
                            } else {
                                "Submitted for admin review. Stay safe!"
                            }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isSubmittingReport = false,
                            snackbarMessage = "Submit failed: ${e.message}"
                        )
                    }
                }
        }
    }

    fun upvoteReport(reportId: String) {
        viewModelScope.launch {
            floodRepository.upvoteReport(reportId).onFailure { e ->
                _uiState.update { it.copy(snackbarMessage = "Upvote failed: ${e.message}") }
            }
        }
    }


    // ------------------------------------------------------------------
    // Routing / navigation
    // ------------------------------------------------------------------

    fun navigateTo(destination: Point) {
        val origin = _uiState.value.userLocation
        val activeRouter = router
        if (origin == null || activeRouter == null) {
            _uiState.update { it.copy(snackbarMessage = "Waiting for GPS fix…") }
            return
        }
        _uiState.update {
            it.copy(destination = destination, isCalculatingRoute = true, pendingPin = null)
        }
        viewModelScope.launch {
            val routes = activeRouter.requestFloodAwareRoute(
                origin = origin,
                destination = destination,
                activeFloods = _uiState.value.floodReports,
                profile = _uiState.value.travelMode.profile
            )
            if (routes.isEmpty()) {
                _uiState.update {
                    it.copy(isCalculatingRoute = false, snackbarMessage = "No route found.")
                }
                return@launch
            }
            _uiState.update { it.copy(routes = routes, isCalculatingRoute = false) }
            updateViewportRoute(routes)
            if (routerCrossesFlood()) {
                // Pause here instead of silently starting to navigate through
                // a blocked flood — let the user decide.
                _uiState.update { it.copy(showFloodCrossingModal = true) }
            } else {
                maybeShowSlowFloodReminder()
                beginTripSession(routes)
            }
        }
    }

    private fun routerCrossesFlood(): Boolean =
        (router?.routingState?.value as? FloodAwareRouter.RoutingState.RouteReady)?.crossesFlood == true

    /** Passable/slow flood on the route — a heads-up reminder, never a blocking modal. */
    private fun routerCrossesSlowFlood(): Boolean =
        (router?.routingState?.value as? FloodAwareRouter.RoutingState.RouteReady)?.crossesSlowFlood == true

    private fun maybeShowSlowFloodReminder() {
        if (routerCrossesSlowFlood()) {
            _uiState.update {
                it.copy(snackbarMessage = "Heads up: part of this route passes a slow, flooded road.")
            }
        }
    }

    private fun beginTripSession(routes: List<NavigationRoute>) {
        mapboxNavigation?.startTripSession()
        router?.watchFloodsAndReroute()
        navigationCamera?.requestNavigationCameraToFollowing()
        val distance = routes.firstOrNull()?.directionsRoute?.distance() ?: 0.0
        val duration = routes.firstOrNull()?.directionsRoute?.duration() ?: 0.0
        _uiState.update {
            it.copy(
                isNavigating = true,
                isCalculatingRoute = false,
                routeDistanceMeters = distance,
                remainingDistanceMeters = distance,
                remainingDurationSeconds = duration,
                showFloodCrossingModal = false,
                noAlternativeFound = false
            )
        }
    }

    /** User chose "Continue Anyway" on the flood-crossing warning. */
    fun continueThroughFlood() {
        beginTripSession(_uiState.value.routes)
    }

    /**
     * User chose "Find Alternative": one on-demand denser search, only now,
     * only because it was asked for — see [FloodAwareRouter] for why this
     * isn't the default behavior.
     */
    fun findAlternativeRoute() {
        val origin = _uiState.value.userLocation
        val destination = _uiState.value.destination
        val activeRouter = router
        if (origin == null || destination == null || activeRouter == null) return
        _uiState.update { it.copy(isFindingAlternative = true, noAlternativeFound = false) }
        viewModelScope.launch {
            val routes = activeRouter.requestDenserAlternative(
                origin = origin,
                destination = destination,
                activeFloods = _uiState.value.floodReports,
                profile = _uiState.value.travelMode.profile
            )
            val stillCrosses = routerCrossesFlood()
            if (routes.isNotEmpty() && !stillCrosses) {
                _uiState.update { it.copy(isFindingAlternative = false) }
                updateViewportRoute(routes)
                maybeShowSlowFloodReminder()
                beginTripSession(routes)
            } else {
                _uiState.update {
                    it.copy(
                        isFindingAlternative = false,
                        noAlternativeFound = true,
                        routes = routes.ifEmpty { it.routes }
                    )
                }
                updateViewportRoute(routes.ifEmpty { _uiState.value.routes })
            }
        }
    }

    /** User dismissed the warning without choosing — cancel this destination. */
    fun dismissFloodCrossingModal() {
        updateViewportRoute(emptyList())
        _uiState.update {
            it.copy(
                showFloodCrossingModal = false,
                noAlternativeFound = false,
                destination = null,
                destinationName = null,
                routes = emptyList()
            )
        }
    }

    fun stopNavigation() {
        router?.stop()
        mapboxNavigation?.stopTripSession()
        // Stopping the trip session only prevents NEW voice instructions from
        // being queued — whatever announcement is already mid-playback keeps
        // talking unless the player is told to stop explicitly.
        voiceInstructionsPlayer.clear()
        reroutingBannerJob?.cancel()
        navigationCamera?.requestNavigationCameraToIdle()
        updateViewportRoute(emptyList())
        _uiState.update {
            it.copy(
                isNavigating = false,
                routes = emptyList(),
                destination = null,
                destinationName = null,
                currentInstruction = null,
                maneuverModifier = null,
                isRerouting = false,
                remainingDistanceMeters = 0.0,
                remainingDurationSeconds = 0.0,
                routeDistanceMeters = 0.0
            )
        }
    }

    private fun observeRouterState() {
        routerStateJob?.cancel()
        routerStateJob = viewModelScope.launch {
            launch {
                router?.routingState?.collect { state ->
                    when (state) {
                        is FloodAwareRouter.RoutingState.RouteReady -> {
                            updateViewportRoute(state.routes)
                            _uiState.update {
                                // Only auto-warn via snackbar for a MID-TRIP reroute
                                // (isNavigating already true) — popping a blocking
                                // modal while the user is actively driving would be
                                // unsafe. The pre-navigation case is handled by the
                                // confirmation modal in navigateTo() instead, so it
                                // must NOT also fire this snackbar.
                                val diagnosticNote = if (state.crossesFlood && it.isNavigating) {
                                    "⚠️ Still crosses a blocked flood area — proceed " +
                                        "with caution. [${state.diagnostics}]"
                                } else null
                                it.copy(
                                    routes = state.routes,
                                    snackbarMessage = diagnosticNote ?: it.snackbarMessage
                                )
                            }
                        }
                        is FloodAwareRouter.RoutingState.Failed ->
                            _uiState.update {
                                it.copy(snackbarMessage = "Routing failed: ${state.reason}")
                            }
                        else -> Unit
                    }
                }
            }
            launch {
                router?.rerouteTrigger?.collect { trigger ->
                    if (trigger != null) {
                        showReroutingBanner()
                        router?.consumeRerouteTrigger()
                    }
                }
            }
        }
    }

    /** Shows the "Approaching Flooded Area : Re-routing" card for a few seconds. */
    private fun showReroutingBanner() {
        reroutingBannerJob?.cancel()
        _uiState.update { it.copy(isRerouting = true) }
        reroutingBannerJob = viewModelScope.launch {
            delay(6000)
            _uiState.update { it.copy(isRerouting = false) }
        }
    }

    /**
     * Gets a fix as fast as possible, then keeps a continuous GPS stream
     * running so a slow/failed first fix (cold GPS, indoors, etc.) doesn't
     * leave the map stuck on "Waiting for GPS fix" forever — it just fills
     * in as soon as a real fix arrives.
     */
    private fun fetchInitialLocation() {
        viewModelScope.launch {
            locationProvider.getCurrentLocation()?.let { loc ->
                _uiState.update {
                    it.copy(userLocation = Point.fromLngLat(loc.longitude, loc.latitude))
                }
                maybeShowNearbyMessage()
            }
        }
        viewModelScope.launch {
            locationProvider.locationUpdates().collect { loc ->
                _uiState.update {
                    it.copy(userLocation = Point.fromLngLat(loc.longitude, loc.latitude))
                }
                maybeShowNearbyMessage()
            }
        }
    }

    fun onPermissionGranted() = fetchInitialLocation()

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        detachNavigation()
        detachMap()
        speechApi.cancel()
        voiceInstructionsPlayer.shutdown()
        super.onCleared()
    }
}
