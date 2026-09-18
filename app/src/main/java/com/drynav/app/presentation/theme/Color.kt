package com.drynav.app.presentation.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ---- Brand ----
val TealPrimary = Color(0xFF17798A)      // logo drop / buttons / headings
val TealDark = Color(0xFF0E5E6F)
val TealLight = Color(0xFF4FA8B8)
val AccentGreen = Color(0xFF43A047)      // logo pin, "Re-routing" text
val NavBlue = Color(0xFF1565C0)          // turn-by-turn banners & ETA bar
val NavBlueDark = Color(0xFF0D47A1)

// ---- Sky gradient (onboarding / auth / splash backgrounds) ----
val SkyTop = Color(0xFF8EC9F2)
val SkyMid = Color(0xFF5FA9EA)
val SkyBottom = Color(0xFF3B87DB)

// ---- Semantic ----
val WarnYellow = Color(0xFFFFC107)
val FloodRed = Color(0xFFE53935)
val TextDark = Color(0xFF111827)
val TextGray = Color(0xFF43505F)
val CardStroke = Color(0xFFE5E7EB)
val LogoutRed = Color(0xFFE53935)

// ---- Design-refresh tokens (paper theme from the approved mockup) ----
val Mist = Color(0xFFF3F6F9)          // subtle gray fills: chips, icon buttons, tracks
val TealTint = Color(0xFFD2ECF1)      // teal-washed tiles/avatars
val GreenTint = Color(0xFFE3F2E4)
val Amber = Color(0xFFFFA000)         // "passable/slow" severity
val AmberInk = Color(0xFF8A5A00)
val AmberTint = Color(0xFFFFF1D6)
val CoralTint = Color(0xFFFBDEDC)

// ---- Dark theme surfaces ----
val DarkBackground = Color(0xFF0F1720)
val DarkSurface = Color(0xFF16212C)
val DarkCard = Color(0xFF1D2A38)

fun skyGradient() = Brush.verticalGradient(listOf(SkyTop, SkyMid, SkyBottom))
fun skyGradientHorizontal() = Brush.linearGradient(listOf(SkyTop, SkyBottom))
