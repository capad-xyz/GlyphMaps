package com.glyphnavtoy.nav

import com.glyphnavtoy.glyph.Maneuver

/**
 * One step of a simulated route — what would be one "navigation instruction"
 * in Maps. Held in a list, played in order by the Live Route Simulation card.
 *
 * @param streetName short label shown in the current-direction card header.
 * @param instruction one-line driver-facing instruction (e.g. "Turn left onto Main St").
 * @param distanceMeters distance until the maneuver takes effect.
 * @param holdMillis how long to dwell on this step before advancing. Used by
 *   the simulator coroutine so the matrix actually changes at a watchable rate.
 */
data class RouteStep(
    val maneuver: Maneuver,
    val streetName: String,
    val instruction: String,
    val distanceMeters: Int,
    val holdMillis: Long = 4_000L,
)

/** A named sequence of steps. */
data class PresetRoute(
    val name: String,
    val steps: List<RouteStep>,
) {
    /** Total distance for the title (e.g. "1.2km"). */
    val totalDistance: String
        get() {
            val sum = steps.sumOf { it.distanceMeters }
            return if (sum < 1000) "${sum}m" else "%.1fkm".format(sum / 1000.0)
        }

    /** Number of turns excluding the final ARRIVE. */
    val turnCount: Int
        get() = steps.count { it.maneuver != Maneuver.ARRIVE && it.maneuver != Maneuver.STRAIGHT }

    /**
     * Title used in the dropdown: "Downtown to Home (3 turns, 1.2km)".
     *
     * A showcase reel carries no meaningful total distance (every step is
     * distance 0 so the matrix shows a clean arrow), so it's labelled by step
     * count instead of "(… turns, 0m)".
     */
    val title: String
        get() = if (steps.sumOf { it.distanceMeters } > 0)
            "$name ($turnCount turns, $totalDistance)"
        else
            "$name (${steps.size} steps)"
}

/**
 * The handful of canned routes the picker offers. Distances + street names
 * are illustrative — the point is to demonstrate the visual variety of
 * maneuvers on the matrix, not to be geographically accurate.
 */
object PresetRoutes {

