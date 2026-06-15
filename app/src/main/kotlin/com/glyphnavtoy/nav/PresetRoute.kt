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

    /** Title used in the dropdown: "Downtown to Home (3 turns, 1.2km)". */
    val title: String
        get() = "$name ($turnCount turns, $totalDistance)"
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
