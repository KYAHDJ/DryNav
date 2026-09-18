package com.drynav.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.drynav.app.R

/**
 * Manrope — geometric display face for headlines, big numbers, buttons.
 * Uses real static-weight instances (not the variable font + FontVariation axis),
 * because runtime variation-axis application is unreliable across devices/Skia
 * versions and was silently rendering everything at the font's default (thin) weight.
 */
val Manrope = FontFamily(
    Font(R.font.manrope_semibold_static, FontWeight.SemiBold),
    Font(R.font.manrope_bold_static, FontWeight.Bold),
    Font(R.font.manrope_extrabold_static, FontWeight.ExtraBold)
)

/** Public Sans — civic-grade legibility for body text, labels, and fields. */
val PublicSans = FontFamily(
    Font(R.font.public_sans_regular_static, FontWeight.Normal),
    Font(R.font.public_sans_medium_static, FontWeight.Medium),
    Font(R.font.public_sans_semibold_static, FontWeight.SemiBold),
    Font(R.font.public_sans_bold_static, FontWeight.Bold)
)

val DryNavTypography = Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 40.sp),
    displayMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 34.sp),
    displaySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = PublicSans, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = PublicSans, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = PublicSans, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = PublicSans, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = PublicSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = PublicSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
)