    val all: List<PresetRoute> = listOf(
        PresetRoute(
            name = "Downtown Office to Home",
            steps = listOf(
                RouteStep(Maneuver.STRAIGHT, "Grand Avenue",
                    "Head north on Grand Avenue", 250),
                RouteStep(Maneuver.RIGHT, "5th Street",
                    "Turn right onto 5th Street", 150),
                RouteStep(Maneuver.KEEP_LEFT, "Main Boulevard",
                    "Keep left to stay on Main Blvd", 800),
                RouteStep(Maneuver.ARRIVE, "Home",
                    "You have arrived", 0, holdMillis = 6_000L),
            ),
        ),
        PresetRoute(
            name = "Highway Commute",
            steps = listOf(
                RouteStep(Maneuver.STRAIGHT, "Elm Street",
                    "Head east on Elm Street", 500),
                RouteStep(Maneuver.KEEP_RIGHT, "I-95 N On-Ramp",
                    "Take ramp right onto I-95 N", 300),
                RouteStep(Maneuver.STRAIGHT, "I-95 North",
                    "Continue north on I-95", 12_000),
                RouteStep(Maneuver.FORWARD_RIGHT, "Exit 24",
                    "In 800m take exit 24", 800),
                RouteStep(Maneuver.RIGHT, "Local Road",
                    "Turn right at end of ramp", 200),
                RouteStep(Maneuver.ARRIVE, "Workplace",
                    "You have arrived", 0, holdMillis = 6_000L),
            ),
        ),
        PresetRoute(
            name = "Quick Errand",
            steps = listOf(
                RouteStep(Maneuver.STRAIGHT, "Oak Lane",
                    "Head west on Oak Lane", 100),
                RouteStep(Maneuver.LEFT, "Pine Road",
                    "Turn left at the lights", 50),
                RouteStep(Maneuver.ARRIVE, "Coffee Shop",
                    "You have arrived", 0, holdMillis = 6_000L),
            ),
        ),
        PresetRoute(
            name = "Tricky City Block",
            steps = listOf(
                RouteStep(Maneuver.STRAIGHT, "Hill Street",
                    "Head south on Hill Street", 300),
                RouteStep(Maneuver.FORWARD_LEFT, "Bridge Avenue",
                    "Continue then turn left onto Bridge Ave", 200),
                RouteStep(Maneuver.UTURN, "Bridge Avenue",
                    "Make a U-turn at the roundabout", 80),
                RouteStep(Maneuver.KEEP_RIGHT, "Riverside Road",
                    "Keep right onto Riverside Rd", 400),
                RouteStep(Maneuver.RIGHT, "Market Square",
                    "Turn right at Market Square", 120),
                RouteStep(Maneuver.ARRIVE, "Old Town",
                    "You have arrived", 0, holdMillis = 6_000L),
            ),
        ),

        // ---- Promo capture reel (not a real route — a filming aid) ----
        // Walks every arrow shape at a fixed, beat-friendly 1.5s cadence so the
        // matrix changes at an even rate you can cut to music. distance = 0 on
        // every step means shortDistance() is null → no scrolling marquee → a
        // PURE arrow on the LEDs, the cleanest possible hero / b-roll shot.
        // The street/instruction text only labels the on-screen dev card (it's
        // never drawn on the matrix), so the operator can see which shape is
        // filming. Order sweeps left-family → right-family → special → arrive.
        // Set Live sync ON, pick this, hit START, and film the back of the
        // phone in a dim room. ARRIVE dwells longer to capture the finale.
        PresetRoute(
            name = "🎬 Promo Reel",
            steps = listOf(
                RouteStep(Maneuver.STRAIGHT,      "Straight",      "STRAIGHT",      0, holdMillis = 1_500L),
                RouteStep(Maneuver.KEEP_LEFT,     "Keep left",     "KEEP LEFT",     0, holdMillis = 1_500L),
                RouteStep(Maneuver.LEFT,          "Left",          "LEFT",          0, holdMillis = 1_500L),
                RouteStep(Maneuver.SHARP_LEFT,    "Sharp left",    "SHARP LEFT",    0, holdMillis = 1_500L),
                RouteStep(Maneuver.FORWARD_LEFT,  "Forward-left",  "FORWARD LEFT",  0, holdMillis = 1_500L),
                RouteStep(Maneuver.KEEP_RIGHT,    "Keep right",    "KEEP RIGHT",    0, holdMillis = 1_500L),
                RouteStep(Maneuver.RIGHT,         "Right",         "RIGHT",         0, holdMillis = 1_500L),
                RouteStep(Maneuver.SHARP_RIGHT,   "Sharp right",   "SHARP RIGHT",   0, holdMillis = 1_500L),
                RouteStep(Maneuver.FORWARD_RIGHT, "Forward-right", "FORWARD RIGHT", 0, holdMillis = 1_500L),
                RouteStep(Maneuver.ROUNDABOUT,    "Roundabout",    "ROUNDABOUT",    0, holdMillis = 1_500L),
                RouteStep(Maneuver.UTURN,         "U-turn",        "U-TURN",        0, holdMillis = 1_500L),
                RouteStep(Maneuver.ARRIVE,        "Arrive",        "ARRIVE",        0, holdMillis = 3_000L),
            ),
        ),
    )
}

/**
 * Human-readable instruction phrase for a maneuver, used when no real Maps
 * data is available (manual override mode). Pairs with `streetName` to
 * build the current-direction headline.
 */
fun Maneuver.instructionVerb(): String = when (this) {
    Maneuver.STRAIGHT -> "Continue straight"
    Maneuver.KEEP_LEFT -> "Keep left"
    Maneuver.KEEP_RIGHT -> "Keep right"
    Maneuver.LEFT -> "Turn left"
    Maneuver.RIGHT -> "Turn right"
    Maneuver.SHARP_LEFT -> "Sharp left"
    Maneuver.SHARP_RIGHT -> "Sharp right"
    Maneuver.FORWARD_LEFT -> "Continue then turn left"
    Maneuver.FORWARD_RIGHT -> "Continue then turn right"
    Maneuver.ROUNDABOUT -> "At the roundabout"
    Maneuver.UTURN -> "Make a U-turn"
    Maneuver.ARRIVE -> "You have arrived"
}
