package com.drynav.app.presentation.auth

import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
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

@Composable
fun RegisterScreen(
    onNavigate: (String) -> Unit,
    onGoToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }

    var nameError by rememberSaveable { mutableStateOf(false) }
    var emailError by rememberSaveable { mutableStateOf(false) }
    var passwordError by rememberSaveable { mutableStateOf(false) }
    var confirmError by rememberSaveable { mutableStateOf(false) }
    var phoneError by rememberSaveable { mutableStateOf(false) }
    var nameShake by rememberSaveable { mutableIntStateOf(0) }
    var emailShake by rememberSaveable { mutableIntStateOf(0) }
    var passwordShake by rememberSaveable { mutableIntStateOf(0) }
    var confirmShake by rememberSaveable { mutableIntStateOf(0) }
    var phoneShake by rememberSaveable { mutableIntStateOf(0) }

    val launchGoogleSignIn = rememberGoogleSignInLauncher(viewModel)

    fun submitRegister() {
        val nameValid = name.isNotBlank()
        val emailValid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
        val passwordValid = password.length >= 6
        val confirmValid = confirm.isNotBlank() && confirm == password
        val phoneValid = phone.filter(Char::isDigit).length >= 10
        nameError = !nameValid
        emailError = !emailValid
        passwordError = !passwordValid
        confirmError = !confirmValid
        phoneError = !phoneValid
        if (!nameValid) nameShake++
        if (!emailValid) emailShake++
        if (!passwordValid) passwordShake++
        if (!confirmValid) confirmShake++
        if (!phoneValid) phoneShake++
        if (!nameValid || !emailValid || !passwordValid || !confirmValid || !phoneValid) return
        viewModel.register(name.trim(), email.trim(), password, confirm, phone)
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
            AuthShell(onBack = onGoToLogin) {
                Text(
                    text = "REGISTER",
                    style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 1.sp),
                    color = TextDark,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                DryNavTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    placeholder = "Name",
                    isError = nameError,
                    errorText = "Enter your name.",
                    shakeSignal = nameShake
                )
                Spacer(Modifier.height(14.dp))
                DryNavTextField(
                    value = email,
                    onValueChange = { email = it; emailError = false },
                    placeholder = "Email",
                    keyboardType = KeyboardType.Email,
                    isError = emailError,
                    errorText = "Enter a valid email address.",
                    shakeSignal = emailShake
                )
                Spacer(Modifier.height(14.dp))
                DryNavTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordError = false
                        confirmError = confirm.isNotEmpty() && confirm != it
                    },
                    placeholder = "Password",
                    isPassword = true,
                    isError = passwordError,
                    errorText = "Password must be at least 6 characters.",
                    shakeSignal = passwordShake
                )
                Spacer(Modifier.height(14.dp))
                DryNavTextField(
                    value = confirm,
                    onValueChange = { confirm = it; confirmError = false },
                    placeholder = "Confirm Password",
                    isPassword = true,
                    isError = confirmError,
                    errorText = "Passwords do not match.",
                    shakeSignal = confirmShake
                )
                Spacer(Modifier.height(14.dp))
                DryNavTextField(
                    value = phone,
                    onValueChange = { phone = it; phoneError = false },
                    placeholder = "Phone Number",
                    keyboardType = KeyboardType.Phone,
                    isError = phoneError,
                    errorText = "Enter a valid phone number.",
                    shakeSignal = phoneShake
                )
                Spacer(Modifier.height(20.dp))
                PillButton(
                    text = "Register",
                    loading = uiState.isLoading,
                    onClick = ::submitRegister,
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
                Spacer(Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = TextGray)) {
                                append("Already have an account? ")
                            }
                            withStyle(
                                SpanStyle(color = TealPrimary, fontWeight = FontWeight.Bold)
                            ) {
                                append("Login")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable(onClick = onGoToLogin)
                    )
                }
            }
        }
    }
}
