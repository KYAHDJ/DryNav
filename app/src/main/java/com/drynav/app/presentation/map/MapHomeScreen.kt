package com.drynav.app.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drynav.app.domain.model.Mood
import com.drynav.app.presentation.components.DryNavBottomBar
import com.drynav.app.presentation.components.LoadingOverlay
import com.drynav.app.presentation.components.PillButton
import com.drynav.app.presentation.navigation.Routes
import com.drynav.app.presentation.theme.TealPrimary
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.IconRotationAlignment
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager

/**
 * "Maps1" — the map preview with the curved "Stay on the Dry Path" panel and
 * the Start Navigation button that opens the full navigation map.
 */
@Composable
fun MapHomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapView = remember { MapView(context) }
    var showLayersSheet by rememberSaveable { mutableStateOf(false) }

    // Own mood character — shown here too since this screen is just a
    // stationary preview (nothing moving until "Start Navigation" is
    // tapped). Same billboarded-annotation approach as the other two map
    // screens, so it never rotates/tilts oddly with the map.
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

    DisposableEffect(Unit) {
        mapView.applyDryNavMapDefaults()
        mapView.applyDryNavLocationPuck()
        onDispose {
            mapView.onDestroy()
        }
    }

    LaunchedEffect(uiState.mood, uiState.userLocation) {
        mapView.setDefaultPuckEnabled(uiState.mood == null)
        ownMoodAnnotationManager.deleteAll()
        val bitmap = uiState.mood?.let { moodBitmaps[it] }
        val point = uiState.userLocation
        if (bitmap != null && point != null) {
            ownMoodAnnotationManager.create(
                PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage(bitmap)
                    .withIconAnchor(IconAnchor.BOTTOM)
                    .withIconSize(0.35)
            )
        }
    }

    // Base style + traffic react to the layers sheet, same as the full map.
    LaunchedEffect(uiState.mapType, uiState.trafficEnabled) {
        val styleUri = uiState.mapType.styleUri(uiState.trafficEnabled)
        mapView.getMapboxMap().loadStyleUri(styleUri) { style ->
            updateFloodHeatmap(style, uiState.floodReports, uiState.floodOverlayEnabled)
        }
    }

    // Live flood heat-vision — visible while browsing, no marker icons.
    LaunchedEffect(uiState.floodReports, uiState.floodOverlayEnabled) {
        mapView.getMapboxMap().getStyle()?.let { style ->
            updateFloodHeatmap(style, uiState.floodReports, uiState.floodOverlayEnabled)
        }
    }

    LaunchedEffect(uiState.userLocation != null) {
        uiState.userLocation?.let { loc ->
            mapView.getMapboxMap().setCamera(
                CameraOptions.Builder().center(loc).zoom(15.0).build()
            )
        }
    }

    Scaffold(
        bottomBar = {
            DryNavBottomBar(currentRoute = Routes.MAP_HOME, onNavigate = onNavigate)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Map fills the whole screen — the panel below floats on top of
            // it instead of squeezing it into its own rectangle.
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            // "Your location" pill.
            Surface(
                color = Color(0xE6263238),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .fillMaxWidth(0.86f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(
                        Icons.Default.RadioButtonChecked,
                        contentDescription = null,
                        tint = Color(0xFF4FA3E8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    Text(
                        "Your location",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }

            // Layers button — usable while just browsing the map.
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 68.dp, end = 14.dp)
            ) {
                IconButton(onClick = { showLayersSheet = true }) {
                    Icon(
                        Icons.Outlined.Layers,
                        contentDescription = "Map layers",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            LoadingOverlay(visible = !uiState.floodsLoaded)

            // Floating panel — soft rounded top edge, sits on top of the map
            // (no opaque backing behind it), same treatment as the Report
            // tab's bottom sheet.
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(top = 14.dp, bottom = 22.dp)
                ) {
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .background(Color(0xFFE5E7EB), CircleShape)
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Stay on the Dry Path",
                        style = MaterialTheme.typography.titleLarge,
                        color = TealPrimary
                    )
                    Spacer(Modifier.height(18.dp))
                    PillButton(
                        text = "Start Navigation",
                        onClick = { onNavigate(Routes.MAP) },
                        modifier = Modifier.fillMaxWidth(0.66f)
                    )
                }
            }
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
}
