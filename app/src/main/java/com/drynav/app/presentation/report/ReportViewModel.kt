package com.drynav.app.presentation.report

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drynav.app.data.auth.AuthRepository
import com.drynav.app.data.location.LocationProvider
import com.drynav.app.data.prefs.UserPreferences
import com.drynav.app.data.search.GeocodingService
import com.drynav.app.data.search.MapMatchingService
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.drynav.app.domain.model.AreaPoint
import com.drynav.app.domain.model.FloodReport
import com.drynav.app.domain.model.FloodSeverity
import com.drynav.app.domain.model.Mood
import com.drynav.app.domain.repository.FloodRepository
import com.google.firebase.storage.FirebaseStorage
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject

data class ReportUiState(
    val userLocation: Point? = null,
    /** Where the flood is being reported — tap the mini-map to move it. */
    val reportLocation: Point? = null,
    /** Brush mode: drag on the map paints the affected stretch of road. */
    val drawMode: Boolean = false,
    /** Completed brush strokes, each a trail of map points. */
    val strokes: List<List<Point>> = emptyList(),
    /**
     * Strokes still being road-snapped in the background. Shown immediately
     * (raw, unsnapped) so drawing feels instant instead of waiting on the
     * map-matching network round trip before anything appears.
     */
    val pendingStrokes: List<List<Point>> = emptyList(),
    /** Strokes undone in this session — Redo pops them back off this stack. */
    val redoStack: List<List<Point>> = emptyList(),
    /** Already-approved floods, shown for reference so you can see what's already marked. */
    val existingFloods: List<FloodReport> = emptyList(),
    /** False until the first live flood snapshot arrives — gates the loading overlay. */
    val existingFloodsLoaded: Boolean = false,
    /** As many photos as the user wants to attach — no cap. */
    val photoUris: List<Uri> = emptyList(),
    val description: String = "",
    val severity: FloodSeverity = FloodSeverity.IMPASSABLE,
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    val message: String? = null,
    /** The signed-in user's chosen map-puck character — shown here too since you're stationary while reporting. */
    val mood: Mood? = null
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val floodRepository: FloodRepository,
    private val locationProvider: LocationProvider,
    private val storage: FirebaseStorage,
    private val mapMatching: MapMatchingService,
    private val authRepository: AuthRepository,
    private val geocodingService: GeocodingService,
    private val prefs: UserPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    // Map-matching is a network round trip. If the user hits Clear/Undo while
    // one is still in flight, its result must never resurrect a stroke the
    // user just removed — track and cancel in-flight jobs on any edit.
    // CopyOnWriteArrayList because completion callbacks can fire from a
    // background dispatcher thread concurrently with edits on the main thread.
    private val pendingStrokeJobs = java.util.concurrent.CopyOnWriteArrayList<Job>()

    init {
        fetchLocation()
        observeExistingFloods()
        viewModelScope.launch {
            prefs.mood.collect { name ->
                _uiState.update { it.copy(mood = Mood.fromName(name) ?: Mood.HAPPY) }
            }
        }
    }

    private fun observeExistingFloods() {
        viewModelScope.launch {
            floodRepository.getLiveFloodReports().collect { reports ->
                _uiState.update {
                    it.copy(
                        existingFloods = reports.filter { r -> r.isApproved && r.isLiveHazard },
                        existingFloodsLoaded = true
                    )
                }
            }
        }
    }

    // Same fix as the map screen: a one-shot GPS request can stall forever on
    // a cold fix, so a continuous stream backs it up and fills in as soon as
    // a real fix lands instead of leaving the screen stuck waiting.
    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        viewModelScope.launch {
            locationProvider.getCurrentLocation()?.let(::applyLocation)
        }
        viewModelScope.launch {
            locationProvider.locationUpdates().collect(::applyLocation)
        }
    }

    private fun applyLocation(loc: android.location.Location) {
        val point = Point.fromLngLat(loc.longitude, loc.latitude)
        _uiState.update {
            it.copy(
                userLocation = point,
                // Default the report to the GPS fix until the user taps.
                reportLocation = it.reportLocation ?: point
            )
        }
    }

    /** Called when the user taps the mini-map to place the flood pin. */
    fun setReportLocation(point: Point) = _uiState.update { it.copy(reportLocation = point) }

    // ------------------------------------------------------------------
    // Flood-area brush
    // ------------------------------------------------------------------

    fun toggleDrawMode() = _uiState.update { it.copy(drawMode = !it.drawMode) }

    /**
     * Commits one finished brush stroke: the raw trail is snapped onto the
     * road network first, so only the road itself gets highlighted. Painting
     * a road that's already marked is a no-op.
     */
    fun addStroke(points: List<Point>) {
        if (points.size < 2) return
        // Show the raw stroke immediately — snapping is a network round
        // trip, and waiting for it before drawing anything is what made
        // marking several roads in a row feel like the app had stalled.
        _uiState.update { it.copy(pendingStrokes = it.pendingStrokes + listOf(points)) }

        val job = Job()
        pendingStrokeJobs += job
        job.invokeOnCompletion { pendingStrokeJobs.remove(job) }
        viewModelScope.launch(job) {
            val snapped = mapMatching.snapToRoads(points)
            if (snapped == null) {
                _uiState.update {
                    it.copy(
                        pendingStrokes = it.pendingStrokes - listOf(points),
                        message = "No road found under that stroke — paint along a road."
                    )
                }
                return@launch
            }
            // Only guard against repainting a road already in THIS report —
            // other users' existing flood reports must never block a new,
            // independent confirmation of the same road.
            if (isAlreadyMarked(snapped)) {
                _uiState.update {
                    it.copy(
                        pendingStrokes = it.pendingStrokes - listOf(points),
                        message = "You've already painted that road in this report."
                    )
                }
                return@launch
            }
            _uiState.update { state ->
                state.copy(
                    strokes = state.strokes + listOf(snapped),
                    pendingStrokes = state.pendingStrokes - listOf(points),
                    redoStack = emptyList(), // a fresh stroke invalidates any redo history
                    // Anchor the report marker to the first stroke's start.
                    reportLocation = state.reportLocation ?: snapped.first()
                )
            }
        }
    }

    /** True when most of [stroke] lies on roads already painted in this report. */
    private fun isAlreadyMarked(stroke: List<Point>): Boolean {
        val existing = _uiState.value.strokes.flatten()
        if (existing.isEmpty()) return false
        val covered = stroke.count { p ->
            existing.any {
                TurfMeasurement.distance(p, it, TurfConstants.UNIT_METERS) <= DUPLICATE_DISTANCE_M
            }
        }
        // Only treat it as a repaint of the same road when it's almost
        // entirely covered already — a road that merely touches/crosses an
        // existing one (e.g. at an intersection) must still be paintable.
        return covered >= stroke.size * 0.92
    }

    fun undoStroke() {
        cancelPendingStrokeJobs()
        _uiState.update {
            if (it.strokes.isEmpty()) return@update it.copy(pendingStrokes = emptyList())
            it.copy(
                strokes = it.strokes.dropLast(1),
                redoStack = it.redoStack + listOf(it.strokes.last()),
                pendingStrokes = emptyList()
            )
        }
    }

    fun redoStroke() = _uiState.update {
        if (it.redoStack.isEmpty()) return@update it
        it.copy(strokes = it.strokes + listOf(it.redoStack.last()), redoStack = it.redoStack.dropLast(1))
    }

    fun clearStrokes() {
        cancelPendingStrokeJobs()
        _uiState.update { it.copy(strokes = emptyList(), redoStack = emptyList(), pendingStrokes = emptyList()) }
    }

    private fun cancelPendingStrokeJobs() {
        // Cancelling a job can synchronously fire its own invokeOnCompletion
        // handler (which removes it from this same list) — iterating the
        // live list while cancelling was a ConcurrentModificationException
        // waiting to happen. Snapshot it first, then cancel the copy.
        val jobs = pendingStrokeJobs.toList()
        pendingStrokeJobs.clear()
        jobs.forEach { it.cancel() }
    }

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update { it.copy(photoUris = it.photoUris + uris) }
    }

    fun removePhoto(uri: Uri) = _uiState.update { it.copy(photoUris = it.photoUris - uri) }

    fun setDescription(text: String) = _uiState.update { it.copy(description = text) }

    fun setSeverity(severity: FloodSeverity) = _uiState.update { it.copy(severity = severity) }

    @SuppressLint("MissingPermission")
    fun submit() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }

            // The flood's own location: prefer the centroid of the drawn
            // road strokes when the user marked the flood by brushing (the
            // brush IS the placement — reportLocation is only ever moved by
            // a separate single tap, and stays at its GPS default otherwise,
            // silently mislabeling every brush-only report with wherever the
            // reporter physically was instead of the road they actually
            // painted). Falls back to the tapped pin, then GPS.
            val strokePoints = _uiState.value.strokes.flatten()
            val location = if (strokePoints.isNotEmpty()) {
                Point.fromLngLat(
                    strokePoints.sumOf { it.longitude() } / strokePoints.size,
                    strokePoints.sumOf { it.latitude() } / strokePoints.size
                )
            } else {
                _uiState.value.reportLocation
                    ?: locationProvider.getCurrentLocation()?.let {
                        Point.fromLngLat(it.longitude, it.latitude)
                    }
            }
            if (location == null) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        message = "Couldn't get a location — tap the map to place the flood pin."
                    )
                }
                return@launch
            }

            // Photo upload is best-effort and runs in parallel: one failed
            // upload never blocks the report or the other photos. Photos are
            // downscaled/recompressed first — full-resolution camera photos
            // (often 3-8 MB) were the real reason submits crawled on a weak
            // connection; a ~200 KB JPEG uploads in a fraction of the time.
            val photoUris = _uiState.value.photoUris
            val photoUrls = coroutineScope {
                photoUris.map { uri ->
                    async {
                        runCatching {
                            val bytes = compressPhoto(uri)
                            val ref = storage.reference.child("flood_photos/${UUID.randomUUID()}.jpg")
                            ref.putBytes(bytes).await()
                            ref.downloadUrl.await().toString()
                        }.getOrNull()
                    }
                }.awaitAll()
            }.filterNotNull()
            if (photoUris.isNotEmpty() && photoUrls.size < photoUris.size) {
                _uiState.update { s ->
                    s.copy(
                        message = "${photoUris.size - photoUrls.size} photo(s) failed to upload " +
                            "— submitting the report with the rest."
                    )
                }
            }

            // Decimate each stroke (independently) so huge scribbles stay
            // Firestore-friendly — strokes stay SEPARATE so rendering never
            // draws a straight connecting line between two unrelated roads.
            val areaStrokes = _uiState.value.strokes.map { stroke ->
                val step = ((stroke.size + MAX_POINTS_PER_STROKE - 1) / MAX_POINTS_PER_STROKE)
                    .coerceAtLeast(1)
                stroke.filterIndexed { i, _ -> i % step == 0 || i == stroke.lastIndex }
                    .map { AreaPoint(it.latitude(), it.longitude()) }
            }

            // The flood's own location (where it was pinned/painted) is looked
            // up separately from the reporter's own GPS position — they're
            // often not the same place (reporting a flood elsewhere).
            val reporterLocation = _uiState.value.userLocation
            val areaLabel = geocodingService.reverseGeocode(location).orEmpty()
            val reporterAreaLabel = reporterLocation
                ?.takeIf { it != location }
                ?.let { geocodingService.reverseGeocode(it).orEmpty() }
                ?: areaLabel

            val isAdmin = authRepository.isAdmin
            val report = FloodReport(
                latitude = location.latitude(),
                longitude = location.longitude(),
                severity = _uiState.value.severity,
                timestamp = System.currentTimeMillis(),
                description = _uiState.value.description.trim(),
                photoUrls = photoUrls,
                areaStrokes = areaStrokes,
                status = if (isAdmin) FloodReport.STATUS_APPROVED else FloodReport.STATUS_PENDING,
                areaLabel = areaLabel,
                reporterLatitude = reporterLocation?.latitude(),
                reporterLongitude = reporterLocation?.longitude(),
                reporterAreaLabel = reporterAreaLabel
            )
            floodRepository.submitReport(report)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submitted = true,
                            message = if (isAdmin) null else "Submitted for admin review."
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isSubmitting = false, message = "Submit failed: ${e.message}")
                    }
                }
        }
    }

    fun consumeSubmitted() {
        cancelPendingStrokeJobs()
        _uiState.update {
            it.copy(
                submitted = false,
                photoUris = emptyList(),
                description = "",
                strokes = emptyList(),
                redoStack = emptyList(),
                pendingStrokes = emptyList(),
                drawMode = false
            )
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    /** Downscales to a sane max dimension and re-encodes as JPEG before upload. */
    private suspend fun compressPhoto(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_PHOTO_DIMENSION &&
            bounds.outHeight / (sample * 2) >= MAX_PHOTO_DIMENSION
        ) {
            sample *= 2
        }
        val decoded = appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Couldn't decode photo")

        val scale = MAX_PHOTO_DIMENSION.toFloat() / maxOf(decoded.width, decoded.height)
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true
            )
        } else decoded

        ByteArrayOutputStream().use { out ->
            resized.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    private companion object {
        const val MAX_POINTS_PER_STROKE = 40
        const val DUPLICATE_DISTANCE_M = 12.0
        // Kept small on purpose: this is a "knee-deep water here" snapshot,
        // not a photo people zoom into — 720p uploads in a couple of
        // seconds even on a weak connection instead of tens of seconds.
        const val MAX_PHOTO_DIMENSION = 720
        const val PHOTO_JPEG_QUALITY = 55
    }
}
