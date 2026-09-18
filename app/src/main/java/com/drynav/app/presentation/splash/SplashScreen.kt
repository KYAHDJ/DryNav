package com.drynav.app.presentation.splash

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.drynav.app.presentation.auth.AuthViewModel
import com.drynav.app.presentation.components.DryNavLogo
import com.drynav.app.presentation.components.DryNavWordmark
import com.drynav.app.presentation.theme.skyGradient
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun SplashScreen(
    onNavigate: (String) -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        delay(1600)
        onNavigate(viewModel.resolveStartRoute())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(skyGradient()),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DryNavLogo(size = 190)
            Spacer(Modifier.height(36.dp))
            DryNavWordmark(dryColor = Color.White, fontSize = 44)
            Spacer(Modifier.height(28.dp))
            WaveLoadingIndicator()
        }
    }
}

/**
 * Two overlapping sine waves scrolling in place, in the same spirit as the
 * app's flood/water theme — a rippling water line instead of static dots.
 */
@Composable
private fun WaveLoadingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "splashWave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing)
        ),
        label = "wavePhase"
    )

    Canvas(modifier = modifier.width(120.dp).height(24.dp)) {
        val midY = size.height / 2f
        val amplitude = size.height / 2.4f

        fun wavePath(amplitudeScale: Float, phaseShift: Float, cyclesAcrossWidth: Float): Path {
            val path = Path()
            val step = 4f
            var x = 0f
            var first = true
            while (x <= size.width) {
                val angle = phase + phaseShift + (x / size.width) * cyclesAcrossWidth * (2f * PI.toFloat())
                val y = midY + sin(angle) * amplitude * amplitudeScale
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                x += step
            }
            return path
        }

        drawPath(
            path = wavePath(amplitudeScale = 1f, phaseShift = 0f, cyclesAcrossWidth = 1.5f),
            color = Color.White.copy(alpha = 0.9f),
            style = Stroke(width = 5f, cap = StrokeCap.Round)
        )
        drawPath(
            path = wavePath(amplitudeScale = 0.7f, phaseShift = PI.toFloat() * 0.6f, cyclesAcrossWidth = 1.5f),
            color = Color.White.copy(alpha = 0.45f),
            style = Stroke(width = 5f, cap = StrokeCap.Round)
        )
    }
}
