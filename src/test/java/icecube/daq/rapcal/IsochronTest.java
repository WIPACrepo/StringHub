package icecube.daq.rapcal;

import icecube.daq.util.TimeUnits;
import icecube.daq.util.UTC;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;


/**
 * Test the Isocron class of abstract rap cal
 */
public class IsochronTest
{

    final long GPS_OFFSET = 0;


    // test operates in UTC values, otherwise it
    // is difficult to test edge conditions.
    TimeUnits UTC = TimeUnits.UTC;

    @BeforeClass
    public static void setupLogging()
    {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
    }

    @Before
    public void setUp() throws Exception
    {
    }

    @AfterClass
    public static void tearDown()
    {
        BasicConfigurator.resetConfiguration();
    }

    @Test
    public void testConstruction() throws RAPCalException
    {

        // basic
        {
        long[] left =
                {
                        1000000,
                        3000000,
                        3000600,
                        1000600
                };
            long[] right =
                {
                        1001000,
                        3001000,
                        3001600,
                        1001600
                };
        Isochron isochron =
                new Isochron(left, right, GPS_OFFSET);
        }

        // chaining
        {
            long[] left =
                    {
                            1000000,
                            3000000,
                            3000633,
                            1000600
                    };
            long[] right =
                    {
                            1001000,
                            3001024,
                            3001637,
                            1001600
                    };
            long[] next =
                    {
                            1002011,
                            3002023,
                            3002688,
                            1002667
                    };
            Isochron isochron =
                    new Isochron(left, right, GPS_OFFSET);
            Isochron isochron2 =
                    new Isochron(isochron, next, GPS_OFFSET);
            Isochron isochron3 =
                    new Isochron(right, next, GPS_OFFSET);

            assertEquals(isochron2.getCableLength(), isochron3.getCableLength(), 0.0);
            assertEquals(isochron2.getEpsilon(), isochron3.getEpsilon(), 0.0);
            assertEquals(isochron2.getLowerBound(), isochron3.getLowerBound());
            assertEquals(isochron2.getUpperBound(), isochron3.getUpperBound());
            assertEquals(isochron2.toString(), isochron3.toString());
        }

    }


    @Test
    public void testBadConstruction() throws RAPCalException
    {
        //
        // test attempts at non-monotonic constructions
        //
        String message = "Expected exception on construction";


        // left quad inconsistent
        try
        {
            long[] left =
                    {
                            1000600,
                            3000000,
                            3000600,
                            1000200 //backward DOR
                    };
            long[] right =
                    {
                            1001000,
                            3001000,
                            3001600,
                            1001600
                    };
            Isochron isochron =
                    new Isochron(left, right, GPS_OFFSET);
            fail(message);
        }
        catch (RAPCalException e)
        {
            //desired
            assertTrue(e.getMessage().startsWith("Non-monotonic timestamps"));
        }

        // left quad inconsistent
        try
        {
            long[] left =
                    {
                            1000000,
                            3000600,
                            3000000, //backward DOM
                            1000600
                    };
            long[] right =
                    {
                            1001000,
                            3001000,
                            3001600,
                            1001600
                    };
            Isochron isochron =
                    new Isochron(left, right, GPS_OFFSET);
            fail(message);
        }
        catch (RAPCalException e)
        {
            //desired
            assertTrue(e.getMessage().startsWith("Non-monotonic timestamps"));
        }

        // right quad inconsistent
        try
        {
            long[] left =
                    {
                            1000000,
                            3000000,
                            3000600,
                            1000600
                    };
            long[] right =
                    {
                            1001600,
                            3001000,
                            3001600,
                            1001000  //backward DOR
                    };
            Isochron isochron =
                    new Isochron(left, right, GPS_OFFSET);
            fail(message);
        }
        catch (RAPCalException e)
        {
            //desired
            assertTrue(e.getMessage().startsWith("Non-monotonic timestamps"));
        }


        // right quad inconsistent
        try
        {
            long[] left =
                    {
                            1000000,
                            3000000,
                            3000600,
                            1000600
                    };
            long[] right =
                    {
                            1001000,
                            3001600,
                            3001000,  //backward DOR
                            1001600
                    };
            Isochron isochron =
                    new Isochron(left, right, GPS_OFFSET);
            fail(message);
        }
        catch (RAPCalException e)
        {
            //desired
            assertTrue(e.getMessage().startsWith("Non-monotonic timestamps"));
        }

        // quad pairing inconsistent
        try
        {
            long[] left =
                    {
                            1001000,
                            3000000,
                            3000600,
                            1001600
                    };
            long[] right =
                    {
                            1000000,//backward DOR
                            3001000,
                            3001600,
                            1001600
                    };
            Isochron isochron =
                    new Isochron(left, right, GPS_OFFSET);
            fail(message);
        }
        catch (RAPCalException e)
        {
            //desired
            assertTrue(e.getMessage().startsWith("Non-monotonic timestamps"));
        }

        // quad pairing inconsistent
        try
        {
            long[] left =
                    {
                            1000000,
                            3001000,
                            3001600,
                            1000600
                    };
            long[] right =
                    {
                            1001000,
                            3001000, //backward DOM
                            3001600,
                            1001600
                    };
            Isochron isochron =
                    new Isochron(left, right, GPS_OFFSET);
            fail(message);
        }
        catch (RAPCalException e)
        {
            //desired
            assertTrue(e.getMessage().startsWith("Non-monotonic timestamps"));
        }
    }



