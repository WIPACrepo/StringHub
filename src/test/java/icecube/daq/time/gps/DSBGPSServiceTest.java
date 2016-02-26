package icecube.daq.time.gps;

import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.GPSNotReady;
import icecube.daq.dor.IDriver;
import icecube.daq.time.gps.test.MockGPSDriver;
import icecube.daq.juggler.alert.AlertQueue;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.monitoring.TCalExceptionAlerter;
import icecube.daq.time.monitoring.MockAlerter;
import icecube.daq.util.DeployedDOM;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static icecube.daq.time.gps.test.BuilderMethods.*;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;


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
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
    }


    @AfterClass
    public static void tearDown()
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

        subject.setMoni(null);

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
        MockAlerter alerter = new MockAlerter();
        AlertQueue alertQueue = constructAlertQueue(alerter);
        alertQueue.start();
        TCalExceptionAlerter tcalAlerter = new TCalExceptionAlerter(alertQueue,
                new DeployedDOM(-1L,-1, -1));

        subject.setMoni(tcalAlerter);
        //<


        //after 10 attempts, moni should be notified
        driver.setMode(MockGPSDriver.Mode.Exception);
        driver.setException(new GPSNotReady("test"));
        subject.startService(0);
        subject.waitForReady(0, 10000);
        assertTrue(alerter.alerts.size() > 1);

    }


}
