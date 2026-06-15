package com.glyphnavtoy.glyph

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
 * All patterns are 6 rows × 13 cols, stamped at (0, [ARROW_ORIGIN_Y]). Each
 * respects the circular mask (row widths: 5/7/11/13/13/13/13).
 */
object ArrowBitmaps {

    const val ARROW_ORIGIN_Y = 1
    const val DIGIT_ORIGIN_Y = 7

    // -------- Straight (↑) --------
    // Chevron tip at top, two 2-cell diagonal arms (5 wide at the base),
    // shaft of medium-bright tail extending down through the center column.
    private val STRAIGHT = arrayOf(
        "......X......",
        ".....XoX.....",
        "....X.o.X....",
        "......o......",
        "......o......",
        "......o......",
    )

    // -------- Left (←) --------
    // Chevron tip on the left, 5-row tall chevron (arms extend 2 cells
    // diagonally up-right and down-right), 5-cell horizontal lit tail right.
    private val LEFT = arrayOf(
        ".............",
        "...X.........",
        "..X..........",
        ".Xooooo......",
        "..X..........",
        "...X.........",
    )

    // -------- Right (→) --------
    // Mirror of LEFT.
    private val RIGHT = arrayOf(
        ".............",
        ".........X...",
        "..........X..",
        "......oooooX.",
        "..........X..",
        ".........X...",
    )

    // -------- Keep-left (↖) --------
    // L-shaped head in the NW corner (3 cells horizontal + 3 cells vertical
    // sharing the corner pixel), 3-cell diagonal lit tail going SE.
    private val KEEP_LEFT = arrayOf(
        "...XXX.......",
        "...Xo........",
        "...X.o.......",
        "......o......",
        ".......o.....",
        ".............",
    )

    // -------- Keep-right (↗) --------
    // Mirror of KEEP_LEFT.
    private val KEEP_RIGHT = arrayOf(
        ".......XXX...",
        "........oX...",
        ".......o.X...",
        "......o......",
        ".....o.......",
        ".............",
    )

    // -------- Sharp left (↙, >130° acute turn) --------
    // Turn-angle-as-arrow-angle: KEEP_LEFT points ↖ (slight), LEFT points ←
    // (90°), SHARP_LEFT points ↙ (>90°, curving back down). The arrow's angle
    // literally encodes turn severity. Solid filled head at bottom-left so it
    // reads unambiguously (and never gets confused with the U-turn loop).
    // Dim tail (o) traces in from the upper-right; bright head (X) at the tip.
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
    // Matches prototype 015356. A 5-row chevron tip points LEFT (tip at row
    // 2 col 3), with an L-shaped lit tail: up the right side (col 8, rows
    // 2-5), bending left along row 2 (cols 5-8) into the chevron's back.
    // Reads as "you came from below, you're now turning left."
    private val FORWARD_LEFT = arrayOf(
        ".....X.......",
        "....X........",
        "...X.oooo....",
        "....X...o....",
        ".....X..o....",
        "........o....",
    )

    // -------- Forward-right (↑→) --------
    // Mirror of FORWARD_LEFT. Matches prototype 015346.
    private val FORWARD_RIGHT = arrayOf(
        ".......X.....",
        "........X....",
        "....oooo.X...",
        "....o...X....",
        "....o..X.....",
        "....o........",
    )

    // -------- Roundabout (⟳) --------
    // Prototype-inspired ring with a clear right-side exit arrow.
    private val ROUNDABOUT = arrayOf(
        "....ooooo....",
        "...o.....o...",
        "...o...X.X.X.",
        "...+....X.X..",
        "...+.....X...",
        "....+++++....",
        ".............",
    )

    // -------- U-turn (↶) --------
    // Arrowhead (exiting left) bright, U body holds at mid brightness so
    // the eye reads the loop without the arrowhead getting drowned out.
    private val UTURN = arrayOf(
        "....++++++...",
        "....+....+...",
        "....+....+...",
        "..X.+.X..+...",
        "...X.X...+...",
        "....X....+...",
    )

    // -------- Arrive (●) --------
    // Center dot bright (the destination "pin"), ring at mid brightness.
    private val ARRIVE = arrayOf(
        ".....+++.....",
        "....+...+....",
        "....+.X.+....",
        "....+...+....",
        ".....+++.....",
        ".............",
        ".............",
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
}
