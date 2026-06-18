// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 capad.io <capad.xyz@gmail.com>
// GlyphMaps - licensed under AGPL-3.0 (see LICENSE).
// Brand assets (name, icon, artwork) are NOT covered by the AGPL; see NOTICE.

package com.glyphnavtoy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glyphnavtoy.glyph.MatrixFrame
import com.glyphnavtoy.ui.theme.GlyphColors
import kotlin.math.min

/**
 * Renders a [MatrixFrame] as warm-glow LED dots — the same thing the back of
 * the phone lights up. Lit dots get a soft bloom halo; off dots (inside the
 * circular mask) are barely-there ghosts, giving the panel a tactile look.
 *
 * This is the single source of "what the Glyph shows" on screen — the dial
 * hero, the picker mini-glyphs and any preview all go through here.
 */
@Composable
fun GlyphMatrix(
    frame: MatrixFrame,
    modifier: Modifier = Modifier,
    applyMask: Boolean = true,
    glow: Boolean = true,
    onColor: Color = GlyphColors.Accent,
    intensity: Float = 1f,
) {
    Canvas(modifier = modifier) {
        drawMatrix(frame, applyMask, glow, onColor, intensity)
    }
}

private fun DrawScope.drawMatrix(
    frame: MatrixFrame,
    applyMask: Boolean,
    glow: Boolean,
    onColor: Color,
    intensity: Float,
) {
    val n = MatrixFrame.SIZE
    val cell = min(size.width, size.height) / n
    val dotR = cell * 0.30f
    val offColor = Color(0xFF969696)

    for (y in 0 until n) {
        for (x in 0 until n) {
            val inMask = frame.isLitPixel(x, y)
            if (applyMask && !inMask) continue
            val cx = x * cell + cell / 2f
            val cy = y * cell + cell / 2f
            val b = frame.get(x, y)
            if (b > 0) {
                val a = (b / 255f * intensity).coerceIn(0f, 1f)
                // Bloom halo
                if (glow) {
                    val haloR = cell * 0.95f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                onColor.copy(alpha = 0.42f * a),
                                onColor.copy(alpha = 0f),
                            ),
                            center = Offset(cx, cy),
                            radius = haloR,
                        ),
                        radius = haloR,
                        center = Offset(cx, cy),
                    )
                }
                drawCircle(onColor.copy(alpha = a), dotR, Offset(cx, cy))
            } else {
                // off cell — faint ghost dot so the panel reads as a grid
                drawCircle(offColor.copy(alpha = 0.10f), dotR * 0.82f, Offset(cx, cy))
            }
        }
    }
}

/**
 * The hero "Glyph dial": the matrix sits in a recessed circular well with a
 * progress ring that fills as the distance to the turn shrinks.
 *
 * @param progress 0f..1f ring fill.
 * @param ringColor accent normally, red when a turn is imminent.
 */
@Composable
fun GlyphDial(
    frame: MatrixFrame,
    progress: Float,
    modifier: Modifier = Modifier,
    dialSize: Dp = 270.dp,
    matrixSize: Dp = 226.dp,
    applyMask: Boolean = true,
    glow: Boolean = true,
    onColor: Color = GlyphColors.Accent,
    ringColor: Color = GlyphColors.Accent,
    intensity: Float = 1f,
) {
    Box(modifier = modifier.size(dialSize), contentAlignment = Alignment.Center) {
        if (applyMask) {
            // Recessed glass well + progress ring
            Canvas(modifier = Modifier.size(dialSize)) {
                val d = min(size.width, size.height)
                val c = Offset(size.width / 2f, size.height / 2f)
                // well
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.012f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height * 0.42f),
                        radius = d * 0.5f,
                    ),
                    radius = d / 2f - 14f,
                    center = c,
                )
                // ring track
                val stroke = 5f
                val rr = d / 2f - stroke - 10f
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = rr,
                    center = c,
                    style = Stroke(width = stroke),
                )
                // ring progress (start at top, clockwise)
                if (progress > 0f) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(c.x - rr, c.y - rr),
                        size = androidx.compose.ui.geometry.Size(rr * 2, rr * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        GlyphMatrix(
            frame = frame,
            modifier = Modifier.size(matrixSize),
            applyMask = applyMask,
            glow = glow,
            onColor = onColor,
            intensity = intensity,
        )
    }
}

/**
 * The GlyphNav brand mark — an up-chevron rendered as Glyph-Matrix dots (the
 * same shape that lights the back of the phone). 8 dots, head bright, tail dim.
 */
@Composable
fun GlyphMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    color: Color = GlyphColors.Accent,
    glow: Boolean = true,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = min(this.size.width, this.size.height)
        val u = s / 64f // viewBox 64
        val dotR = 4.4f * u
        // [x, y, isHead]
        val dots = listOf(
            Triple(32f, 11f, true),
            Triple(22.5f, 20.5f, true), Triple(41.5f, 20.5f, true),
            Triple(13f, 30f, true), Triple(51f, 30f, true),
            Triple(32f, 28f, false), Triple(32f, 39f, false), Triple(32f, 50f, false),
        )
        for ((dx, dy, head) in dots) {
            val c = Offset(dx * u, dy * u)
            val a = if (head) 1f else 0.7f
            if (glow) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.4f * a), color.copy(alpha = 0f)),
                        center = c, radius = dotR * 2.2f,
                    ),
                    radius = dotR * 2.2f, center = c,
                )
            }
            drawCircle(color.copy(alpha = a), dotR, c)
        }
    }
}
