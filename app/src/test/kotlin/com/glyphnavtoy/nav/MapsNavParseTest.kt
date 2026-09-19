package com.glyphnavtoy.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapsNavParseTest {

    @Test
    fun packages()
    {
        assertTrue(MapsNavParse.isMapsPackage("com.google.android.apps.maps"))
        assertTrue(MapsNavParse.isMapsPackage("com.google.android.apps.mapslite"))
        assertFalse(MapsNavParse.isMapsPackage("com.glyphnavtoy"))
        assertFalse(MapsNavParse.isMapsPackage(null))
    }

    @Test
    fun distances()
    {
        assertEquals(60, MapsNavParse.parseDistanceMeters("60 m"))
        assertEquals(1500, MapsNavParse.parseDistanceMeters("1.5 km"))
        assertEquals(80, MapsNavParse.parseDistanceMeters("80 metres"))
        assertEquals(91, MapsNavParse.parseDistanceMeters("100 yd"))
        assertEquals(91, MapsNavParse.parseDistanceMeters("300 ft"))
        assertEquals(1609, MapsNavParse.parseDistanceMeters("1 mi"))
        assertNull(MapsNavParse.parseDistanceMeters(null))
        assertNull(MapsNavParse.parseDistanceMeters("soon"))
    }

    @Test
    fun titleSplitAndResolve()
    {
        val title = "60 m · Turn left onto ADM Rd"
        assertEquals(60, MapsNavParse.parseDistanceFromTitle(title))
        assertEquals("Turn left onto ADM Rd", MapsNavParse.stripDistancePrefix(title))
        assertEquals(60, MapsNavParse.resolveDistance("60 m", title))
        assertEquals(200, MapsNavParse.resolveDistance(null, "200 m: Keep left"))
        assertEquals(
            40,
            MapsNavParse.resolveDistance(null, "", "40 m"),
        )
    }
}
