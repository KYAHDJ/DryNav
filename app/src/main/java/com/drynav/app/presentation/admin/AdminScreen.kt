package com.drynav.app.presentation.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.drynav.app.domain.model.FloodReport
import com.drynav.app.domain.model.FloodSeverity
import com.drynav.app.presentation.components.DryNavBottomBar
import com.drynav.app.presentation.map.applyDryNavMapDefaults
import com.drynav.app.presentation.map.updateFloodHeatmap
import com.drynav.app.presentation.navigation.Routes
import com.drynav.app.presentation.theme.FloodRed
import com.drynav.app.presentation.theme.LogoutRed
import com.drynav.app.presentation.theme.Mist
import com.drynav.app.presentation.theme.TealPrimary
import com.drynav.app.presentation.theme.WarnYellow
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which subset of reports the admin is currently browsing. */
private enum class AdminFilter { PENDING, APPROVED }

/** Admin-only moderation queue: approve, reject, delete, or wipe everything. */
@Composable
fun AdminScreen(
    onNavigate: (String) -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteAllDialog by rememberSaveable { mutableStateOf(false) }
    var previewReport by remember { mutableStateOf<FloodReport?>(null) }
    var filter by rememberSaveable { mutableStateOf(AdminFilter.PENDING) }
    var showAreaPicker by rememberSaveable { mutableStateOf(false) }
    var showBarangayPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { DryNavBottomBar(currentRoute = Routes.ADMIN, onNavigate = onNavigate) }
    ) { padding ->
        if (!uiState.isAdmin) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Admins only.", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    "Moderation",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(color = Color(0xFFEFF2F5), shape = RoundedCornerShape(20.dp)) {
                    Text(
                        "Admin",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF374151),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                val pendingCount = uiState.filteredReports.count { it.isPending }
                val approvedCount = uiState.filteredReports.count { it.isApproved }

                // Area filter — one picker row, City then Barangay, so
                // multiple admins can split the queue by area instead of
                // sharing one mixed list. Always visible (not just once
                // reports have area data) so the capability isn't hidden.
                val hasAreaData = uiState.availableCities.isNotEmpty()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Mist)
                        .clickable(enabled = hasAreaData) { showAreaPicker = true }
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (hasAreaData) {
                            buildString {
                                append(uiState.selectedCity ?: "All Cities")
                                uiState.selectedBarangay?.let { append("  →  $it") }
                            }
                        } else {
                            "No area data yet"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (hasAreaData) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            Color(0xFF9CA3AF)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (hasAreaData) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Choose area",
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                // Segmented Pending / Approved control.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Mist)
                        .padding(4.dp)
                ) {
                    SegmentedTab(
                        label = "Pending",
                        count = pendingCount,
                        countColor = FloodRed,
                        selected = filter == AdminFilter.PENDING,
                        onClick = { filter = AdminFilter.PENDING },
                        modifier = Modifier.weight(1f)
                    )
                    SegmentedTab(
                        label = "Approved",
                        count = approvedCount,
                        countColor = TealPrimary,
                        selected = filter == AdminFilter.APPROVED,
                        onClick = { filter = AdminFilter.APPROVED },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { showDeleteAllDialog = true }) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = LogoutRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Delete all", color = LogoutRed, style = MaterialTheme.typography.labelMedium)
                    }
                }

                val visibleReports = uiState.filteredReports.filter {
                    when (filter) {
                        AdminFilter.PENDING -> it.isPending
                        AdminFilter.APPROVED -> it.isApproved
                    }
                }
                if (visibleReports.isEmpty()) {
                    Text(
                        when (filter) {
                            AdminFilter.PENDING -> "No reports awaiting review."
                            AdminFilter.APPROVED -> "No approved floods currently on the map."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B7280)
                    )
                } else {
                    // Grouped by city with an uppercase section header each.
                    val grouped = visibleReports.groupBy { it.areaCity() ?: "Other areas" }
                    grouped.forEach { (city, reports) ->
                        Text(
                            city.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = Color(0xFF6B7280),
                            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                        )
                        reports.forEach { report ->
                            ReportCard(
                                report = report,
                                onApprove = { viewModel.approve(report) },
                                onReject = { viewModel.reject(report) },
                                onDelete = { viewModel.deleteReport(report) },
                                onPreview = { previewReport = report }
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteAllDialog) {
        DeleteAllDialog(
            isDeleting = uiState.isDeletingAll,
            onConfirm = { typed ->
                viewModel.deleteAll(typed)
                showDeleteAllDialog = false
            },
            onDismiss = { showDeleteAllDialog = false }
        )
    }

    // City picker → optional barangay picker, driving the real area filter.
    if (showAreaPicker) {
        AreaPickerDialog(
            title = "Filter by city",
            options = listOf("All Cities") + uiState.availableCities,
            onPick = { picked ->
                showAreaPicker = false
                if (picked == "All Cities") {
                    viewModel.setCityFilter(null)
                } else {
                    viewModel.setCityFilter(picked)
                    showBarangayPicker = true
                }
            },
            onDismiss = { showAreaPicker = false }
        )
    }
    if (showBarangayPicker) {
        val barangays = uiState.availableBarangays
        if (barangays.isEmpty()) {
            showBarangayPicker = false
        } else {
            AreaPickerDialog(
                title = "Filter by barangay",
                options = listOf("All Barangays") + barangays,
                onPick = { picked ->
                    showBarangayPicker = false
                    viewModel.setBarangayFilter(picked.takeIf { it != "All Barangays" })
                },
                onDismiss = { showBarangayPicker = false }
            )
        }
    }

    previewReport?.let { report ->
        ReportPreviewDialog(report = report, onDismiss = { previewReport = null })
    }
}

/**
 * Shows ONLY this one report's own painted road/point, zoomed to fit it —
 * so the admin can judge a single submission without every other flood on
 * the map cluttering the picture.
 */
@Composable
private fun ReportPreviewDialog(report: FloodReport, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    DisposableEffect(Unit) {
        mapView.applyDryNavMapDefaults()
        mapView.getMapboxMap().loadStyleUri(Style.DARK) { style ->
            updateFloodHeatmap(style, listOf(report), enabled = true)
        }
        onDispose { mapView.onDestroy() }
    }
    LaunchedEffect(report) {
        val points = buildList {
            add(Point.fromLngLat(report.longitude, report.latitude))
            report.areaPoints.forEach { add(Point.fromLngLat(it.lng, it.lat)) }
        }
        val camera: CameraOptions = mapView.getMapboxMap().cameraForCoordinates(
            coordinates = points,
            padding = EdgeInsets(60.0, 60.0, 60.0, 60.0)
        )
        mapView.getMapboxMap().setCamera(camera)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
        ) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                Surface(
                    color = Color(0xCC1F2937),
                    shape = RoundedCornerShape(bottomEnd = 16.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        if (report.areaPoints.isNotEmpty()) {
                            "${report.areaPoints.size} road point(s)"
                        } else {
                            "Point-only report"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Surface(color = Color(0xCC1F2937), shape = CircleShape) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close preview",
                            tint = Color.White,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportCard(
    report: FloodReport,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDelete: () -> Unit,
    onPreview: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF8FAFC),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(report)
                Spacer(Modifier.width(8.dp))
                SeverityChip(report.severity)
                Spacer(Modifier.weight(1f))
                Text(
                    formatTimestamp(report.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B7280)
                )
            }

            if (report.areaLabel.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Flood at: ${report.areaLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            // Only worth a separate line when it's actually a different place
            // than the flood itself — otherwise it's just noise.
            if (report.reporterAreaLabel.isNotBlank() && report.reporterAreaLabel != report.areaLabel) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Reported from: ${report.reporterAreaLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF6B7280)
                )
            }

            if (report.photoUrls.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    report.photoUrls.forEach { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "Flood photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(140.dp)
                                .background(Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                        )
                    }
                }
            }

            if (report.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(report.description, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%.5f, %.5f".format(report.latitude, report.longitude) +
                        if (report.areaPoints.isNotEmpty()) " · ${report.areaPoints.size} road point(s)" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CA3AF),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onPreview) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Preview", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(4.dp))
            if (report.isPending) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onApprove,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Approve")
                    }
                    OutlinedButton(
                        onClick = onReject,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Reject")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LogoutRed),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Delete") }
            }
        }
    }
}

