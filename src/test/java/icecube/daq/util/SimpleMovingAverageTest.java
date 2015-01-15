package icecube.daq.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;


/**
 * Test Class
 */
public class SimpleMovingAverageTest
{
    @Test
    public void testSimpleMovingAverage()
    {
        SimpleMovingAverage sma = new SimpleMovingAverage(10);

        // use before adding values
        assertEquals(0, sma.getAverage(), 0);

        // use with partial values
        sma.add(10);
        assertEquals(10, sma.getAverage(), 0);

        // full window
        sma.add(10);
        sma.add(10);
        sma.add(10);
        sma.add(10);
        sma.add(10);
        sma.add(10);
        sma.add(10);
        sma.add(10);
        sma.add(10);
        sma.add(10);
        assertEquals(10, sma.getAverage(), 0);

        // rolling averaging
        sma.add(500);
        assertEquals(59, sma.getAverage(), 0);
        sma.add(-99);
        assertEquals(48.1, sma.getAverage(), 0);


        // roll to a static value.
        sma.add(13270);
        sma.add(13270);
        sma.add(13270);
        sma.add(13270);
        sma.add(13270);
        sma.add(13270);
        sma.add(13270);
        sma.add(13270);
        sma.add(13270);
        sma.add(13270);

        assertEquals(13270, sma.getAverage(), 0);

    }

}
