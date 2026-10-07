package com.pureenhance.ai.presentation.theme

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

private val Dark = darkColorScheme(
    primary = Color(0xFF9D8CFF), onPrimary = Color(0xFF14102B),
    secondary = Color(0xFF5CE1E6), onSecondary = Color(0xFF002022),
    background = Color(0xFF0B0B12), onBackground = Color(0xFFF1F0F8),
    surface = Color(0xFF14141F), onSurface = Color(0xFFF1F0F8),
    surfaceVariant = Color(0xFF1E1E2C), onSurfaceVariant = Color(0xFFB4B2C6),
    outline = Color(0x33FFFFFF), error = Color(0xFFFF6B6B),
)

private val Light = lightColorScheme(
    primary = Color(0xFF5B3DF5), onPrimary = Color.White,
    secondary = Color(0xFF0FA3B1), onSecondary = Color.White,
    background = Color(0xFFF7F6FB), onBackground = Color(0xFF14121F),
    surface = Color.White, onSurface = Color(0xFF14121F),
    surfaceVariant = Color(0xFFEDEBF7), onSurfaceVariant = Color(0xFF5B5870),
    outline = Color(0x22000000), error = Color(0xFFD64545),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
)

@Composable
fun PureEnhanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}
