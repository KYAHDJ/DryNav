package com.drynav.app.presentation.tutorial

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drynav.app.presentation.components.PillButton
import com.drynav.app.presentation.theme.TealPrimary
import com.drynav.app.presentation.theme.TextDark
import com.drynav.app.presentation.theme.TextGray

/** Registers this composable's on-screen bounds under [key] so a step can spotlight it. */
fun Modifier.tutorialTarget(manager: TutorialManager, key: String): Modifier =
    this.onGloballyPositioned { coordinates ->
        manager.targets[key] = coordinates.boundsInRoot()
    }

/**
 * Renders the current step of [manager]'s tour — a darkened, fully
 * touch-blocking scrim with a cutout "spotlight" around the target (a
 * gently pulsing ring traces the edge) plus a description card. Tapping
 * ANYWHERE on the overlay — including the visually-lit spotlight itself,
 * which is a pure draw-time effect, not an actual gap in touch handling —
 * advances to the next step, so the real button underneath can never be
 * triggered by accident. Must be the LAST child of the same root `Box`
 * that hosts the targets on this screen, so root-relative bounds line up.
 */
@Composable
fun TutorialOverlay(manager: TutorialManager) {
    val step = manager.currentStep ?: return
    val bounds = manager.targets[step.targetKey]
    val density = LocalDensity.current
    val playClick = com.drynav.app.presentation.sound.rememberClickSound()

    val infiniteTransition = rememberInfiniteTransition(label = "tutorialPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Any tap anywhere just advances — never lets a tap through to
            // whatever's underneath, spotlighted or not.
            .pointerInput(manager.stepIndex) { detectTapGestures { playClick(); manager.next() } }
    ) {
        val padPx = with(density) { 10.dp.toPx() }
        val maxCornerPx = with(density) { 28.dp.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Required for BlendMode.Clear to actually punch a hole
                // instead of just blending — forces offscreen compositing.
                .graphicsLayer(alpha = 0.99f)
        ) {
            drawRect(Color.Black.copy(alpha = 0.72f))
            bounds?.let { r ->
                val topLeft = Offset(r.left - padPx, r.top - padPx)
                val size = Size(r.width + padPx * 2, r.height + padPx * 2)
                val corner = (minOf(size.width, size.height) / 2f).coerceAtMost(maxCornerPx)
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = topLeft,
                    size = size,
                    cornerRadius = CornerRadius(corner),
                    blendMode = BlendMode.Clear
                )
                // Subtle pulsing ring traced right at the spotlight's edge —
                // "only a little", so alpha breathes gently rather than any
                // scale/size change.
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.5f + 0.4f * pulse),
                    topLeft = topLeft,
                    size = size,
                    cornerRadius = CornerRadius(corner),
                    style = Stroke(width = with(density) { 2.5.dp.toPx() })
                )
            }
        }

        // Rather than guessing the card's rendered height (descriptions vary
        // in length, so a fixed offset either overlapped the spotlight or
        // left an awkward gap), the card is placed inside a region sized to
        // EXACTLY the space above or below the target and aligned to the far
        // edge of that region — its actual height, whatever it ends up
        // being, can never eat into the target's spotlight.
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val spaceBelowPx = if (bounds != null) screenHeightPx - bounds.bottom - padPx else screenHeightPx
        val spaceAbovePx = if (bounds != null) bounds.top - padPx else 0f
        val showBelow = bounds == null || spaceBelowPx >= spaceAbovePx

        val regionModifier = with(density) {
            if (showBelow) {
                Modifier
                    .align(Alignment.BottomStart)
                    .height(spaceBelowPx.toDp())
            } else {
                Modifier
                    .align(Alignment.TopStart)
                    .height(spaceAbovePx.toDp())
            }
        }

        Box(
            modifier = regionModifier.fillMaxWidth(),
            contentAlignment = if (showBelow) Alignment.TopCenter else Alignment.BottomCenter
        ) {
            TutorialCard(
                step = step,
                stepNumber = manager.stepIndex + 1,
                totalSteps = manager.steps.size,
                showBack = manager.stepIndex > 0,
                isLast = manager.stepIndex == manager.steps.lastIndex,
                onBack = manager::back,
                onNext = manager::next,
                onSkip = manager::skip,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun TutorialCard(
    step: TutorialStep,
    stepNumber: Int,
    totalSteps: Int,
    showBack: Boolean,
    isLast: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playClick = com.drynav.app.presentation.sound.rememberClickSound()
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "STEP $stepNumber OF $totalSteps",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = TealPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(step.title, style = MaterialTheme.typography.titleMedium, color = TextDark)
            Spacer(Modifier.height(4.dp))
            Text(step.description, style = MaterialTheme.typography.bodyMedium, color = TextGray)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextGray,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { playClick(); onSkip() }
                        )
                )
                Spacer(Modifier.weight(1f))
                if (showBack) {
                    Text(
                        "Back",
                        style = MaterialTheme.typography.labelLarge,
                        color = TealPrimary,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { playClick(); onBack() }
                            )
                    )
                }
                PillButton(
                    text = if (isLast) "Got it" else "Next",
                    onClick = onNext,
                    fontSize = 14,
                    modifier = Modifier.width(100.dp)
                )
            }
        }
    }
}
