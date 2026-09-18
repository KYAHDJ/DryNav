package com.drynav.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Darkens the screen and swallows every touch until [visible] goes false —
 * used while flood data is still loading so the map can't be tapped/dragged
 * before there's anything real to route or draw against.
 */
@Composable
fun LoadingOverlay(visible: Boolean, label: String = "Loading flood data…") {
    if (!visible) return
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            // Consumes all taps/drags so nothing underneath reacts while loading.
            .pointerInput(Unit) {}
    ) {
        Surface(
            color = Color(0xE61F2937),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp)
            ) {
                CircularProgressIndicator(color = Color.White)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
                Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
