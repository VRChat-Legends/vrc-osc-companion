package com.vrchatlegends.osccompanion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val BrandPurple = Color(0xFF8B5CF6)
val BrandPurpleDim = Color(0xFF6D3FE0)
val BrandCyan = Color(0xFF22D3EE)
val Surface0 = Color(0xFF0B0B14)
val Surface1 = Color(0xFF14141F)
val Surface2 = Color(0xFF1D1D2B)
val OnSurfaceDim = Color(0xFF9CA3AF)
val Good = Color(0xFF34D399)
val Warn = Color(0xFFFBBF24)
val Bad = Color(0xFFF87171)

private val DarkColors = darkColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    primaryContainer = BrandPurpleDim,
    onPrimaryContainer = Color.White,
    secondary = BrandCyan,
    onSecondary = Color(0xFF06202A),
    background = Surface0,
    onBackground = Color(0xFFE5E7EB),
    surface = Surface1,
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Surface2,
    onSurfaceVariant = OnSurfaceDim,
    error = Bad,
    outline = Color(0xFF33334A),
)

private val LightColors = lightColorScheme(
    primary = BrandPurpleDim,
    secondary = Color(0xFF0E7490),
)

/**
 * Type is scaled up: a Quest panel sits about two virtual metres away, so anything under
 * roughly 16sp is unreadable in the headset.
 */
private val QuestTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 18.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp),
)

@Composable
fun VrcOscTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = QuestTypography,
        content = content,
    )
}
