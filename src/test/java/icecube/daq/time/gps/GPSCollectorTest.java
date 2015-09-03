package icecube.daq.time.gps;

import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.GPSNotReady;
import icecube.daq.time.gps.test.MockGPSDriver;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

import static icecube.daq.time.gps.test.BuilderMethods.*;

/**
 * Tests the GPSCollector class in DSBGPSService.java
 */
public class GPSCollectorTest
{

    @BeforeClass
    public static void setupLogging()
    {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
    }


    @AfterClass
    public static void tearDown()
    {
        BasicConfigurator.resetConfiguration();
    }


    @Test
    public void testInstantiation()
    {
        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService base = new DSBGPSService(driver);

        DSBGPSService.GPSCollector subject = base.new GPSCollector(driver, 0);

        assertFalse("should not be ready", subject.isRunning());

        try
        {
            subject.getGps();
            fail("expected exception");
        }
        catch (GPSServiceError gpsServiceError)
        {
            String expected = "Service not running for card 0";
            assertEquals("", expected, gpsServiceError.getMessage());
        }
    }

    @Test
    public void testStartup() throws InterruptedException, GPSServiceError
    {
        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService base = new DSBGPSService(driver);

        DSBGPSService.GPSCollector subject = base.new GPSCollector(driver, 0);

        driver.setMode(MockGPSDriver.Mode.Value);
        driver.setValue(generateGPSInfo("001:22:33:44", 1231235L));

        subject.startup();

        // NOTE:
        // probable chance to catch pre-init gps, if this test consistently
        // fails, delete this case
        try
        {
            subject.getGps();
            fail("expected exception");
        }
        catch (GPSServiceError gpsServiceError)
        {
            String expected = "Service not initialized for card 0";
            assertEquals("", expected, gpsServiceError.getMessage());
        }


        assertTrue("should be ready", subject.waitForReady(1000));

        GPSInfo gps = subject.getGps();
        assertEquals("", "001:22:33:44", gps.getTimestring() );
        assertEquals("", 1231235L, gps.getDorclk() );
        assertEquals("", 812239384382500L, gps.getOffset().in_0_1ns() );

        //advance GPS and DOR the same amount
        driver.setValue(generateGPSInfo("001:22:33:45", 1231235L + 20000000));
        try{ Thread.sleep(1000);} catch (InterruptedException e){}

        gps = subject.getGps();
        assertEquals("", "001:22:33:45", gps.getTimestring() );
        assertEquals("", (1231235L + 20000000), gps.getDorclk() );
        assertEquals("", 812239384382500L, gps.getOffset().in_0_1ns() );


        subject.shutdown();

        assertFalse("should shut down", subject.isRunning());

        try
        {
            subject.getGps();
            fail("expected exception");
        }
        catch (GPSServiceError gpsServiceError)
        {
            String expected = "Service not running for card 0";
            assertEquals("", expected, gpsServiceError.getMessage());
        }

    }

    @Test
    public void testBadShutdown()
            throws InterruptedException, GPSServiceError
    {
        //
        // Test interrupting the thread externally
        //
        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService base = new DSBGPSService(driver);

        DSBGPSService.GPSCollector subject = base.new GPSCollector(driver, 0);

        driver.setMode(MockGPSDriver.Mode.Value);
        driver.setValue(generateGPSInfo("001:22:33:44", 1231235L));

        subject.startup();

        assertTrue("should be ready", subject.waitForReady(1000));

        GPSInfo gps = subject.getGps();
        assertEquals("", "001:22:33:44", gps.getTimestring() );
        assertEquals("", 1231235L, gps.getDorclk() );
        assertEquals("", 812239384382500L, gps.getOffset().in_0_1ns() );

        // behave bad and interrupt the thread
        subject.interrupt();

        try{ Thread.sleep(1000);} catch (InterruptedException e){}


        try
        {
            subject.getGps();
            fail("expected exception");
        }
        catch (GPSServiceError gpsServiceError)
        {
            String expected = "Unexpected interruption";
            assertEquals("", expected, gpsServiceError.getMessage());
        }

    }

    @Test
    public void testBufferDrain() throws InterruptedException,
            GPSServiceError
    {
        //
        // Tests that the first 11 snaps are read and discarded
        //

        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService base = new DSBGPSService(driver);

        DSBGPSService.GPSCollector subject = base.new GPSCollector(driver, 0);

        driver.setProducer(new MockGPSDriver.Producer()
        {
            int count=0;
            @Override
            public GPSInfo readGPS(final File gpsFile) throws GPSException
            {
                count++;
                if(count <= 11)
                {
                    return generateGPSInfo("001:02:03:04", 1234L);
                }
                else
                {
                    return generateGPSInfo("111:22:33:44", 4321L);
                }
            }
        });
        driver.setMode(MockGPSDriver.Mode.Producer);

        subject.startup();
        assertTrue("should be ready", subject.waitForReady(1000));

        GPSInfo gps = subject.getGps();
        assertEquals("", "111:22:33:44", gps.getTimestring() );
        assertEquals("", 4321L, gps.getDorclk() );
        assertEquals("", 95852239997839500L, gps.getOffset().in_0_1ns() );


        subject.shutdown();
    }

