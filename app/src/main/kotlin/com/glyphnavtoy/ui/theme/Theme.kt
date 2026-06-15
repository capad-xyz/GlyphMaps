package com.glyphnavtoy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GlyphDarkColors = darkColorScheme(
    primary = GlyphColors.Accent,
    onPrimary = GlyphColors.AccentText,
    background = GlyphColors.Bg,
    onBackground = GlyphColors.Text,
    surface = GlyphColors.Bg,
    onSurface = GlyphColors.Text,
    surfaceVariant = GlyphColors.LineStrong,
    onSurfaceVariant = GlyphColors.Text,
    secondary = GlyphColors.Accent,
    onSecondary = GlyphColors.AccentText,
)

@Composable
fun GlyphNavToyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GlyphDarkColors,
        typography = GlyphTypography,
        content = content,
    )
}
