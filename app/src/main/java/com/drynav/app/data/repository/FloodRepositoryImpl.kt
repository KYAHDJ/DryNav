package com.drynav.app.data.repository

import com.drynav.app.domain.model.FloodReport
import com.drynav.app.domain.repository.FloodRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloodRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : FloodRepository {

    private val collection
        get() = firestore.collection(FloodReport.FIRESTORE_COLLECTION)

    override suspend fun checkReportRateLimit(report: FloodReport): Result<Unit> = runCatching {
        if (report.reporterId.isBlank()) return@runCatching
        val cutoff = System.currentTimeMillis() - FloodReport.SAME_REPORT_COOLDOWN_MS
        val recent = collection
            .whereEqualTo("reporterId", report.reporterId)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                val timestamp = (doc.get("timestamp") as? Number)?.toLong() ?: return@mapNotNull null
                if (timestamp < cutoff) return@mapNotNull null
                val lat = (doc.get("latitude") as? Number)?.toDouble() ?: return@mapNotNull null
                val lng = (doc.get("longitude") as? Number)?.toDouble() ?: return@mapNotNull null
                Triple(timestamp, lat, lng)
            }
        val duplicate = recent.firstOrNull { (_, lat, lng) ->
            TurfMeasurement.distance(
                Point.fromLngLat(report.longitude, report.latitude),
                Point.fromLngLat(lng, lat),
                TurfConstants.UNIT_METERS
            ) <= FloodReport.SAME_REPORT_RADIUS_METERS
        }
        if (duplicate != null) {
            throw IllegalStateException(FloodReport.DUPLICATE_REPORT_MESSAGE)
        }
    }

    override suspend fun submitReport(report: FloodReport): Result<String> = runCatching {
        checkReportRateLimit(report).getOrThrow()
        val doc = collection.document() // pre-generate id
        doc.set(report.copy(id = doc.id).toFirestoreMap()).await()
        doc.id
    }

    override fun getLiveFloodReports(): Flow<List<FloodReport>> = callbackFlow {
        val cutoff = System.currentTimeMillis() - FloodReport.MAX_REPORT_AGE_MS

        val registration = collection
            .whereEqualTo("isCleared", false)
            .whereGreaterThan("timestamp", cutoff)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Don't crash the app on a Firestore error — just show no floods
                    // and let the UI's snackbar (from the .catch{} in the ViewModel) surface it.
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val documents = snapshot?.documents.orEmpty()
                val expired = documents.filter {
                    val expiresAt = (it.get("expiresAt") as? Number)?.toLong()
                    expiresAt != null && expiresAt <= System.currentTimeMillis()
                }
                if (expired.isNotEmpty()) {
                    // Best-effort client-side cleanup. For guaranteed deletion
                    // even when nobody opens the app, configure Firestore TTL
                    // on flood_reports.expiresAt in the Firebase console.
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        expired.chunked(400).forEach { chunk ->
                            val batch = firestore.batch()
                            chunk.forEach { batch.delete(it.reference) }
                            runCatching { batch.commit().await() }
                        }
                    }
                }
                val reports = documents.mapNotNull { doc ->
                    doc.data?.let { FloodReport.fromFirestore(doc.id, it) }
                }
                trySend(reports)
            }

        awaitClose { registration.remove() }
    }.conflate()

    override suspend fun upvoteReport(reportId: String): Result<Unit> = runCatching {
        collection.document(reportId)
            .update("upvotes", FieldValue.increment(1))
            .await()
    }

    override suspend fun deleteReport(reportId: String, reporterId: String): Result<Unit> = runCatching {
        require(reporterId.isNotBlank()) { "Missing report owner." }
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: error("You must be signed in to delete a report.")
        require(userId == reporterId) { "You can only delete your own report." }

        val doc = collection.document(reportId).get().await()
        require(doc.exists()) { "This flood report no longer exists." }
        val storedReporterId = doc.getString("reporterId").orEmpty()
        require(storedReporterId == userId) { "You can only delete your own report." }

        // Delete the public Firestore post. The associated Storage photo is
        // intentionally retained for now because legacy reports may use
        // different storage paths and the current Storage rules do not safely
        // associate those paths with a report owner.
        doc.reference.delete().await()
    }

}