    @Test
    public void testBufferDrainError() throws InterruptedException,
            GPSServiceError
    {
        //
        // Tests that read misses are ignored during first 11 buffer reads
        //

        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService base = new DSBGPSService(driver);

        DSBGPSService.GPSCollector subject = base.new GPSCollector(driver, 0);

        driver.setProducer(new MockGPSDriver.Producer()
        {
            int count=0;
            @Override
            public GPSInfo readGPS(final File gpsFile) throws GPSException
            {
                count++;
                if(count < 2)
                {
                    return generateGPSInfo("001:02:03:04", 1234L);
                }
                else if(count < 3)
                {
                    throw new GPSNotReady("test");
                }

                else
                {
                    return generateGPSInfo("111:22:33:44", 4321L);
                }
            }
        });
        driver.setMode(MockGPSDriver.Mode.Producer);

        subject.startup();
        assertTrue("should be ready", subject.waitForReady(1000));

        GPSInfo gps = subject.getGps();
        assertEquals("", "111:22:33:44", gps.getTimestring() );
        assertEquals("", 4321L, gps.getDorclk() );
        assertEquals("", 95852239997839500L, gps.getOffset().in_0_1ns() );


        subject.shutdown();
    }


    @Test
    public void testBufferDrainError2() throws InterruptedException,
            GPSServiceError
    {
        //
        // Tests that read errors are ignored during first 11 buffer reads
        //

        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService base = new DSBGPSService(driver);

        DSBGPSService.GPSCollector subject = base.new GPSCollector(driver, 0);

        driver.setProducer(new MockGPSDriver.Producer()
        {
            int count=0;
            @Override
            public GPSInfo readGPS(final File gpsFile) throws GPSException
            {
                count++;
                if(count < 2)
                {
                    return generateGPSInfo("001:02:03:04", 1234L);
                }
                else if(count < 3)
                {
                    throw new GPSException("test");
                }

                else
                {
                    return generateGPSInfo("111:22:33:44", 4321L);
                }
            }
        });
        driver.setMode(MockGPSDriver.Mode.Producer);

        subject.startup();
        assertTrue("should be ready", subject.waitForReady(1000));

        GPSInfo gps = subject.getGps();
        assertEquals("", "111:22:33:44", gps.getTimestring() );
        assertEquals("", 4321L, gps.getDorclk() );
        assertEquals("", 95852239997839500L, gps.getOffset().in_0_1ns() );


        subject.shutdown();
    }

    @Test
    public void testUnstableOffset()
            throws InterruptedException, GPSServiceError
    {
        //
        // Tests the case of an unstable GPS offset
        //

        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService base = new DSBGPSService(driver);

        DSBGPSService.GPSCollector subject = base.new GPSCollector(driver, 0);

        driver.setMode(MockGPSDriver.Mode.Value);
        driver.setValue(generateGPSInfo("001:22:33:44", 1231235L));

        subject.startup();
        assertTrue("should be ready", subject.waitForReady(1000));

        GPSInfo gps = subject.getGps();
        assertEquals("", "001:22:33:44", gps.getTimestring() );
        assertEquals("", 1231235L, gps.getDorclk() );
        assertEquals("", 812239384382500L, gps.getOffset().in_0_1ns() );

        //advance GPS and DOR the same amount
        driver.setValue(generateGPSInfo("001:22:33:45", 1231235L + 20000000));
        try{ Thread.sleep(1000);} catch (InterruptedException e){}

        gps = subject.getGps();
        assertEquals("", "001:22:33:45", gps.getTimestring() );
        assertEquals("", (1231235L + 20000000), gps.getDorclk() );
        assertEquals("", 812239384382500L, gps.getOffset().in_0_1ns() );

        // advance GPS more than the DOR clock
        driver.setValue(generateGPSInfo("001:22:33:46", 1231235L + 39999999));
        try{ Thread.sleep(1000);} catch (InterruptedException e){}

        try
        {
            subject.getGps();
            fail("expected exception");
        }
        catch (GPSServiceError gpsServiceError)
        {
            String expected = "GPS offset mis-alignment detected -" +
                    " old GPS: 001:22:33:45 : Quality = 32 DOR clk: 21231235" +
                    " GPS offset: 812239384382500 new GPS: 001:22:33:46 :" +
                    " Quality = 32 DOR clk: 41231234" +
                    " GPS offset: 812239384383000";
            assertEquals("", expected, gpsServiceError.getMessage());
        }

        assertFalse("failure should stop", subject.isRunning());

        subject.shutdown();

    }