    @Test
    public void testBounds() throws RAPCalException
    {
        //
        // test the interval boundaries
        //
        long[] left =
                {
                        1000000,
                        3000000,
                        3000600, // lower bound (exclusive)
                        1000600
                };
        long[] right =
                {
                        1001000,
                        3001000,
                        3001600, // upper bound (inclusive)
                        1001600
                };

        Isochron isochron =
                new Isochron(left, right, GPS_OFFSET);

        assertEquals("Bad lower bound", (3000600), isochron.getLowerBound());
        assertEquals("Bad upper bound", 3001600, isochron.getUpperBound());

        assertTrue("Is later than " + 0, isochron.laterThan(0, UTC));
        assertTrue("Is later than " + 3000000, isochron.laterThan(3000000, UTC));
        assertTrue("Is later than " + 3000600, isochron.laterThan(3000600, UTC));
        assertTrue("Is later than " + 3001000, isochron.laterThan(3001000, UTC));
        assertTrue("Is later than " + 3001600, isochron.laterThan(3001600, UTC));
        assertFalse("Not later than " + 22, isochron.laterThan(3001601, UTC));
        assertFalse("Not later than " + 22, isochron.laterThan(TimeUnits.DOM.maxValue(), TimeUnits.DOM));
        assertFalse("Not later than " + 22, isochron.laterThan(Long.MAX_VALUE, UTC));

        assertFalse("Should not contain " + 3000600,
                isochron.containsDomClock(3000600, UTC));

        assertTrue("Should contain " + 3000601, isochron.containsDomClock(3000601, UTC));
        assertTrue("Should contain " + 3000602, isochron.containsDomClock(3000602, UTC));

        assertTrue("Should contain " + 3001599, isochron.containsDomClock(3001599, UTC));
        assertTrue("Should contain " + 3001600, isochron.containsDomClock(3001600, UTC));

        assertFalse("Should not contain " + 3001601,
                isochron.containsDomClock(3001601, UTC));

    }


