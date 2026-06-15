package com.glyphnavtoy.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * GlyphNav "CLEAN" palette — one calm, premium NothingOS system.
 *
 * Pure black, hairline structure, a single warm accent (= the LED glow).
 * No cards, no textures, no second accent. Content floats on black and is
 * organized by whitespace + hairline rules.
 */
object GlyphColors {
    val Bg = Color(0xFF000000)            // pure black
    val Text = Color(0xFFF5F5F5)
    val TextDim = Color(0xFFF5F5F5).copy(alpha = 0.52f)
    val TextFaint = Color(0xFFF5F5F5).copy(alpha = 0.30f)

    val Line = Color(0xFFFFFFFF).copy(alpha = 0.08f)        // hairline divider
    val LineStrong = Color(0xFFFFFFFF).copy(alpha = 0.16f)  // control outline
    val Surface = Color(0xFFFFFFFF).copy(alpha = 0.028f)    // selected fills (sparingly)
    val SurfaceSolid = Color(0xFF0C0C0C)

    // The single accent — drives both the UI accent and the LED glow, so the
    // whole app stays one warm temperature.
    val Accent = Color(0xFFFFE9B5)        // true warm-white LED
    val AccentText = Color(0xFF0A0A0A)

    val Gps = Color(0xFF6BCB77)
    val Alert = Color(0xFFFF3B30)         // imminent-turn red

    const val GlyphRadius = 12            // control corner radius (dp)
}
