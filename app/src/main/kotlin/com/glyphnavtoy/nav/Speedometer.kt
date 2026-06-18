// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 capad.io <capad.xyz@gmail.com>
// GlyphMaps - licensed under AGPL-3.0 (see LICENSE).
// Brand assets (name, icon, artwork) are NOT covered by the AGPL; see NOTICE.

package com.glyphnavtoy.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Derives current driving/walking speed from successive `android.progress`
 * deltas in Maps notifications, and converts that to a Google-Maps-style
 * **time-based** morph threshold.
 *
 * Why time-based: a fixed 300 m threshold is wrong at both ends —
 *  - Walking @ 5 km/h: 300 m = 3.5 min, way too early for FORWARD_*
 *  - Highway @ 100 km/h: 300 m = 11 s, way too late
 *
 * Research finding (Google's own preview-to-imperative window):
 *  Maps switches from "in 500 m, turn left" to "Turn left" roughly 15-20 s
 *  out from the maneuver, scaled by vehicle speed. We adopt the same window:
 *
 *      threshold_m = MORPH_TARGET_SECONDS × current_speed_mps
 *
 *  - distance > threshold → FORWARD_*  (preview — "you'll bend later")
 *  - distance ≤ threshold → direct LEFT/RIGHT (commit to turn)
 *
 * Sharp turns intentionally bypass this morph entirely — they're rare and
 * surprising enough that always-direct rendering serves the user better.
 *
 * Speed is exponentially smoothed (EMA, alpha=0.4) so a single noisy delta
 * doesn't yank the threshold around.
 */
object Speedometer {

    /** Target time-to-turn at which we flip from preview to direct icon. */
    private const val MORPH_TARGET_SECONDS = 15.0

    /** Lower bound — even stationary, never preview turns < 50 m. */
    private const val MIN_THRESHOLD_M = 50

    /** Upper bound — never preview > 1500 m even at highway speed. */
    private const val MAX_THRESHOLD_M = 1500

    /** Default threshold when we haven't observed two updates yet. */
    private const val DEFAULT_THRESHOLD_M = 300

    /** EMA blending — higher = more responsive, lower = smoother. */
    private const val EMA_ALPHA = 0.4

    /** Updates faster than this between notifications get ignored (noise). */
    private const val MIN_DELTA_SECONDS = 0.5

    private var prevProgressM: Int? = null
    private var prevTimeMs: Long? = null
    private val _speedMps = MutableStateFlow(0.0)

    /** Smoothed speed in m/s. Exposed for the in-app debug readout. */
    val speedMps: StateFlow<Double> = _speedMps.asStateFlow()

    /**
     * Feed a fresh `android.progress` reading from the notification. Call on
     * every Maps notification update before checking [thresholdMeters].
     */
    fun observe(progressM: Int?, nowMs: Long = System.currentTimeMillis()) {
        val prev = prevProgressM
        val prevT = prevTimeMs

        if (prev != null && prevT != null && progressM != null) {
            val deltaM = progressM - prev
            val deltaSec = (nowMs - prevT) / 1000.0

            // Forward progress + minimum sample interval = trustworthy delta.
            // Reroutes / GPS jumps cause backward progress → skip the update.
            if (deltaM > 0 && deltaSec > MIN_DELTA_SECONDS) {
                val instant = deltaM / deltaSec
                val current = _speedMps.value
                _speedMps.value = if (current == 0.0) instant
                else current * (1 - EMA_ALPHA) + instant * EMA_ALPHA
            }
        }

        prevProgressM = progressM
        prevTimeMs = nowMs
    }

    /**
     * The current morph threshold in meters. Match Maps' own ~15-second
     * preview window when we have a speed signal; fall back to a fixed
     * 300 m before we've observed two updates.
     */
    fun thresholdMeters(): Int {
        val mps = _speedMps.value
        if (mps < 0.5) return DEFAULT_THRESHOLD_M  // ~no signal / very slow
        return (MORPH_TARGET_SECONDS * mps).toInt().coerceIn(MIN_THRESHOLD_M, MAX_THRESHOLD_M)
    }

    /** Reset between trips (call on notification removed). */
    fun reset() {
        prevProgressM = null
        prevTimeMs = null
        _speedMps.value = 0.0
    }

    /** Convenience: "32 km/h" for in-app display. */
    fun speedKmhString(): String = "%.0f km/h".format(_speedMps.value * 3.6)
}
