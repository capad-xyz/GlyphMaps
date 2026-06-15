package com.glyphnavtoy.glyph

object StartupAnimations {

    const val FRAME_DURATION_MS = 100L
    const val STARTUP_TICKS = 24

    enum class Concept {
        RADAR_SWEEP,
        RADAR_PULSE,
    }

    val DEFAULT_CONCEPT = Concept.RADAR_PULSE

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
        Concept.RADAR_SWEEP -> RADAR_SWEEP_FRAMES
        Concept.RADAR_PULSE -> RADAR_PULSE_FRAMES
    }

    private val BEAMS: List<List<Pixel>> = listOf(
        listOf(Pixel(6, 6), Pixel(6, 5), Pixel(6, 4), Pixel(6, 3), Pixel(6, 2), Pixel(6, 1)),
        listOf(Pixel(6, 6), Pixel(7, 5), Pixel(8, 4), Pixel(9, 3), Pixel(10, 2), Pixel(11, 1)),
        listOf(Pixel(6, 6), Pixel(7, 6), Pixel(8, 6), Pixel(9, 6), Pixel(10, 6), Pixel(11, 6)),
        listOf(Pixel(6, 6), Pixel(7, 7), Pixel(8, 8), Pixel(9, 9), Pixel(10, 10)),
        listOf(Pixel(6, 6), Pixel(6, 7), Pixel(6, 8), Pixel(6, 9), Pixel(6, 10), Pixel(6, 11)),
        listOf(Pixel(6, 6), Pixel(5, 7), Pixel(4, 8), Pixel(3, 9), Pixel(2, 10)),
        listOf(Pixel(6, 6), Pixel(5, 6), Pixel(4, 6), Pixel(3, 6), Pixel(2, 6), Pixel(1, 6)),
        listOf(Pixel(6, 6), Pixel(5, 5), Pixel(4, 4), Pixel(3, 3), Pixel(2, 2), Pixel(1, 1)),
    )

    private val INNER_RING = listOf(
        Pixel(6, 5), Pixel(7, 5), Pixel(7, 6), Pixel(7, 7),
        Pixel(6, 7), Pixel(5, 7), Pixel(5, 6), Pixel(5, 5),
    )

    private val BLIPS = listOf(
        listOf(Pixel(9, 2), Pixel(10, 2)),
        listOf(Pixel(11, 4)),
        listOf(Pixel(11, 6)),
        listOf(Pixel(9, 9)),
        listOf(Pixel(6, 11)),
        listOf(Pixel(3, 9)),
        listOf(Pixel(1, 6)),
        listOf(Pixel(3, 3)),
    )

    private val RADAR_SWEEP_FRAMES = buildSweepFrames(includePulse = false)
    private val RADAR_PULSE_FRAMES = buildSweepFrames(includePulse = true)

    private fun buildSweepFrames(includePulse: Boolean): List<Frame> = buildList {
        val size = BEAMS.size
        for (i in 0 until size) {
            add(
                buildFrame {
                    add(BEAMS[i], 1.0f)
                    add(BEAMS[(i + size - 1) % size], 0.45f)
                    add(BEAMS[(i + size - 2) % size], 0.2f)
                    add(BLIPS[i], 0.9f)
                    if (includePulse && i % 2 == 0) {
                        add(INNER_RING, 0.25f)
                    }
                }
            )
        }
        if (includePulse) {
            add(
                buildFrame {
                    add(INNER_RING, 0.4f)
                }
            )
        }
        add(emptyMap())
    }

    private fun buildFrame(builder: MutableMap<Pixel, Float>.() -> Unit): Frame {
        val map = mutableMapOf<Pixel, Float>()
        map.builder()
        return map
    }

    private fun MutableMap<Pixel, Float>.add(points: List<Pixel>, brightness: Float) {
        for (p in points) {
            val current = this[p] ?: 0f
            if (brightness > current) this[p] = brightness
        }
    }
}
