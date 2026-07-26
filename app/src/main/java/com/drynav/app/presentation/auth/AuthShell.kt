package com.drynav.app.presentation.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.drynav.app.presentation.components.CircleBackButton
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

/**
 * Shared scaffold for Login / Register / OTP / OTP-success screens: flat
 * white background, an optional back arrow, everything else scrollable
 * below it. Matches the approved visual-refresh mockup — no persistent
 * sky-gradient hero on every auth screen.
 */
@Composable
fun AuthShell(
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                CircleBackButton(onClick = onBack)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Shared "Continue with Google" launcher for Login + Register: looks up
 * `default_web_client_id` at runtime (it only exists once Google sign-in is
 * enabled in the Firebase console) and shows a snackbar if the provider
 * isn't configured, instead of crashing.
 */
@Composable
fun rememberGoogleSignInLauncher(viewModel: AuthViewModel): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                viewModel.signInWithGoogle(idToken)
            } else {
                viewModel.showMessage("Google sign-in returned no credential.")
            }
        } catch (e: ApiException) {
            viewModel.showMessage("Google sign-in cancelled or failed (code ${e.statusCode}).")
        }
    }
    return {
        val resId = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName
        )
        if (resId == 0) {
            viewModel.showMessage(
                "Enable the Google sign-in provider in the Firebase console first."
            )
        } else {
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(resId))
                .requestEmail()
                .build()
            val client = GoogleSignIn.getClient(context as Activity, options)
            client.signOut() // always show the account picker
            launcher.launch(client.signInIntent)
        }
    }
}
