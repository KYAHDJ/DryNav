package com.drynav.app.presentation.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drynav.app.data.auth.AuthRepository
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject

data class EditProfileUiState(
    val displayName: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val selectedPhoto: Uri? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val storage: FirebaseStorage,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    init {
        val user = authRepository.currentUser
        _uiState.update {
            it.copy(
                displayName = user?.displayName.orEmpty(),
                email = user?.email.orEmpty(),
                phone = user?.phoneNumber.orEmpty(),
                photoUrl = user?.photoUrl?.toString()
            )
        }
        user?.uid?.let { uid ->
            viewModelScope.launch {
                val data = runCatching { firestore.collection("users").document(uid).get().await() }.getOrNull()?.data
                if (data != null) _uiState.update { state -> state.copy(
                    firstName = (data["firstName"] as? String).orEmpty(),
                    lastName = (data["lastName"] as? String).orEmpty(),
                    phone = (data["phone"] as? String).orEmpty().ifBlank { state.phone }
                ) }
            }
        }
    }

    fun setDisplayName(value: String) = _uiState.update { it.copy(displayName = value) }
    fun setFirstName(value: String) = _uiState.update { it.copy(firstName = value) }
    fun setLastName(value: String) = _uiState.update { it.copy(lastName = value) }
    fun setPhone(value: String) = _uiState.update { it.copy(phone = value) }

    fun setSelectedPhoto(uri: Uri) = _uiState.update { it.copy(selectedPhoto = uri) }

    fun save() {
        val state = _uiState.value
        if (state.displayName.isBlank()) {
            _uiState.update { it.copy(message = "Name can't be empty.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            runCatching {
                val photoUrl = state.selectedPhoto?.let { uploadProfilePhoto(it) } ?: state.photoUrl
                authRepository.updateProfile(state.displayName, photoUrl, state.firstName, state.lastName, state.phone).getOrThrow()
                photoUrl
            }.onSuccess { savedPhotoUrl ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saved = true,
                        photoUrl = savedPhotoUrl,
                        selectedPhoto = null,
                        message = "Profile updated."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isSaving = false, message = "Couldn't update profile: ${error.localizedMessage}")
                }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private suspend fun uploadProfilePhoto(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = compressPhoto(uri)
        val uid = authRepository.currentUser?.uid ?: error("No signed-in user")
        val ref = storage.reference.child("profile_photos/$uid-${UUID.randomUUID()}.jpg")
        ref.putBytes(bytes).await()
        ref.downloadUrl.await().toString()
    }

    private fun compressPhoto(uri: Uri): ByteArray {
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error("Couldn't read the selected photo")
        val maxDimension = 720
        val scale = maxDimension.toFloat() / maxOf(decoded.width, decoded.height)
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else decoded
        return ByteArrayOutputStream().use { out ->
            resized.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
    }
}
