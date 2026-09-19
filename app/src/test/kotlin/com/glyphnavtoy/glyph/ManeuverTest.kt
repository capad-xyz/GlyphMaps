package com.glyphnavtoy.glyph

import com.glyphnavtoy.nav.MapsNavParse
import org.junit.Assert.assertEquals
import org.junit.Test

class ManeuverTest {

    private fun parse(title: String): Maneuver =
        MapsNavParse.maneuverFromTitle(title)

    @Test
    fun liveUpdateTitles()
    {
        assertEquals(Maneuver.LEFT, parse("60 m · Turn left onto ADM Rd"))
        assertEquals(Maneuver.RIGHT, parse("1.5 km · Turn right onto Ring Rd"))
        assertEquals(Maneuver.SHARP_LEFT, parse("80 m · Sharp left toward X"))
        assertEquals(Maneuver.KEEP_LEFT, parse("Keep left to stay on PC Colony Rd"))
        assertEquals(Maneuver.KEEP_RIGHT, parse("Bear right onto NH-48"))
        assertEquals(Maneuver.UTURN, parse("Make a U-turn"))
        assertEquals(Maneuver.ROUNDABOUT, parse("At the roundabout, take the 2nd exit"))
        assertEquals(Maneuver.LEFT, parse("At the roundabout, turn left onto Mall Rd"))
        assertEquals(Maneuver.STRAIGHT, parse("Head south on Rajendra Nagar Br"))
        assertEquals(Maneuver.ARRIVE, parse("Arriving at destination"))
        assertEquals(Maneuver.ARRIVE, parse("Your destination is on the left"))
        assertEquals(Maneuver.FORWARD_LEFT, parse("Fork left"))
        assertEquals(Maneuver.KEEP_LEFT, parse("Stay left"))
        assertEquals(Maneuver.KEEP_RIGHT, parse("Take the exit"))
    }

    @Test
    fun routesApiConstants()
    {
        assertEquals(Maneuver.SHARP_LEFT, Maneuver.fromMapsString("turn-sharp-left"))
        assertEquals(Maneuver.LEFT, Maneuver.fromMapsString("roundabout-left"))
        assertEquals(Maneuver.STRAIGHT, Maneuver.fromMapsString("exit-the-roundabout"))
        assertEquals(Maneuver.KEEP_LEFT, Maneuver.fromMapsString("ramp-left"))
    }

    @Test
    fun emptyIsStraight()
    {
        assertEquals(Maneuver.STRAIGHT, Maneuver.fromMapsString(null))
        assertEquals(Maneuver.STRAIGHT, Maneuver.fromMapsString(""))
        assertEquals(Maneuver.STRAIGHT, parse("Continue onto ADM Rd"))
    }
}
