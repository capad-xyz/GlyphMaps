// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 capad.io <capad.xyz@gmail.com>
// GlyphMaps - licensed under AGPL-3.0 (see LICENSE).
// Brand assets (name, icon, artwork) are NOT covered by the AGPL; see NOTICE.

package com.glyphnavtoy.glyph

object ArrivalConcepts {

    const val FRAME_DURATION_MS = 120L

    enum class Concept {
        PROTOTYPE_PULSE,
        FLAG_WAVE,
    }

    fun frameAt(concept: Concept, tick: Int): MatrixFrame {
        val frame = MatrixFrame()
        val frames = framesFor(concept)
        if (frames.isNotEmpty()) {
            val idx = tick.mod(frames.size)
            ArrowAnimations.stampFrame(frame, frames[idx])
        }
        return frame
    }

    private fun framesFor(concept: Concept): List<Frame> = when (concept) {
        Concept.PROTOTYPE_PULSE -> PROTOTYPE_FRAMES
        Concept.FLAG_WAVE -> FLAG_WAVE_FRAMES
    }

    // Prototype arrive pulse (from nothing-glyph-matrix app.js)
    private val PROTOTYPE_LEVEL0 = listOf(
        Pixel(6, 6),
    )
    private val PROTOTYPE_LEVEL1 = listOf(
        Pixel(6, 5), Pixel(6, 7), Pixel(5, 6), Pixel(7, 6),
        Pixel(5, 5), Pixel(7, 5), Pixel(5, 7), Pixel(7, 7),
    )
    private val PROTOTYPE_LEVEL2 = listOf(
        Pixel(6, 4), Pixel(6, 8), Pixel(4, 6), Pixel(8, 6),
        Pixel(4, 4), Pixel(8, 4), Pixel(4, 8), Pixel(8, 8),
        Pixel(5, 4), Pixel(7, 4), Pixel(5, 8), Pixel(7, 8),
        Pixel(4, 5), Pixel(4, 7), Pixel(8, 5), Pixel(8, 7),
    )

    private val PROTOTYPE_FRAMES: List<Frame> = listOf(
        buildFrame {
            add(PROTOTYPE_LEVEL0, 1.0f)
        },
        buildFrame {
            add(PROTOTYPE_LEVEL0, 0.4f)
            add(PROTOTYPE_LEVEL1, 1.0f)
        },
        buildFrame {
            add(PROTOTYPE_LEVEL1, 0.4f)
            add(PROTOTYPE_LEVEL2, 1.0f)
        },
        buildFrame {
            add(PROTOTYPE_LEVEL2, 0.4f)
        },
        buildFrame {
            add(PROTOTYPE_LEVEL2, 0.1f)
        },
        emptyMap(),
    )

    // Original concept: waving flag + sparkle dance
    private val FLAG_POLE = listOf(
        Pixel(2, 3), Pixel(2, 4), Pixel(2, 5), Pixel(2, 6),
        Pixel(2, 7), Pixel(2, 8), Pixel(2, 9),
    )

    private val FLAG_WAVE_FRAMES: List<Frame> = listOf(
        buildFrame {
            add(FLAG_POLE, 0.7f)
            add(
                listOf(
                    Pixel(3, 3), Pixel(4, 3), Pixel(5, 3), Pixel(6, 3),
                    Pixel(3, 4), Pixel(4, 4), Pixel(5, 4),
                    Pixel(3, 5), Pixel(4, 5),
                ),
                1.0f,
            )
            add(listOf(Pixel(9, 3), Pixel(10, 4)), 1.0f)
        },
        buildFrame {
            add(FLAG_POLE, 0.7f)
            add(
                listOf(
                    Pixel(3, 3), Pixel(4, 3), Pixel(5, 3),
                    Pixel(3, 4), Pixel(4, 4), Pixel(5, 4), Pixel(6, 4),
                    Pixel(3, 5), Pixel(4, 5), Pixel(5, 5),
                ),
                1.0f,
            )
            add(listOf(Pixel(9, 2), Pixel(10, 3)), 1.0f)
        },
        buildFrame {
            add(FLAG_POLE, 0.7f)
            add(
                listOf(
                    Pixel(3, 3), Pixel(4, 3), Pixel(5, 3),
                    Pixel(3, 4), Pixel(4, 4),
                    Pixel(3, 5), Pixel(4, 5), Pixel(5, 5), Pixel(6, 5),
                ),
                1.0f,
            )
            add(listOf(Pixel(8, 3), Pixel(9, 4)), 1.0f)
        },
        buildFrame {
            add(FLAG_POLE, 0.7f)
            add(
                listOf(
                    Pixel(3, 3), Pixel(4, 3), Pixel(5, 3), Pixel(6, 3),
                    Pixel(3, 4), Pixel(4, 4), Pixel(5, 4), Pixel(6, 4),
                    Pixel(3, 5), Pixel(4, 5), Pixel(5, 5),
                ),
                1.0f,
            )
            add(listOf(Pixel(10, 2), Pixel(11, 3)), 1.0f)
        },
    )

    private fun buildFrame(builder: MutableMap<Pixel, Float>.() -> Unit): Frame {
        val map = mutableMapOf<Pixel, Float>()
        map.builder()
        return map
    }


    private fun MutableMap<Pixel, Float>.add(points: List<Pixel>, brightness: Float) {
        for (p in points) this[p] = brightness
    }
}
