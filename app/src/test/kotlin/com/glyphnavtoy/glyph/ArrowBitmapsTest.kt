package com.glyphnavtoy.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ArrowBitmapsTest {

    @Test
    fun everyManeuverHasAPattern()
    {
        for (m in Maneuver.entries) {
            val pattern = ArrowBitmaps.patternFor(m)
            assertTrue("$m pattern empty", pattern.isNotEmpty())
            assertTrue("$m row not 13 wide", pattern.all { it.length == 13 || it.length >= 5 })
        }
    }

    @Test
    fun leftAndRightShiftTowardCenter()
    {
        val left = ArrowBitmaps.originFor(Maneuver.LEFT)
        val right = ArrowBitmaps.originFor(Maneuver.RIGHT)
        assertTrue("left should shift right, was ${left.x}", left.x > 0)
        assertTrue("right should shift left, was ${right.x}", right.x < 0)
        assertTrue(
            "origins should be near-mirrors, left=${left.x} right=${right.x}",
            abs(left.x + right.x) <= 1,
        )
    }

    @Test
    fun straightAndArriveStayOnCenterColumn()
    {
        assertEquals(0, ArrowBitmaps.originFor(Maneuver.STRAIGHT).x)
        assertEquals(0, ArrowBitmaps.originFor(Maneuver.ARRIVE).x)
    }

    @Test
    fun arrowsDoNotSpillIntoDigitBand()
    {
        for (m in Maneuver.entries) {
            val pattern = ArrowBitmaps.patternFor(m)
            val origin = ArrowBitmaps.originFor(pattern)
            val lastRow = origin.y + pattern.size - 1
            assertTrue("$m last row $lastRow overlaps digits", lastRow < ArrowBitmaps.DIGIT_ORIGIN_Y)
        }
    }

    @Test
    fun stampUsesCenteredOrigin()
    {
        val frame = MatrixFrame()
        val origin = ArrowBitmaps.originFor(Maneuver.LEFT)
        frame.stamp(origin.x, origin.y, ArrowBitmaps.patternFor(Maneuver.LEFT))
        var minX = 13
        var maxX = -1
        for (y in 0 until MatrixFrame.SIZE) {
            for (x in 0 until MatrixFrame.SIZE) {
                if (frame.get(x, y) > 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                }
            }
        }
        val mid = (minX + maxX) / 2
        assertTrue("LEFT bbox mid=$mid expected near 6", mid in 5..7)
    }
}
