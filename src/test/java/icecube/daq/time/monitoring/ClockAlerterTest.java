package icecube.daq.time.monitoring;

import icecube.daq.domapp.dataprocessor.DataProcessorError;
import icecube.daq.juggler.alert.AlertQueue;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests icecube.daq.time.monitoring.ClockAlerter
 */
public class ClockAlerterTest
{

    // these need to be cleaned up in tear down
    AlertQueue alertQueue;
    MockAlerter mockAlerter;

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

    @Before
    public void setUp() throws Exception
    {
        mockAlerter = new MockAlerter();
        alertQueue = new AlertQueue(mockAlerter);
        alertQueue.start();
    }

    @After
    public void tearDown()
    {
        alertQueue.stop();
        mockAlerter.waitForClose();
    }

    @Test
    public void testAlertsSinglePeriod() throws DataProcessorError
    {
        ClockAlerter subject = new ClockAlerter(alertQueue, 1);

        for (int x=0; x<100; x++)
        {
            subject.alertNTPServer("test", "test");
            subject.alertSystemClockOffset(123.4d, "test");
            subject.alertMasterClockOffset(123.4d, "test");
            subject.alertMasterClockQuality(42);
        }

        alertQueue.stop();
        mockAlerter.waitForClose();

        //should only be one alert delivered per type
        int numDelivered = mockAlerter.alerts.size();
        assertEquals("Alert throttling failed", 4, numDelivered);
    }

    @Test
    public void testAlertsMultiplePeriod() throws DataProcessorError
    {
        final int interval = 5;
        final TimeUnit intervalUnit = TimeUnit.SECONDS;

        ClockAlerter subject = new ClockAlerter(alertQueue, interval,
                intervalUnit);

        //period 1
        for (int x=0; x<100; x++)
        {
            subject.alertNTPServer("test", "test");
            subject.alertSystemClockOffset(123.4d, "test");
            subject.alertMasterClockOffset(123.4d, "test");
            subject.alertMasterClockQuality(42);
        }

        //sleep a period
        try
        {
            System.out.println("Test sleeping for " +
                    intervalUnit.toSeconds(interval) +" seconds...");
            Thread.sleep(intervalUnit.toMillis(interval));
        }
        catch (InterruptedException e)
        {
            fail("interupted");
        }

        //period 2
        for (int x=0; x<100; x++)
        {
            subject.alertNTPServer("test", "test");
            subject.alertSystemClockOffset(123.4d, "test");
            subject.alertMasterClockOffset(123.4d, "test");
            subject.alertMasterClockQuality(42);
        }

        alertQueue.stop();
        mockAlerter.waitForClose();

        //should only be one alert delivered per type per period
        int numDelivered = mockAlerter.alerts.size();
        assertEquals("Alert throttling failed", 8, numDelivered);

//        for(Object obj : mockAlerter.alerts)
//        {
//            System.out.println(obj);
//        }
    }


}
