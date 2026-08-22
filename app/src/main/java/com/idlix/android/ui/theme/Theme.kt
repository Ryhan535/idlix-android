package com.idlix.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RedAccent = Color(0xFFE50914)
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val CardBackground = Color(0xFF2A2A2A)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB0B0B0)
val FocusOutline = Color(0xFFFFD700) // TV Focus Gold Highlight

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
