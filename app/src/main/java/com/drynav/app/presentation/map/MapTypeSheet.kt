package com.drynav.app.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Traffic
import androidx.compose.material.icons.outlined.Water
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.drynav.app.presentation.theme.TealPrimary

/** "Maps3" — the map type / map details chooser sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTypeSheet(
    mapType: MapType,
    trafficEnabled: Boolean,
    floodEnabled: Boolean,
    onMapType: (MapType) -> Unit,
    onToggleTraffic: () -> Unit,
    onToggleFlood: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Map types", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MapOption(
                    label = "Default",
                    selected = mapType == MapType.DEFAULT,
                    background = Color(0xFFEDEDE3),
                    icon = Icons.Outlined.Map,
                    iconTint = Color(0xFF607D8B),
                    onClick = { onMapType(MapType.DEFAULT) }
                )
                MapOption(
                    label = "Satellite",
                    selected = mapType == MapType.SATELLITE,
                    background = Color(0xFF2F3B33),
                    icon = Icons.Outlined.SatelliteAlt,
                    iconTint = Color(0xFFB6C9BD),
                    onClick = { onMapType(MapType.SATELLITE) }
                )
                MapOption(
                    label = "Terrain",
                    selected = mapType == MapType.TERRAIN,
                    background = Color(0xFFDDEBDC),
                    icon = Icons.Outlined.Terrain,
                    iconTint = Color(0xFF5B8A72),
                    onClick = { onMapType(MapType.TERRAIN) }
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("Map details", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MapOption(
                    label = "Traffic",
                    selected = trafficEnabled,
                    background = Color(0xFFF6F1DC),
                    icon = Icons.Outlined.Traffic,
                    iconTint = Color(0xFFE0A100),
                    onClick = onToggleTraffic
                )
                MapOption(
                    label = "Flood",
                    selected = floodEnabled,
                    background = Color.Black,
                    icon = Icons.Outlined.Water,
                    iconTint = Color.White,
                    iconBackground = Brush.radialGradient(
                        listOf(Color(0xFFE53935), Color(0xFFFFC107), Color(0xFF43A047))
                    ),
                    onClick = onToggleFlood
                )
            }
        }
    }
}

@Composable
private fun MapOption(
    label: String,
    selected: Boolean,
    background: Color,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    iconBackground: Brush? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) TealPrimary else Color(0xFFD0D7DE),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(3.dp)
                .clip(RoundedCornerShape(9.dp))
                .then(
                    if (iconBackground != null) {
                        Modifier.background(iconBackground, RoundedCornerShape(9.dp))
                    } else {
                        Modifier.background(background, RoundedCornerShape(9.dp))
                    }
                )
                .clickable(onClick = onClick)
        ) {
            Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
