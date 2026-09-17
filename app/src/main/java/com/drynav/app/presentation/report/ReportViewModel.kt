package com.drynav.app.presentation.report

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drynav.app.data.auth.AuthRepository
import com.drynav.app.data.location.LocationProvider
import com.drynav.app.data.presence.PresenceManager
import com.drynav.app.data.search.GeocodingService
import com.drynav.app.data.search.MapMatchingService
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.drynav.app.domain.model.AreaPoint
import com.drynav.app.domain.model.FloodReport
import com.drynav.app.domain.model.FloodSeverity
import com.drynav.app.domain.model.Presence
import com.drynav.app.domain.repository.FloodRepository
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
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
import com.drynav.app.domain.model.PhotoMetadata
import javax.inject.Inject

data class ReportUiState(
    val userLocation: Point? = null,
    /** Where the flood is being reported — tap the mini-map to move it. */
    val reportLocation: Point? = null,
    /** Brush mode: drag on the map paints the affected stretch of road. */
    val drawMode: Boolean = false,
    /** Pin placement mode on the map. */
    val floodRadiusMeters: Float = 40f,
    val confirmBeforeSubmit: Boolean = false,
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
    /** Photos captured through the live camera, with capture-time metadata. */
    val photos: List<CapturedPhoto> = emptyList(),
    val description: String = "",
    val severity: FloodSeverity = FloodSeverity.IMPASSABLE,
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    val message: String? = null,
    /** Other online users' live position feed. */
    val otherPresences: List<Presence> = emptyList()
)

