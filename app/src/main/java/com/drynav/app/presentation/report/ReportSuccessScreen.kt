package com.drynav.app.presentation.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drynav.app.presentation.components.CircleBackButton
import com.drynav.app.presentation.components.DryNavBottomBar
import com.drynav.app.presentation.components.PillButton
import com.drynav.app.presentation.navigation.Routes
import com.drynav.app.presentation.theme.CardStroke
import com.drynav.app.presentation.theme.TealPrimary

/** "Report Floods 2" — the report success confirmation. */
@Composable
fun ReportSuccessScreen(
    onNavigate: (String) -> Unit,
    onGoBack: () -> Unit
) {
    Scaffold(
        bottomBar = {
            DryNavBottomBar(currentRoute = Routes.REPORT, onNavigate = onNavigate)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                CircleBackButton(onClick = onGoBack)
                Spacer(Modifier.width(14.dp))
                Text(
                    "REPORT FLOODS",
                    style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                border = BorderStroke(1.dp, CardStroke),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 30.dp)
                ) {
                    Spacer(Modifier.weight(0.8f))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(104.dp)
                            .border(6.dp, TealPrimary, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Success",
                            tint = TealPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "REPORT SUCCESS",
                        style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 1.sp),
                        color = TealPrimary
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Thanks for your report!\nYou're helping others\nstay safe and dry",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TealPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.weight(1f))
                    PillButton(
                        text = "Go back",
                        onClick = onGoBack,
                        containerColor = Color.White,
                        contentColor = TealPrimary,
                        borderColor = TealPrimary,
                        modifier = Modifier.fillMaxWidth(0.65f)
                    )
                }
            }
        }
    }
}
