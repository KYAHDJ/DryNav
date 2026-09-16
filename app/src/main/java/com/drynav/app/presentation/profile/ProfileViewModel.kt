package com.drynav.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drynav.app.data.auth.AuthRepository
import com.drynav.app.data.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val notificationsEnabled: Boolean = true,
    val darkMode: Boolean = false,
    val message: String? = null,
    val loggedOut: Boolean = false,
    /** Google-linked accounts have no DryNav-managed password — hide "Change Password" entirely. */
    val isGoogleAccount: Boolean = false,
    val isChangingPassword: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val accountDeleted: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val prefs: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refreshUser()
        viewModelScope.launch {
            prefs.notificationsEnabled.collect { enabled ->
                _uiState.update { it.copy(notificationsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            prefs.darkMode.collect { dark ->
                _uiState.update { it.copy(darkMode = dark) }
            }
        }
    }

    private fun refreshUser() {
        val user = authRepository.currentUser
        _uiState.update {
            it.copy(
                displayName = user?.displayName ?: "DryNav User",
                email = user?.email.orEmpty(),
                photoUrl = user?.photoUrl?.toString(),
                isGoogleAccount = authRepository.isGoogleAccount
            )
        }
    }

    fun updateDisplayName(name: String) {
        if (name.isBlank()) {
            _uiState.update { it.copy(message = "Name can't be empty.") }
            return
        }
        viewModelScope.launch {
            authRepository.updateDisplayName(name.trim())
                .onSuccess {
                    refreshUser()
                    _uiState.update { it.copy(message = "Profile updated.") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(message = "Update failed: ${e.localizedMessage}") }
                }
        }
    }

    fun sendPasswordReset() {
        val email = authRepository.currentUser?.email
        if (email.isNullOrBlank()) {
            _uiState.update { it.copy(message = "No email on this account.") }
            return
        }
        viewModelScope.launch {
            authRepository.sendPasswordReset(email)
                .onSuccess {
                    _uiState.update { it.copy(message = "Password reset email sent to $email.") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(message = "Couldn't send reset email: ${e.localizedMessage}") }
                }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        _uiState.update { it.copy(isChangingPassword = true) }
        viewModelScope.launch {
            authRepository.changePassword(currentPassword, newPassword)
                .onSuccess {
                    _uiState.update {
                        it.copy(isChangingPassword = false, message = "Password changed.")
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            message = "Couldn't change password: ${e.localizedMessage}"
                        )
                    }
                }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setNotificationsEnabled(enabled) }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { prefs.setDarkMode(enabled) }
    }


    fun deleteAccountWithPassword(password: String) {
        _uiState.update { it.copy(isDeletingAccount = true) }
        viewModelScope.launch {
            authRepository.deleteAccountWithPassword(password)
                .onSuccess { _uiState.update { it.copy(isDeletingAccount = false, accountDeleted = true) } }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isDeletingAccount = false,
                            message = "Couldn't delete account: ${e.localizedMessage}"
                        )
                    }
                }
        }
    }

    fun deleteAccountWithGoogleIdToken(idToken: String) {
        _uiState.update { it.copy(isDeletingAccount = true) }
        viewModelScope.launch {
            authRepository.deleteAccountWithGoogleIdToken(idToken)
                .onSuccess { _uiState.update { it.copy(isDeletingAccount = false, accountDeleted = true) } }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isDeletingAccount = false,
                            message = "Couldn't delete account: ${e.localizedMessage}"
                        )
                    }
                }
        }
    }

    fun logout() {
        authRepository.signOut()
        _uiState.update { it.copy(loggedOut = true) }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
