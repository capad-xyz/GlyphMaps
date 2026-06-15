package com.glyphnavtoy.nav

import com.glyphnavtoy.glyph.Maneuver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Rich snapshot of "what Maps is currently telling us." Strictly more
 * information than [NavState] — that's the slim render-side type the matrix
 * cares about. This one carries everything we lift from the notification so
 * the in-app UI can show the same context the user would see in Maps itself.
 */
data class LiveNavSnapshot(
    /** Bucketed maneuver after distance-aware morphing (LEFT → FORWARD_LEFT etc). */
    val maneuver: Maneuver,
    /** Original maneuver before morphing — for the "you'll bend later" indicator. */
    val rawManeuver: Maneuver,
    /** Distance to next maneuver in meters. Null while Maps hasn't announced one. */
    val distanceMeters: Int?,
    /** Full title text as Maps wrote it (e.g. "60 m · Turn left onto ADM Rd"). */
    val title: String,
    /** Street/road name extracted from the title (e.g. "ADM Rd"). Null when not applicable. */
    val streetName: String?,
    /** ETA string from notification.subText (e.g. "Arrive 5:54 am"). */
    val eta: String?,
    /** Meters traveled along the full route so far. */
    val progressMeters: Int?,
    /** Total route distance in meters. */
    val progressMaxMeters: Int?,
    /** System time when this snapshot was captured. */
    val capturedAtMs: Long = System.currentTimeMillis(),
)

/**
 * Singleton in-memory bridge between [com.glyphnavtoy.service.MapsNotificationListener]
 * and the Compose UI in MainActivity.
 *
 * Listener writes → repo emits → MainActivity collects → recomposes.
 *
 * Why a singleton (vs Service-binding or LocalBroadcast):
 *  - Listener and Activity are in the same process — same JVM, same heap
 *  - Compose loves StateFlow; one line in the composable wires it up
 *  - Survives Activity recreate (config change) because it's an object,
 *    not held by the Activity instance
 *  - No IPC boilerplate
 */
object NavStateRepo {
    private val _live = MutableStateFlow<LiveNavSnapshot?>(null)
    val live: StateFlow<LiveNavSnapshot?> = _live.asStateFlow()

    fun update(snapshot: LiveNavSnapshot) {
        _live.value = snapshot
    }

    /** Cleared when Maps' nav notification is dismissed (nav ended). */
    fun clear() {
        _live.value = null
    }
}
