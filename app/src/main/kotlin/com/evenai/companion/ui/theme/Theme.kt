package com.evenai.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// G1 Companion brand palette
private val GreenPrimary   = Color(0xFF00C97A)
private val GreenContainer = Color(0xFF004D30)
private val Surface        = Color(0xFF0D0D0D)
private val OnSurface      = Color(0xFFF0F0F0)
private val SurfaceVariant = Color(0xFF1A1A1A)
private val Error          = Color(0xFFFF5252)

private val DarkColors = darkColorScheme(
    primary          = GreenPrimary,
    onPrimary        = Color.Black,
    primaryContainer = GreenContainer,
    secondary        = Color(0xFF4ECDC4),
    surface          = Surface,
    onSurface        = OnSurface,
    surfaceVariant   = SurfaceVariant,
    background       = Surface,
    onBackground     = OnSurface,
    error            = Error
)

private val LightColors = lightColorScheme(
    primary          = Color(0xFF00855A),
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFB2F5DC),
    secondary        = Color(0xFF00897B),
    surface          = Color(0xFFF5F5F5),
    onSurface        = Color(0xFF111111),
    background       = Color.White,
    onBackground     = Color(0xFF111111),
    error            = Error
)

@Composable
fun G1CompanionTheme(
    darkTheme: Boolean = true, // Default dark for glasses context
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
