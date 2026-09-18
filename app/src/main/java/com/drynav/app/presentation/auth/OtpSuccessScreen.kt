package com.drynav.app.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drynav.app.presentation.navigation.Routes
import com.drynav.app.presentation.theme.TealPrimary
import com.drynav.app.presentation.theme.TextDark
import com.drynav.app.presentation.theme.TextGray
import kotlinx.coroutines.delay

@Composable
fun OtpSuccessScreen(onNavigate: (String) -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onNavigate(Routes.GET_STARTED)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 40.dp)
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .border(5.dp, TealPrimary, CircleShape)
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Verified",
                tint = TealPrimary,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Your account has been verified\nsuccessfully",
            style = MaterialTheme.typography.titleMedium,
            color = TextDark,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Redirecting…",
            style = MaterialTheme.typography.bodyMedium,
            color = TextGray
        )
        Spacer(Modifier.weight(1f))
    }
}
