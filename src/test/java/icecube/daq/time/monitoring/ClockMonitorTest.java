package icecube.daq.time.monitoring;

import icecube.daq.juggler.alert.AlertQueue;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;
import static icecube.daq.time.monitoring.TestUtilities.*;


/**
 * Tests icecube.daq.time.monitoring.ClockAlerter
 */
public class ClockMonitorTest
{

    // these need to be cleaned up in tear down
    private AlertQueue alertQueue;
    private MockAlerter mockAlerter;
    private ClockAlerter clockAlerter;

    public static final int ZERO_DELTA = 0;


    @BeforeClass
    public static void setupLogging()
    {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
    }

    @AfterClass
    public static void tearDownClass()
    {
        BasicConfigurator.resetConfiguration();
    }

    @Before
    public void setUp() throws Exception
    {
        mockAlerter = new MockAlerter();
        alertQueue = new AlertQueue(mockAlerter);
        alertQueue.start();

        clockAlerter = new ClockAlerter(alertQueue, "", false, 0);
    }

    @After
    public void tearDown()
    {

        alertQueue.stop();
        mockAlerter.waitForClose();
    }


    //NOTE: Time dependency, alerts travel through a threaded queue
    int sleepRead(MockAlerter source)
    {
        try{ Thread.sleep(500);} catch (InterruptedException e){}
        return source.alerts.size();
    }

    @Test
    public void testConstruction()
    {
        //
        // Test the initial state
        //

        ClockMonitor subject =
                new ClockMonitor(clockAlerter, 50,50,500, 100, 1, 1, 1);

        //test primordial state
        assertNotNull(subject.getSystemClockOffsets());
        assertNotNull(subject.getMasterClockOffsets());
        assertNotNull(subject.getMasterClockCardOffsets());
        assertNotNull(subject.getRejectedNTPCount());
        assertNotNull(subject.getRejectedTCalCount());
    }

