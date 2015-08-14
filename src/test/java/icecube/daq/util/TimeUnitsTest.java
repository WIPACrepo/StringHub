package icecube.daq.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test the TimeUnits.java enumeration
 */
public class TimeUnitsTest
{

    @Test
    public void testUnitConversion()
    {
        //
        // Test conversion to UTC units
        //
        assertEquals("UCT conversion ",
                173491324123512L,
                TimeUnits.UTC.asUTC(173491324123512L));

        assertEquals("DOM conversion ",
                173491324123512L * 250,
                TimeUnits.DOM.asUTC(173491324123512L));

        assertEquals("UCT conversion ",
                173491324123512L * 500,
                TimeUnits.DOR.asUTC(173491324123512L));
    }

    @Test
    public void testMaxValue()
    {
        //
        // Tests maximum values.
        //
        assertEquals("Max DOM", 281474976710655L, TimeUnits.DOM.maxValue());
        assertEquals("Max DOR", 72057594037927935L, TimeUnits.DOR.maxValue());
        assertEquals("Max UTC", Long.MAX_VALUE, TimeUnits.UTC.maxValue());
    }

}
