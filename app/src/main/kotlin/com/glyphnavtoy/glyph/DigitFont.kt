package com.glyphnavtoy.glyph

/**
 * Hand-tuned 3×5 pixel font for digits 0–9 and a few helper glyphs (dot, k).
 *
 * Why custom instead of `GlyphMatrixObject.setText()`: the SDK's built-in
 * text rendering uses an unknown font size that doesn't fit our split layout,
 * and renders centered with no way to constrain to the bottom half. With a
 * known-size pixel font we can plan pixel placement exactly.
 *
 * Each glyph is 3 wide × 5 tall. Three digits side-by-side with 1-pixel gaps
 * fit in 11 columns, which is the width of row 10 of the circular mask —
 * meaning the bottom of the digits doesn't get clipped.
 */
object DigitFont {

    const val W = 3
    const val H = 5

    private val zero = arrayOf(
        "XXX",
        "X.X",
        "X.X",
        "X.X",
        "XXX",
    )
    private val one = arrayOf(
        ".X.",
        "XX.",
        ".X.",
        ".X.",
        "XXX",
    )
    private val two = arrayOf(
        "XXX",
        "..X",
        "XXX",
        "X..",
        "XXX",
    )
    private val three = arrayOf(
        "XXX",
        "..X",
        "XXX",
        "..X",
        "XXX",
    )
    private val four = arrayOf(
        "X.X",
        "X.X",
        "XXX",
        "..X",
        "..X",
    )
    private val five = arrayOf(
        "XXX",
        "X..",
        "XXX",
        "..X",
        "XXX",
    )
    private val six = arrayOf(
        "XXX",
        "X..",
        "XXX",
        "X.X",
        "XXX",
    )
    private val seven = arrayOf(
        "XXX",
        "..X",
        ".X.",
        ".X.",
        ".X.",
    )
    private val eight = arrayOf(
        "XXX",
        "X.X",
        "XXX",
        "X.X",
        "XXX",
    )
    private val nine = arrayOf(
        "XXX",
        "X.X",
        "XXX",
        "..X",
        "XXX",
    )
    private val dot = arrayOf(
        "...",
        "...",
        "...",
        "...",
        ".X.",
    )
    private val kLower = arrayOf(
        "X..",
        "X.X",
        "XX.",
        "X.X",
        "X.X",
    )
    private val mLower = arrayOf(
        "...",
        "X.X",
        "XXX",
        "X.X",
        "X.X",
    )
    /** Used as a trailing breathing-room glyph in the marquee. */
    private val space = arrayOf(
        "...",
        "...",
        "...",
        "...",
        "...",
    )

    private val glyphs: Map<Char, Array<String>> = mapOf(
        '0' to zero, '1' to one, '2' to two, '3' to three, '4' to four,
        '5' to five, '6' to six, '7' to seven, '8' to eight, '9' to nine,
        '.' to dot, 'k' to kLower, 'm' to mLower, ' ' to space,
    )

    /** Bitmap pattern for a single character, or null if no glyph defined. */
    fun glyph(ch: Char): Array<String>? = glyphs[ch]

    /**
     * Render [text] into [frame] starting at (originX, originY). Each glyph
     * is followed by a 1-pixel gap. Returns the total width drawn (incl gaps).
     */
    fun renderTo(
        frame: MatrixFrame,
        text: String,
        originX: Int,
        originY: Int,
        brightness: Int = 255,
    ): Int {
        var x = originX
        for ((i, ch) in text.withIndex()) {
            val pattern = glyphs[ch] ?: continue
            frame.stamp(x, originY, pattern, brightness)
            x += W
            if (i < text.lastIndex) x += 1 // 1-pixel gap
        }
        return x - originX
    }

    /** Width of [text] in pixels (incl 1-pixel gaps between glyphs). */
    fun measure(text: String): Int =
        text.length * W + (text.length - 1).coerceAtLeast(0)
}
