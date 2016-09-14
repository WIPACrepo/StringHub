package icecube.daq.performance.common;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests PowersOfTwo.java
 */
public class PowersOfTwoTest
{

    @Test
    public void testValues()
    {
        for(PowersOfTwo p2: PowersOfTwo.values())
        {
            assertTrue(PowersOfTwo.isPowerOfTwo(p2.value()));
        }
    }

    @Test
    public void testNames()
    {
        for(PowersOfTwo p2: PowersOfTwo.values())
        {
            String digits = p2.name().substring(1, p2.name().length());
            assertEquals(Integer.parseInt(digits), p2.value());
        }
    }

    @Test
    public void testMask()
    {
        for(PowersOfTwo p2: PowersOfTwo.values())
        {
            assertEquals(p2.mask(), (p2.value() -1));

            // (n % value) == (n & mask) for val > 0;
            for(int i=0; i<10000; i++)
            {
                long anyVal = (long) (Math.random() * Long.MAX_VALUE);
                long modded = anyVal % p2.value();
                long masked = anyVal & p2.mask();

                assertEquals(modded, masked);
            }
        }
    }

    @Test
    public void testWrappingCounter()
    {

        for(PowersOfTwo p2: PowersOfTwo.values())
        {
            // logical counter wraps at value when actual counter passes value
            int counter = p2.value() - 2;
            counter++;
            assertEquals( (p2.value()-1), (counter & p2.mask()) );
            counter++;
            assertEquals( 0, (counter & p2.mask()) );

            // logical counter wraps at value when actual counter passes a
            // multiple of value
            counter = (p2.value() * 100) - 2;
            counter++;
            assertEquals( (p2.value()-1), (counter & p2.mask()) );
            counter++;
            assertEquals( 0, (counter & p2.mask()) );

            // logical counter increments/wraps when actual counter wraps at
            // Integer.MAX_VALUE
            counter = Integer.MAX_VALUE - 2;
            int logical = counter % p2.value();

            assertEquals( logical, (counter & p2.mask()) );

            counter++;
            logical = (logical+1) < p2.value() ? (logical+1) : 0;

            assertEquals( logical, (counter & p2.mask()) );

            counter++;
            logical = (logical+1) < p2.value() ? (logical+1) : 0;

            assertEquals( logical, (counter & p2.mask()) );

            // counter moves to negative
            counter++;
            logical = (logical+1) < p2.value() ? (logical+1) : 0;

            assertEquals( logical, (counter & p2.mask()) );

            counter++;
            logical = (logical+1) < p2.value() ? (logical+1) : 0;

            assertEquals( logical, (counter & p2.mask()) );


            // counter moves back to positive
            int jump = (0 - counter) - 2;
            logical = ((logical + jump) % p2.value());
            counter += jump;

            assertEquals( logical, (counter & p2.mask()) );

            counter++;
            logical = (logical+1) < p2.value() ? (logical+1) : 0;

            assertEquals( logical, (counter & p2.mask()) );

            counter++;
            logical = (logical+1) < p2.value() ? (logical+1) : 0;

            assertEquals( logical, (counter & p2.mask()) );

            counter++;
            logical = (logical+1) < p2.value() ? (logical+1) : 0;

            assertEquals( logical, (counter & p2.mask()) );

            counter++;
            logical = (logical+1) < p2.value() ? (logical+1) : 0;

            assertEquals( logical, (counter & p2.mask()) );

        }


    }

    @Test
    public void testLookup()
    {
        for(PowersOfTwo p2: PowersOfTwo.values())
        {
            assertEquals(p2, PowersOfTwo.lookup(p2.value()));
        }

        assertNull(PowersOfTwo.lookup(-8));
        assertNull(PowersOfTwo.lookup(-4));
        assertNull(PowersOfTwo.lookup(-3));
        assertNull(PowersOfTwo.lookup(-2));
        assertNull(PowersOfTwo.lookup(0));
        assertNull(PowersOfTwo.lookup(3));
        assertNull(PowersOfTwo.lookup(Integer.MAX_VALUE));
    }
}
