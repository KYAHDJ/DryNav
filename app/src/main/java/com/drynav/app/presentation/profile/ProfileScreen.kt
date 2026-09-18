package com.drynav.app.presentation.profile

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.drynav.app.presentation.components.CircleBackButton
import com.drynav.app.presentation.components.DryNavBottomBar
import com.drynav.app.presentation.components.PillButton
import com.drynav.app.presentation.navigation.Routes
import com.drynav.app.presentation.tutorial.TutorialCelebrationOverlay
import com.drynav.app.presentation.tutorial.TutorialOverlay
import com.drynav.app.presentation.tutorial.TutorialViewModel
import com.drynav.app.presentation.tutorial.tutorialTarget
import com.drynav.app.presentation.theme.AccentGreen
import com.drynav.app.presentation.theme.LogoutRed
import com.drynav.app.presentation.theme.NavBlue
import com.drynav.app.presentation.theme.TealPrimary
import com.drynav.app.presentation.theme.TealTint
import com.drynav.app.presentation.theme.TextGray
import com.drynav.app.presentation.theme.TextDark
import com.drynav.app.presentation.theme.WarnYellow
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun ProfileScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showUpgrade by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    var showChangePassword by rememberSaveable { mutableStateOf(false) }
    var showDeleteAccount by rememberSaveable { mutableStateOf(false) }
    val tutorialManager = hiltViewModel<TutorialViewModel>().manager

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(uiState.loggedOut) {
        if (uiState.loggedOut) onLoggedOut()
    }
    LaunchedEffect(uiState.accountDeleted) {
        if (uiState.accountDeleted) onLoggedOut()
    }

    // Reauth via a fresh Google sign-in, only used right before deleting a
    // Google-linked account (Firebase requires a recent credential to delete).
    val googleReauthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                viewModel.deleteAccountWithGoogleIdToken(idToken)
            }
        } catch (_: ApiException) {
            // Cancelled or failed — the delete simply doesn't proceed.
        }
    }
    fun launchGoogleReauthAndDelete() {
        val resId = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName
        )
        if (resId == 0) return
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(resId))
            .build()
        val client = GoogleSignIn.getClient(context as Activity, options)
        googleReauthLauncher.launch(client.signInIntent)
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            DryNavBottomBar(currentRoute = Routes.PROFILE, onNavigate = onNavigate)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .tutorialTarget(tutorialManager, "profile_page")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
            // ---- Flat header: circular back button + left-aligned title ----
            Column(Modifier.statusBarsPadding()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    CircleBackButton(onClick = onBack)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "PROFILE",
                        style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // ---- Avatar card: teal-tint circle with the user's initials ----
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 24.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(96.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(TealTint)
                            ) {
                                if (!uiState.photoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = uiState.photoUrl,
                                        contentDescription = "Profile picture",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Person,
                                        contentDescription = "User profile",
                                        tint = TealPrimary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                            Surface(
                                onClick = { onNavigate(Routes.EDIT_PROFILE) },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "Edit profile",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(9.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            uiState.displayName.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // ---- Settings ----
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        "Profile Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))

                    SettingsRow(
                        icon = Icons.Outlined.Person,
                        iconTint = NavBlue,
                        title = "Profile Details",
                        subtitle = "Click here to edit your profile details",
                        onClick = { onNavigate(Routes.EDIT_PROFILE) },
                    )
                    // A Google-linked account has no DryNav-managed password
                    // to change — this row simply doesn't exist for it.
                    if (!uiState.isGoogleAccount) {
                        SettingsRow(
                            icon = Icons.Outlined.GppGood,
                            iconTint = AccentGreen,
                            title = "Password",
                            subtitle = "Click here to change your password",
                            onClick = { showChangePassword = true }
                        )
                    }
                    SettingsRow(
                        icon = Icons.Outlined.WarningAmber,
                        iconTint = WarnYellow,
                        title = "Notifications",
                        subtitle = "Click here to manage notifications alert",
                        trailing = {
                            Switch(
                                checked = uiState.notificationsEnabled,
                                onCheckedChange = viewModel::setNotificationsEnabled
                            )
                        }
                    )
                    SettingsRow(
                        icon = Icons.Outlined.DarkMode,
                        iconTint = Color(0xFF5C6BC0),
                        title = "Dark Mode",
                        subtitle = "Click here to change view preference",
                        trailing = {
                            Switch(
                                checked = uiState.darkMode,
                                onCheckedChange = viewModel::setDarkMode
                            )
                        }
                    )
                    SettingsRow(
                        icon = Icons.Outlined.WorkspacePremium,
                        iconTint = TealPrimary,
                        title = "Upgrade Your Plan",
                        subtitle = "Click here to Unlock the full experience",
                        onClick = { showUpgrade = true }
                    )
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        iconTint = LogoutRed,
                        title = "Log out",
                        subtitle = "Click here to go back to log in page",
                        onClick = { showLogoutConfirm = true }
                    )
                    SettingsRow(
                        icon = Icons.Filled.DeleteForever,
                        iconTint = LogoutRed,
                        title = "Delete Account",
                        subtitle = "Permanently delete your account and data",
                        onClick = { showDeleteAccount = true }
                    )
                }
            }
            }
        }
    }

        if (tutorialManager.isActive && tutorialManager.currentStep?.route == Routes.PROFILE) {
            TutorialOverlay(tutorialManager)
        }
        if (tutorialManager.showCelebration) {
            TutorialCelebrationOverlay(onDismiss = tutorialManager::dismissCelebration)
        }
    }

    if (showUpgrade) {
        ProfileDialog(
            onDismissRequest = { showUpgrade = false },
            title = "Upgrade Your Plan",
            primaryLabel = "OK",
            onPrimary = { showUpgrade = false },
            onSecondary = null
        ) {
            Text(
                "Premium plans are coming soon. Stay tuned!",
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray
            )
        }
    }

    if (showLogoutConfirm) {
        ProfileDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = "Log out",
            primaryLabel = "Log out",
            primaryColor = LogoutRed,
            onPrimary = {
                showLogoutConfirm = false
                viewModel.logout()
            },
            onSecondary = { showLogoutConfirm = false }
        ) {
            Text(
                "Are you sure you want to log out?",
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray
            )
        }
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            isSaving = uiState.isChangingPassword,
            onConfirm = { current, new ->
                viewModel.changePassword(current, new)
                showChangePassword = false
            },
            onForgotPassword = viewModel::sendPasswordReset,
            onDismiss = { showChangePassword = false }
        )
    }



    if (showDeleteAccount) {
        DeleteAccountDialog(
            isGoogleAccount = uiState.isGoogleAccount,
            isDeleting = uiState.isDeletingAccount,
            onConfirmWithPassword = { password ->
                viewModel.deleteAccountWithPassword(password)
            },
            onConfirmWithGoogle = {
                showDeleteAccount = false
                launchGoogleReauthAndDelete()
            },
            onDismiss = { showDeleteAccount = false }
        )
    }
}

