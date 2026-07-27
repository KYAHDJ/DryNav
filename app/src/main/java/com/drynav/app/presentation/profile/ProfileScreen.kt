package com.drynav.app.presentation.profile

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drynav.app.domain.model.Mood
import com.drynav.app.presentation.components.CircleBackButton
import com.drynav.app.presentation.components.DryNavBottomBar
import com.drynav.app.presentation.components.PillButton
import com.drynav.app.presentation.music.MusicViewModel
import com.drynav.app.presentation.navigation.Routes
import com.drynav.app.presentation.tutorial.TutorialCelebrationOverlay
import com.drynav.app.presentation.tutorial.TutorialOverlay
import com.drynav.app.presentation.tutorial.TutorialViewModel
import com.drynav.app.presentation.tutorial.tutorialTarget
import com.drynav.app.presentation.theme.AccentGreen
import com.drynav.app.presentation.theme.LogoutRed
import com.drynav.app.presentation.theme.Mist
import com.drynav.app.presentation.theme.NavBlue
import com.drynav.app.presentation.theme.TealPrimary
import com.drynav.app.presentation.theme.TealTint
import com.drynav.app.presentation.theme.TextDark
import com.drynav.app.presentation.theme.TextGray
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
    var showEditName by rememberSaveable { mutableStateOf(false) }
    var showUpgrade by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    var showMoodPicker by rememberSaveable { mutableStateOf(false) }
    var showChangePassword by rememberSaveable { mutableStateOf(false) }
    var showDeleteAccount by rememberSaveable { mutableStateOf(false) }
    var showMusicSettings by rememberSaveable { mutableStateOf(false) }
    val tutorialManager = hiltViewModel<TutorialViewModel>().manager
    val musicManager = hiltViewModel<MusicViewModel>().manager

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                        val initials = uiState.displayName.trim()
                            .split(' ')
                            .filter { it.isNotBlank() }
                            .take(2)
                            .joinToString("") { it.first().uppercase() }
                            .ifBlank { "DN" }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(TealTint)
                        ) {
                            // Shows the chosen mood character (what other
                            // users see on the map) instead of plain initials.
                            val mood = uiState.mood
                            if (mood != null) {
                                Image(
                                    painter = painterResource(mood.iconRes),
                                    contentDescription = mood.label,
                                    modifier = Modifier.size(72.dp)
                                )
                            } else {
                                Text(
                                    initials,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = TealPrimary
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
                        onClick = { showEditName = true },
                        modifier = Modifier.tutorialTarget(tutorialManager, "profile_details_row")
                    )
                    SettingsRow(
                        icon = Icons.Outlined.EmojiEmotions,
                        iconTint = TealPrimary,
                        title = "Mood",
                        subtitle = uiState.mood?.label?.let { "Currently: $it" }
                            ?: "Pick the character other drivers see",
                        onClick = { showMoodPicker = true },
                        modifier = Modifier.tutorialTarget(tutorialManager, "mood_row")
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
                        icon = Icons.Outlined.MusicNote,
                        iconTint = Color(0xFF8B5CF6),
                        title = "Background Music",
                        subtitle = if (musicManager.enabled) {
                            "${(musicManager.volume * 100).toInt()}% — lowers while driving"
                        } else {
                            "Off"
                        },
                        onClick = { showMusicSettings = true }
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

        if (tutorialManager.isActive && tutorialManager.currentStep?.route == Routes.PROFILE) {
            TutorialOverlay(tutorialManager)
        }
        if (tutorialManager.showCelebration) {
            TutorialCelebrationOverlay(onDismiss = tutorialManager::dismissCelebration)
        }
    }

    if (showEditName) {
        var newName by rememberSaveable { mutableStateOf(uiState.displayName) }
        ProfileDialog(
            onDismissRequest = { showEditName = false },
            title = "Profile Details",
            primaryLabel = "Save",
            onPrimary = {
                viewModel.updateDisplayName(newName)
                showEditName = false
            },
            onSecondary = { showEditName = false }
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (uiState.email.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Email: ${uiState.email}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
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

    if (showMoodPicker) {
        MoodPickerDialog(
            selected = uiState.mood,
            onSelect = {
                viewModel.setMood(it)
                showMoodPicker = false
            },
            onDismiss = { showMoodPicker = false }
        )
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

    if (showMusicSettings) {
        MusicSettingsDialog(
            enabled = musicManager.enabled,
            volume = musicManager.volume,
            onSetEnabled = musicManager::updateEnabled,
            onSetVolume = musicManager::updateVolume,
            onDismiss = { showMusicSettings = false }
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

/** Grid-patterned square buttons — one per [Mood], picking replaces the map puck. */
@Composable
private fun MoodPickerDialog(
    selected: Mood?,
    onSelect: (Mood) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp)) {
                Text("Choose your mood", style = MaterialTheme.typography.headlineSmall, color = TextDark)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Other drivers using DryNav will see this character at your location.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                Spacer(Modifier.height(18.dp))
                Mood.entries.chunked(2).forEach { rowMoods ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowMoods.forEach { mood ->
                            val isSelected = mood == selected
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (isSelected) TealTint else Mist,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clickable { onSelect(mood) }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize().padding(10.dp)
                                ) {
                                    Image(
                                        painter = painterResource(mood.iconRes),
                                        contentDescription = mood.label,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        mood.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) TealPrimary else TextDark
                                    )
                                }
                            }
                        }
                        if (rowMoods.size < 2) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                }
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                )
            }
        }
    }
}

/** On/off + volume for the app's looping background music, plus how the driving-duck works. */
@Composable
private fun MusicSettingsDialog(
    enabled: Boolean,
    volume: Float,
    onSetEnabled: (Boolean) -> Unit,
    onSetVolume: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val playClick = com.drynav.app.presentation.sound.rememberClickSound()
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Background Music", style = MaterialTheme.typography.headlineSmall, color = TextDark)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Ambient music while you use DryNav.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { playClick(); onSetEnabled(it) }
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Volume", style = MaterialTheme.typography.labelLarge, color = TextDark)
                    Text(
                        "${(volume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = TealPrimary
                    )
                }
                Slider(
                    value = volume,
                    onValueChange = onSetVolume,
                    enabled = enabled,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = TealPrimary,
                        activeTrackColor = TealPrimary
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Automatically lowers to about 30% while you're actively navigating, so voice directions stay clear.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                Spacer(Modifier.height(20.dp))
                PillButton(
                    text = "Done",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
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
    val playClick = com.drynav.app.presentation.sound.rememberClickSound()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = { playClick(); onClick() }) else Modifier)
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