    @Test
    public void testMCOffsetManagement()
    {
        //
        // Test management of the master clock offset across several cards
        // and readings.
        //
        // I.E. maintaining  min/max/current reading by card.
        //

        ClockMonitor subject =
                new ClockMonitor(clockAlerter, 50,50,500, 100, 1, 1, 1);

        final long nanoBase = -2341235235451L;
        final long dorBase = 9321414991234L;


        // for one ntp reading, issue a tcal/gps from each card
        PointInTime pit = new PointInTime(11,3,59,3);
        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase));
        for(int card = 0; card<8; card++)
        {
            int offsetMillis = card * 33;
            subject.process(generateTCALMeasurement(dorBase, nanoBase, card, "31A"));
            subject.process(generateGPSSnapshot(card, pit.GPSString,
                    (byte)32, dorBase + millisAsDor(offsetMillis)));
        }

        Map<String, Double[]> map = subject.getMasterClockCardOffsets();
        for(Map.Entry<String, Double[]> entry : map.entrySet())
        {
            int card = Integer.parseInt(entry.getKey());


            assertEquals("Wrong min offset for card", entry.getValue()[0],
                    card * 33, ZERO_DELTA);

            assertEquals("Wrong max offset for card", entry.getValue()[1],
                    card * 33, ZERO_DELTA);

            assertEquals("Wrong offset for card", entry.getValue()[2],
                    card * 33, ZERO_DELTA);
        }

        //make a new minimum
        for(int card = 0; card<8; card++)
        {
            int offsetMillis = card * -11;
            subject.process(generateTCALMeasurement(dorBase, nanoBase, card, "31A"));
            subject.process(generateGPSSnapshot(card, pit.GPSString,
                    (byte)32, dorBase + millisAsDor(offsetMillis)));

        }
        map = subject.getMasterClockCardOffsets();
        for(Map.Entry<String, Double[]> entry : map.entrySet())
        {
            int card = Integer.parseInt(entry.getKey());

            assertEquals("Wrong min offset for card", entry.getValue()[0],
                    card * -11, ZERO_DELTA);

            assertEquals("Wrong max offset for card", entry.getValue()[1],
                    card * 33, ZERO_DELTA);

            assertEquals("Wrong offset for card", entry.getValue()[2],
                    card * -11, ZERO_DELTA);
        }

        //make a new maximum
        for(int card = 0; card<8; card++)
        {
            int offsetMillis = card * 87;
            subject.process(generateTCALMeasurement(dorBase, nanoBase, card, "31A"));
            subject.process(generateGPSSnapshot(card, pit.GPSString,
                    (byte)32, dorBase + millisAsDor(offsetMillis)));

        }
        map = subject.getMasterClockCardOffsets();
        for(Map.Entry<String, Double[]> entry : map.entrySet())
        {
            int card = Integer.parseInt(entry.getKey());

            assertEquals("Wrong min offset for card", entry.getValue()[0],
                    card * -11, ZERO_DELTA);

            assertEquals("Wrong max offset for card", entry.getValue()[1],
                    card * 87, ZERO_DELTA);

            assertEquals("Wrong offset for card", entry.getValue()[2],
                    card * 87, ZERO_DELTA);
        }

    }

    @Test
    public void testSCOffsetManagement()
    {
        //
        // Test management of the system clock offset
        //
        ClockMonitor subject =
                new ClockMonitor(clockAlerter, 50,50,500, 100, 1, 1, 1);

        final long nanoBase = -2341235235451L;

        // one sample
        PointInTime pit = new PointInTime(11,3,59,3);
        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, 123));

        assertEquals("Incorrect min value", 123, subject.getSystemClockOffsets()[0], ZERO_DELTA);
        assertEquals("Incorrect max value", 123, subject.getSystemClockOffsets()[1], ZERO_DELTA);
        assertEquals("Incorrect current value", 123, subject.getSystemClockOffsets()[2], ZERO_DELTA);

        // new min
        pit = new PointInTime(11,3,59,3);
        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, -80.4));

        assertEquals("Incorrect min value", -80.4, subject.getSystemClockOffsets()[0], ZERO_DELTA);
        assertEquals("Incorrect max value",123, subject.getSystemClockOffsets()[1], ZERO_DELTA);
        assertEquals("Incorrect current value", -80.4, subject.getSystemClockOffsets()[2], ZERO_DELTA);

        // new max
        pit = new PointInTime(11,3,59,3);
        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, 254));

        assertEquals("Incorrect min value", -80.4, subject.getSystemClockOffsets()[0], ZERO_DELTA);
        assertEquals("Incorrect max value", 254, subject.getSystemClockOffsets()[1], ZERO_DELTA);
        assertEquals("Incorrect current value", 254, subject.getSystemClockOffsets()[2], ZERO_DELTA);

    }

    @Test
    public void testFilters()
    {
        //
        // Test the filtering of samples with long execution times.
        //
        //

        int MAX_TCAL_DURATION_MILLIS = 50;
        int MAX_NTP_DURATION_MILLIS = 50;
        ClockMonitor subject =
                new ClockMonitor(clockAlerter,
                        MAX_TCAL_DURATION_MILLIS, MAX_NTP_DURATION_MILLIS, 500, 100, 1, 1, 1);

        final long nanoBase = -2341235235451L;
        final long dorBase = 9321414991234L;


        // issue NTP(123), LONG_NTP(555) and verify LONG_NTP is filtered
        PointInTime pit = new PointInTime(11,3,59,3);
        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, 123, millisAsNano(MAX_NTP_DURATION_MILLIS)));
        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, 555, millisAsNano(MAX_NTP_DURATION_MILLIS)+1 ));

        assertEquals("Incorrect offset", 123, subject.getSystemClockOffsets()[2], ZERO_DELTA);

        //issue TCAL(0)/GPS(-444), LONG_TCAL(123)/GPS(345) and verify
        // offset is TCAL(0)/GPS(345)
        int card=8;
        subject.process(generateTCALMeasurement(dorBase, nanoBase, card, "31A", millisAsNano(MAX_TCAL_DURATION_MILLIS)));
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                (byte)32, dorBase - millisAsDor(444)));
        assertEquals("Incorrect offset", -444, subject.getMasterClockCardOffsets().get(Integer.toString(card))[2], ZERO_DELTA);

        subject.process(generateTCALMeasurement(dorBase+123, nanoBase, card, "31A", millisAsNano(MAX_TCAL_DURATION_MILLIS)+1 ));
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                (byte) 32, dorBase + millisAsDor(345)));
        assertEquals("Incorrect offset", 345, subject.getMasterClockCardOffsets().get(Integer.toString(card))[2], ZERO_DELTA);

    }


    @Test
    public void testNTPFilterAlert()
    {
        //
        // Test the issuance of alert when NTP is over-filtered
        //
        //
        int MAX_NTP_REJECTS = 314;
        int MAX_NTP_DURATION_MILLIS = 50;
        ClockMonitor subject =
                new ClockMonitor(clockAlerter, 50, MAX_NTP_DURATION_MILLIS,500, 100, 1, 1, MAX_NTP_REJECTS);

        final long nanoBase = 2324782475252415L;
        PointInTime pit = new PointInTime(6,7,8,9);

        //non-successive should not make an alert
        for(int i = 0; i<5*MAX_NTP_REJECTS; i++)
        {
            subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, 123, millisAsNano(MAX_NTP_DURATION_MILLIS)+1 ));
            if(i % MAX_NTP_REJECTS == 0)
            {
                subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, 123, millisAsNano(MAX_NTP_DURATION_MILLIS) ));
            }
        }
        //NOTE: timing dependent
        assertEquals("False Alert", 0,  sleepRead(mockAlerter));

        //reset count
        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, 123, millisAsNano(MAX_NTP_DURATION_MILLIS) ));


        // after MMAX_NTP_REJECTS, alerts should be made.
        // NOTE: ALERT dampening is OFF for this test class, in production, the
        //       alerts are dampened by time.
        for(int i = 0; i<MAX_NTP_REJECTS; i++)
        {
            subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, 123, millisAsNano(MAX_NTP_DURATION_MILLIS)+1 ));
        }

        //NOTE: timing dependent
        assertEquals("False Alert", 0,  sleepRead(mockAlerter));

        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, 123, millisAsNano(MAX_NTP_DURATION_MILLIS)+1 ));
        assertEquals("False Alert", 1,  sleepRead(mockAlerter));

        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, 123, millisAsNano(MAX_NTP_DURATION_MILLIS)+1 ));
        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, 123, millisAsNano(MAX_NTP_DURATION_MILLIS)+1 ));
        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, 123, millisAsNano(MAX_NTP_DURATION_MILLIS)+1 ));
        assertEquals("False Alert", 4,  sleepRead(mockAlerter));
    }

    @Test
    public void testMCQualityAlerts()
    {
        //
        // Test that alerts are raise when the Master lock quality falls
        // below "very good"
        //
        byte VERY_GOOD = " ".getBytes(US_ASCII)[0];
        byte GOOD = ".".getBytes(US_ASCII)[0];
        byte AVERAGE = "*".getBytes(US_ASCII)[0];
        byte BAD = "#".getBytes(US_ASCII)[0];
        byte VERY_BAD = "?".getBytes(US_ASCII)[0];

        byte GARBAGE = (byte)0x121;

        ClockMonitor subject =
                new ClockMonitor(clockAlerter, 50, 50, 500, 100, 1, 1, 1);

        int card = 3;
        final long nanoBase = 47259749572435L;
        final long dorBase = 2323452345L;
        // for one ntp reading, issue a tcal/gps from each card
        PointInTime pit = new PointInTime(11,3,59,3);


        //NOTE: timing dependent
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                VERY_GOOD, dorBase));
        assertEquals("False Alert", 0,  sleepRead(mockAlerter));

        //NOTE: timing dependent
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                GOOD, dorBase));
        assertEquals("Missing Alert", 1, sleepRead(mockAlerter));

        //NOTE: timing dependent
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                AVERAGE, dorBase));
        assertEquals("Missing Alert", 2,  sleepRead(mockAlerter));

        //NOTE: timing dependent
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                BAD, dorBase));
        assertEquals("Missing Alert", 3,  sleepRead(mockAlerter));

        //NOTE: timing dependent
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                VERY_BAD, dorBase));
        assertEquals("Missing Alert", 4,  sleepRead(mockAlerter));

        //NOTE: timing dependent
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                GARBAGE, dorBase));
        assertEquals("Missing Alert", 5,  sleepRead(mockAlerter));
    }


    @Test
    public void testMCQualityAlertsWithWindow()
    {
        //
        // Test that alerts are raise when the Master lock quality falls
        // below "very good" for a longer window
        //
        byte VERY_GOOD = " ".getBytes(US_ASCII)[0];
        byte GOOD = ".".getBytes(US_ASCII)[0];
        byte AVERAGE = "*".getBytes(US_ASCII)[0];
        byte BAD = "#".getBytes(US_ASCII)[0];
        byte VERY_BAD = "?".getBytes(US_ASCII)[0];

        byte GARBAGE = (byte)0x121;

        int MC_WINDOW = 30; // alerts require 30 bad samples
        ClockMonitor subject =
                new ClockMonitor(clockAlerter, 50, 50, 500, 100, 1, MC_WINDOW, 1);

        int card = 3;
        final long nanoBase = 47259749572435L;
        final long dorBase = 2323452345L;
        // for one ntp reading, issue a tcal/gps from each card
        PointInTime pit = new PointInTime(11,3,59,3);


        //NOTE: timing dependent
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                VERY_GOOD, dorBase));
        assertEquals("False Alert", 0,  sleepRead(mockAlerter));

        //NOTE: timing dependent
        for(int i=0; i<MC_WINDOW-1; i++)
        {
            subject.process(generateGPSSnapshot(card, pit.GPSString,
                    GOOD, dorBase));
        }
        assertEquals("False Alert", 0, sleepRead(mockAlerter));

        subject.process(generateGPSSnapshot(card, pit.GPSString,
                GOOD, dorBase));
        assertEquals("Missing Alert", 1, sleepRead(mockAlerter));



        //NOTE: timing dependent
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                VERY_GOOD, dorBase));
        for(int i=0; i<MC_WINDOW-1; i++)
        {
            subject.process(generateGPSSnapshot(card, pit.GPSString,
                    AVERAGE, dorBase));
        }
        assertEquals("False Alert", 1, sleepRead(mockAlerter));

        subject.process(generateGPSSnapshot(card, pit.GPSString,
                AVERAGE, dorBase));
        assertEquals("Missing Alert", 2,  sleepRead(mockAlerter));



        //NOTE: timing dependent
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                VERY_GOOD, dorBase));
        for(int i=0; i<MC_WINDOW-1; i++)
        {
            subject.process(generateGPSSnapshot(card, pit.GPSString,
                    BAD, dorBase));
        }
        assertEquals("False Alert", 2, sleepRead(mockAlerter));

        subject.process(generateGPSSnapshot(card, pit.GPSString,
                BAD, dorBase));
        assertEquals("Missing Alert", 3,  sleepRead(mockAlerter));



        //NOTE: timing dependent

        subject.process(generateGPSSnapshot(card, pit.GPSString,
                VERY_GOOD, dorBase));
        for(int i=0; i<MC_WINDOW-1; i++)
        {
            subject.process(generateGPSSnapshot(card, pit.GPSString,
                    VERY_BAD, dorBase));
        }
        assertEquals("False Alert", 3, sleepRead(mockAlerter));

        subject.process(generateGPSSnapshot(card, pit.GPSString,
                VERY_BAD, dorBase));
        assertEquals("Missing Alert", 4,  sleepRead(mockAlerter));



        //NOTE: timing dependent
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                VERY_GOOD, dorBase));
        for(int i=0; i<MC_WINDOW-1; i++)
        {
            subject.process(generateGPSSnapshot(card, pit.GPSString,
                    GARBAGE, dorBase));
        }
        assertEquals("False Alert", 4, sleepRead(mockAlerter));

        subject.process(generateGPSSnapshot(card, pit.GPSString,
                GARBAGE, dorBase));
        assertEquals("Missing Alert", 5,  sleepRead(mockAlerter));
    }

    @Test
    public void testSCOffsetAlerts()
    {
        //
        // Test that system clock offset alerts are raised at thresholds
        //
        int SYSTEM_CLOCK_THRESHOLD_MILLIS = 500;
        ClockMonitor subject =
                new ClockMonitor(clockAlerter, 50, 50,
                        SYSTEM_CLOCK_THRESHOLD_MILLIS,
                        100, 1, 1, 1);

        final long nanoBase = 47259749572435L;

        PointInTime pit = new PointInTime(11,3,59,3);
        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, millisAsNano(SYSTEM_CLOCK_THRESHOLD_MILLIS)+1));

        assertEquals("Missing Alert: ", 1, sleepRead(mockAlerter));

    }

    @Test
    public void testSCOffsetAlertsWithWindow()
    {
        //
        // Test that system clock offset alerts are raised at thresholds
        // with a longer sample window
        //
        int SYSTEM_CLOCK_THRESHOLD_MILLIS = 500;
        int SC_SAMPLE_WINDOW = 1;
        ClockMonitor subject =
                new ClockMonitor(clockAlerter, 50, 50,
                        SYSTEM_CLOCK_THRESHOLD_MILLIS,
                        100, SC_SAMPLE_WINDOW, 1, 1);

        final long nanoBase = 47259749572435L;

        PointInTime pit = new PointInTime(11,3,59,3);

        for (int i=0; i<SC_SAMPLE_WINDOW-1; i++)
        {
            subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, SYSTEM_CLOCK_THRESHOLD_MILLIS+1));

        }
        assertEquals("False Alert: ", 0, sleepRead(mockAlerter));

        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, SYSTEM_CLOCK_THRESHOLD_MILLIS+1));

        assertEquals("Missing Alert: ", 1, sleepRead(mockAlerter));


        //another window
        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, SYSTEM_CLOCK_THRESHOLD_MILLIS));

        for (int i=0; i<SC_SAMPLE_WINDOW-1; i++)
        {
            subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, SYSTEM_CLOCK_THRESHOLD_MILLIS+1));

        }
        assertEquals("False Alert: ", 1, sleepRead(mockAlerter));

        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase, SYSTEM_CLOCK_THRESHOLD_MILLIS+1));

        assertEquals("Missing Alert: ", 2, sleepRead(mockAlerter));
    }

    @Test
    public void testMCOffsetAlerts()
    {
        //
        // Test that master clock offset alerts are raised at thresholds
        //
        int MASTER_CLOCK_THRESHOLD_MILLIS = 100;
        ClockMonitor subject =
                new ClockMonitor(clockAlerter, 50, 50,
                        500, MASTER_CLOCK_THRESHOLD_MILLIS, 1, 1, 1);

        final int card = 3;
        final long nanoBase = 47259749572435L;
        final long dorBase = 2323452345L;


        PointInTime pit = new PointInTime(11,3,59,3);
        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase));

        subject.process(generateTCALMeasurement(dorBase, nanoBase, card, "31A"));
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                (byte) 32, dorBase - millisAsDor(MASTER_CLOCK_THRESHOLD_MILLIS)));
        assertEquals("False Alert: ", 0, sleepRead(mockAlerter));


        subject.process(generateGPSSnapshot(card, pit.GPSString,
                (byte)32, dorBase - millisAsDor(MASTER_CLOCK_THRESHOLD_MILLIS+1)));
        assertEquals("Missing Alert: ", 1, sleepRead(mockAlerter));

        subject.process(generateGPSSnapshot(card, pit.GPSString,
                (byte)32, dorBase - millisAsDor(MASTER_CLOCK_THRESHOLD_MILLIS+1)));
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                (byte)32, dorBase - millisAsDor(MASTER_CLOCK_THRESHOLD_MILLIS+99)));
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                (byte)32, dorBase - millisAsDor(MASTER_CLOCK_THRESHOLD_MILLIS+50000)));
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                (byte)32, dorBase - millisAsDor(MASTER_CLOCK_THRESHOLD_MILLIS+33333333)));
        subject.process(generateGPSSnapshot(card, pit.GPSString,
                (byte)32, dorBase - millisAsDor(MASTER_CLOCK_THRESHOLD_MILLIS+234523452)));
        assertEquals("Missing Alert: ", 6, sleepRead(mockAlerter));
    }

    @Test
    public void testMCOffsetAlertsWithWindow()
    {
        //
        // Test that master clock offset alerts are raised at thresholds
        //
        int MASTER_CLOCK_THRESHOLD_MILLIS = 100;
        int MC_SAMPLE_WINDOW = 5;
        ClockMonitor subject =
                new ClockMonitor(clockAlerter, 50, 50,
                        500, MASTER_CLOCK_THRESHOLD_MILLIS, 1, MC_SAMPLE_WINDOW, 1);

        final int cardA = 3;
        final int cardB = 7;
        final long nanoBase = 47259749572435L;
        final long dorBase = 2323452345L;


        PointInTime pit = new PointInTime(11,3,59,3);
        subject.process(generateNTPMeasurement(pit.epochTimeMillis, nanoBase));

        subject.process(generateTCALMeasurement(dorBase, nanoBase, cardA, "22A"));
        subject.process(generateTCALMeasurement(dorBase, nanoBase, cardB, "17B"));

        for (int i=0; i<MC_SAMPLE_WINDOW-1; i++)
        {
            subject.process(generateGPSSnapshot(cardA, pit.GPSString,
                    (byte)32, dorBase - millisAsDor(MASTER_CLOCK_THRESHOLD_MILLIS+1)));
            subject.process(generateGPSSnapshot(cardB, pit.GPSString,
                    (byte)32, dorBase - millisAsDor(MASTER_CLOCK_THRESHOLD_MILLIS+1)));
        }
        assertEquals("False Alert: ", 0, sleepRead(mockAlerter));

        subject.process(generateGPSSnapshot(cardA, pit.GPSString,
                (byte)32, dorBase - millisAsDor(MASTER_CLOCK_THRESHOLD_MILLIS+1)));
        assertEquals("Missing Alert: ", 1, sleepRead(mockAlerter));

        subject.process(generateGPSSnapshot(cardB, pit.GPSString,
                (byte)32, dorBase - millisAsDor(MASTER_CLOCK_THRESHOLD_MILLIS+1)));
        assertEquals("Missing Alert: ", 2, sleepRead(mockAlerter));


        subject.process(generateGPSSnapshot(cardA, pit.GPSString,
                (byte)32, dorBase - millisAsDor(MASTER_CLOCK_THRESHOLD_MILLIS+111)));
        subject.process(generateGPSSnapshot(cardB, pit.GPSString,
                (byte)32, dorBase - millisAsDor(MASTER_CLOCK_THRESHOLD_MILLIS+222)));
        subject.process(generateGPSSnapshot(cardB, pit.GPSString,
                (byte)32, dorBase - millisAsDor(MASTER_CLOCK_THRESHOLD_MILLIS+213)));
        subject.process(generateGPSSnapshot(cardA, pit.GPSString,
                (byte)32, dorBase - millisAsDor(MASTER_CLOCK_THRESHOLD_MILLIS+999)));
        subject.process(generateGPSSnapshot(cardA, pit.GPSString,
                (byte)32, dorBase - millisAsDor(MASTER_CLOCK_THRESHOLD_MILLIS+55)));
        assertEquals("Missing Alert: ", 7, sleepRead(mockAlerter));


    }


}