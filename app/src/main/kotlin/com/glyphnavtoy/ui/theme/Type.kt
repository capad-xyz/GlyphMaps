// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 capad.io <capad.xyz@gmail.com>
// GlyphMaps - licensed under AGPL-3.0 (see LICENSE).
// Brand assets (name, icon, artwork) are NOT covered by the AGPL; see NOTICE.

package com.glyphnavtoy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.glyphnavtoy.R

/**
 * Three faces, each with a job:
 *  - [Doto]   — dot-matrix display face. Echoes the LED matrix. Big numbers,
 *               the wordmark, the distance readout.
 *  - [Grotesk]— Space Grotesk. Clean UI body / instruction text.
 *  - [Mono]   — Space Mono. Thin-caps section labels, ticks, status chips.
 *
 * Bundled as TTF in res/font so there's no download / GMS dependency.
 */
object GlyphFonts {
    val Doto = FontFamily(Font(R.font.doto, FontWeight.Black))

    val Grotesk = FontFamily(Font(R.font.space_grotesk, FontWeight.Normal))

    val Mono = FontFamily(
        Font(R.font.space_mono, FontWeight.Normal),
        Font(R.font.space_mono_bold, FontWeight.Bold),
    )
}

/** Material typography (used by Slider/Material widgets). UI mostly styles inline. */
val GlyphTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = GlyphFonts.Doto,
        fontWeight = FontWeight.Black,
        fontSize = 48.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = GlyphFonts.Grotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = GlyphFonts.Grotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = GlyphFonts.Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    ),
)