data class CapturedPhoto(
    val uri: Uri,
    val metadata: PhotoMetadata,
    val aiAnalysis: ImageAnalysis? = null,
    val aiAnalyzing: Boolean = false
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val floodRepository: FloodRepository,
    private val locationProvider: LocationProvider,
    private val storage: FirebaseStorage,
    private val mapMatching: MapMatchingService,
    private val authRepository: AuthRepository,
    private val geocodingService: GeocodingService,
    private val presenceManager: PresenceManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val floodImageClassifier = FloodImageClassifier(appContext)

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
            presenceManager.otherPresences.collect { others ->
                _uiState.update { it.copy(otherPresences = others) }
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
                // GPS is the reporter's physical location only.
                // The flood location MUST be chosen manually on the map.
                userLocation = point
            )
        }
    }

    /** Called when the user taps the mini-map to place the flood pin. */
    fun setReportLocation(point: Point) = _uiState.update { it.copy(reportLocation = point) }

    // ------------------------------------------------------------------
    // Flood-area brush
    // ------------------------------------------------------------------

    fun toggleDrawMode() = _uiState.update { it.copy(drawMode = !it.drawMode) }

    fun setFloodRadiusMeters(value: Float) = _uiState.update { it.copy(floodRadiusMeters = value.coerceIn(20f, 300f)) }

    fun requestSubmit() {
        val state = _uiState.value
        when {
            state.photos.isEmpty() -> _uiState.update { it.copy(message = "A live camera photo is required before submitting.") }
            state.photos.any { it.aiAnalyzing || it.aiAnalysis == null } ->
                _uiState.update { it.copy(message = "Analyzing your photo — please wait a moment.") }
            state.photos.any { it.aiAnalysis?.canSubmit != true } ->
                _uiState.update { it.copy(message = "The photo needs a clearer flood result before it can be submitted.") }
            state.reportLocation == null -> _uiState.update { it.copy(message = "Pin the flood location on the map before submitting.") }
            else -> _uiState.update { it.copy(confirmBeforeSubmit = true) }
        }
    }

    fun cancelSubmitConfirmation() = _uiState.update { it.copy(confirmBeforeSubmit = false) }


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
                    // Do NOT auto-create a flood pin from a painted stroke.
                    // The user must explicitly tap the map to choose the report location.
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

    fun addCapturedPhoto(uri: Uri, metadata: PhotoMetadata) {
        _uiState.update {
            it.copy(photos = it.photos + CapturedPhoto(uri, metadata, aiAnalyzing = true))
        }
        viewModelScope.launch(Dispatchers.Default) {
            val result = floodImageClassifier.classify(uri)
            _uiState.update { state ->
                state.copy(
                    photos = state.photos.map { photo ->
                        if (photo.uri == uri) {
                            photo.copy(aiAnalysis = result, aiAnalyzing = false)
                        } else photo
                    }
                )
            }
        }
    }

    fun removePhoto(uri: Uri) = _uiState.update { state ->
        state.copy(photos = state.photos.filterNot { it.uri == uri })
    }

    override fun onCleared() {
        floodImageClassifier.close()
        super.onCleared()
    }

    fun setDescription(text: String) = _uiState.update { it.copy(description = text) }

    fun setSeverity(severity: FloodSeverity) = _uiState.update { it.copy(severity = severity) }

    @SuppressLint("MissingPermission")
    fun submit() {
        viewModelScope.launch {
            val photos = _uiState.value.photos
            if (photos.isEmpty()) {
                _uiState.update { it.copy(message = "A live camera photo is required before submitting.") }
                return@launch
            }
            if (photos.any { it.aiAnalysis?.canSubmit != true }) {
                _uiState.update { it.copy(message = "The photo must pass the flood image check before it can be submitted.") }
                return@launch
            }
            _uiState.update { it.copy(isSubmitting = true) }

            // Firebase Auth startup is asynchronous. Do not attempt Storage
            // or Firestore writes until we have a real authenticated user;
            // otherwise the backend sees request.auth == null and returns
            // PERMISSION_DENIED / Missing or insufficient permissions.
            val authenticatedUser = try {
                authRepository.ensureAuthenticated()
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        message = "Authentication is still starting. Please try Submit again."
                    )
                }
                return@launch
            }

            // The flood location is always the user-confirmed map pin.
            // The reporter GPS is stored separately as the place where the
            // person physically documented the flood.
            val location = _uiState.value.reportLocation
            if (location == null) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        message = "Couldn't get a location — tap the map to place the flood pin."
                    )
                }
                return@launch
            }

            // Every attached photo must upload successfully. A report with a
            // missing/failed mandatory photo is rejected rather than silently
            // submitting an incomplete report.
            val uploaded = coroutineScope {
                photos.map { photo ->
                    async {
                        runCatching {
                            val bytes = compressPhoto(photo.uri)
                            val ref = storage.reference.child("flood_photos/${UUID.randomUUID()}.jpg")
                            val metadata = StorageMetadata.Builder()
                                .setContentType("image/jpeg")
                                .build()
                            ref.putBytes(bytes, metadata).await()
                            ref.downloadUrl.await().toString() to photo.metadata
                        }
                    }
                }.awaitAll()
            }
            val failedUploads = uploaded.count { it.isFailure }
            if (failedUploads > 0) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        message = "Photo upload failed. Please retake the photo and try again."
                    )
                }
                return@launch
            }
            val photoUrls = uploaded.map { it.getOrThrow().first }
            val photoMetadata = uploaded.map { it.getOrThrow().second }

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

            val report = FloodReport(
                latitude = location.latitude(),
                longitude = location.longitude(),
                severity = _uiState.value.severity,
                timestamp = System.currentTimeMillis(),
                description = _uiState.value.description.trim(),
                photoUrls = photoUrls,
                photoMetadata = photoMetadata,
                areaStrokes = areaStrokes,
                status = FloodReport.STATUS_APPROVED,
                areaLabel = areaLabel,
                reporterLatitude = reporterLocation?.latitude(),
                reporterLongitude = reporterLocation?.longitude(),
                reporterAreaLabel = reporterAreaLabel,
                floodRadiusMeters = _uiState.value.floodRadiusMeters.toDouble(),
                reporterId = authenticatedUser.uid,
                reporterName = authenticatedUser.displayName.orEmpty(),
                reporterPhotoUrl = authenticatedUser.photoUrl?.toString().orEmpty()
            )
            floodRepository.submitReport(report)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submitted = true,
                            message = null
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
                photos = emptyList(),
                description = "",
                strokes = emptyList(),
                redoStack = emptyList(),
                pendingStrokes = emptyList(),
                drawMode = false,
                reportLocation = null,
                floodRadiusMeters = 40f,
                confirmBeforeSubmit = false
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
