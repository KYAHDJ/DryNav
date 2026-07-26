package com.drynav.app.data.auth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.drynav.app.R
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Firebase-backed authentication.
 *
 * OTP note: this app uses a demo OTP flow — a 4-digit code is generated on the
 * device and delivered as a notification (standing in for an SMS). Swap
 * [startOtpVerification]/[verifyOtp] for Firebase Phone Auth to go production.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) {
    val currentUser: FirebaseUser? get() = auth.currentUser
    val isLoggedIn: Boolean get() = auth.currentUser != null

    /**
     * Hardcoded admin accounts for flood-report moderation. Good enough for a
     * thesis-scale deployment; a real rollout would move this to a Firestore
     * custom-claims / roles collection instead.
     */
    val isAdmin: Boolean
        get() = currentUser?.email?.let { email ->
            ADMIN_EMAILS.any { it.equals(email, ignoreCase = true) }
        } == true

    /**
     * True when Google is (one of) this account's sign-in providers. Drives
     * whether "Change Password" is offered at all — a Google-only account
     * has no DryNav-managed password to change.
     */
    val isGoogleAccount: Boolean
        get() = currentUser?.providerData
            ?.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } == true

    var pendingPhone: String? = null
        private set
    private var pendingOtp: String? = null

    /** Set when notifications were blocked and the code must be shown in-app. */
    var lastFallbackCode: String? = null
        private set

    // ------------------------------------------------------------------
    // Email / password
    // ------------------------------------------------------------------

    suspend fun register(name: String, email: String, password: String): Result<Unit> =
        runCatching {
            auth.createUserWithEmailAndPassword(email, password).await()
            auth.currentUser
                ?.updateProfile(userProfileChangeRequest { displayName = name })
                ?.await()
            Unit
        }

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    suspend fun signInWithGoogle(idToken: String): Result<Unit> = runCatching {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        Unit
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    fun signOut() = auth.signOut()

    // ------------------------------------------------------------------
    // Demo OTP
    // ------------------------------------------------------------------

    /**
     * Generates a fresh 4-digit code for [phone] and posts it as a
     * notification. Returns the code when notifications are blocked so the
     * UI can surface it another way, or null when the notification was shown.
     */
    fun startOtpVerification(phone: String): String? {
        pendingPhone = phone
        val code = "%04d".format(Random.nextInt(0, 10_000))
        pendingOtp = code
        lastFallbackCode = if (postOtpNotification(code)) null else code
        return lastFallbackCode
    }

    fun verifyOtp(input: String): Boolean = input.length == 4 && input == pendingOtp

    fun clearOtp() {
        pendingOtp = null
        pendingPhone = null
        lastFallbackCode = null
    }

    private fun postOtpNotification(code: String): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        manager.createNotificationChannel(
            NotificationChannel(
                OTP_CHANNEL_ID,
                "Verification codes",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        if (!manager.areNotificationsEnabled()) return false
        val notification = NotificationCompat.Builder(context, OTP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_drynav_logo)
            .setContentTitle("DryNav verification")
            .setContentText("Your DryNav verification code is $code")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(OTP_NOTIFICATION_ID, notification)
        return true
    }

    // ------------------------------------------------------------------
    // Profile
    // ------------------------------------------------------------------

    suspend fun updateDisplayName(name: String): Result<Unit> = runCatching {
        auth.currentUser
            ?.updateProfile(userProfileChangeRequest { displayName = name })
            ?.await()
        Unit
    }

    // ------------------------------------------------------------------
    // Change password / delete account
    // ------------------------------------------------------------------

    /** Email/password accounts only — reauthenticates with the current password first. */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        runCatching {
            val user = auth.currentUser ?: error("Not signed in")
            val email = user.email ?: error("No email on this account")
            user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
            user.updatePassword(newPassword).await()
            Unit
        }

    /** Email/password account deletion — reauthenticates with the current password first. */
    suspend fun deleteAccountWithPassword(password: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in")
        val email = user.email ?: error("No email on this account")
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        user.delete().await()
        Unit
    }

    /** Google account deletion — reauthenticates with a freshly obtained Google ID token first. */
    suspend fun deleteAccountWithGoogleIdToken(idToken: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in")
        user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
        user.delete().await()
        Unit
    }

    private companion object {
        const val OTP_CHANNEL_ID = "drynav_otp"
        const val OTP_NOTIFICATION_ID = 1001
        val ADMIN_EMAILS = setOf("kyaiko.dev@gmail.com", "qdjgbenitez@tip.edu.ph")
    }
}
