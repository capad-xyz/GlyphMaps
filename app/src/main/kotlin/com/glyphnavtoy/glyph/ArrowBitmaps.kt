// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 capad.io <capad.xyz@gmail.com>
// GlyphMaps - licensed under AGPL-3.0 (see LICENSE).
// Brand assets (name, icon, artwork) are NOT covered by the AGPL; see NOTICE.

package com.glyphnavtoy.glyph

import kotlin.math.roundToInt

/** Where a pattern should be stamped so its lit bbox is centered. */
data class PatternOrigin(val x: Int, val y: Int)

/**
 * Static arrow patterns for the top half of the Matrix (rows 1-6).
 *
 * Brightness encoding (interpreted by [MatrixFrame.stamp]):
 *   `X` → 255  (full bright — arrow head)
 *   `o` → 178  (lit tail — the trail behind the head, at ~70% brightness)
 *   `+` → 102  (kept for future use)
 *   `:` →  40  (kept for future use)
 *   `.` → off
 *
 * Design principle: the *head* of each arrow (chevron, L-corner, etc.) is
 * solid bright. The trail behind the head is uniformly lit at medium
 * brightness — a continuously visible tail rather than a fading-off trail.
 *
 * Patterns are authored in a 13-col field. They are *not* stamped at a
 * fixed (0, [ARROW_ORIGIN_Y]) anymore: [originFor] shifts each pattern so
 * the bounding box of its lit cells is centered on column [MatrixFrame.CENTER]
 * and vertically in the arrow band (so distance digits below stay clear).
 */
object ArrowBitmaps {

    const val ARROW_ORIGIN_Y = 1
    const val DIGIT_ORIGIN_Y = 7

    /** Vertical center of the arrow band (rows 1–6). */
    const val ARROW_BAND_CENTER_Y = 3

    // -------- Straight (↑) --------
    private val STRAIGHT = arrayOf(
        "......X......",
        ".....XoX.....",
        "....X.o.X....",
        "......o......",
        "......o......",
        "......o......",
    )

    // -------- Left (←) --------
    private val LEFT = arrayOf(
        ".............",
        "...X.........",
        "..X..........",
        ".Xooooo......",
        "..X..........",
        "...X.........",
    )

    // -------- Right (→) --------
    private val RIGHT = arrayOf(
        ".............",
        ".........X...",
        "..........X..",
        "......oooooX.",
        "..........X..",
        ".........X...",
    )

    // -------- Keep-left (↖) --------
    private val KEEP_LEFT = arrayOf(
        "...XXX.......",
        "...Xo........",
        "...X.o.......",
        "......o......",
        ".......o.....",
        ".............",
    )

    // -------- Keep-right (↗) --------
    private val KEEP_RIGHT = arrayOf(
        ".......XXX...",
        "........oX...",
        ".......o.X...",
        "......o......",
        ".....o.......",
        ".............",
    )

    // -------- Sharp left (↙, >130° acute turn) --------
    private val SHARP_LEFT = arrayOf(
        ".......oo....",
        "......o.o....",
        "...X.o..o....",
        "...Xo...o....",
        "...XXX..o....",
        ".............",
    )

    // -------- Sharp right (↘, exact mirror of SHARP_LEFT) --------
    private val SHARP_RIGHT = arrayOf(
        "....oo.......",
        "....o.o......",
        "....o..o.X...",
        "....o...oX...",
        "....o..XXX...",
        ".............",
    )

    // -------- Forward-left (↑←) --------
    private val FORWARD_LEFT = arrayOf(
        ".....X.......",
        "....X........",
        "...X.oooo....",
        "....X...o....",
        ".....X..o....",
        "........o....",
    )

    // -------- Forward-right (↑→) --------
    private val FORWARD_RIGHT = arrayOf(
        ".......X.....",
        "........X....",
        "....oooo.X...",
        "....o...X....",
        "....o..X.....",
        "....o........",
    )

    // -------- Roundabout (⟳) --------
    private val ROUNDABOUT = arrayOf(
        "....ooooo....",
        "...o.....o...",
        "...o...X.X.X.",
        "...+....X.X..",
        "...+.....X...",
        "....+++++....",
    )

    // -------- U-turn (↶) --------
    private val UTURN = arrayOf(
        "....++++++...",
        "....+....+...",
        "....+....+...",
        "..X.+.X..+...",
        "...X.X...+...",
        "....X....+...",
    )

    // -------- Arrive (●) --------
    private val ARRIVE = arrayOf(
        ".....+++.....",
        "....+...+....",
        "....+.X.+....",
        "....+...+....",
        ".....+++.....",
    )

    fun patternFor(maneuver: Maneuver): Array<String> = when (maneuver) {
        Maneuver.STRAIGHT -> STRAIGHT
        Maneuver.LEFT -> LEFT
        Maneuver.RIGHT -> RIGHT
        Maneuver.SHARP_LEFT -> SHARP_LEFT
        Maneuver.SHARP_RIGHT -> SHARP_RIGHT
        Maneuver.KEEP_LEFT -> KEEP_LEFT
        Maneuver.KEEP_RIGHT -> KEEP_RIGHT
        Maneuver.FORWARD_LEFT -> FORWARD_LEFT
        Maneuver.FORWARD_RIGHT -> FORWARD_RIGHT
        Maneuver.ROUNDABOUT -> ROUNDABOUT
        Maneuver.UTURN -> UTURN
        Maneuver.ARRIVE -> ARRIVE
    }

    /**
     * Origin that places the lit bounding box of [pattern] on the matrix
     * center column and in the arrow band (above the distance digits).
     */
    fun originFor(pattern: Array<String>): PatternOrigin {
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for ((y, row) in pattern.withIndex()) {
            for ((x, ch) in row.withIndex()) {
                if (ch != '.' && !ch.isWhitespace()) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (minX == Int.MAX_VALUE) return PatternOrigin(0, ARROW_ORIGIN_Y)

        val boxCx = (minX + maxX) / 2f
        val boxCy = (minY + maxY) / 2f
        val ox = (MatrixFrame.CENTER - boxCx).roundToInt()
        val oy = (ARROW_BAND_CENTER_Y - boxCy).roundToInt()

        // Keep the bbox inside the 13×13 grid when possible.
        val width = maxX - minX + 1
        val height = maxY - minY + 1
        val clampedX = ox.coerceIn(0 - minX, MatrixFrame.SIZE - minX - width)
        val maxYOrigin = (DIGIT_ORIGIN_Y - height).coerceAtLeast(0)
        val clampedY = oy.coerceIn(0, maxYOrigin)
        return PatternOrigin(clampedX, clampedY)
    }

    fun originFor(maneuver: Maneuver): PatternOrigin = originFor(patternFor(maneuver))
}
