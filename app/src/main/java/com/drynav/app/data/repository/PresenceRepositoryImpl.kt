package com.drynav.app.data.repository

import com.drynav.app.data.auth.AuthRepository
import com.drynav.app.domain.model.Presence
import com.drynav.app.domain.repository.PresenceRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.mapbox.geojson.Point
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresenceRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) : PresenceRepository {

    private val collection
        get() = firestore.collection(Presence.FIRESTORE_COLLECTION)

    override suspend fun publishPresence(mood: String, point: Point): Result<Unit> = runCatching {
        val uid = authRepository.currentUser?.uid ?: return@runCatching
        val presence = Presence(
            userId = uid,
            displayName = authRepository.currentUser?.displayName.orEmpty(),
            mood = mood,
            latitude = point.latitude(),
            longitude = point.longitude(),
            updatedAt = System.currentTimeMillis()
        )
        // One doc per user, overwritten in place — never an append-only log.
        collection.document(uid).set(presence.toFirestoreMap()).await()
    }

    override suspend fun clearPresence(): Result<Unit> = runCatching {
        val uid = authRepository.currentUser?.uid ?: return@runCatching
        collection.document(uid).delete().await()
    }

    override fun observeOthers(): Flow<List<Presence>> = callbackFlow {
        val selfId = authRepository.currentUser?.uid
        val registration = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val others = snapshot?.documents.orEmpty()
                .mapNotNull { doc -> doc.data?.let { Presence.fromFirestore(doc.id, it) } }
                .filter { it.userId != selfId && !it.isStale && it.mood.isNotBlank() }
            trySend(others)
        }
        awaitClose { registration.remove() }
    }.conflate()
}