    @Test
    public void testEpsilon() throws RAPCalException
    {
        //
        // Test epsilon calculation
        //
        {
            long[] t0 =
                    {
                            1000000,
                            3000000,
                            3000600,
                            1000600
                    };
            long[] t1 =
                    {
                            1001600,
                            3001600,
                            3002200,
                            1002200
                    };

            Isochron isochron =
                    new Isochron(t0, t1, GPS_OFFSET);

            assertEquals("Epsilon:", 0.0, isochron.getEpsilon(), 0.0);
        }

        {
            long[] t0 =
                    {
                            1000000,
                            3000000,
                            3000600,
                            1000600
                    };
            long[] t1 =
                    {
                            1001600,
                            3001350, //DOM slow by 250/1350
                            3001950,
                            1002200
                    };


            Isochron isochron =
                    new Isochron(t0, t1, GPS_OFFSET);

            assertEquals("Epsilon:", 250d/1350d, isochron.getEpsilon(), 0.0);
        }

        {
            long[] t0 =
                    {
                            1000000,
                            3000000,
                            3000600,
                            1000600
                    };
            long[] t1 =
                    {
                            1001600,
                            3001850, //DOM fast by 250/1850
                            3002450,
                            1002200
                    };


            Isochron isochron =
                    new Isochron(t0, t1, GPS_OFFSET);

            assertEquals("Epsilon:", -250d/1850d, isochron.getEpsilon(), 0.0);
        }
    }

    @Test
    public void testCableLength() throws RAPCalException
    {
        //
        // Test cable length calculation
        //
        {
            long[] t0 =
                    {
                            1000000,
                            3000000,
                            3000600,
                            1000600
                    };
            long[] t1 =
                    {
                            1001600,
                            3001600,
                            3002200,
                            1002200
                    };

            Isochron isochron =
                    new Isochron(t0, t1, GPS_OFFSET);

            assertEquals("Cable Len:", 0.0, isochron.getCableLength(), 0.0);
        }

        {
            long[] t0 =
                    {
                            1000000,
                            3000000,
                            3000550,
                            1000600
                    };
            long[] t1 =
                    {
                            1001600,
                            3001600,
                            3002200,
                            1002250 // +50 DOR
                    };

            Isochron isochron =
                    new Isochron(t0, t1, GPS_OFFSET);

            assertEquals("Cable Len:", asSeconds(25.0), isochron.getCableLength(), 1E-23);
        }

        {
            long[] t0 =
                    {
                            1000000,
                            3000000,
                            3000600,
                            1000550
                    };
            long[] t1 =
                    {
                            1001600,
                            3001600,
                            3002250, //+50 DOM
                            1002200
                    };

            Isochron isochron =
                    new Isochron(t0, t1, GPS_OFFSET);

            assertEquals("Cable Len:", asSeconds(-25), isochron.getCableLength(),  1E-23);
        }


    }

