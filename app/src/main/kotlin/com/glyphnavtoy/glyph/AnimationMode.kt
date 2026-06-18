// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 capad.io <capad.xyz@gmail.com>
// GlyphMaps - licensed under AGPL-3.0 (see LICENSE).
// Brand assets (name, icon, artwork) are NOT covered by the AGPL; see NOTICE.

package com.glyphnavtoy.glyph

/**
 * How the arrow region renders. The digit half (distance) is always static —
 * only the maneuver indicator changes.
 */
enum class AnimationMode {
    /** Single static frame, instant push. */
    STATIC,

    /** Looping "sweep" animation tracing the arrow path. */
    FLOWING,
}
