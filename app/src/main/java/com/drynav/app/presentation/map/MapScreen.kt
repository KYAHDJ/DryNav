package com.drynav.app.presentation.map

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.UTurnRight
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drynav.app.domain.model.Mood
import com.drynav.app.domain.model.SavedPlace
import com.drynav.app.presentation.components.LoadingOverlay
import com.drynav.app.presentation.components.PillButton
import com.drynav.app.presentation.navigation.Routes
import com.drynav.app.presentation.tutorial.TutorialCelebrationOverlay
import com.drynav.app.presentation.tutorial.TutorialManager
import com.drynav.app.presentation.tutorial.TutorialOverlay
import com.drynav.app.presentation.tutorial.TutorialViewModel
import com.drynav.app.presentation.tutorial.tutorialTarget
import com.drynav.app.presentation.theme.AccentGreen
import com.drynav.app.presentation.theme.Amber
import com.drynav.app.presentation.theme.AmberTint
import com.drynav.app.presentation.theme.Mist
import com.drynav.app.presentation.theme.PublicSans
import com.drynav.app.presentation.theme.NavBlue
import com.drynav.app.presentation.theme.TealPrimary
import com.drynav.app.presentation.theme.TextDark
import com.drynav.app.presentation.theme.TextGray
import com.drynav.app.presentation.theme.WarnYellow
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.IconRotationAlignment
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMapLongClickListener
import com.mapbox.maps.plugin.locationcomponent.DefaultLocationProvider
import com.mapbox.maps.plugin.locationcomponent.LocationComponentConstants
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * "Maps2–6" — the full-screen navigation map: destination search, layer
 * chooser, flood heatmap, and the turn-by-turn overlay with flood re-routing.
 */
