package icecube.daq.time.gps;

import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.time.gps.test.MockGPSDriver;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import static icecube.daq.time.gps.test.BuilderMethods.*;


/**
 * Tests the adaptive GPS Service
 */
public class FailsafeGPSServiceTest
{


    @BeforeClass
    public static void setupLogging()
    {
        // exercise logging calls, but output to nowhere
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(new NullAppender());
        Logger.getRootLogger().setLevel(Level.ALL);
    }

    @AfterClass
    public static void tearDownLogging()
    {
        BasicConfigurator.resetConfiguration();
    }

    @Test
    public void testInstantiation() throws InterruptedException
    {
        MockGPSDriver mockDriver = new MockGPSDriver();
        FailsafeGPSService subject =
                new FailsafeGPSService(new DSBGPSService(mockDriver),
                        new NullGPSService());

        // use before starting
        try
        {
            subject.getGps(0);
            fail("Expected Error");
        }
        catch (GPSServiceError gpsError)
        {
            //desired
        }

        assertFalse("should not be ready", subject.waitForReady(0, 1000));


        subject.setMoni(null);

        // shutdown without starting
        subject.shutdownAll();
    }

    @Test
    public void testStartupNoGPS() throws InterruptedException, GPSServiceError
    {
        //
        // Test fallback to secondary NULL GPS
        //
        MockGPSDriver mockDriver = new MockGPSDriver();
        FailsafeGPSService subject =
                new FailsafeGPSService(new DSBGPSService(mockDriver),
                        new NullGPSService());

        // sabatage the DSBGPSService
        mockDriver.setException(new GPSException("test"));
        mockDriver.setMode(MockGPSDriver.Mode.Exception);

        subject.startService(0);

        assertTrue("Mock service should be ready",
                subject.waitForReady(0, 1200));

        GPSInfo gps = subject.getGps(0);
        assertEquals("", 0L, gps.getDorclk());
        assertEquals("", 0L, gps.getOffset().in_0_1ns());
        assertEquals("", "001:00:00:00", gps.getTimestring());

        // even if driver recovers, initial selection should be fixed
        mockDriver.setMode(MockGPSDriver.Mode.Value);
        mockDriver.setValue(generateGPSInfo("123:45:55:33", 123456789L));

        gps = subject.getGps(0);
        assertEquals("", 0L, gps.getDorclk());
        assertEquals("", 0L, gps.getOffset().in_0_1ns());
        assertEquals("", "001:00:00:00", gps.getTimestring());

        //startup another card
        subject.startService(1);
        assertTrue("Mock service should be ready",
                subject.waitForReady(1, 1200));
        gps = subject.getGps(1);
        assertEquals("", 0L, gps.getDorclk());
        assertEquals("", 0L, gps.getOffset().in_0_1ns());
        assertEquals("", "001:00:00:00", gps.getTimestring());

        subject.shutdownAll();

    }

    @Test
    public void testStartupWithGPS() throws InterruptedException, GPSServiceError
    {
        //
        // Test running with a working primary DSB service
        //
        MockGPSDriver mockDriver = new MockGPSDriver();
        FailsafeGPSService subject =
                new FailsafeGPSService(new DSBGPSService(mockDriver),
                        new NullGPSService());

        mockDriver.setMode(MockGPSDriver.Mode.Value);
        mockDriver.setValue(generateGPSInfo("123:45:55:33", 123456789L));

        subject.startService(0);

        assertTrue("Mock service should be ready",
                subject.waitForReady(0, 1200));

        GPSInfo gps = subject.getGps(0);
        assertEquals("", 123456789L, gps.getDorclk());
        assertEquals("", 107061268271605500L, gps.getOffset().in_0_1ns());
        assertEquals("", "123:45:55:33", gps.getTimestring());


        // even if driver fails subsequently, the initial selection
        // should be fixed
        mockDriver.setMode(MockGPSDriver.Mode.Exception);
        mockDriver.setException(new GPSException("Test"));

        gps = subject.getGps(0);
        assertEquals("", 123456789L, gps.getDorclk());
        assertEquals("", 107061268271605500L, gps.getOffset().in_0_1ns());
        assertEquals("", "123:45:55:33", gps.getTimestring());

        //startup another card
        mockDriver.setMode(MockGPSDriver.Mode.Value);
        subject.startService(1);
        assertTrue("Mock service should be ready",
                subject.waitForReady(1, 1200));
        gps = subject.getGps(1);
        assertEquals("", 123456789L, gps.getDorclk());
        assertEquals("", 107061268271605500L, gps.getOffset().in_0_1ns());
        assertEquals("", "123:45:55:33", gps.getTimestring());

        subject.shutdownAll();

    }
}
