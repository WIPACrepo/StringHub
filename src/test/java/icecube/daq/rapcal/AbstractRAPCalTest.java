package icecube.daq.rapcal;

import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.TimeCalib;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.monitoring.IRunMonitor;
import icecube.daq.util.DOMInfo;
import icecube.daq.util.TimeUnits;
import icecube.daq.util.UTC;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.apache.log4j.varia.NullAppender;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


import static icecube.daq.rapcal.BuildTCalMethods.*;
import static org.junit.Assert.*;

class MyRAPCal
    extends AbstractRAPCal
{
    boolean triggerException = false;
    MyRAPCal()
    {
        super();
        // default monitor will complain if it receives anything
        setRunMonitor(new MyMonitor());
    }

    public MyRAPCal(final double w, final int maxHistory)
    {
        super(w, maxHistory);
    }

    public double getFineTimeCorrection(short[] w)
        throws RAPCalException
    {
        if(triggerException)
        {
            throw new BadTCalException("test", w);
        }
        else
        {
            return 0.0;
        }
    }
}

class MyMonitor
    implements IRunMonitor
{
    private String exceptionString;
    private boolean receivedException;
    private boolean expectWildTCal;
    private int wildCount;

    @Override
    public void countHLCHit(long mbid, long[] utc)
    {
        throw new Error("Unimplemented");
    }

    public void expectExceptionString(String excStr)
    {
        exceptionString = excStr;
    }

    public void expectWildTCal()
    {
        expectWildTCal = true;
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

    public int getWildCount()
    {
        return wildCount;
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
        if (exceptionString == null)
        {
            fail("Unexpected exception \"" + exceptionString + "\" for tcal " +
                 tcal);
        }
        else if (exception == null || exception.getMessage() == null ||
                 !exception.getMessage().startsWith(exceptionString))
        {
            fail("Exception \"" + exception + "\" for tcal " + tcal +
                 " should start with \"" + exceptionString + "\"");
        }

        receivedException = true;
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
        throw new Error("Unimplemented");
    }

    @Override
    public void pushWildTCal(long mbid, double cableLength, double averageLen)
    {
        if (!expectWildTCal)
        {
            fail("Unexpected wild TCal");
        }

        wildCount++;
    }

    public boolean receivedException()
    {
        return receivedException;
    }

    public void reset()
    {
        receivedException = false;
        wildCount = 0;
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
    public void setConfiguredDOMs(Collection<DOMInfo> configuredDOMs)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void setRunNumber(int runNum)
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
 * Tests AbstractRAPCal.java
 */
public class AbstractRAPCalTest
{
    //public static final long ONE_SECOND = 12345678901234L;

    static long DOR_TICS_PER_SECOND = 20000000;
    static long DOM_TICS_PER_SECOND = 40000000;


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
    public void testDefault()
    {
        //
        // test the state of RAPCal prior to receiving any tcal values
        //

        AbstractRAPCal rc = new MyRAPCal();

        assertTrue("Unexpected cable length " +
                   rc.cableLength(),
                   Double.isNaN(rc.cableLength()));

        assertFalse("Unexpected laterThan(" + 0 + ")",
                    rc.laterThan(0));

        assertFalse("Unexpected laterThan(" + DOM_TICS_PER_SECOND + ")",
                rc.laterThan(DOM_TICS_PER_SECOND));

        assertFalse("Unexpected laterThan(" + DOM_TICS_PER_SECOND * 31536000 + ")",
                rc.laterThan(DOM_TICS_PER_SECOND * 31536000));

        assertFalse("Unexpected laterThan(" + -1 + ")",
                rc.laterThan(-1));

        assertFalse("Should not answer ready", rc.isReady());


        UTC utc = rc.domToUTC(123456789L);
        assertNull("domToUTC(" + 123456789L + ") returned " + utc +
                   " instead of null", utc);
    }

    @Test
    public void testUpdate()
        throws RAPCalException
    {
        //
        // Test updating with tcals
        //
        UTC GPS = new UTC(1252454516L);

        AbstractRAPCal rc = new MyRAPCal();

        final long oneHundredDORDays = 100 * 24 * 3600 * DOR_TICS_PER_SECOND;
        final long oneHundredDOMDays = 100 * 24 * 3600 * DOM_TICS_PER_SECOND;

        final long dordt = 379L;
        final long domdt = 612L;

        long dorTx = oneHundredDORDays;
        long domRx = oneHundredDOMDays;
        long domTx = domRx + domdt;
        long dorRx = domTx + dordt;

        TimeCalib tcal;
        long domclk;
        UTC utc;

        // 1st update (does not result in an isochron)
        tcal = buildTimeCalib(dorTx, dorRx, domRx, domTx);

        rc.update(tcal, GPS);
        assertFalse("Should not answer ready", rc.isReady());

        // 2nd update about 30 seconds later
        long dorTx2 = dorRx +  30 * DOR_TICS_PER_SECOND;
        long domRx2 = domRx + 30 * DOM_TICS_PER_SECOND;
        long domTx2 = domRx2 + domdt;
        long dorRx2 = dorTx2 + dordt;

        tcal = buildTimeCalib(dorTx2, dorRx2, domRx2, domTx2);
        rc.update(tcal, GPS);
        assertTrue("Should be ready", rc.isReady());

        assertEquals("Unexpected cable length ",
                -1.1015981750087336,  rc.cableLength(), 0.0);

        domclk = domTx2 + 1L;
        assertFalse("Unexpected laterThan(" + domclk + ")",
                    rc.laterThan(domclk));
        assertTrue("Unexpected laterThan(" + domTx2 + ")",
                rc.laterThan(domTx2));
        assertTrue("Unexpected laterThan(" + domTx + ")",
            rc.laterThan(domTx));

        utc = rc.domToUTC(domclk);
        assertNotNull("domToUTC(" + domclk + ") returned null instead of " +
                      utc, utc);
    }



    @Test
    public void testReconstruction() throws RAPCalException
    {
        //
        //test time reconstructions for a constant frequency variation
        //

        final long GPS_OFFSET = 188 * 24 * 60 * 60 * 10000000000L; //180 days
        final UTC GPS = new UTC(GPS_OFFSET);
        final long DOR_BASE=1234567123419L;
        final long DOM_BASE=23542345234643L;
        final long dordt = 300;
        final long domdt = 612;

        final long DOR_INTERVAL = DOR_TICS_PER_SECOND;// + dordt;
        final long DOM_INTERVAL = DOM_TICS_PER_SECOND + 4444;

        final long[] DOR_TIMES =
                {
                        DOR_BASE,
                        DOR_BASE + dordt,
                        DOR_BASE + 1*DOR_INTERVAL,
                        DOR_BASE + 1*DOR_INTERVAL + dordt,
                        DOR_BASE + 2*DOR_INTERVAL,
                        DOR_BASE + 2*DOR_INTERVAL + dordt,
                        DOR_BASE + 3*DOR_INTERVAL,
                        DOR_BASE + 3*DOR_INTERVAL + dordt,

                };

        final long[] DOM_TIMES =
                {
                        DOM_BASE,
                        DOM_BASE + domdt,
                        DOM_BASE + 1*DOM_INTERVAL,
                        DOM_BASE + 1*DOM_INTERVAL + domdt,
                        DOM_BASE + 2*DOM_INTERVAL,
                        DOM_BASE + 2*DOM_INTERVAL + domdt,
                        DOM_BASE + 3*DOM_INTERVAL,
                        DOM_BASE + 3*DOM_INTERVAL + domdt,
                };

        //reform into tcal arrays
        long[][] tcals = new long[DOM_TIMES.length/2][];
        for (int i = 0; i < DOM_TIMES.length; i+=2)
        {
            tcals[i/2] = new long[]
                    {
                            DOR_TIMES[i],
                            DOR_TIMES[i+1],
                            DOM_TIMES[i],
                            DOM_TIMES[i+1],
                    };
        }

        final RAPCal rapcal =  new MyRAPCal();

        //first tcal can not make an isochron
        rapcal.update(buildTimeCalib(tcals[0]), GPS);


        //remaining tcals form isochrons
        for (int i = 1; i < tcals.length; i++)
        {
            rapcal.update(buildTimeCalib(tcals[i]), GPS);
            MyIsochron isochron = new MyIsochron(tcals[i-1], tcals[i]);

            //check some reconstructions
            long[] testValues =
                    {
                            0,
                            1234,
                            tcals[i][1],                   //domtx
                            (tcals[i][3] + tcals[i][1])/2, //tcal midpoint
                            tcals[i][3],                   //domrx
                            TimeUnits.DOM.maxValue()


                    };
            for (long domclk : testValues)
            {
                assertEquals("UTC reconstruction of " + domclk,
                        isochron.reconstruct(domclk, GPS.in_0_1ns()),
                        rapcal.domToUTC(domclk).in_0_1ns()
                );
            }
        }

    }


    @Test
    public void testHistory() throws RAPCalException
    {
        //
        // Test the Isochron retention history and lookup
        //

        UTC GPS_OFFSET = new UTC(285078529678171500L);
        long[][] tcals =
                {
                        {6883191079596L, 6883191080633L, 13742940041669L, 13742940042281L},
                        {6883522209501L, 6883522210538L, 13743602300363L, 13743602300975L},
                        {6883757056626L, 6883757057663L, 13744071993821L, 13744071994433L},
                        {6884401073845L, 6884401074881L, 13745360026087L, 13745360026699L},
                        {6884422578279L, 6884422579315L, 13745403034881L, 13745403035493L},
                        {6884681647218L, 6884681648255L, 13745921171887L, 13745921172499L},
                        {6885025651463L, 6885025652499L, 13746609179215L, 13746609179827L},
                        {6885069176474L, 6885069177510L, 13746696229091L, 13746696229703L},
                        {6885090889506L, 6885090890543L, 13746739655083L, 13746739655695L},
                        {6885413807798L, 6885413808835L, 13747385490577L, 13747385491189L},
                        {6885541204430L, 6885541205466L, 13747640283411L, 13747640284023L},
                        {6886306730674L, 6886306731711L, 13749171333317L, 13749171333929L},
                        {6886393860096L, 6886393861132L, 13749345591867L, 13749345592479L},
                        {6886437769104L, 6886437770141L, 13749433409735L, 13749433410347L},
                        {6886503185447L, 6886503186484L, 13749564242201L, 13749564242813L},
                        {6886590616483L, 6886590617520L, 13749739103979L, 13749739104591L},
                        {6886612133806L, 6886612134843L, 13749782138551L, 13749782139163L},
                        {6886633603704L, 6886633604741L, 13749825078275L, 13749825078887L},
                        {6886913477303L, 6886913478340L, 13750384824529L, 13750384825141L},
                        {6886956733285L, 6886956734322L, 13750471336347L, 13750471336959L},
                        {6887128282635L, 6887128283672L, 13750814434469L, 13750814435081L},
                        {6887405485066L, 6887405486103L, 13751368838395L, 13751368839007L},
                        {6887642805199L, 6887642806236L, 13751843477861L, 13751843478473L},
                        {6888050681159L, 6888050682196L, 13752659228405L, 13752659229017L},
                        {6888266888726L, 6888266889763L, 13753091642809L, 13753091643421L},
                        {6888309799643L, 6888309800680L, 13753177464499L, 13753177465111L},
                };

        long EARLIEST_DOM = tcals[0][2];

        int HISTORY = 20;
        AbstractRAPCal subject = new MyRAPCal(0.1, HISTORY);

        //CASE 0a: No isochron
        assertNull("No isochron", subject.lookupIsochron(EARLIEST_DOM+1, TimeUnits.DOM));
        subject.update(buildTimeCalib(tcals[0]), GPS_OFFSET);
        assertNull("No isochron", subject.lookupIsochron(EARLIEST_DOM + 1, TimeUnits.DOM));

        for (int i = 1; i < tcals.length; i++)
        {
            long[] current = tcals[i];
            subject.update(buildTimeCalib(current), GPS_OFFSET);

            // rapcal should track the DOM TX of the latest tcal
            assertTrue("Later than", subject.laterThan(current[3]));
            assertFalse("Not Later than", subject.laterThan(current[3] + 1));

            int oldestTCal = (i<= HISTORY) ? (0) : (i- HISTORY);
            int latestTCal = i;


            // CASE I: Time earlier than RAPCal initalization
            //
            // Rapcal should use the earliest available isochron
            {
                String message = "Should find earliest interval";
                long eariestLeft = tcals[oldestTCal][3];
                long earliestRight = tcals[oldestTCal+1][3];
                Isochron best = subject.lookupIsochron(EARLIEST_DOM-1, TimeUnits.DOM);
                Isochron best2 = subject.lookupIsochron(EARLIEST_DOM-999999, TimeUnits.DOM);
                Isochron best3 = subject.lookupIsochron(0, TimeUnits.DOM);
                assertTrue(message, best == best2);
                assertTrue(message, best2 == best3);
                assertInterval(message, eariestLeft, earliestRight, best);
            }

            // CASE II: Time from within the tcal stream
            //
            // Rapcal should find a bounding isochron, or nearest one for
            // time that have aged out of history
            for (int j = 1; j <= i; j++)
            {
                long left = tcals[j-1][3];
                long right = tcals[j][3];
                Isochron best = subject.lookupIsochron(left + 1, TimeUnits.DOM);
                Isochron best2 = subject.lookupIsochron(right, TimeUnits.DOM);
                assertTrue("should find same isochron", best == best2);
                if((i-j)< HISTORY)
                {
                    // CASE IIa: time within history
                    //
                    // Rapcal should find the bounding isochron
                    String message = "Should find bounding isochron";
                    assertInterval(message, left, right, best);
                }
                else
                {
                    //CASE IIb: time earlier than history
                    // Rapcal should find the nearest, which in this case
                    // is the oldest

                    String message = "Should find oldest isochron";
                    long oldestLeft = tcals[oldestTCal][3];
                    long oldestRight = tcals[oldestTCal+1][3];
                    assertInterval(message, oldestLeft, oldestRight, best);

                }
            }

            // CASE: Time later than the tcal stream.
            //
            // RAPCal should find the nearest isochron, which is the last
            {
                String message = "Should find latest isochron";

                long latestLeft = tcals[latestTCal-1][3];
                long latestRight = tcals[latestTCal][3];
                Isochron best = subject.lookupIsochron(latestRight + 1, TimeUnits.DOM);
                Isochron best2 =
                        subject.lookupIsochron(latestRight + 9999999999L, TimeUnits.DOM);
                assertTrue(message, best == best2);
                assertInterval(message, latestLeft, latestRight, best);
            }
        }

    }

    /**
     * Common routine to assert that an isochron exactly bounds
     * an interval;
     */
    private static void assertInterval(String message,
                                       long left, long right,
                                       Isochron isochron)
    {
        assertEquals(message, left * 250, isochron.getLowerBound());
        assertEquals(message, right * 250, isochron.getUpperBound());
        assertTrue(message, isochron.containsDomClock(left+1, TimeUnits.DOM));
        assertTrue(message, isochron.containsDomClock(right, TimeUnits.DOM));
        assertFalse(message, isochron.containsDomClock(left, TimeUnits.DOM));
        assertFalse(message, isochron.containsDomClock(right+1, TimeUnits.DOM));
    }

    @Test
    public void testBaselineCalculation()
    {
        //
        // Tests the baseline calculation
        //

        short[] dorwf = {500,500,500,499,500,502,501,501,
                         500,501,501,500,500,502,499,499,
                         499,501,499,499,999,999,999,999,
                         999,999,999,999,999,999,999,999,
                         999,999,999,999,999,999,999,999,
                         999,999,999,999,999,999,999,999,
                         0,  0,  0,  0,  0,  0,  0,  0,
                         0,  0,  0,  0,  0,  0,  0,  0};

        short[] domwf = {512,512,512,512,512,512,512,512,
                         512,511,512,512,512,512,512,513,
                         513,513,513,513,999,999,999,999,
                         999,999,999,999,999,999,999,999,
                         999,999,999,999,999,999,999,999,
                         999,999,999,999,999,999,999,999,
                         0,  0,  0,  0,  0,  0,  0,  0,
                         0,  0,  0,  0,  0,  0,  0,  0};

        AbstractRAPCal subject = new MyRAPCal();

        assertEquals("Baseline Estimate", 500.15,
                subject.getBaseline(dorwf), 0.0 );
        assertEquals("Baseline Estimate", 512.2,
                subject.getBaseline(domwf), 0.0 );

    }

    @Test
    public void testBadFineTimeCorrection() throws RAPCalException
    {
        //
        // Test exceptions thrown from subclassed fine time corrections
        //

        long[] tcal_1 = { 140283608470950L, 140283608471918L, 19486528785811L, 19486528786423L };
        long[] tcal_2 = { 140284233909156L, 140284233910124L, 19487779661177L, 19487779661789L };
        short[] wf = new short[64];
        Arrays.fill(wf, (short)27);

        MyRAPCal subject = new MyRAPCal();
        subject.triggerException = true;

        MyMonitor runMonitor = new MyMonitor();
        runMonitor.expectExceptionString("test");
        subject.setRunMonitor(runMonitor);

        try
        {
            subject.update(buildTimeCalib(tcal_1, wf, wf), new UTC(0));
            subject.update(buildTimeCalib(tcal_2), new UTC(0));
            fail("Expected update rejection");
        }
        catch (RAPCalException e)
        {
            // expected, exception should carry details from the fine time
            // correction provider
            assertTrue("", e instanceof BadTCalException);
            assertEquals("", "test", ((BadTCalException) e).getSource());
            assertArrayEquals("", wf, ((BadTCalException)e).getWaveform());
        }

        assertTrue("RunMonitor did not receive exception", runMonitor.receivedException());
    }

    @Test
    public void testDOMRollover()
    {
        //
        // Test that DOM clock rollover or other backward step in
        // dom clock is rejected
        //
        long[] before_rollover_1 =
                {
                        140817092391568L,
                        140817092392460L,
                        281474719563929L,
                        281474719564541L,
                };
        long[] before_rollover_2 =
                {
                        140817155293348L,
                        140817155294240L,
                        281474845367419L,
                        281474845368031L
                };
        long[] after_rollover =
                {
                        140817447503929L,
                        140817447504821L,
                        453077595L,
                        453078207L
                };
        final UTC GPS = new UTC(0);

        MyRAPCal rapcal = new MyRAPCal();

        MyMonitor runMonitor = new MyMonitor();
        runMonitor.expectExceptionString("Non-monotonic timestamps: ");
        rapcal.setRunMonitor(runMonitor);

        try
        {
            rapcal.update(buildTimeCalib(before_rollover_1), GPS);
            rapcal.update(buildTimeCalib(before_rollover_2), GPS);
        }
        catch (RAPCalException e)
        {
            fail("unexpected rapcal failure: " + e.getMessage());
        }

        assertFalse("RunMonitor received exception", runMonitor.receivedException());

        try
        {
            rapcal.update(buildTimeCalib(after_rollover), GPS);
            fail("Expected rapcal update exception");
        }
        catch (RAPCalException e)
        {
            // expected
        }

        assertTrue("RunMonitor did not receive exception", runMonitor.receivedException());
    }


    @Test
    public void testWildTCAL()
        throws RAPCalException
    {
        //
        // Test that Wild Tcal data is rejected
        //

        long[] before_wild_1 = { 140283608470950L, 140283608471918L, 19486528785811L, 19486528786423L };
        long[] before_wild_2 = { 140284233909156L, 140284233910124L, 19487779661177L, 19487779661789L };
        long[] before_wild_3 = { 140284404113165L, 140284404114133L, 19488120068909L, 19488120069521L };
        long[] before_wild_4 = { 140284661699023L, 140284661699991L, 19488635240195L, 19488635240807L };
        long[] before_wild_5 = { 140284688764968L, 140284688765936L, 19488689372039L, 19488689372651L };
        long[] before_wild_6 = { 140285324954832L, 140285324955800L, 19489961750703L, 19489961751315L };
        long[] wild = { 140285607890297L, 140285607891266L, 19490527621161L, 19490527621773L };


        final UTC GPS = new UTC(0);

        RAPCal rapcal = new MyRAPCal();
        try
        {
            rapcal.update(buildTimeCalib(before_wild_1), GPS);
            rapcal.update(buildTimeCalib(before_wild_2), GPS);
            rapcal.update(buildTimeCalib(before_wild_3), GPS);
            rapcal.update(buildTimeCalib(before_wild_4), GPS);
            rapcal.update(buildTimeCalib(before_wild_5), GPS);
            rapcal.update(buildTimeCalib(before_wild_6), GPS);
        }
        catch (RAPCalException e)
        {
            fail("unexpected rapcal failure");
        }

        MyMonitor runMonitor = new MyMonitor();
        rapcal.setRunMonitor(runMonitor);
        runMonitor.expectWildTCal();

        assertFalse("Expected Wild Tcal exception",
                    rapcal.update(buildTimeCalib(wild), GPS));
    }

    @Test
    public void testWildInitialization() throws RAPCalException
    {
        //
        // Test the infrequent condition of encountering wild tcals
        // while rapcal is initializing.
        //
        // The cable length average should adapt until 3 successive
        // stable samples.
        //

        long[] wild_1 = { 140790225301897L, 140790225302866L, 20499761600177L, 20499761600789L };
        long[] wild_2 = { 140790696157751L, 140790696158720L, 20500703311097L, 20500703311709L };
        long[] good_1 = { 140790718847770L, 140790718848738L, 20500748691097L, 20500748691709L };
        long[] wild_3 = { 140790745238405L, 140790745239374L, 20500801472323L, 20500801472935L };
        long[] good_2 = { 140790766214661L, 140790766215629L, 20500843424799L, 20500843425411L };
        long[] wild_4 = { 140791369492067L, 140791369493036L, 20502049978603L, 20502049979215L };
        long[] good_3 = { 140791392950877L, 140791392951845L, 20502096896183L, 20502096896795L };
        long[] good_4 = { 140791486207859L, 140791486208827L, 20502283409991L, 20502283410603L };
        long[] wild_5 = { 140792117402375L, 140792117403344L, 20503545797967L, 20503545798579L };
        long[] good_5 = { 140792185350063L, 140792185351031L, 20503681693229L, 20503681693841L };
        long[] good_6 = { 140792395162464L, 140792395163432L, 20504101317679L, 20504101318291L };
        long[] good_7 = { 140792770737635L, 140792770738603L, 20504852467393L, 20504852468005L };
        long[] wild_6 = { 140794588242217L, 140794588243187L, 20508487473519L, 20508487474131L };
        long[] wild_7 = { 140795113034000L, 140795113034969L, 20509537056205L, 20509537056817L };
        long[] good_8 = { 140795136209758L, 140795136210726L, 20509583407681L, 20509583408293L };
        long[] good_9 = { 140795279509011L, 140795279509979L, 20509870005947L, 20509870006559L };
        long[] good_10 = { 140796529146076L, 140796529147044L, 20512369277987L, 20512369278599L };
        long[] wild_8 = { 140797435608739L, 140797435609708L, 20514182201797L, 20514182202409L };
        long[] wild_9 = { 140798201818555L, 140798201819524L, 20515714620147L, 20515714620759L };
        long[] wild_10 = { 140798429036039L, 140798429037008L, 20516169054735L, 20516169055347L };
        long[] wild_11 = { 140799741709668L, 140799741710637L, 20518794399797L, 20518794400409L };
        long[] wild_12 = { 140799784438939L, 140799784439908L, 20518879858267L, 20518879858879L };
        long[] wild_13 = { 140801491628266L, 140801491629236L, 20522294234067L, 20522294234679L };
        long[] wild_14 = { 140801512960947L, 140801512961916L, 20522336899391L, 20522336900003L };
        long[] wild_15 = { 140801743820375L, 140801743821344L, 20522798617861L, 20522798618473L };
        long[] wild_16 = { 140802466612725L, 140802466613694L, 20524244201353L, 20524244201965L };
        long[] good_11 = { 140802487794247L, 140802487795215L, 20524286564359L, 20524286564971L };

        UTC GPS = new UTC(12312343241234L);

        MyRAPCal subject = new MyRAPCal();
        assertUpdate("", wild_1, GPS, subject, false);
        assertUpdate("", wild_2, GPS, subject, false);
        assertEquals("Cable Length", 1.657499359867251E-5, subject.cableLength(), 0.0);
        assertUpdate("", good_1, GPS, subject, false); // average should reset
        assertEquals("Cable Length", 1.6549993762670783E-5, subject.cableLength(), 0.0);
        assertUpdate("", wild_3, GPS, subject, false); // average should reset
        assertEquals("Cable Length", 1.6574993477794545E-5, subject.cableLength(), 0.0);
        assertUpdate("", good_2, GPS, subject, false); // average should reset
        assertEquals("Cable Length", 1.6549993617778367E-5, subject.cableLength(), 0.0);
        assertUpdate("", wild_4, GPS, subject, false); // average should reset
        assertEquals("Cable Length", 1.6574993602564613E-5, subject.cableLength(), 0.0);
        assertUpdate("", good_3, GPS, subject, false); // average should reset
        assertEquals("Cable Length", 1.654999364097637E-5, subject.cableLength(), 0.0);
        assertUpdate("", good_4, GPS, subject, false);
        assertEquals("Cable Length", 1.65499936373918E-5, subject.cableLength(), 0.0);
        assertUpdate("", wild_5, GPS, subject, false); // average should reset
        assertEquals("Cable Length", 1.6574993594639562E-5, subject.cableLength(), 0.0);
        assertUpdate("", good_5, GPS, subject, false); // average should reset
        assertEquals("Cable Length", 1.6549993638851077E-5, subject.cableLength(), 0.0);
        assertUpdate("", good_6, GPS, subject, false);
        assertEquals("Cable Length", 1.654999363375856E-5, subject.cableLength(), 0.0);
        assertUpdate("", good_7, GPS, subject, false); // average established
        assertEquals("Cable Length", 1.654999363107182E-5, subject.cableLength(), 0.0);
        assertUpdate("", wild_6, GPS, subject, true); // reject wild
        assertEquals("Cable Length", 1.654999363107182E-5, subject.cableLength(), 0.0);
        assertUpdate("", wild_7, GPS, subject, true); // reject wild
        assertEquals("Cable Length", 1.654999363107182E-5, subject.cableLength(), 0.0);
        assertUpdate("", good_8, GPS, subject, false);
        assertEquals("Cable Length", 1.6549993628234E-5, subject.cableLength(), 0.0);
        assertUpdate("", good_9, GPS, subject, false);
        assertEquals("Cable Length", 1.6549993625105477E-5, subject.cableLength(), 0.0);
        assertUpdate("", good_10, GPS, subject, false);
        assertEquals("Cable Length", 1.6549993623071998E-5, subject.cableLength(), 0.0);
        assertUpdate("", wild_8, GPS, subject, true); // reject wild
        assertEquals("Cable Length", 1.6549993623071998E-5, subject.cableLength(), 0.0);
        assertUpdate("", wild_9, GPS, subject, true); // reject wild
        assertEquals("Cable Length", 1.6549993623071998E-5, subject.cableLength(), 0.0);
        assertUpdate("", wild_10, GPS, subject, true); // reject wild
        assertEquals("Cable Length", 1.6549993623071998E-5, subject.cableLength(), 0.0);
        assertUpdate("", wild_11, GPS, subject, true); // reject wild
        assertEquals("Cable Length", 1.6549993623071998E-5, subject.cableLength(), 0.0);
        assertUpdate("", wild_12, GPS, subject, true); // reject wild
        assertEquals("Cable Length", 1.6549993623071998E-5, subject.cableLength(), 0.0);
        assertUpdate("", wild_13, GPS, subject, true); // reject wild
        assertEquals("Cable Length", 1.6549993623071998E-5, subject.cableLength(), 0.0);
        assertUpdate("", wild_14, GPS, subject, true); // reject wild
        assertEquals("Cable Length", 1.6549993623071998E-5, subject.cableLength(), 0.0);
        assertUpdate("", wild_15, GPS, subject, true); // reject wild
        assertEquals("Cable Length", 1.6549993623071998E-5, subject.cableLength(), 0.0);
        assertUpdate("", wild_16, GPS, subject, true); // reject wild
        assertEquals("Cable Length", 1.6549993623071998E-5, subject.cableLength(), 0.0);
        assertUpdate("", good_11, GPS, subject, false);
        assertEquals("Cable Length", 1.6549993620975558E-5, subject.cableLength(), 0.0);

    }

    private static void assertUpdate(String messagePrefix, long[] tcal,
                                     UTC gpsOffset, MyRAPCal rapCal,
                                     boolean wildTCalExpected)
    {
        MyMonitor runMonitor = new MyMonitor();
        rapCal.setRunMonitor(runMonitor);
        if (wildTCalExpected)
        {
            runMonitor.expectWildTCal();
        }

        try
        {
            assertEquals("Bad update", !wildTCalExpected,
                         rapCal.update(buildTimeCalib(tcal), gpsOffset));
            if (!wildTCalExpected)
            {
                assertEquals(messagePrefix + " update should NOT have wild tcal (count=" + runMonitor.getWildCount() + ")", 0, runMonitor.getWildCount());
            }
            else
            {
                assertTrue(messagePrefix + " update should have wild tcal",
                        runMonitor.getWildCount() > 0);
            }

        }
        catch (RAPCalException e)
        {
            fail(messagePrefix + " update should have succeded");
        }
    }

    @Test
    public void testDegenerateInitialization() throws RAPCalException
    {
        //
        // Test the infrequent condition of initializing with a degenerate
        // tcals
        //

        // basic case of initial tcal degenerate
        {
            long[] degenerate =
                    {
                            2000000,
                            2000500,
                            5000000,
                            4000500 // backward DOM
                    };

            long[] ok =
                    {
                            3000000,
                            3000500,
                            6000000,
                            6000500
                    };
            long[] ok_2 =
                    {
                            4000000,
                            4000500,
                            7000000,
                            7000500
                    };

            MyRAPCal subject = new MyRAPCal();
            subject.update(buildTimeCalib(degenerate), new UTC(0));

            MyMonitor runMonitor = new MyMonitor();
            runMonitor.expectExceptionString("Non-monotonic timestamps: ");
            subject.setRunMonitor(runMonitor);

            try
            {
                subject.update(buildTimeCalib(ok), new UTC(0));
                fail("Initialization accepted bad tcal:");
            }
            catch (RAPCalException e)
            {
                //desired
            }

            assertTrue("RunMonitor did not receive exception", runMonitor.receivedException());
            runMonitor.reset();

            try
            {
                subject.update(buildTimeCalib(ok_2), new UTC(0));
            }
            catch (RAPCalException e)
            {
                fail("Initialization did not recover from bad tcal:" +
                        e.getMessage());
            }

            assertFalse("RunMonitor received exception", runMonitor.receivedException());
        }

        // convoluted case of multiple degenerate tcals
        {
            long[] degenerate =
                    {
                            2000000,
                            2000500,
                            5000000,
                            4000500 // backward DOM
                    };
            long[] degenerate_2 =
                    {
                            3000000,
                            3000500,
                            6000000,
                            5000500 // backward DOM
                    };
            long[] ok =
                    {
                            4000000,
                            4000500,
                            7000000,
                            7000500
                    };
            long[] degenerate_3 =
                    {
                            5000000,
                            5000500,
                            6000500, // backward DOM, but internally consistent
                            6001000
                    };

            long[] ok_2 =
                    {
                            6000000,
                            6000500,
                            8000000,
                            8000500
                    };
            long[] ok_3 =
                    {
                            7000000,
                            7000500,
                            9000000,
                            9000500
                    };

            RAPCal subject = new MyRAPCal();
            subject.update(buildTimeCalib(degenerate), new UTC(0));

            MyMonitor runMonitor = new MyMonitor();
            runMonitor.expectExceptionString("Non-monotonic timestamps: ");
            subject.setRunMonitor(runMonitor);

            try
            {
                subject.update(buildTimeCalib(degenerate_2), new UTC(0));
                fail("Initialization accepted bad tcal:");
            }
            catch (RAPCalException e)
            {
                //desired
            }
            assertTrue("RunMonitor did not receive exception", runMonitor.receivedException());
            runMonitor.reset();
            try
            {
                subject.update(buildTimeCalib(ok), new UTC(0));
                fail("Initialization accepted bad tcal:");
            }
            catch (RAPCalException e)
            {
                //desired
            }
            assertTrue("RunMonitor did not receive exception", runMonitor.receivedException());
            runMonitor.reset();
            try
            {
                subject.update(buildTimeCalib(degenerate_3), new UTC(0));
                fail("Initialization accepted bad tcal:");
            }
            catch (RAPCalException e)
            {
                //desired
            }

            assertTrue("RunMonitor did not receive exception", runMonitor.receivedException());
            runMonitor.reset();
            try
            {
                subject.update(buildTimeCalib(ok_2), new UTC(0));
                subject.update(buildTimeCalib(ok_3), new UTC(0));
            }
            catch (RAPCalException e)
            {
                fail("Initialization did not recover from bad tcal:" +
                        e.getMessage());
            }

            assertFalse("RunMonitor received exception", runMonitor.receivedException());
        }

    }

    @Test
    public void testDebug() throws RAPCalException
    {
        //
        // Test the debug logging
        //
        Level original = Logger.getRootLogger().getLevel();
        Logger.getRootLogger().setLevel(Level.DEBUG);
        MyRAPCal rapCal = new MyRAPCal();
        rapCal.update(buildTimeCalib(new long[]{1,2,3,4}),new UTC(0));
        rapCal.update(buildTimeCalib(new long[]{3,4,5,6}),new UTC(0));
        rapCal.update(buildTimeCalib(new long[]{5,6,7,8}),new UTC(0));
        rapCal.update(buildTimeCalib(new long[]{7,8,9,10}),new UTC(0));
        rapCal.update(buildTimeCalib(new long[]{9,10,11,12}),new UTC(0));

        Logger.getRootLogger().setLevel(original);
    }

    @Test
    public void testMoniAlert() throws RAPCalException
    {

        UTC GPS = new UTC(13112341235L);
        MyRAPCal subject = new MyRAPCal();



        // CASE I: BadTCalException, i.e. degenerate waveform
        {
            MyMonitor runMonitor = new MyMonitor();
            runMonitor.expectExceptionString("test");

            subject.setRunMonitor(runMonitor);

            try
            {
                subject.triggerException = true;
                subject.update(buildTimeCalib(new long[]{1,2,3,4}),GPS);
                subject.update(buildTimeCalib(new long[]{3,4,5,6}),GPS);
                fail("Exception not generated");
            }
            catch (RAPCalException e)
            {
                assertTrue("RunMonitor did not receive exception", runMonitor.receivedException());
            }
        }


        // CASE II: RAPCalException, i.e. wild tcal, non-monotonic
        {
            subject.triggerException = false;

            MyMonitor runMonitor = new MyMonitor();
            runMonitor.expectExceptionString("Non-monotonic timestamps: ");

            subject.setRunMonitor(runMonitor);

            long[] ok = { 140284688764968L, 140284688765936L, 19488689372039L, 19488689372651L };
            long[] ok2 = { 140285324954832L, 140285324955800L, 19489961750703L, 19489961751315L };
            long[] wild = { 140285607890297L, 140285607891266L, 19490527821161L, 19490527631773L };
            subject.update(buildTimeCalib(ok),GPS);
            subject.update(buildTimeCalib(ok2),GPS);

            try
            {
                subject.update(buildTimeCalib(wild),GPS);
                fail("Exception not generated");
            }
            catch (RAPCalException e)
            {
                assertTrue("RunMonitor did not receive exception", runMonitor.receivedException());
            }
        }

    }


    /**
     * Implements AbstractRapCal details independently
     */
    private static class MyIsochron
    {
        final long dortx_a;
        final long dorrx_a;
        final long domrx_a;
        final long domtx_a;

        final long dortx_b;
        final long dorrx_b;
        final long domrx_b;
        final long domtx_b;

        final double epsilon;

        final long dormid;
        final long dommid;
        final long dordt;
        final long domdt;

        final double clen;

        private MyIsochron(long[] tcalA, long[] tcalB)
        {

            dortx_a = tcalA[0] * 500;
            dorrx_a = tcalA[1] * 500;
            domrx_a = tcalA[2] * 250;
            domtx_a = tcalA[3] * 250;

            dortx_b = tcalB[0] * 500;
            dorrx_b = tcalB[1] * 500;
            domrx_b = tcalB[2] * 250;
            domtx_b = tcalB[3] * 250;

            dordt = ((dortx_b + dorrx_b) - (dortx_a + dorrx_a)) / 2;
            domdt = ((domrx_b + domtx_b) - (domrx_a + domtx_a)) / 2;

            dormid = (dortx_b + dorrx_b) / 2;
            dommid = (domrx_b + domtx_b) / 2;

            epsilon = (double)(dordt - domdt)/domdt;

            clen = 0.5 *
                    ( 1E-10 * (dorrx_b-dortx_b) -
                            ((1 + epsilon) * 1E-10 * (domtx_b - domrx_b)) );
        }

        private long reconstruct(long domclk, long GPS)
        {
            long dt = ( (250*domclk) - dommid);
            dt += (long) (epsilon * dt);

            return GPS + dormid + dt;
        }
    }


}
