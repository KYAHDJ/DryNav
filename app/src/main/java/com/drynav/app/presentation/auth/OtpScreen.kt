package com.drynav.app.presentation.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MailLock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drynav.app.presentation.components.PillButton
import com.drynav.app.presentation.theme.FloodRed
import com.drynav.app.presentation.theme.TealPrimary
import com.drynav.app.presentation.theme.TealTint
import com.drynav.app.presentation.theme.TextDark
import com.drynav.app.presentation.theme.TextGray
import kotlin.math.roundToInt

@Composable
fun OtpScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var code by rememberSaveable { mutableStateOf("") }
    var otpInvalid by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(uiState.otpErrorSignal) {
        if (uiState.otpErrorSignal > 0) {
            otpInvalid = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            listOf(-10f, 10f, -8f, 8f, -4f, 4f, 0f).forEach { x ->
                shakeOffset.animateTo(x, animationSpec = tween(40))
            }
        }
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
            AuthShell(onBack = onBack) {
                Spacer(Modifier.height(24.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .background(TealTint, RoundedCornerShape(18.dp))
                ) {
                    Icon(
                        Icons.Outlined.MailLock,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Enter Verification Code",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextDark,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "A 4-digit verification code has\nbeen sent to your phone number",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))

                // Four circular digit boxes backed by one invisible text field.
                BasicTextField(
                    value = code,
                    onValueChange = { new ->
                        code = new.filter(Char::isDigit).take(4)
                        otpInvalid = false
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.focusRequester(focusRequester),
                    decorationBox = { _ ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { focusRequester.requestFocus() }
                        ) {
                            repeat(4) { index ->
                                val digit = code.getOrNull(index)?.toString().orEmpty()
                                val isActive = index == code.length
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(Color.White, CircleShape)
                                        .border(
                                            width = if (isActive || otpInvalid) 2.dp else 1.5.dp,
                                            color = if (otpInvalid) FloodRed else TealPrimary,
                                            shape = CircleShape
                                        )
                                ) {
                                    Text(
                                        text = digit,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = if (otpInvalid) FloodRed else TextDark
                                    )
                                }
                            }
                        }
                    }
                )
                if (otpInvalid) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Incorrect code. Please try again.",
                        style = MaterialTheme.typography.labelSmall,
                        color = FloodRed
                    )
                }

                Spacer(Modifier.height(32.dp))
                PillButton(
                    text = "Confirm",
                    enabled = code.length == 4,
                    onClick = { viewModel.verifyOtp(code) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = TextGray)) {
                            append("Didn't receive the code? ")
                        }
                        withStyle(
                            SpanStyle(color = TealPrimary, fontWeight = FontWeight.Bold)
                        ) {
                            append("resend it.")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable {
                        code = ""
                        viewModel.resendOtp()
                    }
                )
            }
        }
    }
}
