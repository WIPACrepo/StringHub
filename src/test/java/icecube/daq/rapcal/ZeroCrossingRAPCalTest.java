package icecube.daq.rapcal;

import icecube.daq.util.UTC;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static icecube.daq.rapcal.BuildTCalMethods.*;
import static org.junit.Assert.*;


/**
 * Tests ZeroCrossingRAPCal.java
 */
public class ZeroCrossingRAPCalTest
{

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
    public void testFineTimeCorrection() throws RAPCalException
    {
        short[] dorwf =
                {
                        500,500,500,499,500,502,501,501,
                        500,501,501,500,500,502,499,499,
                        499,501,499,499,500,516,548,586,
                        627,669,710,748,784,820,847,848,
                        817,774,718,661,602,542,481,423,
                        371,332,313,301,296,294,293,296,
                        0,  0,  0,  0,  0,  0,  0,  0,
                        0,  0,  0,  0,  0,  0,  0,  0
                };

        short[] domwf =
                {
                        512,512,512,512,512,512,512,512,
                        512,511,512,512,512,512,512,513,
                        513,513,513,513,514,522,542,568,
                        596,625,653,680,706,731,755,762,
                        743,710,672,632,590,549,507,468,
                        429,399,383,375,370,370,369,370,
                        0,  0,  0,  0,  0,  0,  0,  0,
                        0,  0,  0,  0,  0,  0,  0,  0
                };

        ZeroCrossingRAPCal subject = new ZeroCrossingRAPCal();

        assertEquals("Unexpected fine time correction",
                -5.156967213114754E-7,
                subject.getFineTimeCorrection(dorwf), 0.0);

        assertEquals("Unexpected fine time correction",
                -5.061904761904763E-7,
                subject.getFineTimeCorrection(domwf), 0.0);
    }

    @Test
    public void testDegenerateWaveform() throws RAPCalException
    {
        //
        // tests a wavform without a zero crossing in
        // element 31 - 47
        //

        short[] degenerate =
                {
                        500,500,500,499,500,502,501,501,
                        500,501,501,500,500,502,499,499,
                        499,501,499,499,500,516,548,586,
                        627,669,710,748,784,820,847,848,
                        817,774,718,661,602,542,542,542,
                        542,542,542,542,542,542,542,542,
                        0,  0,  0,  0,  0,  0,  0,  0,
                        0,  0,  0,  0,  0,  0,  0,  0
                };

        ZeroCrossingRAPCal subject = new ZeroCrossingRAPCal();

        try
        {
            double val = subject.getFineTimeCorrection(degenerate);
            fail("Expected exception, got " + val);
        }
        catch (RAPCalException e)
        {
            //expected
            assertTrue(e instanceof BadTCalException);
            BadTCalException narrow = (BadTCalException) e;
            assertEquals("message", ZeroCrossingRAPCal.class.getName(),
                    narrow.getSource());
            assertArrayEquals("waveform", degenerate,
                    narrow.getWaveform());
        }

    }

    @Test
    public void testThreshold() throws RAPCalException
    {
        //
        // test the threshold control
        //

        String THRESHOLD_PROPERTY =
                "icecube.daq.rapcal.LeadingEdgeRAPCal.threshold";
        String original = System.getProperty(THRESHOLD_PROPERTY);
        System.setProperty(THRESHOLD_PROPERTY,  "90.0");

        try
        {
            short[] dorwf =
                    {
                            500,500,500,499,500,502,501,501,
                            500,501,501,500,500,502,499,499,
                            499,501,499,499,500,516,548,586,
                            627,669,710,748,784,820,847,848,
                            817,774,718,661,602,542,481,423,
                            371,332,313,301,296,294,293,296,
                            0,  0,  0,  0,  0,  0,  0,  0,
                            0,  0,  0,  0,  0,  0,  0,  0
                    };

            short[] domwf =
                    {
                            512,512,512,512,512,512,512,512,
                            512,511,512,512,512,512,512,513,
                            513,513,513,513,514,522,542,568,
                            596,625,653,680,706,731,755,762,
                            743,710,672,632,590,549,507,468,
                            429,399,383,375,370,370,369,370,
                            0,  0,  0,  0,  0,  0,  0,  0,
                            0,  0,  0,  0,  0,  0,  0,  0
                    };

            ZeroCrossingRAPCal subject = new ZeroCrossingRAPCal();

            assertEquals("Unexpected fine time correction",
                    -5.90125E-7,
                    subject.getFineTimeCorrection(dorwf), 0.0);

            assertEquals("Unexpected fine time correction",
                    -6.145238095238095E-7,
                    subject.getFineTimeCorrection(domwf), 0.0);
        }
        finally
        {
            if(original != null)
            {
                System.setProperty(THRESHOLD_PROPERTY, original);
            }
            else
            {
                System.clearProperty(THRESHOLD_PROPERTY);
            }
        }
    }

    @Test
    public void testIntegration() throws RAPCalException
    {
        //
        // Tests the integration of fine time correction into AbstractRAPCal
        //
        long[] tcal_1 =
                {24767312917086L, 24767312917465L, 14613698885L, 14613699497L};
        long[] tcal_2 =
                {24767314917086L, 24767314917465L, 14617698885L, 14617699497L};


        short[] dorwf =
                {
                        500,500,500,499,500,502,501,501,
                        500,501,501,500,500,502,499,499,
                        499,501,499,499,500,516,548,586,
                        627,669,710,748,784,820,847,848,
                        817,774,718,661,602,542,481,423,
                        371,332,313,301,296,294,293,296,
                        0,  0,  0,  0,  0,  0,  0,  0,
                        0,  0,  0,  0,  0,  0,  0,  0
                };

        short[] domwf =
                {
                        512,512,512,512,512,512,512,512,
                        512,511,512,512,512,512,512,513,
                        513,513,513,513,514,522,542,568,
                        596,625,653,680,706,731,755,762,
                        743,710,672,632,590,549,507,468,
                        429,399,383,375,370,370,369,370,
                        0,  0,  0,  0,  0,  0,  0,  0,
                        0,  0,  0,  0,  0,  0,  0,  0
                };

        ZeroCrossingRAPCal subject = new ZeroCrossingRAPCal();
        subject.update(buildTimeCalib(tcal_1, dorwf, domwf), new UTC(0));
        subject.update(buildTimeCalib(tcal_2, dorwf, domwf), new UTC(0));
        assertEquals("Cable Length", 1.31415E-6, subject.cableLength(), 0);

        // correction applied to DOM RX, not DOM_TX so boundary is the same
        assertTrue("Isocron boundary", subject.laterThan(tcal_2[3]));

        assertEquals("UTC Reconstruction",
                12383656458561203L,
                subject.domToUTC(tcal_1[2]).in_0_1ns());
        assertEquals("UTC Reconstruction",
                12383656458714203L,
                subject.domToUTC(tcal_1[3]).in_0_1ns());
        assertEquals("UTC Reconstruction",
                12383657458561203L,
                subject.domToUTC(tcal_2[2]).in_0_1ns());
        assertEquals("UTC Reconstruction",
                12383657458714203L,
                subject.domToUTC(tcal_2[3]).in_0_1ns());

    }
}
