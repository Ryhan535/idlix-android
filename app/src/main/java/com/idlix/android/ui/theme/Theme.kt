package com.idlix.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RedAccent = Color(0xFFE50914)
val DarkBackground = Color(0xFF0F1015)
val DarkSurface = Color(0xFF16181E)
val CardBackground = Color(0xFF1A1D24)
val BorderColor = Color(0xFF242834)
val AmberStar = Color(0xFFF59E0B)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9CA3AF)
val FocusOutline = Color(0xFFFF1E27) // Accent Highlight

private val DarkColorScheme = darkColorScheme(
    primary = RedAccent,
    secondary = RedAccent,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun IDLIXTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

