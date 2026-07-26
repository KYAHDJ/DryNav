package com.drynav.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drynav.app.presentation.components.DryNavLogo
import com.drynav.app.presentation.components.PillButton
import com.drynav.app.presentation.theme.TealPrimary
import com.drynav.app.presentation.theme.skyGradient

@Composable
fun GetStartedScreen(onGetStarted: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(skyGradient()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 32.dp)
        ) {
            Spacer(Modifier.weight(1f))
            DryNavLogo(size = 210)
            Spacer(Modifier.weight(0.7f))
            Text(
                text = "Dry Roads, Safe path,\nSafe Journey",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(Color(0x59000000), Offset(0f, 2f), blurRadius = 8f)
                ),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            PillButton(
                text = "Get Started",
                onClick = onGetStarted,
                containerColor = Color.White,
                contentColor = TealPrimary,
                modifier = Modifier.fillMaxWidth(0.72f)
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}
