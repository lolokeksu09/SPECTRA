package com.spectra.twin.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Night = Color(0xFF05070C)
val Panel = Color(0xFF111720)
val PanelUp = Color(0xFF1A2530)
val Ice = Color(0xFF77D7FF)
val Muted = Color(0xFF98AAB9)
val Good = Color(0xFF89E0C5)

private val spectraColors = darkColorScheme(
    primary = Ice,
    onPrimary = Night,
    secondary = Good,
    onSecondary = Night,
    background = Night,
    onBackground = Color(0xFFEEF5FA),
    surface = Panel,
    surfaceVariant = PanelUp,
    onSurface = Color(0xFFEAF2F8),
    onSurfaceVariant = Muted,
    outline = Color(0xFF344758),
    error = Color(0xFFFF9E9E),
)

@Composable
fun SpectraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = spectraColors, content = content)
}