    @Test
    public void testReconstruction() throws RAPCalException
    {
        //
        // Test utc time reconstruction
        //
        {
            long[] t0 =
                    {
                            1000000,
                            3000000,
                            3000600,
                            1000600
                    };
            long[] t1 =
                    {
                            1001600,
                            3001600,
                            3002200,
                            1002200
                    };

            Isochron isochron =
                    new Isochron(t0, t1, GPS_OFFSET);

            assertEquals("Epsilon:", 0.0, isochron.getEpsilon(), 0.0);
            assertEquals("Reconstruction:", -2000000, isochron.reconstructUTC(0, UTC));
            assertEquals("Reconstruction:", 1000000, isochron.reconstructUTC(3000000, UTC));
            assertEquals("Reconstruction:", 1000600, isochron.reconstructUTC(3000600, UTC));
            assertEquals("Reconstruction:", 1001600, isochron.reconstructUTC(3001600, UTC));
            assertEquals("Reconstruction:", 1002200, isochron.reconstructUTC(3002200, UTC));
            assertEquals("Reconstruction:", 6004449832L - 2000000, isochron.reconstructUTC(6004449832L, UTC));
        }

        {
            long[] t0 =
                    {
                            1000000,
                            3000000,
                            3000600,
                            1000600
                    };
            long[] t1 =
                    {
                            1001600,
                            3001350, //DOM slow by 250/1350
                            3001950,
                            1002200
                    };


            Isochron isochron =
                    new Isochron(t0, t1, GPS_OFFSET);

            assertEquals("Epsilon:", 250d/1350d, isochron.getEpsilon(), 0.0);
            assertEquals("Reconstruction:", -2555611, isochron.reconstructUTC(0, UTC));
            assertEquals("Reconstruction:", 999945, isochron.reconstructUTC(3000000, UTC));
            assertEquals("Reconstruction:", 1000656, isochron.reconstructUTC(3000600, UTC));
            assertEquals("Reconstruction:", 1001545, isochron.reconstructUTC(3001350, UTC));
            assertEquals("Reconstruction:", 1002255, isochron.reconstructUTC(3001950, UTC));
            assertEquals("Reconstruction:", 7113829374L, isochron.reconstructUTC(6004449832L, UTC));

        }

        {
            long[] t0 =
                    {
                            1000000,
                            3000000,
                            3000600,
                            1000600
                    };
            long[] t1 =
                    {
                            1001600,
                            3001850, //DOM fast by 250/1850
                            3002450,
                            1002200
                    };


            Isochron isochron =
                    new Isochron(t0, t1, GPS_OFFSET);

            assertEquals("Epsilon:", -250d/1850d, isochron.getEpsilon(), 0.0);
            assertEquals("Reconstruction:", -1594555, isochron.reconstructUTC(0, UTC));
            assertEquals("Reconstruction:", 1000040, isochron.reconstructUTC(3000000, UTC));
            assertEquals("Reconstruction:", 1000559, isochron.reconstructUTC(3000600, UTC));
            assertEquals("Reconstruction:", 1001640, isochron.reconstructUTC(3001850, UTC));
            assertEquals("Reconstruction:", 1002160, isochron.reconstructUTC(3002450, UTC));
            assertEquals("Reconstruction:", 5191443139L, isochron.reconstructUTC(6004449832L, UTC));
        }
    }

    @Test
    public void testToString() throws RAPCalException
    {
        //
        // Test stringification
        //

        {
            long[] t0 =
                    {
                            1000000,
                            3000000,
                            3000600,
                            1000600
                    };
            long[] t1 =
                    {
                            1001600,
                            3001600,
                            3002200,
                            1002200
                    };

            Isochron isochron =
                    new Isochron(t0, t1, GPS_OFFSET);

String expected = "Isochron[[1000000, 3000000, 3000600, 1000600]," +
        " [1001600, 3001600, 3002200, 1002200],epsilon=0.000000," +
        "domMid=3001900,dorMid=1001900,clen=0.000000]";
            assertEquals("toString:", expected, isochron.toString());
        }
    }

