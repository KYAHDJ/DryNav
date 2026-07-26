package com.drynav.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.drynav.app.R
import com.drynav.app.presentation.navigation.Routes
import com.drynav.app.presentation.theme.AccentGreen
import com.drynav.app.presentation.theme.CardStroke
import com.drynav.app.presentation.theme.FloodRed
import com.drynav.app.presentation.theme.Mist
import com.drynav.app.presentation.theme.TealPrimary
import com.drynav.app.presentation.theme.TextDark
import com.drynav.app.presentation.theme.TextGray
import kotlin.math.roundToInt

/**
 * A [Text] that shrinks its own font size (down to [minFontScale] of the
 * requested size) until it fits the width/height Compose actually gives it,
 * instead of wrapping oddly, clipping, or spilling past a button/card edge.
 * Renders invisibly for the one frame it takes to measure so the shrink
 * never visibly "pops."
 */
@Composable
fun AutoSizeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    minFontScale: Float = 0.5f
) {
    var fontSize by remember(text, style) { mutableStateOf(style.fontSize) }
    var readyToDraw by remember(text, style) { mutableStateOf(false) }

    Text(
        text = text,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        softWrap = maxLines > 1,
        style = style.copy(fontSize = fontSize),
        onTextLayout = { result ->
            if (!readyToDraw) {
                if ((result.didOverflowWidth || result.didOverflowHeight) &&
                    fontSize.value / style.fontSize.value > minFontScale
                ) {
                    fontSize *= 0.94f
                } else {
                    readyToDraw = true
                }
            }
        },
        modifier = modifier.alpha(if (readyToDraw) 1f else 0f)
    )
}

/** The DryNav droplet logo (official artwork, transparent PNG). */
@Composable
fun DryNavLogo(modifier: Modifier = Modifier, size: Int = 120) {
    Image(
        painter = painterResource(R.drawable.img_drynav_logo),
        contentDescription = "DryNav logo",
        modifier = modifier.size(size.dp)
    )
}

/** "DryNav" wordmark — "Dry" in the given color, "Nav" always green. */
@Composable
fun DryNavWordmark(
    modifier: Modifier = Modifier,
    dryColor: Color = TealPrimary,
    fontSize: Int = 34
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = dryColor)) { append("Dry") }
            withStyle(SpanStyle(color = AccentGreen)) { append("Nav") }
        },
        style = MaterialTheme.typography.displaySmall.copy(fontSize = fontSize.sp),
        modifier = modifier
    )
}

/**
 * Design-refresh pill button. Default is the primary style — SOLID teal fill
 * with a white label. Pass [containerColor]/[contentColor]/[borderColor] for
 * the variants: white-on-gradient (onboarding), outlined-teal (Go back), and
 * outlined-neutral (secondary actions).
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = TealPrimary,
    contentColor: Color = Color.White,
    borderColor: Color? = null,
    fontSize: Int = 16,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = CircleShape,
        border = borderColor?.let { BorderStroke(1.5.dp, it) },
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.7f)
        ),
        // A minimum, not a fixed height — at a larger system font-scale
        // setting the label can grow taller than 52dp without clipping
        // against the pill's own edge.
        modifier = modifier.heightIn(min = 52.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
        } else {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(10.dp))
            }
            AutoSizeText(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = contentColor,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

/**
 * Design-refresh text field: uppercase label above a rounded rectangle input
 * with a quiet gray border (teal only when focused). [placeholder] doubles as
 * the label text.
 *
 * [isError]/[errorText] turn the border red and show a message underneath.
 * Bump [shakeSignal] (e.g. a counter incremented on a failed submit) to play
 * a short shake + haptic buzz drawing attention to the field.
 */
@Composable
fun DryNavTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorText: String? = null,
    shakeSignal: Int = 0
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(shakeSignal) {
        if (shakeSignal > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            listOf(-10f, 10f, -8f, 8f, -4f, 4f, 0f).forEach { x ->
                shakeOffset.animateTo(x, animationSpec = tween(40))
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
    ) {
        Text(
            placeholder.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            color = if (isError) FloodRed else TextGray
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            isError = isError,
            leadingIcon = leadingIcon?.let {
                {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = if (isError) FloodRed else TealPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password visibility",
                            tint = TextGray
                        )
                    }
                }
            } else null,
            visualTransformation = if (isPassword && !showPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType
            ),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealPrimary,
                unfocusedBorderColor = CardStroke,
                errorBorderColor = FloodRed,
                errorLeadingIconColor = FloodRed,
                errorTrailingIconColor = FloodRed,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                errorContainerColor = Color.White,
                focusedTextColor = TextDark,
                unfocusedTextColor = TextDark
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (isError && !errorText.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                errorText,
                style = MaterialTheme.typography.labelSmall,
                color = FloodRed
            )
        }
    }
}

/** Circular back button on a quiet gray tint — the mockup's "iconbtn". */
@Composable
fun CircleBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Mist,
    tint: Color = TextDark
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Back",
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

private data class NavItem(
    val route: String,
    val outlined: ImageVector,
    val filled: ImageVector,
    val label: String
)

/** Bottom navigation bar: Home · Maps · Report · Profile (+ Admin, if you are one). */
@Composable
fun DryNavBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isAdmin = hiltViewModel<BottomBarViewModel>().isAdmin
    val items = buildList {
        add(NavItem(Routes.HOME, Icons.Outlined.Home, Icons.Filled.Home, "Home"))
        add(NavItem(Routes.MAP_HOME, Icons.Outlined.LocationOn, Icons.Filled.LocationOn, "Maps"))
        add(NavItem(Routes.REPORT, Icons.Outlined.WarningAmber, Icons.Filled.WarningAmber, "Report"))
        add(NavItem(Routes.PROFILE, Icons.Outlined.Person, Icons.Filled.Person, "Profile"))
        if (isAdmin) {
            add(
                NavItem(
                    Routes.ADMIN,
                    Icons.Outlined.AdminPanelSettings,
                    Icons.Filled.AdminPanelSettings,
                    "Admin"
                )
            )
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route ||
                    (item.route == Routes.MAP_HOME && currentRoute == Routes.MAP)
                IconButton(onClick = { if (!selected) onNavigate(item.route) }) {
                    Icon(
                        imageVector = if (selected) item.filled else item.outlined,
                        contentDescription = item.label,
                        tint = if (selected) TealPrimary else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/** Onboarding page dots: small circles, active page stretches into a pill. */
@Composable
fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            Box(
                modifier = Modifier
                    .size(width = if (active) 20.dp else 6.dp, height = 6.dp)
                    .background(
                        color = if (active) Color.White else Color(0x66FFFFFF),
                        shape = RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}
