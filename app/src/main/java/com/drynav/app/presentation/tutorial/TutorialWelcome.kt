package com.drynav.app.presentation.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.drynav.app.presentation.components.PillButton
import com.drynav.app.presentation.theme.TealPrimary
import com.drynav.app.presentation.theme.TealTint
import com.drynav.app.presentation.theme.TextDark
import com.drynav.app.presentation.theme.TextGray

/**
 * First-run "want a quick tour?" prompt — shown once before the guided tour
 * itself starts (see [TutorialManager.checkForUser]). Declining still marks
 * the tour as seen, same as finishing it.
 */
@Composable
fun TutorialWelcomeDialog(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit
) {
    Dialog(onDismissRequest = onSkip) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .background(TealTint, RoundedCornerShape(20.dp))
                ) {
                    Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = TealPrimary)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Welcome to DryNav!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextDark,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Want a quick tour of how everything works? It only takes a minute.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                PillButton(
                    text = "Get Started",
                    onClick = onGetStarted,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                val playClick = com.drynav.app.presentation.sound.rememberClickSound()
                Text(
                    "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextGray,
                    modifier = Modifier
                        .padding(4.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { playClick(); onSkip() }
                        )
                )
            }
        }
    }
}