@Composable
fun MapScreen(
    onBack: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLayersSheet by rememberSaveable { mutableStateOf(false) }
    val tutorialManager = hiltViewModel<TutorialViewModel>().manager

    // ---- Mapbox singletons scoped to this screen ----
    val mapboxNavigation: MapboxNavigation = remember {
        if (MapboxNavigationProvider.isCreated()) {
            MapboxNavigationProvider.retrieve()
        } else {
            val token = context.getString(com.drynav.app.R.string.mapbox_access_token)
            MapboxNavigationProvider.create(
                NavigationOptions.Builder(context.applicationContext)
                    .accessToken(token)
                    .build()
            )
        }
    }
    // Pin the route line below the location puck's own layer so the puck
    // always renders on top, no matter the order style-reload listeners
    // (the puck's internal one vs. this screen's route-render callback)
    // happen to fire in.
    val routeLineOptions = remember {
        MapboxRouteLineOptions.Builder(context)
            .withRouteLineBelowLayerId(LocationComponentConstants.LOCATION_INDICATOR_LAYER)
            .build()
    }
    val routeLineApi = remember { MapboxRouteLineApi(routeLineOptions) }
    val routeLineView = remember { MapboxRouteLineView(routeLineOptions) }

    val mapView = remember { MapView(context) }
    // Only the destination pin is a real annotation now — every flood is
    // shown purely as heat-vision, no marker icons cluttering the map.
    val annotationManager: PointAnnotationManager = remember {
        mapView.annotations.createPointAnnotationManager()
    }
    val pinIcon = remember { createPinBitmap(AndroidColor.rgb(23, 121, 138)) }
    val savedPlacePinIcon = remember { createPinBitmap(AndroidColor.rgb(230, 156, 0)) }
    val defaultLocationProvider = remember { DefaultLocationProvider(context) }
    // One other-drivers annotation manager, separate from the destination-pin
    // one — other users' mood characters come and go independently of pins.
    // VIEWPORT alignment + plain PointAnnotations (not Mapbox's location
    // puck system) means these are always screen-billboarded: they never
    // rotate as the map bearing changes and never tilt into the ground
    // plane as the map pitches, so the character always faces the camera.
    val othersAnnotationManager: PointAnnotationManager = remember {
        mapView.annotations.createPointAnnotationManager().apply {
            iconRotationAlignment = IconRotationAlignment.VIEWPORT
        }
    }
    // The signed-in user's own mood character — shown ONLY while actively
    // navigating (browsing still uses Mapbox's small default arrow puck);
    // same billboarded-annotation approach as othersAnnotationManager.
    val ownMoodAnnotationManager: PointAnnotationManager = remember {
        mapView.annotations.createPointAnnotationManager().apply {
            iconRotationAlignment = IconRotationAlignment.VIEWPORT
        }
    }
    val moodBitmaps = remember {
        Mood.entries.associateWith { mood ->
            ContextCompat.getDrawable(context, mood.iconRes)!!.toBitmap()
        }
    }
    // Kept modest — matches the destination pin's on-screen footprint
    // instead of the mood drawables' much larger 96dp intrinsic size.
    val moodIconSize = 0.35

    fun renderRoutesOnStyle() {
        val map = mapView.getMapboxMap()
        routeLineApi.setNavigationRoutes(uiState.routes) { value ->
            map.getStyle()?.let { style -> routeLineView.renderRouteDrawData(style, value) }
        }
    }

    // Wire the ViewModel to MapboxNavigation for the screen's lifetime.
    DisposableEffect(Unit) {
        viewModel.attachNavigation(mapboxNavigation)
        viewModel.attachMap(mapView.getMapboxMap(), mapView.camera)
        mapView.applyDryNavMapDefaults()
        mapView.applyDryNavLocationPuck()
        // Single tap drops a pin: navigate there or mark the spot as flooded.
        mapView.getMapboxMap().addOnMapClickListener { point ->
            viewModel.onMapTapped(point)
            true
        }
        mapView.getMapboxMap().addOnMapLongClickListener { point ->
            viewModel.navigateTo(point)
            true
        }
        onDispose {
            viewModel.detachNavigation()
            viewModel.detachMap()
            routeLineApi.cancel()
            routeLineView.cancel()
            mapView.onDestroy()
        }
    }

    // Load / reload the style whenever the chosen base map or traffic changes.
    LaunchedEffect(uiState.mapType, uiState.trafficEnabled) {
        val styleUri = uiState.mapType.styleUri(uiState.trafficEnabled)
        mapView.getMapboxMap().loadStyleUri(styleUri) { style ->
            updateFloodHeatmap(style, uiState.floodReports, uiState.floodOverlayEnabled)
            renderRoutesOnStyle()
        }
    }

    // Own mood character replaces the default puck ONLY while just browsing
    // the map (not moving) — once a trip actually starts, the puck switches
    // back to the small default arrow so the driver has clear directional
    // info while in motion. Toggling the Mapbox puck's `enabled` off avoids
    // showing two pucks at once.
    LaunchedEffect(uiState.isNavigating, uiState.mood, uiState.userLocation) {
        val showMoodPuck = !uiState.isNavigating && uiState.mood != null
        mapView.setDefaultPuckEnabled(!showMoodPuck)
        ownMoodAnnotationManager.deleteAll()
        val bitmap = uiState.mood?.let { moodBitmaps[it] }
        val point = uiState.userLocation
        if (showMoodPuck && bitmap != null && point != null) {
            ownMoodAnnotationManager.create(
                PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage(bitmap)
                    .withIconAnchor(IconAnchor.BOTTOM)
                    .withIconSize(moodIconSize)
            )
        }
    }

    // Other online users' live characters — a separate annotation per
    // presence doc, refreshed on every heartbeat tick from MapViewModel. A
    // small name label rides along; Mapbox's own label-collision handling
    // hides it automatically when zoomed out or crowded, so it never
    // clutters the view while driving.
    LaunchedEffect(uiState.otherPresences) {
        othersAnnotationManager.deleteAll()
        uiState.otherPresences.forEach { presence ->
            val bitmap = moodBitmaps[Mood.fromName(presence.mood)] ?: return@forEach
            othersAnnotationManager.create(
                PointAnnotationOptions()
                    .withPoint(Point.fromLngLat(presence.longitude, presence.latitude))
                    .withIconImage(bitmap)
                    .withIconAnchor(IconAnchor.BOTTOM)
                    .withIconSize(moodIconSize)
                    .withTextField(presence.displayName.ifBlank { "Driver" })
                    .withTextSize(11.0)
                    .withTextColor(AndroidColor.WHITE)
                    .withTextHaloColor(AndroidColor.rgb(23, 121, 138))
                    .withTextHaloWidth(1.5)
                    .withTextOffset(listOf(0.0, 1.4))
            )
        }
    }

    // Mapbox's own location puck (see applyDryNavLocationPuck) needs its own
    // LocationProvider swapped in: browsing uses the SDK's plain GPS feed,
    // but once a trip starts the puck switches to the same enhanced/
    // map-matched location already driving rerouting and ETA, so it never
    // shows a slower, worse feed than the one the app is actually
    // navigating with.
    LaunchedEffect(uiState.isNavigating) {
        mapView.location.setLocationProvider(
            if (uiState.isNavigating) viewModel.navigationLocationProvider else defaultLocationProvider
        )
    }

    // Center the camera once on the first GPS fix, and once again when
    // navigation starts — NOT on every subsequent location tick. Re-snapping
    // the camera on every GPS update was fighting the user's own pinch-zoom,
    // making it impossible to zoom in to place a pin or inspect the route.
    var hasCenteredOnFix by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.userLocation) {
        if (!hasCenteredOnFix) {
            uiState.userLocation?.let { loc ->
                hasCenteredOnFix = true
                mapView.getMapboxMap().setCamera(
                    CameraOptions.Builder().center(loc).zoom(14.0).build()
                )
            }
        }
    }
    // Once a trip starts, the NavigationCamera (fed continuously via
    // MapViewModel.attachMap) takes over: follow + rotate-to-heading +
    // speed-adaptive zoom, requested from MapViewModel.beginTripSession().
    fun recenterCamera() {
        if (uiState.isNavigating) {
            viewModel.resumeFollowingCamera()
            return
        }
        uiState.userLocation?.let { loc ->
            mapView.getMapboxMap().setCamera(
                CameraOptions.Builder().center(loc).zoom(14.0).build()
            )
        }
    }

    // Destination pin + flood heat-vision react to state changes.
    LaunchedEffect(
        uiState.floodReports, uiState.floodOverlayEnabled,
        uiState.pendingPin, uiState.pendingSavedPlacePoint
    ) {
        annotationManager.deleteAll()
        uiState.pendingPin?.let { pin ->
            annotationManager.create(
                PointAnnotationOptions()
                    .withPoint(pin)
                    .withIconImage(pinIcon)
                    .withIconAnchor(IconAnchor.BOTTOM)
                    .withIconSize(1.0)
            )
        }
        uiState.pendingSavedPlacePoint?.let { pin ->
            annotationManager.create(
                PointAnnotationOptions()
                    .withPoint(pin)
                    .withIconImage(savedPlacePinIcon)
                    .withIconAnchor(IconAnchor.BOTTOM)
                    .withIconSize(1.0)
            )
        }
        mapView.getMapboxMap().getStyle()?.let { style ->
            updateFloodHeatmap(style, uiState.floodReports, uiState.floodOverlayEnabled)
        }
    }

    // Draw / clear the route line when routes change.
    LaunchedEffect(uiState.routes) {
        renderRoutesOnStyle()
    }

    // One-shot snackbar messages.
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            if (uiState.isNavigating) {
                NavigationOverlay(
                    uiState = uiState,
                    onBack = {
                        viewModel.stopNavigation()
                        onBack()
                    },
                    onStop = viewModel::stopNavigation,
                    onTravelMode = viewModel::setTravelMode,
                    onRecenter = ::recenterCamera,
                    onToggleMute = viewModel::toggleVoiceMute
                )
            } else {
                BrowseOverlay(
                    uiState = uiState,
                    onBack = onBack,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onClearQuery = viewModel::clearSearch,
                    onSelectPlace = viewModel::selectPlace,
                    onSelectSavedPlace = viewModel::selectSavedPlace,
                    onDeleteSavedPlace = viewModel::deleteSavedPlace,
                    onStartPinningSavedPlace = viewModel::startPinningSavedPlace,
                    onShowLayers = { showLayersSheet = true },
                    onTravelMode = viewModel::setTravelMode,
                    onNavigateToPin = viewModel::navigateToPin,
                    onDismissPin = viewModel::dismissPin,
                    onRecenter = ::recenterCamera,
                    tutorialManager = tutorialManager
                )
            }

            // Waze-style brief "N people nearby" toast — fades in, sits for
            // a few seconds, fades out. MapViewModel already clears the
            // count back to null on its own timer; the animation just makes
            // that transition gentle instead of an abrupt pop.
            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.nearbyPeopleCount != null,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 190.dp)
            ) {
                Surface(
                    color = Color(0xE6455A64),
                    shape = RoundedCornerShape(999.dp),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            Icons.Default.PeopleAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        val count = uiState.nearbyPeopleCount ?: 0
                        Text(
                            if (count == 1) "1 person nearby" else "$count people nearby",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }
            }

            // Waiting for the user to tap the map while saving a place.
            if (uiState.isPinningSavedPlace && uiState.pendingSavedPlacePoint == null) {
                Surface(
                    color = Color(0xE6455A64),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 70.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            "Tap the map to pin this place",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Cancel",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFBFE3EA),
                            modifier = Modifier.clickable { viewModel.cancelPinningSavedPlace() }
                        )
                    }
                }
            }

            LoadingOverlay(visible = !uiState.floodsLoaded)
        }
    }

        if (tutorialManager.isActive && tutorialManager.currentStep?.route == Routes.MAP) {
            TutorialOverlay(tutorialManager)
        }
        if (tutorialManager.showCelebration) {
            TutorialCelebrationOverlay(onDismiss = tutorialManager::dismissCelebration)
        }
    }

    if (showLayersSheet) {
        MapTypeSheet(
            mapType = uiState.mapType,
            trafficEnabled = uiState.trafficEnabled,
            floodEnabled = uiState.floodOverlayEnabled,
            onMapType = viewModel::setMapType,
            onToggleTraffic = viewModel::toggleTraffic,
            onToggleFlood = viewModel::toggleFloodOverlay,
            onDismiss = { showLayersSheet = false }
        )
    }

    if (uiState.showFloodCrossingModal) {
        FloodCrossingDialog(
            isSearching = uiState.isFindingAlternative,
            noAlternativeFound = uiState.noAlternativeFound,
            onFindAlternative = viewModel::findAlternativeRoute,
            onContinueAnyway = viewModel::continueThroughFlood,
            onDismiss = viewModel::dismissFloodCrossingModal
        )
    }

    uiState.pendingSavedPlacePoint?.let {
        NameSavedPlaceDialog(
            onConfirm = viewModel::confirmSavedPlace,
            onDismiss = viewModel::cancelPinningSavedPlace
        )
    }
}

