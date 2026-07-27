package com.drynav.app.presentation.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.drynav.app.R
import com.drynav.app.presentation.auth.AuthViewModel
import com.drynav.app.presentation.components.PageIndicator
import com.drynav.app.presentation.components.PillButton
import com.drynav.app.presentation.theme.TealPrimary
import com.drynav.app.presentation.theme.skyGradient
import kotlinx.coroutines.launch

private data class OnboardPage(
    val title: String,
    val description: String,
    val illustration: Int
)

private val pages = listOf(
    OnboardPage(
        title = "Navigate Safely",
        description = "Get real-time flood updates and travel with confidence, even in unpredictable weather.",
        illustration = R.drawable.ill_onboard_1
    ),
    OnboardPage(
        title = "Avoid Flooded Roads",
        description = "Receive instant alerts and get safer alternative routes to avoid flooded or dangerous areas.",
        illustration = R.drawable.ill_onboard_2
    ),
    OnboardPage(
        title = "Community Updates",
        description = "Report road conditions and see updates from other users for more reliable navigation",
        illustration = R.drawable.ill_onboard_3
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    HorizontalPager(
        state = pagerState,
        // A stable key per page (title, not the index) so Compose never
        // reuses a swiped-away page's composition state for a different
        // page when swiping back — see AutoSizeText's doc for the bug this
        // class of implicit-key slot reuse could otherwise cause.
        key = { pages[it].title },
        modifier = Modifier.fillMaxSize()
    ) { pageIndex ->
        val page = pages[pageIndex]
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(skyGradient())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.6f))
            // Illustration first, full-bleed, scaling with the screen.
            Image(
                painter = painterResource(page.illustration),
                contentDescription = page.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .weight(1.6f)
            )
            Spacer(Modifier.height(26.dp))
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    shadow = Shadow(Color(0x59000000), Offset(0f, 2f), blurRadius = 8f)
                ),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                    shadow = Shadow(Color(0x40000000), Offset(0f, 1f), blurRadius = 4f)
                ),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            PageIndicator(pageCount = pages.size, currentPage = pageIndex)
            Spacer(Modifier.height(20.dp))
            val isLast = pageIndex == pages.lastIndex
            PillButton(
                text = if (isLast) "Proceed to Login" else "Next",
                onClick = {
                    if (isLast) {
                        viewModel.markOnboardingSeen()
                        onFinished()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
                    }
                },
                containerColor = Color.White,
                contentColor = TealPrimary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