/** Current + new password, in-app — email/password accounts only (see [ProfileUiState.isGoogleAccount]). */
@Composable
private fun ChangePasswordDialog(
    isSaving: Boolean,
    onConfirm: (current: String, new: String) -> Unit,
    onForgotPassword: () -> Unit,
    onDismiss: () -> Unit
) {
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var confirmNew by rememberSaveable { mutableStateOf("") }
    val mismatch = confirmNew.isNotEmpty() && new != confirmNew

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp)) {
                Text("Change Password", style = MaterialTheme.typography.headlineSmall, color = TextDark)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text("Current password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = new,
                    onValueChange = { new = it },
                    label = { Text("New password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirmNew,
                    onValueChange = { confirmNew = it },
                    label = { Text("Confirm new password") },
                    singleLine = true,
                    isError = mismatch,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                if (mismatch) {
                    Text(
                        "Passwords don't match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LogoutRed,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Forgot your current password?",
                    style = MaterialTheme.typography.labelMedium,
                    color = TealPrimary,
                    modifier = Modifier.clickable(onClick = onForgotPassword)
                )
                Spacer(Modifier.height(18.dp))
                PillButton(
                    text = "Change password",
                    onClick = { onConfirm(current, new) },
                    enabled = !isSaving && current.isNotBlank() && new.length >= 6 && !mismatch,
                    loading = isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSaving, onClick = onDismiss)
                )
            }
        }
    }
}

/** Destructive confirm — typed phrase, plus a password (email accounts) or a Google reauth step. */
@Composable
private fun DeleteAccountDialog(
    isGoogleAccount: Boolean,
    isDeleting: Boolean,
    onConfirmWithPassword: (String) -> Unit,
    onConfirmWithGoogle: () -> Unit,
    onDismiss: () -> Unit
) {
    var typed by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val phraseOk = typed == "DELETE"

    Dialog(onDismissRequest = { if (!isDeleting) onDismiss() }) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp)) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = LogoutRed)
                Spacer(Modifier.height(12.dp))
                Text("Delete account?", style = MaterialTheme.typography.headlineSmall, color = TextDark)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This permanently deletes your DryNav account and signs you out. " +
                        "This can't be undone.\n\nType \"DELETE\" to confirm:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    enabled = !isDeleting,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isGoogleAccount) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Current password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !isDeleting,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(22.dp))
                PillButton(
                    text = if (isGoogleAccount) "Continue with Google to delete" else "Delete account",
                    onClick = {
                        if (isGoogleAccount) onConfirmWithGoogle() else onConfirmWithPassword(password)
                    },
                    containerColor = LogoutRed,
                    loading = isDeleting,
                    enabled = !isDeleting && phraseOk && (isGoogleAccount || password.isNotBlank()),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isDeleting, onClick = onDismiss)
                )
            }
        }
    }
}

/**
 * Shared dialog shell for Profile's popups — white rounded card with a
 * PillButton primary action, matching the design system used everywhere
 * else instead of a bare default AlertDialog.
 */
@Composable
private fun ProfileDialog(
    onDismissRequest: () -> Unit,
    title: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryColor: Color = TealPrimary,
    onSecondary: (() -> Unit)?,
    secondaryLabel: String = "Cancel",
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = TextDark)
                Spacer(Modifier.height(16.dp))
                content()
                Spacer(Modifier.height(22.dp))
                PillButton(
                    text = primaryLabel,
                    onClick = onPrimary,
                    containerColor = primaryColor,
                    modifier = Modifier.fillMaxWidth()
                )
                if (onSecondary != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        secondaryLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSecondary)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
        Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
    }
}
