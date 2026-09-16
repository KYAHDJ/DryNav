package com.drynav.app.presentation.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drynav.app.data.auth.AuthRepository
import com.drynav.app.data.prefs.UserPreferences
import com.drynav.app.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val navigateTo: String? = null,
    /** Bumped on every wrong OTP submission so the screen can shake/highlight the boxes. */
    val otpErrorSignal: Int = 0
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val prefs: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val pendingPhone: String? get() = authRepository.pendingPhone

    /** Where the splash screen should land. */
    suspend fun resolveStartRoute(): String = when {
        !prefs.onboardingSeen.first() -> Routes.ONBOARDING
        authRepository.isLoggedIn -> Routes.HOME
        else -> Routes.LOGIN
    }

    fun markOnboardingSeen() {
        viewModelScope.launch { prefs.setOnboardingSeen() }
    }

    // ------------------------------------------------------------------
    // Login
    // ------------------------------------------------------------------

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(message = "Please enter your email and password.") }
            return
        }
        launchAuth {
            authRepository.login(email.trim(), password)
                .onSuccess { navigate(Routes.GET_STARTED) }
                .onFailure { fail("Login failed", it) }
        }
    }

    fun forgotPassword(email: String) {
        if (email.isBlank()) {
            _uiState.update { it.copy(message = "Type your email above first, then tap Forgot Password.") }
            return
        }
        launchAuth {
            authRepository.sendPasswordReset(email.trim())
                .onSuccess {
                    _uiState.update {
                        it.copy(isLoading = false, message = "Password reset email sent to ${email.trim()}.")
                    }
                }
                .onFailure { fail("Couldn't send reset email", it) }
        }
    }

    // ------------------------------------------------------------------
    // Register + demo OTP
    // ------------------------------------------------------------------

    fun register(name: String, email: String, password: String, confirm: String, phone: String) {
        val error = when {
            name.isBlank() -> "Please enter your name."
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Please enter a valid email address."
            password.length < 6 -> "Password must be at least 6 characters."
            password != confirm -> "Passwords do not match."
            phone.filter(Char::isDigit).length < 10 -> "Please enter a valid phone number."
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(message = error) }
            return
        }
        launchAuth {
            authRepository.register(name.trim(), email.trim(), password)
                .onSuccess {
                    sendOtp(phone)
                    navigate(Routes.OTP)
                }
                .onFailure { fail("Registration failed", it) }
        }
    }

    fun resendOtp() {
        val phone = authRepository.pendingPhone
        if (phone == null) {
            _uiState.update { it.copy(message = "No pending verification. Please register again.") }
            return
        }
        sendOtp(phone)
    }

    private fun sendOtp(phone: String) {
        val fallbackCode = authRepository.startOtpVerification(phone)
        _uiState.update {
            it.copy(
                isLoading = false,
                message = if (fallbackCode == null) {
                    "Verification code sent — check your notifications."
                } else {
                    // Notifications are blocked on this device; surface the code here.
                    "Notifications are off — your verification code is $fallbackCode"
                }
            )
        }
    }

    fun verifyOtp(code: String) {
        if (authRepository.verifyOtp(code)) {
            authRepository.clearOtp()
            navigate(Routes.OTP_SUCCESS)
        } else {
            _uiState.update { it.copy(otpErrorSignal = it.otpErrorSignal + 1) }
        }
    }

    // ------------------------------------------------------------------
    // Google
    // ------------------------------------------------------------------

    fun signInWithGoogle(idToken: String) {
        launchAuth {
            authRepository.signInWithGoogle(idToken)
                .onSuccess { navigate(Routes.GET_STARTED) }
                .onFailure { fail("Google sign-in failed", it) }
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private fun launchAuth(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            block()
        }
    }

    private fun navigate(route: String) {
        _uiState.update { it.copy(isLoading = false, navigateTo = route) }
    }

    private fun fail(prefix: String, e: Throwable) {
        _uiState.update {
            it.copy(isLoading = false, message = "$prefix: ${e.localizedMessage ?: "unknown error"}")
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    fun consumeNavigation() = _uiState.update { it.copy(navigateTo = null) }
}
