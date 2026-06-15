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
