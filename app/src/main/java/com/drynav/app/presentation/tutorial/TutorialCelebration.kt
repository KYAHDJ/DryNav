package com.drynav.app.presentation.tutorial

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drynav.app.presentation.components.PillButton
import com.drynav.app.presentation.theme.AccentGreen
import com.drynav.app.presentation.theme.Amber
import com.drynav.app.presentation.theme.TealPrimary
import kotlin.random.Random

private data class ConfettiPiece(
    val x: Float,
    val startDelay: Float,
    val color: Color,
    val size: Float,
    val spinSpeed: Float
)

/**
 * Post-tour celebration: a dark overlay, centered welcome message, and a
 * gentle continuous confetti shower behind it — shown once, right after the
 * guided tour finishes or is skipped, then never again for this account
 * (see [TutorialManager.dismissCelebration]).
 */
@Composable
fun TutorialCelebrationOverlay(onDismiss: () -> Unit) {
    val colors = listOf(TealPrimary, AccentGreen, Amber, Color.White)
    val pieces = remember {
        List(50) {
            ConfettiPiece(
                x = Random.nextFloat(),
                startDelay = Random.nextFloat(),
                color = colors[Random.nextInt(colors.size)],
                size = Random.nextFloat() * 6f + 6f,
                spinSpeed = Random.nextFloat() * 3f + 1f
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "confetti")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing)
        ),
        label = "confettiTime"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(0xF01B2A33))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            pieces.forEach { piece ->
                val t = (time + piece.startDelay) % 1f
                val y = t * size.height * 1.15f - size.height * 0.08f
                val x = piece.x * size.width
                rotate(degrees = t * 360f * piece.spinSpeed, pivot = Offset(x, y)) {
                    drawRect(
                        color = piece.color.copy(alpha = 0.9f),
                        topLeft = Offset(x - piece.size / 2f, y - piece.size / 2f),
                        size = Size(piece.size, piece.size * 1.8f)
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
        ) {
            Text(
                "You're all set!",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Welcome to the DryNav community — stay safe, stay dry, and thanks for helping others do the same.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFE0F2F1),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            PillButton(
                text = "Let's go",
                onClick = onDismiss,
                modifier = Modifier.width(160.dp)
            )
        }
    }
}
