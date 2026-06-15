package com.glyphnavtoy.glyph

/**
 * The maneuver **sweep** animation (FLOWING mode) — generated procedurally from
 * the static [ArrowBitmaps] pattern so the two can never drift apart.
 *
 * Design: the full static shape is shown the whole time at its settled
 * brightness (head cells = head, tail cells = tail). On top of that, a small
 * bright **comet** repeatedly runs from the tail-origin toward the head tip,
 * boosting the cells it passes up toward head brightness. So:
 *
 *  - The set of lit cells is *always identical to the static pattern* — FLOWING
 *    and STATIC match by construction. No hand-authored frame can drift.
 *  - The direction reads correctly because the comet's path is derived from the
 *    real arrow geometry (trail → head), not a separate hand-drawn timeline.
 *  - Energy visibly "flows into the turn" — the dim trail sparkles up to head
 *    brightness as the comet passes, the chevron stays lit.
 *
 * The comet path is found by walking the tail cells nearest-neighbour from the
 * point furthest from the head (so bends and loops are traced naturally), then
 * revealing the head cells outward to the tip.
 */
object ArrowSweep {

    const val FRAME_DURATION_MS = 100L

    /** Comet profile: glow boost at the lead cell and the cells trailing it. */
    private val COMET = floatArrayOf(1f, 0.6f, 0.3f)

    /** Frames of pure static (no comet) appended each loop, so it pulses. */
    private const val REST_FRAMES = 6

    enum class Role { HEAD, TAIL }

    /** One cell of a sweep frame: its static role + how strongly the comet is
     *  over it (0 = settled, 1 = full head-bright spark). */
    class SweepPx(val role: Role, val glow: Float)

    // Precomputed once at class load (single-threaded), then read-only — so the
    // UI-preview thread and the service render thread can share it safely (a
    // plain HashMap with lazy getOrPut would corrupt under concurrent access).
    private val cache: Map<Maneuver, List<Map<Pixel, SweepPx>>> =
        Maneuver.entries.associateWith { build(it) }

    /** Sweep frames for [maneuver]; empty if the pattern has nothing to light. */
    fun framesFor(maneuver: Maneuver): List<Map<Pixel, SweepPx>> =
        cache[maneuver] ?: emptyList()

    /**
     * Stamp a sweep [frame] with independent [head]/[tail] brightness — the
     * same two values STATIC mode uses, so a settled sweep frame ≡ the static
     * pattern exactly. The comet lerps a cell from its settled level toward
     * [head].
     */
    fun stamp(target: MatrixFrame, frame: Map<Pixel, SweepPx>, head: Int, tail: Int) {
        for ((p, px) in frame) {
            val base = if (px.role == Role.HEAD) head else tail
            val level = base + ((head - base) * px.glow)
            target.set(p.x, p.y, level.toInt())
        }
    }

    // ---- generation ----

    private fun build(maneuver: Maneuver): List<Map<Pixel, SweepPx>> {
        val pattern = ArrowBitmaps.patternFor(maneuver)
        val roleOf = HashMap<Pixel, Role>()
        for ((dy, row) in pattern.withIndex()) {
            for ((dx, ch) in row.withIndex()) {
                val role = when (ch) {
                    'X' -> Role.HEAD
                    'o', '+', ':' -> Role.TAIL
                    else -> null
                } ?: continue
                roleOf[Pixel(dx, dy + ArrowBitmaps.ARROW_ORIGIN_Y)] = role
            }
        }
        if (roleOf.isEmpty()) return emptyList()

        val seq = orderPath(roleOf)
        val loopLen = seq.size + REST_FRAMES

        return (0 until loopLen).map { t ->
            // Base: the whole static shape, settled.
            val f = HashMap<Pixel, SweepPx>(roleOf.size)
            for ((p, role) in roleOf) f[p] = SweepPx(role, 0f)
            // Comet: lead at seq[t], fading tail behind it. Silent during rest.
            for ((k, g) in COMET.withIndex()) {
                val idx = t - k
                val p = seq.getOrNull(idx) ?: continue
                val cur = f[p] ?: continue
                if (g > cur.glow) f[p] = SweepPx(cur.role, g)
            }
            f
        }
    }

    /** Order cells tail-origin → head tip into one comet path. */
    private fun orderPath(roleOf: Map<Pixel, Role>): List<Pixel> {
        val heads = roleOf.filterValues { it == Role.HEAD }.keys.toList()
        val tails = roleOf.filterValues { it == Role.TAIL }.keys.toList()

        if (heads.isEmpty()) {
            // All-tail pattern (rare) — just walk it from one extreme.
            if (tails.isEmpty()) return emptyList()
            return nearestNeighbourWalk(tails, farthestFrom(tails, centroid(tails)))
        }
        val headCentroid = centroid(heads)
        val seq = ArrayList<Pixel>()
        if (tails.isNotEmpty()) {
            val origin = farthestFrom(tails, headCentroid)
            seq.addAll(nearestNeighbourWalk(tails, origin))
        }
        // Reveal the head outward from where the trail ended toward the tip.
        val from = seq.lastOrNull() ?: farthestFrom(heads, headCentroid)
        seq.addAll(heads.sortedBy { dist2(it, from) })
        return seq
    }

    private fun nearestNeighbourWalk(points: List<Pixel>, origin: Pixel): List<Pixel> {
        val remaining = points.toMutableList()
        remaining.remove(origin)
        val out = ArrayList<Pixel>(points.size)
        var cur = origin
        out.add(cur)
        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { dist2(it, cur) }!!
            remaining.remove(next)
            out.add(next)
            cur = next
        }
        return out
    }

    private fun centroid(points: List<Pixel>): Pair<Float, Float> {
        var sx = 0f; var sy = 0f
        for (p in points) { sx += p.x; sy += p.y }
        val n = points.size.toFloat()
        return sx / n to sy / n
    }

    private fun farthestFrom(points: List<Pixel>, ref: Pair<Float, Float>): Pixel =
        points.maxByOrNull { p ->
            val dx = p.x - ref.first; val dy = p.y - ref.second
            dx * dx + dy * dy
        }!!

    private fun dist2(a: Pixel, b: Pixel): Int {
        val dx = a.x - b.x; val dy = a.y - b.y
        return dx * dx + dy * dy
    }
}