    @Test
    public void testRecoverableDriverErrors() throws InterruptedException,
            GPSServiceError
    {
        //
        // Tests the case of a small number of errors from driver
        //

        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService base = new DSBGPSService(driver);

        DSBGPSService.GPSCollector subject = base.new GPSCollector(driver, 0);

        // CASE I: start up with GPS not ready
        driver.setMode(MockGPSDriver.Mode.Exception);
        driver.setException(new GPSNotReady("test"));
        subject.startup();

        assertFalse("should not be ready", subject.waitForReady(1000));

        driver.setMode(MockGPSDriver.Mode.Value);
        driver.setValue(generateGPSInfo("001:22:33:44", 1231235L));
        assertTrue("should be ready", subject.waitForReady(1000));

        GPSInfo gps = subject.getGps();
        assertEquals("", "001:22:33:44", gps.getTimestring() );
        assertEquals("", 1231235L, gps.getDorclk() );
        assertEquals("", 812239384382500L, gps.getOffset().in_0_1ns() );


        // CASE II: other GPSException
        driver.setException(new GPSException("test2"));
        try{ Thread.sleep(1000);} catch (InterruptedException e){}

        gps = subject.getGps();
        assertEquals("", "001:22:33:44", gps.getTimestring() );
        assertEquals("", 1231235L, gps.getDorclk() );
        assertEquals("", 812239384382500L, gps.getOffset().in_0_1ns() );

        subject.shutdown();
    }

    @Test
    public void testNonRecoverableMissedReads() throws InterruptedException,
            GPSServiceError
    {
        //
        // Tests the case of a large number of missed reads
        //

        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService base = new DSBGPSService(driver);

        DSBGPSService.GPSCollector subject =
                base.new GPSCollector(driver, 0, 11, 10);

        driver.setMode(MockGPSDriver.Mode.Value);
        driver.setValue(generateGPSInfo("001:22:33:44", 1231235L));
        subject.startup();
        assertTrue("should be ready", subject.waitForReady(1000));

        GPSInfo gps = subject.getGps();
        assertEquals("", "001:22:33:44", gps.getTimestring() );
        assertEquals("", 1231235L, gps.getDorclk() );
        assertEquals("", 812239384382500L, gps.getOffset().in_0_1ns() );

        // simulate hijacking of gpssync file
        driver.setException(new GPSNotReady("test"));
        driver.setMode(MockGPSDriver.Mode.Exception);
        try{ Thread.sleep(10000);} catch (InterruptedException e){}
        try
        {
            subject.getGps();
            fail("expected exception");
        }
        catch (GPSServiceError gpsServiceError)
        {
            String expected = "GPS service is not receiving readings" +
                    " from gpssync file";
            assertEquals("", expected, gpsServiceError.getMessage());
        }

        assertFalse("failure should stop", subject.isRunning());
    }

    @Test
    public void testNonRecoverableExceptions() throws InterruptedException,
            GPSServiceError
    {
        //
        // Tests the case of a large number of exceptions from driver
        //

        MockGPSDriver driver = new MockGPSDriver();
        DSBGPSService base = new DSBGPSService(driver);

        DSBGPSService.GPSCollector subject =
                base.new GPSCollector(driver, 0, 11, 10);

        driver.setMode(MockGPSDriver.Mode.Value);
        driver.setValue(generateGPSInfo("001:22:33:44", 1231235L));
        subject.startup();
        assertTrue("should be ready", subject.waitForReady(1000));

        GPSInfo gps = subject.getGps();
        assertEquals("", "001:22:33:44", gps.getTimestring() );
        assertEquals("", 1231235L, gps.getDorclk() );
        assertEquals("", 812239384382500L, gps.getOffset().in_0_1ns() );

        // simulate driver failure
        driver.setException(new GPSException("test-xyzzy"));
        driver.setMode(MockGPSDriver.Mode.Exception);
        try{ Thread.sleep(10000);} catch (InterruptedException e){}
        try
        {
            subject.getGps();
            fail("expected exception");
        }
        catch (GPSServiceError gpsServiceError)
        {
            String expected = "GPS service received 10 consecutive" +
                    " errors reading gpssync file.";
            assertEquals("", expected, gpsServiceError.getMessage());
        }

        assertFalse("failure should stop", subject.isRunning());
    }



}