/** "Name this place" — shown once a pin has been dropped in saved-place mode. */
@Composable
private fun NameSavedPlaceDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp)) {
                Text("Name this place", style = MaterialTheme.typography.headlineSmall, color = TextDark)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Home", "Work").forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (name == preset) TealPrimary else Mist,
                            modifier = Modifier.clickable { name = preset }
                        ) {
                            Text(
                                preset,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (name == preset) Color.White else TextDark,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("e.g. Gym, Mom's house") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(22.dp))
                PillButton(
                    text = "Save place",
                    onClick = { onConfirm(name) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                )
            }
        }
    }
}

/**
 * Pauses before navigation actually starts when the computed route crosses a
 * blocked flood, instead of silently proceeding with just a snackbar warning.
 */
@Composable
private fun FloodCrossingDialog(
    isSearching: Boolean,
    noAlternativeFound: Boolean,
    onFindAlternative: () -> Unit,
    onContinueAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!isSearching) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = !isSearching)
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .background(AmberTint, RoundedCornerShape(18.dp))
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                            color = Amber
                        )
                    } else {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = Amber,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    when {
                        isSearching -> "Looking for another way"
                        noAlternativeFound -> "No alternative found"
                        else -> "This route crosses a flooded road"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextDark,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        isSearching -> "Searching for a route that avoids the flooded area…"
                        noAlternativeFound -> "Every route checked still crosses a blocked flood " +
                            "area. You can continue anyway, or cancel and pick a different destination."
                        else -> "You can continue anyway, or we can look for a " +
                            "different way there."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray,
                    textAlign = TextAlign.Center
                )
                if (!isSearching) {
                    Spacer(Modifier.height(24.dp))
                    if (noAlternativeFound) {
                        PillButton(
                            text = "Continue anyway",
                            onClick = onContinueAnyway,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Cancel",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextGray,
                            modifier = Modifier.clickable(onClick = onDismiss)
                        )
                    } else {
                        PillButton(
                            text = "Find Another Way",
                            onClick = onFindAlternative,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Continue anyway",
                            style = MaterialTheme.typography.labelLarge,
                            color = TealPrimary,
                            modifier = Modifier.clickable(onClick = onContinueAnyway)
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Browse mode (Maps2-4): search bar, results, layers button, bottom card
// ----------------------------------------------------------------------

@Composable
private fun BrowseOverlay(
    uiState: MapUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSelectPlace: (com.drynav.app.data.search.PlaceResult) -> Unit,
    onSelectSavedPlace: (SavedPlace) -> Unit,
    onDeleteSavedPlace: (String) -> Unit,
    onStartPinningSavedPlace: () -> Unit,
    onShowLayers: () -> Unit,
    onTravelMode: (TravelMode) -> Unit,
    onNavigateToPin: () -> Unit,
    onDismissPin: () -> Unit,
    onRecenter: () -> Unit,
    tutorialManager: TutorialManager
) {
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(top = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                // Search Destination bar.
                Surface(
                    color = Color(0xE6455A64),
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .tutorialTarget(tutorialManager, "search_bar")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = uiState.searchQuery,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontFamily = PublicSans,
                                fontSize = 15.sp
                            ),
                            decorationBox = { innerTextField ->
                                if (uiState.searchQuery.isEmpty()) {
                                    Text(
                                        "Search Destination",
                                        color = Color(0xFFB0BEC5),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isSearchFocused = it.isFocused }
                        )
                        if (uiState.searchQuery.isNotEmpty() || isSearchFocused) {
                            IconButton(
                                onClick = {
                                    onClearQuery()
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Live search results.
            if (uiState.isSearching || uiState.searchResults.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 44.dp, top = 6.dp)
                ) {
                    Column {
                        if (uiState.isSearching) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Searching…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        uiState.searchResults.forEach { place ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPlace(place) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(place.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    place.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            } else if (
                isSearchFocused &&
                uiState.searchQuery.isEmpty() &&
                uiState.pendingPin == null
            ) {
                // Save-a-place action, saved places, then recent searches —
                // shown only while the search bar is focused, so it doesn't
                // linger on screen the rest of the time; scrollable since
                // neither list is capped.
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 44.dp, top = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onStartPinningSavedPlace)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.AddLocationAlt,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Save a place",
                                style = MaterialTheme.typography.titleSmall,
                                color = TealPrimary
                            )
                        }
                        if (uiState.savedPlaces.isNotEmpty()) {
                            Text(
                                "Saved",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                            )
                            uiState.savedPlaces.forEach { place ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectSavedPlace(place) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        if (place.label.equals("Home", ignoreCase = true)) {
                                            Icons.Default.Home
                                        } else if (place.label.equals("Work", ignoreCase = true)) {
                                            Icons.Default.Work
                                        } else {
                                            Icons.Default.PushPin
                                        },
                                        contentDescription = null,
                                        tint = TealPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(place.label, style = MaterialTheme.typography.titleSmall)
                                        if (place.address.isNotBlank()) {
                                            Text(
                                                place.address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteSavedPlace(place.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove ${place.label}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (uiState.recentSearches.isNotEmpty()) {
                            Text(
                                "Recent",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 10.dp)
                            )
                            uiState.recentSearches.forEach { place ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectPlace(place) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(place.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            place.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Layers button.
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 68.dp, end = 14.dp)
        ) {
            IconButton(onClick = onShowLayers) {
                Icon(
                    Icons.Outlined.Layers,
                    contentDescription = "Map layers",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Recenter button — snap back to your GPS position without the
        // camera fighting your own pinch-zoom on every location tick.
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 122.dp, end = 14.dp)
                .tutorialTarget(tutorialManager, "recenter_button")
        ) {
            IconButton(onClick = onRecenter) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Recenter on my location",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Bottom: pin action card when a pin is dropped, else the destination card.
        if (uiState.pendingPin != null) {
            PinActionCard(
                onNavigateToPin = onNavigateToPin,
                onDismiss = onDismissPin,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        } else {
            Surface(
                color = Color(0xE6455A64),
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.RadioButtonChecked,
                            contentDescription = null,
                            tint = Color(0xFF4FA3E8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            uiState.destinationName ?: "Your location",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        if (uiState.isCalculatingRoute) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            TravelModeChips(
                                selected = uiState.travelMode,
                                onSelect = onTravelMode,
                                modifier = Modifier.tutorialTarget(tutorialManager, "travel_mode_chips")
                            )
                        }
                    }
                    HorizontalDivider(
                        color = Color(0x33FFFFFF),
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.tutorialTarget(tutorialManager, "navigate_prompt")
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFFB0BEC5),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Where to? Type above or tap the map to pin",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB0BEC5)
                        )
                    }
                }
            }
        }
    }
}

/** Car / Motor / Walk selector shown on the destination card. */
@Composable
private fun TravelModeChips(
    selected: TravelMode,
    onSelect: (TravelMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        TravelMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Surface(
                onClick = { onSelect(mode) },
                color = if (isSelected) Color(0xFF4FA3E8) else Color(0x33FFFFFF),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = travelModeIcon(mode),
                    contentDescription = mode.label,
                    tint = Color.White,
                    modifier = Modifier
                        .padding(7.dp)
                        .size(18.dp)
                )
            }
        }
    }
}

/**
 * Card offering "Navigate here" for the dropped pin. Marking a road as
 * flooded is only available from the dedicated Report screen now — not
 * duplicated here on the navigation map.
 */
@Composable
private fun PinActionCard(
    onNavigateToPin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xF2455A64),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.RadioButtonChecked,
                    contentDescription = null,
                    tint = Color(0xFF4FA3E8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Pinned location",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss pin", tint = Color.White)
                }
            }
            Spacer(Modifier.height(6.dp))
            Surface(
                onClick = onNavigateToPin,
                color = Color(0xFF4FA3E8),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Navigate here",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// Navigation mode (Maps5-6): instruction banner, re-routing card, ETA bar
// ----------------------------------------------------------------------

@Composable
private fun NavigationOverlay(
    uiState: MapUiState,
    onBack: () -> Unit,
    onStop: () -> Unit,
    onTravelMode: (TravelMode) -> Unit,
    onRecenter: () -> Unit,
    onToggleMute: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(top = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                // Blue maneuver banner: "Head North" style.
                Surface(
                    color = NavBlue,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Icon(
                            maneuverIcon(uiState.maneuverModifier),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            uiState.currentInstruction ?: "Head North",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 2
                        )
                    }
                }
            }

            // Flood re-routing warning card.
            if (uiState.isRerouting) {
                Surface(
                    color = Color(0xF2FFFFFF),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 44.dp, top = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = WarnYellow,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = Color(0xFF1F2937))) {
                                    append("Approaching Flooded\nArea : ")
                                }
                                withStyle(SpanStyle(color = AccentGreen)) {
                                    append("Re-routing")
                                }
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        // Mute/unmute voice guidance — pulled well clear of the recenter
        // button below so the two circular controls never read as one
        // cluster while driving.
        Surface(
            color = Color(0xE6455A64),
            shape = CircleShape,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp, bottom = 300.dp)
        ) {
            IconButton(onClick = onToggleMute) {
                Icon(
                    if (uiState.isVoiceMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (uiState.isVoiceMuted) "Unmute voice guidance" else "Mute voice guidance",
                    tint = Color.White
                )
            }
        }

        // Resume following — shown only once the driver has manually panned
        // the map away from the live follow camera.
        if (!uiState.isFollowingCamera) {
            Surface(
                color = Color(0xFF4FA3E8),
                shape = CircleShape,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp, bottom = 140.dp)
            ) {
                IconButton(onClick = onRecenter) {
                    Icon(
                        Icons.Default.Navigation,
                        contentDescription = "Resume following",
                        tint = Color.White
                    )
                }
            }
        } else {
            // Camera is already following — this just re-centers/re-levels it.
            Surface(
                color = Color(0xE6455A64),
                shape = CircleShape,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp, bottom = 140.dp)
            ) {
                IconButton(onClick = onRecenter) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Recenter on my location",
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom ETA bar.
        Surface(
            color = NavBlue,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 44.dp, height = 4.dp)
                        .background(Color(0x66FFFFFF), CircleShape)
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val mins = (uiState.remainingDurationSeconds / 60.0).roundToInt()
                            .coerceAtLeast(1)
                        Text(
                            "$mins min",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                travelModeIcon(uiState.travelMode),
                                contentDescription = uiState.travelMode.label,
                                tint = Color(0xCCFFFFFF),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "· ${formatDistance(uiState.remainingDistanceMeters)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xCCFFFFFF)
                            )
                        }
                        Text(
                            "ETA ${formatEta(uiState.remainingDurationSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0x99FFFFFF)
                        )
                    }
                    // Switch transport mid-trip — the route and ETA recalculate.
                    if (uiState.isCalculatingRoute) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        TravelModeChips(
                            selected = uiState.travelMode,
                            onSelect = onTravelMode
                        )
                    }
                    IconButton(onClick = onStop) {
                        Surface(color = Color(0x33FFFFFF), shape = CircleShape) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Stop navigation",
                                tint = Color.White,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                RouteProgressBar(
                    progress = routeProgressFraction(uiState),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )
            }
        }
    }
}

/** White arrow sliding along the route track, like the mockup's trip bar. */
@Composable
private fun RouteProgressBar(progress: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.height(20.dp)) {
        val trackWidth = maxWidth - 20.dp
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0x66FFFFFF), CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(trackWidth * progress)
                .height(4.dp)
                .background(Color.White, CircleShape)
        )
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = trackWidth * progress)
                .size(20.dp)
        )
    }
}

