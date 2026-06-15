package com.glyphnavtoy.glyph

data class Pixel(val x: Int, val y: Int)

/** Sparse animation frame: only lit pixels are listed. brightness ∈ [0,1]. */
typealias Frame = Map<Pixel, Float>

/**
 * Shared animation primitives.
 *
 * The maneuver *sweep* (the FLOWING mode behind the arrows) now lives in
 * [ArrowSweep], which derives its frames procedurally from the static
 * [ArrowBitmaps] pattern so the two can never drift apart again.
 *
 * What remains here is the simple sparse-frame model + [stampFrame], still used
 * by the decorative concept animations ([StartupAnimations] radar,
 * [ArrivalConcepts] pulse) that aren't tied to a maneuver shape.
 *
 * All coordinates are in matrix-space (y already includes any origin offset).
 */
object ArrowAnimations {

    /** Same tick as the marquee — one shared clock keeps things in lockstep. */
    const val FRAME_DURATION_MS = 100L

    /**
     * Stamp a sparse [frame] onto the matrix.
     *
     * @param scale Max brightness (0-255) the brightest pixel maps to. The
     *   frame is a continuous 0..1 gradient, scaled uniformly.
     */
    fun stampFrame(target: MatrixFrame, frame: Frame, scale: Int = 255) {
        for ((p, b) in frame) {
            target.set(p.x, p.y, (b.coerceIn(0f, 1f) * scale).toInt())
        }
    }
}
