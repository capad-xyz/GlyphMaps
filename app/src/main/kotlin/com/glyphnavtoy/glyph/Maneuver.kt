// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 capad.io <capad.xyz@gmail.com>
// GlyphMaps - licensed under AGPL-3.0 (see LICENSE).
// Brand assets (name, icon, artwork) are NOT covered by the AGPL; see NOTICE.

package com.glyphnavtoy.glyph

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
        fun fromMapsString(raw: String?): Maneuver {
            val s = raw?.lowercase().orEmpty()
            return when {
                // Endpoint. "destination is on the left" is still arrival.
                "arriving" in s || "destination" in s || "you-have-arrived" in s ||
                    (s.startsWith("arrive") || "-arrive" in s) -> ARRIVE

                "uturn" in s || "u-turn" in s || "make-a-u-turn" in s -> UTURN

                "roundabout-sharp-left" in s -> SHARP_LEFT
                "roundabout-sharp-right" in s -> SHARP_RIGHT
                "roundabout-slight-left" in s -> KEEP_LEFT
                "roundabout-slight-right" in s -> KEEP_RIGHT
                "roundabout-left" in s -> LEFT
                "roundabout-right" in s -> RIGHT
                "roundabout-straight" in s -> STRAIGHT
                "roundabout-uturn" in s || "roundabout-u-turn" in s -> UTURN
                "exit-the-roundabout" in s -> STRAIGHT

                "roundabout" in s && ("turn-left" in s || "take-the-left" in s) -> LEFT
                "roundabout" in s && ("turn-right" in s || "take-the-right" in s) -> RIGHT
                "roundabout" in s && ("continue-straight" in s || "go-straight" in s) -> STRAIGHT
                "roundabout" in s -> ROUNDABOUT

                ("jct" in s || "junction" in s) && "exit" in s -> ROUNDABOUT

                "take-the-exit" in s || "take-exit" in s -> KEEP_RIGHT
                "fork-left" in s -> FORWARD_LEFT
                "fork-right" in s -> FORWARD_RIGHT

                "sharp-left" in s -> SHARP_LEFT
                "sharp-right" in s -> SHARP_RIGHT

                "turn-left" in s || "make-a-left" in s || "take-a-left" in s ||
                    "take-the-left" in s || "left-onto" in s -> LEFT
                "turn-right" in s || "make-a-right" in s || "take-a-right" in s ||
                    "take-the-right" in s || "right-onto" in s -> RIGHT

                "merge-left" in s || "bear-left" in s || "veer-left" in s ||
                    "slight-left" in s || "ramp-left" in s || "off-ramp-left" in s ||
                    "on-ramp-left" in s || "keep-left" in s || "stay-left" in s ||
                    "stay-on-the-left" in s -> KEEP_LEFT
                "merge-right" in s || "bear-right" in s || "veer-right" in s ||
                    "slight-right" in s || "ramp-right" in s || "off-ramp-right" in s ||
                    "on-ramp-right" in s || "keep-right" in s || "stay-right" in s ||
                    "stay-on-the-right" in s -> KEEP_RIGHT

                else -> STRAIGHT
            }
        }
    }
}
