package com.vrchatlegends.osccompanion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val BrandPurple = Color(0xFF8B5CF6)
val BrandPink = Color(0xFFEC4899)

// Legacy name from the coral era; every old call site now renders the brand purple.
val SignalCoral = BrandPurple
val SignalCyan = Color(0xFF4CC9F0)
val SignalYellow = Color(0xFFF4C95D)
val BrandCyan = SignalCyan
val Surface0 = Color(0xFF0B0F14)
val Surface1 = Color(0xFF121820)
val Surface2 = Color(0xFF1B232D)
val OnSurfaceDim = Color(0xFFA5B0BA)
val Good = Color(0xFF57D18C)
val Warn = SignalYellow
val Bad = Color(0xFFFF6B70)

private val DarkColors = darkColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A2A66),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = SignalCyan,
    onSecondary = Color(0xFF002A35),
    secondaryContainer = Color(0xFF0B3B49),
    onSecondaryContainer = Color(0xFFC2F1FF),
    tertiary = SignalYellow,
    onTertiary = Color(0xFF2B2100),
    background = Surface0,
    onBackground = Color(0xFFF0F4F7),
    surface = Surface1,
    onSurface = Color(0xFFF0F4F7),
    surfaceVariant = Surface2,
    onSurfaceVariant = OnSurfaceDim,
    error = Bad,
    outline = Color(0xFF34414E),
    outlineVariant = Color(0xFF25303A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B46C1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFF007A8C),
    secondaryContainer = Color(0xFFB5EBF4),
    tertiary = Color(0xFF806000),
    background = Color(0xFFF5F7F8),
    onBackground = Color(0xFF172027),
    surface = Color.White,
    onSurface = Color(0xFF172027),
    surfaceVariant = Color(0xFFE9EEF1),
    onSurfaceVariant = Color(0xFF52606A),
    outline = Color(0xFF73808A),
    error = Color(0xFFBA1A1A),
)

/**
 * Type is scaled up: a Quest panel sits about two virtual metres away, so anything under
 * roughly 16sp is unreadable in the headset.
 */
private val QuestTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 18.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, letterSpacing = 0.sp),
)

/** The accents offered in Settings. The first entry is the built in look. */
val AccentChoices: List<Pair<String, Color>> = listOf(
    "Legends Purple" to BrandPurple,
    "Pink" to BrandPink,
    "Cyan" to SignalCyan,
    "Amber" to SignalYellow,
    "Mint" to Good,
    "Rose" to Color(0xFFFF7BAC),
    "Lime" to Color(0xFFB8E986),
    "Ice" to Color(0xFF9AD5FF),
)

@Composable
fun VrcOscTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) DarkColors else LightColors
    // Only the primary family is overridden. Recolouring surfaces from an arbitrary hue is how
    // themes end up unreadable, and the panel already sits two metres from the user's eyes.
    val scheme = accent?.let {
        base.copy(
            primary = it,
            onPrimary = if (it.luminance() > 0.5f) Color(0xFF10161C) else Color.White,
            primaryContainer = it.copy(alpha = 0.30f).compositeOver(base.surface),
            onPrimaryContainer = base.onSurface,
        )
    } ?: base

    MaterialTheme(
        colorScheme = scheme,
        typography = QuestTypography,
        content = content,
    )
}
