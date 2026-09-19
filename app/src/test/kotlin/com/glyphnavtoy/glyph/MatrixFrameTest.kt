package com.glyphnavtoy.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixFrameTest {

    @Test
    fun maskIs137Leds()
    {
        assertEquals(137, MatrixFrame.ROW_WIDTHS.sum())
        var lit = 0
        val f = MatrixFrame()
        for (y in 0 until MatrixFrame.SIZE) {
            for (x in 0 until MatrixFrame.SIZE) {
                if (f.isLitPixel(x, y)) lit++
            }
        }
        assertEquals(137, lit)
        assertFalse(f.isLitPixel(0, 0))
        assertTrue(f.isLitPixel(6, 6))
    }

    @Test
    fun stampAndFlatten()
    {
        val f = MatrixFrame()
        f.stamp(0, 0, arrayOf(".X.", "o.."), head = 200, tail = 50)
        assertEquals(200, f.get(1, 0))
        assertEquals(50, f.get(0, 1))
        assertEquals(0, f.get(0, 0))
        val flat = f.toIntArray()
        assertEquals(13 * 13, flat.size)
        assertEquals(200, flat[1])
    }
}
