package com.glyphnavtoy.nav

import com.glyphnavtoy.glyph.Maneuver

/**
 * Snapshot of "what should the matrix show right now."
 *
 * Produced by [com.glyphnavtoy.service.MapsNotificationListener] when Maps posts
 * an updated nav notification; consumed by [com.glyphnavtoy.glyph.GlyphRenderer]
 * which converts it to a frame and pushes via setAppMatrixFrame.
 */
data class NavState(
    val maneuver: Maneuver,
    /** Distance to the next maneuver, in meters. Null if Maps hasn't told us yet. */
    val distanceMeters: Int?,
    /** Short label for the next street/road. Optional, currently unused on-matrix. */
    val nextStreet: String? = null
) {
    /**
     * Distance formatted for the marquee — always includes a unit suffix.
     *
     * - <1000 m → "300m"
     * - 1000-9999 m → "1.5k"
     * - 10000+ m → "12k"
     */
    fun shortDistance(): String? {
        val m = distanceMeters ?: return null
        return when {
            m < 1000 -> "${m}m"
            m < 10000 -> "%.1fk".format(m / 1000.0)
            else -> "${m / 1000}k"
        }
    }

    companion object {
        val ARRIVED = NavState(Maneuver.ARRIVE, distanceMeters = 0)
    }
}
