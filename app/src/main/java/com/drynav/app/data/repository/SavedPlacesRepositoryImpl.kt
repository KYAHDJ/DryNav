package com.drynav.app.data.repository

import com.drynav.app.data.auth.AuthRepository
import com.drynav.app.domain.model.SavedPlace
import com.google.firebase.firestore.FirebaseFirestore
import com.drynav.app.domain.repository.SavedPlacesRepository
import com.mapbox.geojson.Point
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedPlacesRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) : SavedPlacesRepository {

    private val collection
        get() = firestore.collection(SavedPlace.FIRESTORE_COLLECTION)

    override fun getSavedPlaces(): Flow<List<SavedPlace>> {
        val uid = authRepository.currentUser?.uid ?: return flowOf(emptyList())
        return callbackFlow {
            val registration = collection
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val places = snapshot?.documents.orEmpty()
                        .mapNotNull { doc -> doc.data?.let { SavedPlace.fromFirestore(doc.id, it) } }
                    trySend(places)
                }
            awaitClose { registration.remove() }
        }.conflate()
    }

    override suspend fun savePlace(label: String, address: String, point: Point): Result<Unit> =
        runCatching {
            val uid = authRepository.currentUser?.uid ?: error("Not signed in")
            val doc = collection.document()
            val place = SavedPlace(
                id = doc.id,
                userId = uid,
                label = label,
                address = address,
                latitude = point.latitude(),
                longitude = point.longitude()
            )
            doc.set(place.toFirestoreMap()).await()
        }

    override suspend fun deletePlace(placeId: String): Result<Unit> = runCatching {
        collection.document(placeId).delete().await()
    }
}
