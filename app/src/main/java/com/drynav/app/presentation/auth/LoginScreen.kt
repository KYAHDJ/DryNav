package com.drynav.app.presentation.auth

import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drynav.app.R
import com.drynav.app.presentation.components.DryNavTextField
import com.drynav.app.presentation.components.PillButton
import com.drynav.app.presentation.theme.CardStroke
import com.drynav.app.presentation.theme.TealPrimary
import com.drynav.app.presentation.theme.TextDark
import com.drynav.app.presentation.theme.TextGray
import androidx.compose.ui.graphics.Color

@Composable
fun LoginScreen(
    onNavigate: (String) -> Unit,
    onGoToRegister: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var emailError by rememberSaveable { mutableStateOf(false) }
    var passwordError by rememberSaveable { mutableStateOf(false) }
    var emailShake by rememberSaveable { mutableIntStateOf(0) }
    var passwordShake by rememberSaveable { mutableIntStateOf(0) }
    val launchGoogleSignIn = rememberGoogleSignInLauncher(viewModel)

    fun submitLogin() {
        val emailValid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
        val passwordValid = password.isNotBlank()
        emailError = !emailValid
        passwordError = !passwordValid
        if (!emailValid) emailShake++
        if (!passwordValid) passwordShake++
        if (!emailValid || !passwordValid) return
        viewModel.login(email.trim(), password)
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(uiState.navigateTo) {
        uiState.navigateTo?.let {
            viewModel.consumeNavigation()
            onNavigate(it)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            AuthShell {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "LOGIN",
                    style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 1.sp),
                    color = TextDark,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                DryNavTextField(
                    value = email,
                    onValueChange = { email = it; emailError = false },
                    placeholder = "Email",
                    leadingIcon = Icons.Outlined.AlternateEmail,
                    keyboardType = KeyboardType.Email,
                    isError = emailError,
                    errorText = "Enter a valid email address.",
                    shakeSignal = emailShake
                )
                Spacer(Modifier.height(16.dp))
                DryNavTextField(
                    value = password,
                    onValueChange = { password = it; passwordError = false },
                    placeholder = "Password",
                    leadingIcon = Icons.Outlined.Lock,
                    isPassword = true,
                    isError = passwordError,
                    errorText = "Enter your password.",
                    shakeSignal = passwordShake
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.forgotPassword(email) }) {
                        Text(
                            "Forgot Password?",
                            style = MaterialTheme.typography.labelLarge,
                            color = TealPrimary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                PillButton(
                    text = "Login",
                    loading = uiState.isLoading,
                    onClick = ::submitLogin,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                PillButton(
                    text = "Continue with Google",
                    onClick = launchGoogleSignIn,
                    containerColor = Color.White,
                    contentColor = TextDark,
                    borderColor = CardStroke,
                    leadingIcon = {
                        Image(
                            painter = painterResource(R.drawable.ic_google_g),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Don't have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                PillButton(
                    text = "Sign up for free",
                    onClick = onGoToRegister,
                    containerColor = Color.White,
                    contentColor = TextDark,
                    borderColor = CardStroke,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
