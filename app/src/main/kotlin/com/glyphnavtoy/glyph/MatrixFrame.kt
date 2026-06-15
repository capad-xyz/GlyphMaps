package com.glyphnavtoy.glyph

/**
 * Shared 13×13 frame model. One source of truth for "what should the matrix show."
 *
 * Both the Glyph SDK renderer (pushes to the real LEDs via setAppMatrixFrame)
 * and the in-app Compose preview consume this — so the on-phone preview
 * matches the back of the phone pixel-for-pixel.
 *
 * Coordinate system: (x=0, y=0) is top-left. y grows downward (Android-standard).
 *
 * Circular mask: the 4a Pro Glyph Matrix is circular — only 137 of the 169
 * grid positions are real LEDs. The mask is per-row [5, 7, 11, 13, 13, 13, 13,
 * 13, 13, 13, 11, 7, 5] (centered). Pixels outside the mask are silently
 * dropped at push time — drawing to them is fine, they just won't light up.
 */
class MatrixFrame {
    /** [y][x] = brightness 0..255. 0 = off, 255 = full white. */
    private val grid: Array<IntArray> = Array(SIZE) { IntArray(SIZE) }

    fun set(x: Int, y: Int, brightness: Int) {
        if (x in 0 until SIZE && y in 0 until SIZE) {
            grid[y][x] = brightness.coerceIn(0, 255)
        }
    }

    fun get(x: Int, y: Int): Int =
        if (x in 0 until SIZE && y in 0 until SIZE) grid[y][x] else 0

    /**
     * Stamp a pixel pattern at (originX, originY) with independent **head** and
     * **tail** brightness.
     *
     * Character → brightness mapping:
     *   'X' → [head]   (the bright arrowhead / chevron — the part you read first)
     *   'o' → [tail]   (the dim trail behind the head)
     *   '+' → [tail]   (legacy "body" char — U-turn loop, Arrive ring; tail-tier)
     *   ':' → [tail]   (legacy faint char; tail-tier)
     *   anything else → off
     *
     * Both values are independently tunable per maneuver (see GlyphSettings).
     * Digit/text rendering only uses 'X', so it follows [head] — callers pass
     * the head value as the single brightness argument and tail is irrelevant.
     */
    fun stamp(originX: Int, originY: Int, pattern: Array<String>, head: Int = 255, tail: Int = 128) {
        for ((dy, row) in pattern.withIndex()) {
            for ((dx, ch) in row.withIndex()) {
                val level = when (ch) {
                    'X' -> head
                    'o', '+', ':' -> tail
                    else -> 0
                }
                if (level > 0) set(originX + dx, originY + dy, level)
            }
        }
    }

    /** Brightness-multiply every pixel by [factor] (0.0..1.0). */
    fun dim(factor: Float): MatrixFrame {
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                grid[y][x] = (grid[y][x] * factor).toInt().coerceIn(0, 255)
            }
        }
        return this
    }

    /**
     * Flatten to the `int[]` shape `GlyphMatrixManager.setAppMatrixFrame(int[])`
     * expects: row-major, length 169. Each int is a brightness 0..255.
     */
    fun toIntArray(): IntArray {
        val out = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                out[y * SIZE + x] = grid[y][x]
            }
        }
        return out
    }

    /** True if the pixel at (x,y) is inside the circular LED mask. */
    fun isLitPixel(x: Int, y: Int): Boolean {
        if (x !in 0 until SIZE || y !in 0 until SIZE) return false
        val half = ROW_WIDTHS[y] / 2
        return x in (CENTER - half)..(CENTER + half)
    }

    companion object {
        const val SIZE = 13
        const val CENTER = 6

        /**
         * LED count per row of the 4a Pro Glyph Matrix (circular mask).
         * Sums to 137 = the documented LED count.
         */
        val ROW_WIDTHS = intArrayOf(5, 7, 11, 13, 13, 13, 13, 13, 13, 13, 11, 7, 5)
    }
}
