package icecube.daq.time.gps;

import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.GPSNotReady;
import icecube.daq.dor.IDriver;
import icecube.daq.dor.TimeCalib;
import icecube.daq.juggler.alert.AlertQueue;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.monitoring.IRunMonitor;
import icecube.daq.rapcal.Isochron;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.time.gps.test.MockGPSDriver;
import icecube.daq.util.DeployedDOM;
import icecube.daq.util.Leapseconds;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
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
    public void countHLCHit(long mbid, long utc)
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
    public Iterable<DeployedDOM> getConfiguredDOMs()
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
    public DeployedDOM getDom(long mbid)
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
     * @param map field->value map
     * @param addString if <tt>true</tt>, add "string" entry to map
     */
    public void sendMoni(String varname, Alerter.Priority priority,
                         Map<String, Object> map, boolean addString)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void setConfiguredDOMs(Collection<DeployedDOM> configuredDOMs)
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
            subject.waitForReady(5, 1000);
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

        assertTrue("Should be ready", subject.waitForReady(0, 1000));

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

        assertFalse("Should not be ready", subject.waitForReady(0, 1000));
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
        assertTrue("Should be ready", subject.waitForReady(0, 1000));
        GPSInfo gps = subject.getGps(0);
        assertEquals("", 1251451451L, gps.getDorclk());
        assertEquals("", 191352904274274500L, gps.getOffset().in_0_1ns());
        assertEquals("", "222:11:22:33", gps.getTimestring());

        // more exceptions
        driver.setMode(MockGPSDriver.Mode.Exception);
        driver.setException(new GPSNotReady("test2"));
        try{ Thread.sleep(1000);} catch (InterruptedException e){}

        assertTrue("Should be ready", subject.waitForReady(0, 1000));
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

    @Test
    public void testMisTic() throws Exception
    {
        //
        // Test that the service fails when an offset
        // mis-alignment occurs
        //
        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService subject = constructSubject(driver);

        long DOR_SEC = 10000000000L/500;
        final GPSInfo[] snaps = new GPSInfo[]
                {
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded

                        generateGPSInfo("005:12:33:09", 1234L),
                        generateGPSInfo("005:12:33:10", 1234L + DOR_SEC),
                        generateGPSInfo("005:12:33:11", 1234L + (2*DOR_SEC)),
                        generateGPSInfo("005:12:33:12", 1234L + (3*DOR_SEC)),
                        generateGPSInfo("005:12:33:13", 1234L + (4*DOR_SEC)),
                        generateGPSInfo("005:12:33:14", 1234L + (5*DOR_SEC)),
                        generateGPSInfo("005:12:33:15", 1234L + (6*DOR_SEC) + 1721), // mistic
                        generateGPSInfo("005:12:33:16", 1234L + (7*DOR_SEC)),
                        generateGPSInfo("005:12:33:17", 1234L + (8*DOR_SEC)),
                        generateGPSInfo("005:12:33:18", 1234L + (9*DOR_SEC)),

                };

        driver.setProducer(new MockGPSDriver.Producer()
        {
            int count=0;
            @Override
            public GPSInfo readGPS(final File gpsFile) throws GPSException
            {
                if(count < snaps.length)
                {
                    return snaps[count++];
                }
                else
                {
                    // end of snaps... reuse last
                    return snaps[snaps.length-1];
                }
            }
        });
        driver.setMode(MockGPSDriver.Mode.Producer);



        subject.startService(0);

        try{ Thread.sleep(15000);} catch (InterruptedException e){}

        assertTrue("Should be ready", subject.waitForReady(0, 1000));

        // the mis-tic should have put the service in error mode
        GPSInfo gps = null;
        try
        {
            gps = subject.getGps(0);
            fail("Service should fail after mistic");
        }
        catch (GPSServiceError gpsServiceError)
        {
            //desired
            assertEquals("GPS offset mis-alignment detected - old GPS: 005:12:33:14 : Quality = 32 DOR clk: 100001234 GPS offset: 3907889999383000 new GPS: 005:12:33:15 : Quality = 32 DOR clk: 120002955 GPS offset: 3907889998522500", gpsServiceError.getMessage());
        }
    }

    @Test
    public void test2016LeapSecond() throws Exception
    {
        // Test is only valid in 2016
        assertEquals(2016, Leapseconds.getInstance().defaultYear);

        //
        // Test that the fake leap second is managed
        //
        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService subject = constructSubject(driver);

        long DOR_SEC = 10000000000L/500;
        final GPSInfo[] snaps = new GPSInfo[]
                {
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded

                        generateGPSInfo("365:23:59:55", 1234L),
                        generateGPSInfo("365:23:59:56", 1234L + DOR_SEC),
                        generateGPSInfo("365:23:59:57", 1234L + (2*DOR_SEC)),
                        generateGPSInfo("365:23:59:58", 1234L + (3*DOR_SEC)),
                        generateGPSInfo("365:23:59:59", 1234L + (4*DOR_SEC)),
                        generateGPSInfo("365:23:59:60", 1234L + (5*DOR_SEC)), // Fake ET6000
                                                                              // leap
                        generateGPSInfo("366:00:00:00", 1234L + (6*DOR_SEC)), //2016 is a leap year
                        generateGPSInfo("366:00:00:01", 1234L + (7*DOR_SEC)),
                        generateGPSInfo("366:00:00:02", 1234L + (8*DOR_SEC)),
                        generateGPSInfo("366:00:00:03", 1234L + (9*DOR_SEC)),
                };

        driver.setProducer(new MockGPSDriver.Producer()
        {
            int count=0;
            @Override
            public GPSInfo readGPS(final File gpsFile) throws GPSException
            {
                if(count < snaps.length)
                {
                    return snaps[count++];
                }
                else
                {
                    // end of snaps... reuse last
                    return snaps[snaps.length-1];
                }
            }
        });
        driver.setMode(MockGPSDriver.Mode.Producer);



        subject.startService(0);

        try{ Thread.sleep(15000);} catch (InterruptedException e){}

        assertTrue("Should be ready", subject.waitForReady(0, 1000));

        // last snap should be from 364:23:59:60
        GPSInfo gps = subject.getGps(0);
        assertEquals("", (1234L + (5*DOR_SEC)), gps.getDorclk());
        assertEquals("", 315359949999383000L, gps.getOffset().in_0_1ns());
        assertEquals("", "365:23:59:60", gps.getTimestring());
    }

    @Test
    public void test2016EndOfYear() throws Exception
    {
        // Test is only valid in 2016
        assertEquals(2016, Leapseconds.getInstance().defaultYear);

        //
        // Test that the year rollover is managed
        //
        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService subject = constructSubject(driver);

        long DOR_SEC = 10000000000L/500;
        final GPSInfo[] snaps = new GPSInfo[]
                {
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded
                        generateGPSInfo("123:45:55:12", 999L), // discarded

                        generateGPSInfo("366:23:59:55", 1234L), //2016 is a leap year
                        generateGPSInfo("366:23:59:56", 1234L + DOR_SEC),
                        generateGPSInfo("366:23:59:57", 1234L + (2*DOR_SEC)),
                        generateGPSInfo("366:23:59:58", 1234L + (3*DOR_SEC)),
                        generateGPSInfo("366:23:59:59", 1234L + (4*DOR_SEC)),
                        generateGPSInfo("366:23:59:60", 1234L + (5*DOR_SEC)), // Real 2016 leap

                        generateGPSInfo("001:00:00:00", 1234L + (6*DOR_SEC)),
                        generateGPSInfo("001:00:00:01", 1234L + (7*DOR_SEC)),
                        generateGPSInfo("001:00:00:02", 1234L + (8*DOR_SEC)),
                        generateGPSInfo("001:00:00:03", 1234L + (9*DOR_SEC)),
                };

        driver.setProducer(new MockGPSDriver.Producer()
        {
            int count=0;
            @Override
            public GPSInfo readGPS(final File gpsFile) throws GPSException
            {
                if(count < snaps.length)
                {
                    return snaps[count++];
                }
                else
                {
                    // end of snaps... reuse last
                    return snaps[snaps.length-1];
                }
            }
        });
        driver.setMode(MockGPSDriver.Mode.Producer);



        subject.startService(0);

        try{ Thread.sleep(15000);} catch (InterruptedException e){}

        assertTrue("Should be ready", subject.waitForReady(0, 1000));

        // last snap should be from 366:23:59:60
        GPSInfo gps = subject.getGps(0);
        assertEquals("", (1234L + (5*DOR_SEC)), gps.getDorclk());
        assertEquals("", 316223949999383000L, gps.getOffset().in_0_1ns());
        assertEquals("", "366:23:59:60", gps.getTimestring());
    }
}
