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

}