    @Test
    public void testRealData() throws RAPCalException
    {
        //
        // some realistic real-world cases with data taken
        // from known-good implementation
        //
        {
            //.# dor_dt       = 10304056613
            //.# dom_dt       = 10303979386
            //.# dormid       = 60116215452846
            //.# dommid       = 52578869846231
            //.# clen         = 138326.41151019564
            //.# epsilon      = 7.4948713605665984E-6
            long[]tcal_one =
                    {
                            60105911179500L,
                            52568565788440L,
                            52568565945250L,
                            60105911612965L
                    };
            long[] tcal_two =
                    {
                            60116215236000L,
                            52578869767712L,
                            52578869924750L,
                            60116215669692L
                    };

            Isochron isochron = new Isochron(tcal_one, tcal_two, 0);

            assertEquals("Epsilon", 7.4948713605665984E-6, isochron.getEpsilon(), 0.0);
            assertEquals("Cable Length", 1.3832641151019565E-5, isochron.getCableLength(), 0.0);
            assertEquals("Reconstruction", 60105911317828L, isochron.reconstructUTC(52568565788440L, UTC));
            assertEquals("Reconstruction", 60105911474639L, isochron.reconstructUTC(52568565945250L, UTC));
            assertEquals("Reconstruction", 60116215374327L, isochron.reconstructUTC(52578869767712L, UTC));
            assertEquals("Reconstruction", 60116215531365L, isochron.reconstructUTC(52578869924750L, UTC));
        }
        {
            // .# dor_dt       = 10618293562
            // .# dom_dt       = 10618464438
            // .# dormid       = 60188002429190
            // .# dommid       = 52567418556640
            // .# clen         = 127081.76501124531
            // .# epsilon      = -1.6092345649196776E-5
            long[] tcal_one =
                    {
                            60177383930000L,
                            52556800013654L,
                            52556800170750L,
                            60177384341256L
                    };
            long[] tcal_two =
                    {
                            60188002223500L,
                            52567418478031L,
                            52567418635250L,
                            60188002634880L
                    };

            Isochron isochron = new Isochron(tcal_one, tcal_two, 0);

            assertEquals("Epsilon", -1.6092345649196776E-5, isochron.getEpsilon(), 0.0);
            assertEquals("Cable Length", 1.270817650112453E-5, isochron.getCableLength(), 0.0);
            assertEquals("Reconstruction", 60177384057081L, isochron.reconstructUTC(52556800013654L, UTC));
            assertEquals("Reconstruction", 60177384214174L, isochron.reconstructUTC(52556800170750L, UTC));
            assertEquals("Reconstruction", 60188002350582L, isochron.reconstructUTC(52567418478031L, UTC));
            assertEquals("Reconstruction", 67808463874357L, isochron.reconstructUTC(60188002634880L, UTC));

        }
        {
            //.# dor_dt       = 12849965313
            //.# dom_dt       = 12849937674
            //.# dormid       = 56112601034794
            //.# dommid       = 48479627836318
            //.# clen         = 19862.331301265163
            //.# epsilon      = 2.1509053741111555E-6
            long[] tcal_one =
                    {
                            56099750971000L,
                            48466777820038L,
                            48466777977250L,
                            56099751167962L
                    };
            long[] tcal_two =
                    {
                            56112600936500L,
                            48479627757887L,
                            48479627914750L,
                            56112601133088L
                    };
            Isochron isochron = new Isochron(tcal_one, tcal_two, 0);

            assertEquals("Epsilon", 2.1509053741111555E-6, isochron.getEpsilon(), 0.0);
            assertEquals("Cable Length", 1.9862331301265173E-6, isochron.getCableLength(), 0.0);
            assertEquals("Reconstruction", 56099750990875L, isochron.reconstructUTC(48466777820038L, UTC));
            assertEquals("Reconstruction", 56099751148088L, isochron.reconstructUTC(48466777977250L, UTC));
            assertEquals("Reconstruction", 56112600956363L, isochron.reconstructUTC(48479627757887L, UTC));
            assertEquals("Reconstruction", 56112601113226L, isochron.reconstructUTC(48479627914750L, UTC));
        }
        {
            //.# dor_dt       = 20785804321
            //.# dom_dt       = 20785808190
            //.# dormid       = 58710197394195
            //.# dommid       = 51147382243792
            //.# clen         = 188737.01460390663
            //.# epsilon      = -1.8613661612933415E-7

            long[] tcal_one =
                    {
                            58689411322500L,
                            51126596356954L,
                            51126596514250L,
                            58689411857247L
                    };
            long[] tcal_two =
                    {
                            58710197127000L,
                            51147382165334L,
                            51147382322250L,
                            58710197661390L
                    };

            Isochron isochron = new Isochron(tcal_one, tcal_two, 0);

            assertEquals("Epsilon", -1.8613661612933415E-7, isochron.getEpsilon(), 0.0);
            assertEquals("Cable Length", 1.8873701460390662E-5, isochron.getCableLength(), 0.0);
            assertEquals("Reconstruction", 58689411511226L, isochron.reconstructUTC(51126596356954L, UTC));
            assertEquals("Reconstruction", 58689411668521L, isochron.reconstructUTC(51126596514250L, UTC));
            assertEquals("Reconstruction", 58710197315737L, isochron.reconstructUTC(51147382165334L, UTC));
            assertEquals("Reconstruction", 66273011404077L, isochron.reconstructUTC(58710197661390L, UTC));
        }
    }

    static double asSeconds(double utc)
    {
        return 1.0E-10 * utc;
    }


}
