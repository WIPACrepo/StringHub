package icecube.daq.time.gps;

import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.GPSNotReady;
import icecube.daq.dor.IDriver;
import icecube.daq.dor.TimeCalib;
import icecube.daq.juggler.alert.AlertQueue;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.monitoring.IRunMonitor;
import icecube.daq.payload.IUTCTime;
import icecube.daq.rapcal.Isochron;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.time.gps.test.MockGPSDriver;
import icecube.daq.util.DOMInfo;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static icecube.daq.time.gps.test.BuilderMethods.*;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;


class MyMonitor
    implements IRunMonitor
{
    private boolean expectNotReady;

    @Override
    public void countHLCHit(long mbid[], long[] utc)
    {
        throw new Error("Unimplemented");
    }

    void expectNotReady()
    {
        expectNotReady = true;
    }

    /**
     * Return the list of DOMs configured for this string
     *
     * @return map of mainboard ID -&gt; deployed DOM data
     */
    public Iterable<DOMInfo> getConfiguredDOMs()
    {
        throw new Error("Unimplemented");
    }

    /**
     * Get DOM information
     *
     * @param mbid DOM mainboard ID
     *
     * @return dom information
     */
    public DOMInfo getDom(long mbid)
    {
        throw new Error("Unimplemented");
    }

    /**
     * Get the string representation of the starting time for this run
     *
     * @return starting time
     */
    public String getStartTimeString()
    {
        throw new Error("Unimplemented");
    }

    /**
     * Get the string representation of the ending time for this run
     *
     * @return ending time
     */
    public String getStopTimeString()
    {
        throw new Error("Unimplemented");
    }

    /**
     * Get this string's number
     *
     * @return string number
     */
    public int getString()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public boolean isRunning()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void join()
        throws InterruptedException
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void push(long mbid, Isochron isochron)
    {
        // ignore isochrons
    }

    @Override
    public void pushException(int string, int card, GPSException exception)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void pushException(long mbid, RAPCalException exception,
                              TimeCalib tcal)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void pushGPSMisalignment(int string, int card, GPSInfo oldGPS,
                                    GPSInfo newGPS)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void pushGPSProcfileNotReady(int string, int card)
    {
        if (!expectNotReady) {
            throw new Error(String.format("String %d card %d not ready",
                                          string, card));
        }
    }

    @Override
    public void pushWildTCal(long mbid, double cableLength, double averageLen)
    {
        throw new Error("Unimplemented");
    }

    public void reset()
    {
        expectNotReady = false;
    }

    /**
     * Send monitoring message to Live
     *
     * @param varname quantity name
     * @param priority message priority
     * @param map field->value map
     */
    public void sendMoni(String varname, Alerter.Priority priority,
                         Map<String, Object> map)
    {
        throw new Error("Unimplemented");
    }

    /**
     * Send monitoring message to Live
     *
     * @param varname quantity name
     * @param priority message priority
     * @param utc pDAQ UTC timestamp
     * @param map field->value map
     * @param addString if <tt>true</tt>, add "string" entry to map
     */
    public void sendMoni(String varname, Alerter.Priority priority,
                         IUTCTime utc, Map<String, Object> map,
                         boolean addString)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void setConfiguredDOMs(Collection<DOMInfo> configuredDOMs)
    {
        throw new Error("Unimplemented");
    }

    public void setGPSProcfileNotReady()
    {
        if (!expectNotReady)
        {
            throw new Error("Unexpected GPS procfile error");
        }
    }

    @Override
    public void setRunNumber(int i0)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void start()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void stop()
    {
        throw new Error("Unimplemented");
    }
}

/**
 * Tests DSBGPSService.java
 */
public class DSBGPSServiceTest
{

    // Set huge to support automated testing on sluggish virtual machine.
    public static final int NOMINAL_WAIT_MILLIS = 5000;

    // storage for possible live objects created during tests
    interface ByProduct
    {
        void dispose();
    }
    List<ByProduct> byProducts =  new ArrayList<ByProduct>(1);

    private DSBGPSService constructSubject(IDriver driver)
    {
        final DSBGPSService subject = new DSBGPSService(driver);
        byProducts.add(new ByProduct()
        {
            @Override
            public void dispose()
            {
                subject.shutdownAll();
            }
        });
        return subject;
    }

