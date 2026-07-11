package com.thiengkin.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * ThiengKinTheme — ใช้ MOCKUP-v3 design tokens โดยตรง
 *
 * ไม่มี dynamic color (Material You) — เราเลือก 4 สีเอง ไม่ใช่ให้ระบบสุ่ม
 * ไม่มี dark/light toggle — แอปเลือกเอง (Travel = dark, Near-me = light)
 */

// === Light scheme (Near-me, Favorites) ===
private val LightColors = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF2F2),
    onPrimaryContainer = Red,

    secondary = Mustard,
    onSecondary = Ink,
    secondaryContainer = Color(0xFFFEF9C3),
    onSecondaryContainer = Color(0xFF713F12),

    tertiary = Green,
    onTertiary = Color.White,

    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Paper2,
    onSurfaceVariant = Ink2,

    outline = Line,
    outlineVariant = Color(0xFFEEEEEE),

    error = Red,
    onError = Color.White,
)

// === Dark scheme (Travel) ===
private val DarkColors = darkColorScheme(
    primary = AppRed,
    onPrimary = Color.White,
    primaryContainer = Color(0x33EF4444),
    onPrimaryContainer = AppRed,

    secondary = AppMustard,
    onSecondary = AppBg,
    secondaryContainer = Color(0x33FACC15),
    onSecondaryContainer = AppMustard,

    tertiary = AppGreen,
    onTertiary = AppBg,

    background = AppBg,
    onBackground = AppInk,
    surface = AppCard,
    onSurface = AppInk,
    surfaceVariant = AppCard2,
    onSurfaceVariant = AppInk2,

    outline = AppLine,
    outlineVariant = Color(0xFF1F1F1F),

    error = AppRed,
    onError = Color.White,
)

@Composable
fun ThiengKinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
