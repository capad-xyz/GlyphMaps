package com.glyphnavtoy.nav

import com.glyphnavtoy.glyph.DigitFont
import com.glyphnavtoy.glyph.Maneuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavStateTest {

    @Test
    fun shortDistanceFormat()
    {
        assertEquals("300m", NavState(Maneuver.LEFT, 300).shortDistance())
        assertEquals("1.5k", NavState(Maneuver.LEFT, 1500).shortDistance())
        assertEquals("12k", NavState(Maneuver.LEFT, 12000).shortDistance())
        assertNull(NavState(Maneuver.LEFT, null).shortDistance())
    }

    @Test
    fun digitWidthFitsBottomMask()
    {
        // Row 10 of the circular mask is 11 LEDs wide.
        assertEquals(11, DigitFont.measure("300"))
        assertEquals(11, DigitFont.measure("1.5"))
        assertEquals(11, DigitFont.measure("12k"))
    }
}
