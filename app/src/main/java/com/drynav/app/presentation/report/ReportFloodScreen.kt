package com.drynav.app.presentation.report

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.drynav.app.domain.model.AreaPoint
import com.drynav.app.domain.model.FloodReport
import com.drynav.app.domain.model.FloodSeverity
import com.drynav.app.domain.model.Mood
import com.drynav.app.presentation.components.DryNavBottomBar
import com.drynav.app.presentation.components.LoadingOverlay
import com.drynav.app.presentation.components.PillButton
import com.drynav.app.presentation.map.applyDryNavLocationPuck
import com.drynav.app.presentation.map.applyDryNavMapDefaults
import com.drynav.app.presentation.map.setDefaultPuckEnabled
import com.drynav.app.presentation.map.updateFloodHeatmap
import com.drynav.app.presentation.navigation.Routes
import com.drynav.app.presentation.theme.Amber
import com.drynav.app.presentation.theme.FloodRed
import com.drynav.app.presentation.theme.TealPrimary
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.IconRotationAlignment
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures

/**
 * "Report Floods" — a full-screen map (matching the navigation screen's
 * scale) with the location pin / road-brush tools overlaid on it, plus a
 * bottom sheet for the photo, description, severity and submit action.
 */
@Composable
fun ReportFloodScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Collapsed by default so the map is fully visible; tap the handle to
    // bring the form back up when you're ready to fill it in.
    var sheetExpanded by rememberSaveable { mutableStateOf(false) }

    // No hard cap on how many photos can be attached.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> viewModel.addPhotos(uris) }

    val mapView = remember { MapView(context) }
    // Screen-space preview of the stroke being drawn right now.
    var liveStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // Own mood character — always shown here (never gated on movement, since
    // reporting a flood is inherently a stationary activity). Billboarded
    // PointAnnotation (VIEWPORT alignment), same approach as MapScreen's own
    // puck, so it never tilts/rotates oddly as the map moves.
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

    // Builds a throwaway FloodReport out of the not-yet-submitted strokes
    // (and the point-only pin, if nothing's been brushed) purely so it can
    // be handed to the exact same heat-vision renderer used for real,
    // approved reports — no separate marker icon, one consistent visual.
    fun draftReport(): FloodReport? {
        val loc = uiState.reportLocation ?: return null
        return FloodReport(
            latitude = loc.latitude(),
            longitude = loc.longitude(),
            severity = uiState.severity,
            // Confirmed strokes plus any still being road-snapped in the
            // background — shown immediately so drawing feels instant. Kept
            // as separate strokes so two unrelated roads never get drawn
            // with a straight line bridging the gap between them.
            areaStrokes = (uiState.strokes + uiState.pendingStrokes).map { stroke ->
                stroke.map { AreaPoint(it.latitude(), it.longitude()) }
            }
        )
    }

    DisposableEffect(Unit) {
        mapView.applyDryNavMapDefaults()
        mapView.applyDryNavLocationPuck()
        mapView.getMapboxMap().loadStyleUri(Style.DARK) { style ->
            updateFloodHeatmap(style, uiState.existingFloods + listOfNotNull(draftReport()), enabled = true)
        }
        mapView.gestures.updateSettings {
            rotateEnabled = false
            pitchEnabled = false
        }
        mapView.getMapboxMap().addOnMapClickListener { point ->
            if (!uiState.drawMode) viewModel.setReportLocation(point)
            true
        }
        onDispose {
            mapView.onDestroy()
        }
    }
    // The map's own pan/pinch gesture detector otherwise steals every drag
    // before our brush Canvas ever sees it — disable it while drawing so the
    // finger stroke actually reaches the brush instead of just panning the map.
    LaunchedEffect(uiState.drawMode) {
        mapView.gestures.updateSettings {
            scrollEnabled = !uiState.drawMode
            pinchToZoomEnabled = !uiState.drawMode
            doubleTapToZoomInEnabled = !uiState.drawMode
            quickZoomEnabled = !uiState.drawMode
        }
    }
    // Already-approved floods (for reference) plus this report's own
    // not-yet-submitted strokes/pin — all rendered as heat-vision only,
    // never as a hard-edged line or a marker icon.
    LaunchedEffect(
        uiState.existingFloods, uiState.strokes, uiState.pendingStrokes,
        uiState.severity, uiState.reportLocation
    ) {
        mapView.getMapboxMap().getStyle()?.let { style ->
            updateFloodHeatmap(style, uiState.existingFloods + listOfNotNull(draftReport()), enabled = true)
        }
    }
    LaunchedEffect(uiState.userLocation != null) {
        uiState.userLocation?.let { loc ->
            mapView.getMapboxMap().setCamera(
                CameraOptions.Builder().center(loc).zoom(15.0).build()
            )
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
    // Collapse the form the moment drawing starts so the whole map is
    // visible while painting; the user can still pull it back up anytime.
    LaunchedEffect(uiState.drawMode) {
        if (uiState.drawMode) sheetExpanded = false
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(uiState.submitted) {
        if (uiState.submitted) {
            viewModel.consumeSubmitted()
            onNavigate(Routes.REPORT_SUCCESS)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            DryNavBottomBar(currentRoute = Routes.REPORT, onNavigate = onNavigate)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ---- Big map, same footprint as the navigation screen ----
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            if (uiState.drawMode) {
                // Brush capture layer: swallows touches, converts the drag
                // into map coordinates, previews it live over the real map.
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> liveStroke = listOf(offset) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    liveStroke = liveStroke + change.position
                                },
                                onDragEnd = {
                                    val points = liveStroke.map { o ->
                                        mapView.getMapboxMap().coordinateForPixel(
                                            ScreenCoordinate(o.x.toDouble(), o.y.toDouble())
                                        )
                                    }
                                    viewModel.addStroke(points)
                                    liveStroke = emptyList()
                                },
                                onDragCancel = { liveStroke = emptyList() }
                            )
                        }
                ) {
                    if (liveStroke.size >= 2) {
                        drawPath(
                            path = Path().apply {
                                moveTo(liveStroke.first().x, liveStroke.first().y)
                                liveStroke.drop(1).forEach { lineTo(it.x, it.y) }
                            },
                            color = if (uiState.severity == FloodSeverity.IMPASSABLE) {
                                Color(0x99E53935)
                            } else {
                                Color(0x99FFA000)
                            },
                            style = Stroke(width = 22f, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // ---- Header overlay ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Surface(
                    color = Color(0xE6455A64),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (uiState.drawMode) {
                            "Drag along a road to mark it flooded"
                        } else {
                            "Tap the map to pin, or turn on Draw"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = {
                        uiState.userLocation?.let { loc ->
                            mapView.getMapboxMap().setCamera(
                                CameraOptions.Builder().center(loc).zoom(15.0).build()
                            )
                        }
                    },
                    color = Color(0xE6455A64),
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Recenter",
                        tint = Color.White,
                        modifier = Modifier.padding(10.dp).size(20.dp)
                    )
                }
            }

            // ---- Brush toolbar: always reachable, whatever the sheet is doing ----
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            ) {
                BrushToolButton(
                    icon = Icons.Default.Brush,
                    contentDescription = if (uiState.drawMode) "Stop drawing" else "Draw flooded road",
                    active = uiState.drawMode,
                    onClick = viewModel::toggleDrawMode
                )
                BrushToolButton(
                    icon = Icons.Default.Undo,
                    contentDescription = "Undo last road",
                    enabled = uiState.strokes.isNotEmpty(),
                    onClick = viewModel::undoStroke
                )
                BrushToolButton(
                    icon = Icons.Default.Redo,
                    contentDescription = "Redo",
                    enabled = uiState.redoStack.isNotEmpty(),
                    onClick = viewModel::redoStroke
                )
                BrushToolButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "Clear all roads",
                    enabled = uiState.strokes.isNotEmpty(),
                    onClick = viewModel::clearStrokes
                )
            }

            // ---- Bottom sheet: collapsible so the map stays visible ----
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Color.White,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    // Drag handle + header — tap anywhere here to expand/collapse.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { sheetExpanded = !sheetExpanded }
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "REPORT FLOODS",
                                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.sp),
                                color = Color(0xFF1F2937)
                            )
                            if (uiState.strokes.isNotEmpty()) {
                                Text(
                                    "${uiState.strokes.size} road(s) marked",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF6B7280)
                                )
                            }
                        }
                        Icon(
                            if (sheetExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = if (sheetExpanded) "Collapse" else "Expand",
                            tint = Color(0xFF6B7280)
                        )
                    }

                    if (!sheetExpanded) return@Column
                    Spacer(Modifier.height(12.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        if (uiState.photoUris.isEmpty()) "Add photos (optional)" else {
                            "${uiState.photoUris.size} photo(s) attached"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF1F2937)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        uiState.photoUris.forEach { uri ->
                            Box(modifier = Modifier.size(88.dp)) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Flood photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                )
                                Surface(
                                    onClick = { viewModel.removePhoto(uri) },
                                    shape = CircleShape,
                                    color = Color(0xCC1F2937),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove photo",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .padding(3.dp)
                                            .size(14.dp)
                                    )
                                }
                            }
                        }
                        // Tap to add more — no limit on how many.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(2.dp, TealPrimary, RoundedCornerShape(14.dp))
                                .clickable {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add photo",
                                tint = TealPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = uiState.description,
                        onValueChange = viewModel::setDescription,
                        placeholder = { Text("Description — e.g. knee-deep water near the bridge") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = TealPrimary,
                            unfocusedIndicatorColor = Color(0xFFB9C7D4)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "HOW BAD IS IT?",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = Color(0xFF6B7280)
                    )
                    Spacer(Modifier.height(8.dp))
                    // Mockup severity buttons: amber = passable/slow, red = blocked.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val passableSelected = uiState.severity == FloodSeverity.PASSABLE
                        val blockedSelected = uiState.severity == FloodSeverity.IMPASSABLE
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (passableSelected) Amber else Color(0xFFF3F6F9))
                                .clickable { viewModel.setSeverity(FloodSeverity.PASSABLE) }
                                .padding(vertical = 13.dp)
                        ) {
                            Text(
                                "Passable / Slow",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (passableSelected) Color(0xFF3D2600) else Color(0xFF6B7280)
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (blockedSelected) FloodRed else Color(0xFFF3F6F9))
                                .clickable { viewModel.setSeverity(FloodSeverity.IMPASSABLE) }
                                .padding(vertical = 13.dp)
                        ) {
                            Text(
                                "Blocked",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (blockedSelected) Color.White else Color(0xFF6B7280)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    PillButton(
                        text = "REPORT",
                        loading = uiState.isSubmitting,
                        onClick = viewModel::submit,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 18
                    )
                    Spacer(Modifier.height(4.dp))
                    } // inner scrollable form column
                }
            }

            LoadingOverlay(
                visible = !uiState.existingFloodsLoaded,
                label = "Loading existing flood data…"
            )
        }
    }
}

/** One round button in the always-visible brush toolbar (draw/undo/redo/clear). */
@Composable
private fun BrushToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = when {
            active -> TealPrimary
            enabled -> Color(0xE6455A64)
            else -> Color(0x66455A64)
        },
        shadowElevation = 4.dp,
        modifier = Modifier.size(46.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (enabled) Color.White else Color(0x80FFFFFF),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
