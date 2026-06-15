package com.glyphnavtoy.glyph

import com.glyphnavtoy.nav.NavState

/**
 * Pure: NavState + marquee offset → MatrixFrame.
 *
 * Layout:
 *  - Top half (rows 1-6): static maneuver arrow with built-in brightness
 *    gradient (head bright, trail dim) from [ArrowBitmaps].
 *  - Bottom half (rows 7-11): distance text marquee scrolls left→right,
 *    looping with a small visual gap. Swaps mid-scroll when the value
 *    changes.
 *
 * Same composer powers the on-screen preview AND the LED matrix, so what
 * you see on screen ≡ what's on the LEDs.
 */
object MatrixComposer {

    /** Trailing blank pixels after the marquee text before it wraps. */
    private const val MARQUEE_GAP = 4

    /** Tick rate for the marquee scroll. */
    const val MARQUEE_TICK_MS = 100L
    const val MARQUEE_STEP = 1

    /**
     * Compose a frame for the given [state].
     *
     * @param mode STATIC = lit chevron + tail straight from [ArrowBitmaps].
     *             FLOWING = sweep animation from [ArrowAnimations], cycled by
     *             [animationTick]. The animation loops forever (when the tick
     *             passes the frame count it wraps back to 0).
     * @param animationTick Frame index — increments roughly every
     *             [ArrowAnimations.FRAME_DURATION_MS] in the live loop.
     * @param head Bright-pixel brightness (0-255). Defaults to the per-maneuver
     *             value from [GlyphSettings]. Digits/marquee follow head so the
     *             distance stays as legible as the arrowhead.
     * @param tail Dim-pixel brightness (0-255). Per-maneuver default. Only used
     *             in STATIC mode — the FLOWING sweep is a continuous gradient
     *             with no discrete tail tier, so it scales by head alone.
     */
    fun compose(
        state: NavState,
        marqueeOffset: Int = 0,
        mode: AnimationMode = AnimationMode.STATIC,
        animationTick: Int = 0,
        head: Int = GlyphSettings.headFor(state.maneuver),
        tail: Int = GlyphSettings.tailFor(state.maneuver),
    ): MatrixFrame {
        val frame = MatrixFrame()

        // Top half: arrow region.
        when (mode) {
            AnimationMode.STATIC -> frame.stamp(
                originX = 0,
                originY = ArrowBitmaps.ARROW_ORIGIN_Y,
                pattern = ArrowBitmaps.patternFor(state.maneuver),
                head = head,
                tail = tail,
            )
            AnimationMode.FLOWING -> {
                val frames = ArrowSweep.framesFor(state.maneuver)
                if (frames.isNotEmpty()) {
                    val idx = animationTick.mod(frames.size)
                    // Sweep is derived from the static pattern: a settled frame
                    // ≡ the static arrow, so STATIC and FLOWING always match.
                    ArrowSweep.stamp(frame, frames[idx], head, tail)
                } else {
                    // No sweep path (e.g. empty pattern) → fall back to static.
                    frame.stamp(
                        originX = 0,
                        originY = ArrowBitmaps.ARROW_ORIGIN_Y,
                        pattern = ArrowBitmaps.patternFor(state.maneuver),
                        head = head,
                        tail = tail,
                    )
                }
            }
        }

        // Bottom half: scrolling marquee. Digits follow head (stay legible).
        state.shortDistance()?.let { text ->
            val textWidth = DigitFont.measure(text)
            val cycle = textWidth + MatrixFrame.SIZE + MARQUEE_GAP
            val pos = marqueeOffset.mod(cycle)
            val startX = -textWidth + pos
            DigitFont.renderTo(frame, text, startX, ArrowBitmaps.DIGIT_ORIGIN_Y, head)
        }

        return frame
    }
}