/** Simple list dialog used for the City → Barangay area filter. */
@Composable
private fun AreaPickerDialog(
    title: String,
    options: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Text(
                        option,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(option) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** One tab of the gray-track segmented control — white card when selected. */
@Composable
private fun SegmentedTab(
    label: String,
    count: Int,
    countColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color(0xFF6B7280)
            )
            if (count > 0) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelLarge,
                    color = countColor
                )
            }
        }
    }
}

@Composable
private fun StatusChip(report: FloodReport) {
    val (label, color) = when {
        report.isPending -> "Pending" to WarnYellow
        report.isApproved -> "Approved" to Color(0xFF2E7D32)
        else -> "Rejected" to LogoutRed
    }
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SeverityChip(severity: FloodSeverity) {
    val label = if (severity == FloodSeverity.IMPASSABLE) "Blocked" else "Passable"
    val color = if (severity == FloodSeverity.IMPASSABLE) Color(0xFFE53935) else Color(0xFFFFA000)
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Requires typing the exact phrase before the destructive action is enabled. */
@Composable
private fun DeleteAllDialog(
    isDeleting: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val phrase = AdminViewModel.CONFIRMATION_PHRASE

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = LogoutRed) },
        title = { Text("Delete ALL flood reports?") },
        text = {
            Column {
                Text(
                    "This permanently removes every flood report and photo for all users. " +
                        "This can't be undone.\n\nType \"$phrase\" to confirm:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    enabled = !isDeleting,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(typed) },
                enabled = typed == phrase && !isDeleting
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Delete all", color = LogoutRed)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Cancel") }
        }
    )
}

private fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("MMM d, h:mma", Locale.getDefault()).format(Date(timestamp))
