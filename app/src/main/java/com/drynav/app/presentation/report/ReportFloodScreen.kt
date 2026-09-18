package com.drynav.app.presentation.report

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import java.io.File

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.drynav.app.domain.model.FloodReport
import com.drynav.app.domain.model.FloodSeverity
import com.drynav.app.domain.model.PhotoMetadata
import com.drynav.app.presentation.components.DryNavBottomBar
import com.drynav.app.presentation.components.LoadingOverlay
import com.drynav.app.presentation.components.PillButton
import com.drynav.app.presentation.map.applyDryNavLocationPuck
import com.drynav.app.presentation.map.applyDryNavMapDefaults
import com.drynav.app.presentation.map.updateFloodHeatmap
import com.drynav.app.presentation.navigation.Routes
import com.drynav.app.presentation.tutorial.TutorialCelebrationOverlay
import com.drynav.app.presentation.tutorial.TutorialOverlay
import com.drynav.app.presentation.tutorial.TutorialViewModel
import com.drynav.app.presentation.tutorial.tutorialTarget
import com.drynav.app.presentation.theme.Amber
import com.drynav.app.presentation.theme.FloodRed
import com.drynav.app.presentation.theme.TealPrimary
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import kotlin.math.roundToInt

/**
 * "Report Floods" — a full-screen map (matching the navigation screen's
 * scale) with the location pin / pin-placement tools overlaid on it, plus a
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
    var selectedInternetCheck by remember { mutableStateOf<InternetImageCheck?>(null) }
    // Collapsed by default so the map is fully visible; tap the handle to
    // bring the form back up when you're ready to fill it in.
    var sheetExpanded by rememberSaveable { mutableStateOf(false) }
    val tutorialManager = hiltViewModel<TutorialViewModel>().manager
    // Pinning lives on the map; the report form remains collapsible so the map
    // stays visible while the user chooses the exact flood location.
    LaunchedEffect(tutorialManager.isActive, tutorialManager.stepIndex) {
        if (tutorialManager.isActive && tutorialManager.currentStep?.route == Routes.REPORT) {
            sheetExpanded = when (tutorialManager.currentStep?.targetKey) {
                "pin_tool" -> false
                else -> true
            }
        }
    }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val latestUserLocation = androidx.compose.runtime.rememberUpdatedState(uiState.userLocation)
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            val location = latestUserLocation.value
            if (location != null) {
                viewModel.addCapturedPhoto(
                    uri,
                    PhotoMetadata(System.currentTimeMillis(), location.latitude(), location.longitude())
                )
            } else {
                viewModel.consumeMessage()
            }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = pendingCameraUri
            if (uri != null) cameraLauncher.launch(uri)
        }
    }

    fun startCameraCapture() {
        val location = latestUserLocation.value
        if (location == null) {
            return
        }
        val photoFile = File.createTempFile("flood_", ".jpg", context.cacheDir)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
        pendingCameraUri = uri
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val mapView = remember { MapView(context) }
    fun draftReport(): FloodReport? {
        val loc = uiState.reportLocation ?: return null
        return FloodReport(
            latitude = loc.latitude(),
            longitude = loc.longitude(),
            severity = uiState.severity,
            floodRadiusMeters = uiState.floodRadiusMeters.toDouble()
        )
    }

    DisposableEffect(Unit) {
        mapView.applyDryNavMapDefaults()
        mapView.applyDryNavLocationPuck()
        mapView.getMapboxMap().loadStyleUri(Style.OUTDOORS) { style ->
            updateFloodHeatmap(
                style,
                uiState.existingFloods + listOfNotNull(draftReport()),
                enabled = true,
                pulseScale = 1.0,
                showCenterDots = true
            )
        }
        mapView.gestures.updateSettings {
            rotateEnabled = false
            pitchEnabled = false
        }
        mapView.getMapboxMap().addOnMapClickListener { point ->
            if (uiState.drawMode) viewModel.setReportLocation(point)
            true
        }
        onDispose {
            mapView.onDestroy()
        }
    }
    // Pinning mode keeps normal map pan/zoom available so the user can place
    // the flood precisely; tapping the map updates the center pin.
    LaunchedEffect(uiState.drawMode) {
        mapView.gestures.updateSettings {
            scrollEnabled = true
            pinchToZoomEnabled = true
            doubleTapToZoomInEnabled = true
            quickZoomEnabled = true
        }
    }
    // Already-approved floods (for reference) plus this report's own
    // not-yet-submitted pin — all rendered as heat-vision only,
    // never as a hard-edged line or a marker icon.
    LaunchedEffect(
        uiState.existingFloods, uiState.reportLocation,
        uiState.severity, uiState.floodRadiusMeters
    ) {
        mapView.getMapboxMap().getStyle()?.let { style ->
            updateFloodHeatmap(
                style,
                uiState.existingFloods + listOfNotNull(draftReport()),
                enabled = true,
                pulseScale = 1.0,
                showCenterDots = true
            )
        }
    }
    LaunchedEffect(uiState.userLocation != null) {
        uiState.userLocation?.let { loc ->
            mapView.getMapboxMap().setCamera(
                CameraOptions.Builder().center(loc).zoom(15.0).build()
            )
        }
    }
    // Collapse the form when pinning starts so the whole map is visible; the
    // user can still pull it back up anytime.
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

    Box(Modifier.fillMaxSize()) {
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
                        if (uiState.drawMode) "Tap the map to place or adjust the flood pin"
                        else "Tap Pin Flood to choose the exact flood location",
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

            // ---- Flood pin controls ----
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            ) {
                Surface(
                    onClick = { viewModel.toggleDrawMode() },
                    shape = RoundedCornerShape(18.dp),
                    color = if (uiState.drawMode) TealPrimary else Color(0xE6455A64),
                    shadowElevation = 5.dp
                ) {
                    Text(
                        if (uiState.drawMode) "Pinning…" else "📍 Pin Flood",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                            .tutorialTarget(tutorialManager, "pin_tool")
                    )
                }
                if (uiState.drawMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier.width(190.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text("Flood size", style = MaterialTheme.typography.labelLarge)
                            Text("${uiState.floodRadiusMeters.roundToInt()} m radius", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(
                                value = uiState.floodRadiusMeters,
                                onValueChange = viewModel::setFloodRadiusMeters,
                                valueRange = 20f..300f
                            )
                        }
                    }
                }
            }

            // ---- Bottom sheet: collapsible so the map stays visible ----
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
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
                            .pointerInput(sheetExpanded) {
                                var totalDrag = 0f
                                detectVerticalDragGestures(
                                    onVerticalDrag = { _, dragAmount ->
                                        totalDrag += dragAmount
                                    },
                                    onDragEnd = {
                                        when {
                                            totalDrag < -60f -> sheetExpanded = true
                                            totalDrag > 60f -> sheetExpanded = false
                                        }
                                    },
                                    onDragCancel = { totalDrag = 0f }
                                )
                            }
                            .clickable { sheetExpanded = !sheetExpanded }
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "REPORT FLOODS",
                                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (uiState.reportLocation != null) {
                                Text(
                                    "Flood pin selected · adjust it on the map",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            if (sheetExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = if (sheetExpanded) "Collapse" else "Expand",
                            tint = Color(0xFF6B7280)
                        )
                    }

                    if (!sheetExpanded) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Swipe up to open",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        return@Column
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        if (uiState.photos.isEmpty()) "Photo required" else
                            "${uiState.photos.size} live camera photo(s) attached",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (uiState.photos.isEmpty()) FloodRed else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        uiState.photos.forEach { photo ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.width(330.dp)
                            ) {
                                Box(modifier = Modifier.size(88.dp)) {
                                    AsyncImage(
                                        model = photo.uri,
                                        contentDescription = "Flood photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                                    )
                                    Surface(
                                        onClick = { viewModel.removePhoto(photo.uri) },
                                        shape = CircleShape,
                                        color = Color(0xCC1F2937),
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove photo",
                                            tint = Color.White,
                                            modifier = Modifier.padding(3.dp).size(14.dp)
                                        )
                                    }
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    when {
                                        photo.aiAnalyzing -> {
                                            AnalysisStatusCard(
                                                title = "AI IMAGE CHECK",
                                                message = "Checking the photo…",
                                                icon = Icons.Default.Search,
                                                accent = TealPrimary
                                            )
                                        }
                                        photo.aiAnalysis != null -> {
                                            AiAnalysisCard(
                                                analysis = photo.aiAnalysis,
                                                internetCheck = photo.internetCheck,
                                                internetChecking = photo.internetChecking,
                                                onOpenWebDetails = { selectedInternetCheck = photo.internetCheck }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(2.dp, TealPrimary, RoundedCornerShape(14.dp))
                                .tutorialTarget(tutorialManager, "photo_picker")
                                .clickable { startCameraCapture() }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Add, contentDescription = "Take flood photo", tint = TealPrimary, modifier = Modifier.size(28.dp))
                                Text("Camera", style = MaterialTheme.typography.labelSmall, color = TealPrimary)
                            }
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
                        "FLOOD STATUS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    FloodStatusRadio(
                        selected = uiState.severity == FloodSeverity.PASSABLE,
                        label = "Passable",
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        onClick = { viewModel.setSeverity(FloodSeverity.PASSABLE) }
                    )
                    FloodStatusRadio(
                        selected = uiState.severity == FloodSeverity.IMPASSABLE,
                        label = "Not Passable",
                        labelColor = FloodRed,
                        onClick = { viewModel.setSeverity(FloodSeverity.IMPASSABLE) },
                        modifier = Modifier.tutorialTarget(tutorialManager, "severity_toggle")
                    )

                    Spacer(Modifier.height(14.dp))
                    if (uiState.reportLocation == null) {
                        Text(
                            "Pin the flood location on the map before reporting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FloodRed
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    PillButton(
                        text = "REPORT",
                        loading = uiState.isSubmitting,
                        onClick = viewModel::requestSubmit,
                        enabled = uiState.photos.isNotEmpty() &&
                            uiState.photos.all { !it.aiAnalyzing && it.aiAnalysis?.canSubmit == true } &&
                            uiState.reportLocation != null &&
                            !uiState.isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tutorialTarget(tutorialManager, "submit_button"),
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

        if (uiState.confirmBeforeSubmit) {
            AlertDialog(
                onDismissRequest = viewModel::cancelSubmitConfirmation,
                title = { Text("Confirm flood report") },
                text = {
                    Text(
                        "Are you sure this is the final flood location? Your live camera photo, GPS capture, flood status, and selected flood size will be submitted. You can still cancel and adjust the pin."
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { viewModel.cancelSubmitConfirmation(); viewModel.submit() }) { Text("Confirm & Post") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = viewModel::cancelSubmitConfirmation) { Text("Edit") }
                }
            )
        }

        selectedInternetCheck?.let { check ->
            val photo = uiState.photos.firstOrNull { it.internetCheck == check }
            val analysis = photo?.aiAnalysis
            if (analysis != null) {
                InternetSourceDialog(
                    analysis = analysis,
                    check = check,
                    onDismiss = { selectedInternetCheck = null }
                )
            }
        }

        if (tutorialManager.isActive && tutorialManager.currentStep?.route == Routes.REPORT) {
            TutorialOverlay(tutorialManager)
        }
        if (tutorialManager.showCelebration) {
            TutorialCelebrationOverlay(onDismiss = tutorialManager::dismissCelebration)
        }
    }
}


@Composable
private fun FloodStatusRadio(
    selected: Boolean,
    label: String,
    labelColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = labelColor)
    }
}

@Composable
private fun AnalysisStatusCard(
    title: String,
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall, color = accent)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun AiAnalysisCard(
    analysis: ImageAnalysis,
    internetCheck: InternetImageCheck?,
    internetChecking: Boolean,
    onOpenWebDetails: () -> Unit
) {
    val accent = when {
        analysis.error -> FloodRed
        analysis.label == "FLOOD" && analysis.canSubmit -> TealPrimary
        analysis.label == "BLURRY_UNUSABLE" -> Amber
        else -> FloodRed
    }
    val icon = when {
        analysis.error -> Icons.Default.WarningAmber
        analysis.label == "FLOOD" && analysis.canSubmit -> Icons.Default.CheckCircle
        analysis.label == "BLURRY_UNUSABLE" -> Icons.Default.BlurOn
        analysis.label == "UNCERTAIN" -> Icons.Default.HelpOutline
        else -> Icons.Default.WarningAmber
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "AI IMAGE CHECK · ${analysis.confidence}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                    Text(
                        analysis.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        analysis.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            when {
                internetChecking -> {
                    AnalysisStatusCard(
                        title = "WEB SOURCE CHECK",
                        message = "Checking Google Lens for where this image may have appeared…",
                        icon = Icons.Default.Search,
                        accent = TealPrimary
                    )
                }
                internetCheck != null && internetCheck.error != null -> {
                    Text(
                        "Web source check unavailable — this does not block the flood report.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                internetCheck != null && internetCheck.hasMatches -> {
                    Surface(
                        onClick = onOpenWebDetails,
                        color = FloodRed.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = FloodRed, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (internetCheck.foundExact) "Possible online copy found" else "Similar image(s) found online",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = FloodRed
                                )
                                Text(
                                    "Tap to view sources and links",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.OpenInNew, contentDescription = "View sources", tint = FloodRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                internetCheck != null -> {
                    Text(
                        "No matching web source found by Google Lens.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun InternetSourceDialog(
    analysis: ImageAnalysis,
    check: InternetImageCheck,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("IMAGE SOURCE CHECK")
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Surface(
                    color = TealPrimary.copy(alpha = 0.09f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("DryNav AI", style = MaterialTheme.typography.labelSmall, color = TealPrimary)
                        Text(
                            "${analysis.label.replace('_', ' ')} · ${analysis.confidence}% confidence",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    if (check.foundExact) "Possible online copy" else "Similar image found online",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (check.foundExact) FloodRed else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (check.foundExact) {
                        "Google Lens found exact-match results for this photo. This is a source warning, not proof that the photo was copied."
                    } else {
                        "Google Lens found visually similar results. Similarity alone does not establish where your photo came from."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (check.exactMatches.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("EXACT MATCHES", style = MaterialTheme.typography.labelMedium, color = FloodRed)
                    check.exactMatches.forEach { match ->
                        SourceResultRow(match = match, onOpen = { uriHandler.openUri(match.link) })
                    }
                }

                if (check.visualMatches.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("VISUAL MATCHES", style = MaterialTheme.typography.labelMedium, color = TealPrimary)
                    check.visualMatches.forEach { match ->
                        SourceResultRow(match = match, onOpen = { uriHandler.openUri(match.link) })
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun SourceResultRow(match: InternetMatch, onOpen: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                match.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            Text(
                match.source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.material3.TextButton(onClick = onOpen) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Open source")
            }
        }
    }
}