private fun routeProgressFraction(uiState: MapUiState): Float {
    val total = uiState.routeDistanceMeters
    if (total <= 0.0) return 0f
    return (1.0 - uiState.remainingDistanceMeters / total).toFloat().coerceIn(0f, 1f)
}

private fun travelModeIcon(mode: TravelMode): ImageVector = when (mode) {
    TravelMode.CAR -> Icons.Default.DirectionsCar
    TravelMode.MOTOR -> Icons.Default.TwoWheeler
    TravelMode.WALK -> Icons.Default.DirectionsWalk
}

private fun maneuverIcon(modifier: String?): ImageVector = when (modifier) {
    "left" -> Icons.Default.TurnLeft
    "right" -> Icons.Default.TurnRight
    "slight left" -> Icons.Default.TurnSlightLeft
    "slight right" -> Icons.Default.TurnSlightRight
    "sharp left" -> Icons.Default.TurnLeft
    "sharp right" -> Icons.Default.TurnRight
    "uturn" -> Icons.Default.UTurnLeft
    else -> Icons.Default.Straight
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) "%.1f km".format(meters / 1000.0) else "${meters.roundToInt()} m"

private fun formatEta(remainingSeconds: Double): String {
    val eta = Date(System.currentTimeMillis() + (remainingSeconds * 1000).toLong())
    return SimpleDateFormat("h:mma", Locale.getDefault()).format(eta).lowercase()
}
