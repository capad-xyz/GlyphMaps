package com.glyphnavtoy.glyph

/**
 * Turn-by-turn maneuvers we render on the Glyph Matrix.
 *
 * Bucket strategy: Google Maps' Routes API has 16 maneuver constants
 * (slight/regular/sharp variants per direction, ramps, forks, etc.). We
 * collapse to a pragmatic set that maps cleanly to the 13×13 matrix while
 * preserving the SHARP distinction (research showed Google announces sharp
 * turns differently — they're visually distinct in Maps' own icons too).
 *
 *  - STRAIGHT          – continue, merge, name-change
 *  - KEEP_LEFT/RIGHT   – slight diagonal, ramp, off-ramp (≤30° bend)
 *  - FORWARD_LEFT/RIGHT – fork / "go straight then bend" stem+arm shape
 *  - LEFT/RIGHT        – ~90° turn (regular intersection)
 *  - SHARP_LEFT/RIGHT  – >130° acute turn (kept distinct — users need
 *                        more reaction time for sharp turns, and the
 *                        morph-to-FORWARD preview is skipped for these)
 *  - ROUNDABOUT        – multi-exit roundabout where Maps doesn't
 *                        specify a directional sub-action
 *  - UTURN             – any U-turn
 *  - ARRIVE            – destination / "Arriving"
 *
 * [fromMapsString] does the mapping from Google's raw vocabulary. Order of
 * checks matters — see comments inline for precedence rules.
 */
enum class Maneuver {
    STRAIGHT,
    KEEP_LEFT,
    LEFT,
    SHARP_LEFT,
    KEEP_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    FORWARD_LEFT,
    FORWARD_RIGHT,
    ROUNDABOUT,
    UTURN,
    ARRIVE;

    companion object {
        /**
         * Map a free-form Google Maps maneuver string to one of our buckets.
         *
         * Handles both:
         *  - Routes API constants (`turn-sharp-left`, `roundabout-left`, etc.)
         *  - User-facing notification text (`"At the roundabout, turn left"`,
         *    `"Sharp left toward X"`, `"Exit the roundabout onto Y"`)
         *
         * Precedence rules (most-specific first):
         *  1. Endpoint (`arriving`, `destination`) — ends nav, render flag
         *  2. U-turn — distinctive enough to win
         *  3. Roundabout sub-actions ("at the roundabout, turn left") —
         *     directional action wins over generic roundabout icon
         *  4. Generic roundabout — circle icon
         *  5. Junctions — same visual as roundabouts
         *  6. Exits / forks
         *  7. **Sharp** before regular turn (so "sharp-left" doesn't get
         *     swallowed by the `turn-left` rule)
         *  8. Regular 90° turns
         *  9. Merges, ramps, slights, keeps
         * 10. Default STRAIGHT
         */
        fun fromMapsString(raw: String?): Maneuver {
            val s = raw?.lowercase().orEmpty()
            return when {
                // ---- 1. Endpoint ----
                // "Arriving" fires near end of trip; "destination/arrive" too.
                "arriving" in s || "destination" in s || "arrive" in s -> ARRIVE

                // ---- 2. U-turn (any variant) ----
                "uturn" in s || "u-turn" in s -> UTURN

                // ---- 3. Roundabout sub-actions from API names ----
                "roundabout-sharp-left" in s -> SHARP_LEFT
                "roundabout-sharp-right" in s -> SHARP_RIGHT
                "roundabout-slight-left" in s -> KEEP_LEFT
                "roundabout-slight-right" in s -> KEEP_RIGHT
                "roundabout-left" in s -> LEFT
                "roundabout-right" in s -> RIGHT
                "roundabout-straight" in s -> STRAIGHT
                "roundabout-uturn" in s || "roundabout-u-turn" in s -> UTURN

                // "Exit the roundabout" fires AFTER the decision — you've
                // already taken your exit. STRAIGHT reads accurately now.
                "exit-the-roundabout" in s -> STRAIGHT

                // Roundabout sub-actions from USER-FACING text:
                // "At the roundabout, turn left onto X" — directional action
                // wins over the generic circle icon. Same for right / straight.
                "roundabout" in s && "turn-left" in s -> LEFT
                "roundabout" in s && "turn-right" in s -> RIGHT
                "roundabout" in s && "continue-straight" in s -> STRAIGHT

                // ---- 4. Generic roundabout (multi-exit, no specific direction) ----
                "roundabout" in s -> ROUNDABOUT

                // ---- 5. Numbered junctions — same UX as roundabouts ----
                ("jct" in s || "junction" in s) && "exit" in s -> ROUNDABOUT

                // ---- 6. Highway off-ramp / fork ----
                "take-the-exit" in s -> KEEP_RIGHT
                "fork-left" in s -> FORWARD_LEFT
                "fork-right" in s -> FORWARD_RIGHT

                // ---- 7. SHARP turns (must come before generic "turn-left") ----
                "sharp-left" in s -> SHARP_LEFT
                "sharp-right" in s -> SHARP_RIGHT

                // ---- 8. Regular 90° turns ----
                "turn-left" in s -> LEFT
                "turn-right" in s -> RIGHT

                // ---- 9. Merges, ramps, slights, keeps (all shallow) ----
                "merge-left" in s -> KEEP_LEFT
                "merge-right" in s -> KEEP_RIGHT
                "slight-left" in s || "ramp-left" in s ||
                    "off-ramp-left" in s || "on-ramp-left" in s ||
                    "keep-left" in s -> KEEP_LEFT
                "slight-right" in s || "ramp-right" in s ||
                    "off-ramp-right" in s || "on-ramp-right" in s ||
                    "keep-right" in s -> KEEP_RIGHT

                // ---- 10. Default: continue forward ----
                else -> STRAIGHT
            }
        }
    }
}