    private AlertQueue constructAlertQueue(Alerter alerter)
    {
        final AlertQueue alertQueue = new AlertQueue(alerter);
        byProducts.add(new ByProduct()
        {
            @Override
            public void dispose()
            {
                alertQueue.stop();
            }
        });
        return alertQueue;
    }

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

    @After
    public void cleanUpByProducts()
    {
        while(byProducts.size() > 0)
        {
            ByProduct waste = byProducts.remove(0);
            waste.dispose();
        }
    }

    @Test
    public void testInstantiation() throws InterruptedException
    {
        //
        // Test conditions at instantiation
        //
        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService subject = constructSubject(driver);

        // use before starting
        try
        {
            subject.waitForReady(5, NOMINAL_WAIT_MILLIS);
            fail("Expected Error");
        }
        catch (Throwable th)
        {
            //desired
        }

        try
        {
            subject.getGps(0);
            fail("Expected Error");
        }
        catch (Throwable th)
        {
            //desired
        }

        subject.setRunMonitor(null);

        // shutdown without starting
        subject.shutdownAll();

    }

    @Test
    public void testStartup() throws InterruptedException, GPSServiceError
    {
        //
        // Test basic startup
        //
        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService subject = constructSubject(driver);


        driver.setMode(MockGPSDriver.Mode.Value);
        driver.setValue(generateGPSInfo("238:21:13:55", 1235251125L));

        subject.startService(0);

        assertTrue("Should be ready", subject.waitForReady(0, NOMINAL_WAIT_MILLIS));

        GPSInfo gps = subject.getGps(0);
        assertEquals("", 1235251125L, gps.getDorclk());
        assertEquals("", 205531732374437500L, gps.getOffset().in_0_1ns());
        assertEquals("", "238:21:13:55", gps.getTimestring());
    }

    @Test
    public void testStartupWithExceptions() throws InterruptedException,
            GPSServiceError
    {

        //
        // test startup, forcing exceptions from mock driver
        //

        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService subject = constructSubject(driver);

        // CASE I: GPS not READY
        driver.setMode(MockGPSDriver.Mode.Exception);
        driver.setException(new GPSNotReady("test"));

        subject.startService(0);

        assertFalse("Should not be ready", subject.waitForReady(0, NOMINAL_WAIT_MILLIS));
        try
        {
            subject.getGps(0);
            fail("expected exception");
        }
        catch (GPSServiceError gpsServiceError)
        {
            String expected = "Service not initialized for card 0";
            assertEquals("", expected, gpsServiceError.getMessage());
        }


        // recover
        driver.setValue(generateGPSInfo("222:11:22:33", 1251451451L));
        driver.setMode(MockGPSDriver.Mode.Value);
        assertTrue("Should be ready", subject.waitForReady(0, NOMINAL_WAIT_MILLIS));
        GPSInfo gps = subject.getGps(0);
        assertEquals("", 1251451451L, gps.getDorclk());
        assertEquals("", 191352904274274500L, gps.getOffset().in_0_1ns());
        assertEquals("", "222:11:22:33", gps.getTimestring());

        // more exceptions
        driver.setMode(MockGPSDriver.Mode.Exception);
        driver.setException(new GPSNotReady("test2"));
        try{ Thread.sleep(1000);} catch (InterruptedException e){}

        assertTrue("Should be ready", subject.waitForReady(0, NOMINAL_WAIT_MILLIS));
        gps = subject.getGps(0);
        assertEquals("", 1251451451L, gps.getDorclk());
        assertEquals("", 191352904274274500L, gps.getOffset().in_0_1ns());
        assertEquals("", "222:11:22:33", gps.getTimestring());
    }


    @Test
    public void testMoniAlerts() throws InterruptedException
    {
        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService subject = constructSubject(driver);

        //> set moni
        MyMonitor runMonitor = new MyMonitor();
        runMonitor.expectNotReady();

        subject.setRunMonitor(runMonitor);
        //<


        //after 10 attempts, moni should be notified
        driver.setMode(MockGPSDriver.Mode.Exception);
        driver.setException(new GPSNotReady("test"));
        subject.startService(0);
        subject.waitForReady(0, 10000);

    }


}
