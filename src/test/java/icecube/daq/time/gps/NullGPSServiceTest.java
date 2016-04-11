package icecube.daq.time.gps;

import icecube.daq.dor.GPSInfo;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;


/**
 * Tests NullGPSService.java
 */
public class NullGPSServiceTest
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
    public void testAll() throws InterruptedException, GPSServiceError
    {
        //
        // Test the full interface
        //
        NullGPSService subject = new NullGPSService();
        subject.startService(0);

        assertTrue("should be ready", subject.waitForReady(0,0));
        GPSInfo gps = subject.getGps(0);
        assertEquals("", "001:00:00:00", gps.getTimestring());
        assertEquals("", 0, gps.getDorclk());
        assertEquals("", 0, gps.getOffset().in_0_1ns());

        subject.setRunMonitor(null);
        subject.shutdownAll();

    }
}
