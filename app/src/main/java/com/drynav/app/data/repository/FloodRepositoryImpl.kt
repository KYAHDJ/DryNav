package com.drynav.app.data.repository

import com.drynav.app.domain.model.FloodReport
import com.drynav.app.domain.repository.FloodRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
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

    override suspend fun submitReport(report: FloodReport): Result<String> = runCatching {
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
                val reports = snapshot?.documents.orEmpty().mapNotNull { doc ->
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

    override suspend fun deleteReport(report: FloodReport): Result<Unit> = runCatching {
        collection.document(report.id).delete().await()
        // Best-effort photo cleanup — the report is already gone either way,
        // and every photo (not just one) must go with it so nothing orphans.
        report.photoUrls.forEach { url ->
            runCatching { storage.getReferenceFromUrl(url).delete().await() }
        }
    }

    override suspend fun setReportStatus(reportId: String, status: String): Result<Unit> =
        runCatching {
            collection.document(reportId).update("status", status).await()
        }

    override suspend fun deleteAllReports(): Result<Unit> = runCatching {
        val snapshot = collection.get().await()
        // Firestore batches cap at 500 writes; chunk to stay well under that.
        snapshot.documents.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()
        }
        // Best-effort photo cleanup for every deleted report — every photo,
        // plus the legacy single-photo field from before multi-photo support.
        snapshot.documents.forEach { doc ->
            val urls = (doc.get("photoUrls") as? List<*>)?.filterIsInstance<String>()
                ?: doc.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { listOf(it) }
                ?: emptyList()
            urls.forEach { url ->
                runCatching { storage.getReferenceFromUrl(url).delete().await() }
            }
        }
    }
}
