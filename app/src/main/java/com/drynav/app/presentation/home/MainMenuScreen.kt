package com.drynav.app.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drynav.app.R
import com.drynav.app.presentation.components.AutoSizeText
import com.drynav.app.presentation.components.DryNavBottomBar
import com.drynav.app.presentation.navigation.Routes
import com.drynav.app.presentation.tutorial.TutorialCelebrationOverlay
import com.drynav.app.presentation.tutorial.TutorialOverlay
import com.drynav.app.presentation.tutorial.TutorialViewModel
import com.drynav.app.presentation.tutorial.TutorialWelcomeDialog
import com.drynav.app.presentation.tutorial.tutorialTarget
import com.drynav.app.presentation.theme.FloodRed
import com.drynav.app.presentation.theme.TealDark
import com.drynav.app.presentation.theme.TealPrimary
import com.drynav.app.presentation.theme.TealTint
import com.drynav.app.presentation.theme.TextGray

@Composable
fun MainMenuScreen(
    onNavigate: (String) -> Unit,
    viewModel: MainMenuViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tutorialManager = hiltViewModel<TutorialViewModel>().manager
    LaunchedEffect(uiState.userId) {
        if (uiState.userId.isNotBlank()) tutorialManager.checkForUser(uiState.userId)
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            DryNavBottomBar(currentRoute = Routes.HOME, onNavigate = onNavigate)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Upper half — greeting, name/icon, and the live hazard summary.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Greeting row — the signed-in user's real name and initials.
                val firstName = uiState.displayName.trim().substringBefore(' ')
                    .ifBlank { "there" }
                val initials = uiState.displayName.trim()
                    .split(' ')
                    .filter { it.isNotBlank() }
                    .take(2)
                    .joinToString("") { it.first().uppercase() }
                    .ifBlank { "DN" }
                val greeting = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
                    in 0..11 -> "Good morning,"
                    in 12..17 -> "Good afternoon,"
                    else -> "Good evening,"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting, style = MaterialTheme.typography.bodyMedium, color = TextGray)
                        Text(
                            firstName,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(TealTint)
                    ) {
                        Text(
                            initials,
                            style = MaterialTheme.typography.titleLarge,
                            color = TealPrimary
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))

                // Live flood-count summary — real data from FloodRepository,
                // not a static tagline.
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            Brush.linearGradient(listOf(TealPrimary, TealDark)),
                            RoundedCornerShape(28.dp)
                        )
                        .tutorialTarget(tutorialManager, "flood_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        // Label top-left, hazard icon top-right — same row.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "RIGHT NOW NEARBY",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFFBFE3EA),
                                modifier = Modifier.weight(1f)
                            )
                            Image(
                                painter = painterResource(R.drawable.img_drynav_logo),
                                contentDescription = null,
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        if (uiState.loaded) {
                            val total = uiState.blockedCount + uiState.slowCount
                            Spacer(Modifier.weight(1f))
                            // Big number, "flooded roads" as one combined
                            // phrase, then the hazard icon — one line instead
                            // of splitting "flooded" and "roads" across two.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Shrinks instead of overflowing the card on a
                                // narrow screen, a large system font scale, or
                                // an unusually high flood count (many digits).
                                AutoSizeText(
                                    text = "$total",
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontSize = 84.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "flooded roads",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White
                                )
                                Spacer(Modifier.width(10.dp))
                                Icon(
                                    Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = Color(0xFFBFE3EA),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${uiState.blockedCount} blocked · ${uiState.slowCount} slow",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFFBFE3EA),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .size(24.dp)
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Lower half — Navigate / Report, each a big tap target. Slightly
            // smaller than a true even half, with a clear gap above so the
            // buttons don't sit flush against the nearby-hazard card.
            Column(
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 28.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ActionRow(
                    icon = Icons.Filled.Navigation,
                    iconTint = TealPrimary,
                    title = "Navigate",
                    subtitle = "Search a destination",
                    onClick = { onNavigate(Routes.MAP_HOME) },
                    modifier = Modifier
                        .weight(1f)
                        .tutorialTarget(tutorialManager, "navigate_button")
                )
                ActionRow(
                    icon = Icons.Filled.WarningAmber,
                    iconTint = FloodRed,
                    title = "Report a Flood",
                    subtitle = "Mark where you are",
                    onClick = { onNavigate(Routes.REPORT) },
                    modifier = Modifier
                        .weight(1f)
                        .tutorialTarget(tutorialManager, "report_button")
                )
            }
        }
    }

        if (tutorialManager.isActive && tutorialManager.currentStep?.route == Routes.HOME) {
            TutorialOverlay(tutorialManager)
        }
        if (tutorialManager.showCelebration) {
            TutorialCelebrationOverlay(onDismiss = tutorialManager::dismissCelebration)
        }
    }

    if (tutorialManager.showWelcomePrompt) {
        TutorialWelcomeDialog(
            onGetStarted = tutorialManager::beginTour,
            onSkip = tutorialManager::declineTour
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(iconTint.copy(alpha = 0.12f))
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextGray)
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextGray,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
