package com.spectra.twin.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Night = Color(0xFF090D12)
val Panel = Color(0xFF151D25)
val PanelUp = Color(0xFF25323B)
val Ice = Color(0xFF9EDCD9)
val Muted = Color(0xFFACBDC4)
val Good = Color(0xFF9EDDC0)

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

