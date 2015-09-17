package icecube.daq.livemoni;


import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.AlertQueue;
import icecube.daq.time.monitoring.MockAlerter;
import icecube.daq.util.TimeUnits;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;


/**
 * Tests DOMClockRolloverAlerter.java
 */
public class DOMClockRolloverAlerterTest
{

    private final static long NANOS_PER_DAY = 24 * 60 * 60 * 1000000000L;

    DOMClockRolloverAlerter subject;
    MockAlerter mock;
    AlertQueue alertQueue;

    @BeforeClass
    public static void setupLogging()
    {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
    }

    @Before
    public void setUp() throws Exception
    {
        subject = new DOMClockRolloverAlerter();
        mock = new MockAlerter();
        alertQueue = new AlertQueue(mock);
        alertQueue.start();
    }

    @After
    public void tearDown() throws Exception
    {
        alertQueue.stop();
        mock.waitForClose();
    }

    @AfterClass
    public static void tearDownClass()
    {
        BasicConfigurator.resetConfiguration();
    }

    @Test
    public void testMonitorDOMClocks_1() throws AlertException
    {
        //
        // Tests the dom clock check when threshold not exceded
        //
        final long THRESHOLD =
                (DOMClockRolloverAlerter.DOM_UPTIME_WARN_THRESHOLD_DAYS *
                NANOS_PER_DAY) / 25L;
        Map<DOMChannelInfo, Long> domRecords = new HashMap<DOMChannelInfo, Long>(10);
        domRecords.put(new DOMChannelInfo(Long.toHexString(91234988934L), 0,1,'A'),
                THRESHOLD);
        domRecords.put(new DOMChannelInfo(Long.toHexString(91234988934L), 0,1,'B'),
                THRESHOLD-1);
        domRecords.put(new DOMChannelInfo(Long.toHexString(91234988934L), 5,1,'A'),
                THRESHOLD-999999999);
        domRecords.put(new DOMChannelInfo(Long.toHexString(91234988934L), 5,3,'B'),
                0L);

        subject.monitorDOMClocks(alertQueue, domRecords);

        alertQueue.stop();

        mock.waitForClose();

        assertEquals("", 0, mock.alerts.size());
    }

    @Test
    public void testMonitorDOMClocks_2() throws AlertException
    {
        //
        // Tests the dom clock check when warning threshold is exceeded
        //
        final long THRESHOLD =
                (DOMClockRolloverAlerter.DOM_UPTIME_WARN_THRESHOLD_DAYS *
                        NANOS_PER_DAY) / 25L;
        Map<DOMChannelInfo, Long> domRecords = new HashMap<DOMChannelInfo, Long>(10);
        domRecords.put(new DOMChannelInfo(Long.toHexString(91234988934L), 0,1,'A'),
                THRESHOLD+1);
        domRecords.put(new DOMChannelInfo(Long.toHexString(91234988934L), 0,1,'B'),
                THRESHOLD-1);
        domRecords.put(new DOMChannelInfo(Long.toHexString(91234988934L), 5,1,'A'),
                THRESHOLD-999999999);
        domRecords.put(new DOMChannelInfo(Long.toHexString(91234988934L), 5,3,'B'),
                0L);

        subject.monitorDOMClocks(alertQueue, domRecords);

        alertQueue.stop();

        mock.waitForClose();

        assertEquals("", 1, mock.alerts.size());
    }

    @Test
    public void testMonitorDOMClocks_3() throws AlertException
    {
        //
        // Tests the dom clock check when critical threshold is exceeded
        //
        final long THRESHOLD =
                (DOMClockRolloverAlerter.DOM_UPTIME_CRITICAL_THRESHOLD_DAYS *
                        NANOS_PER_DAY) / 25L;
        Map<DOMChannelInfo, Long> domRecords = new HashMap<DOMChannelInfo, Long>(10);
        domRecords.put(new DOMChannelInfo(Long.toHexString(91234988934L), 0,1,'A'),
                THRESHOLD+1);
        domRecords.put(new DOMChannelInfo(Long.toHexString(91234988934L), 0,1,'B'),
                THRESHOLD-1);
        domRecords.put(new DOMChannelInfo(Long.toHexString(91234988934L), 5,1,'A'),
                THRESHOLD-999999999);
        domRecords.put(new DOMChannelInfo(Long.toHexString(91234988934L), 5,3,'B'),
                0L);

        subject.monitorDOMClocks(alertQueue, domRecords);

        alertQueue.stop();

        mock.waitForClose();

        assertEquals("", 1, mock.alerts.size());
    }

    @Test
    public void testMonitorDOMClocks_4() throws AlertException
    {
        //
        // Tests the max dom clock
        //
        Map<DOMChannelInfo, Long> domRecords = new HashMap<DOMChannelInfo, Long>(10);
        domRecords.put(new DOMChannelInfo(Long.toHexString(91234988934L), 0,1,'A'),
                TimeUnits.DOM.maxValue());

        subject.monitorDOMClocks(alertQueue, domRecords);

        alertQueue.stop();

        mock.waitForClose();

        assertEquals("", 1, mock.alerts.size());
    }

    @Test
    public void testMonitorDOMClocks_5() throws AlertException
    {
        //
        // Tests a pathological condition of no doms
        //
        final long THRESHOLD =
                (DOMClockRolloverAlerter.DOM_UPTIME_CRITICAL_THRESHOLD_DAYS *
                        NANOS_PER_DAY) / 25L;
        Map<DOMChannelInfo, Long> domRecords = new HashMap<DOMChannelInfo, Long>(10);

        subject.monitorDOMClocks(alertQueue, domRecords);

        alertQueue.stop();

        mock.waitForClose();

        assertEquals("", 0, mock.alerts.size());
    }

}
