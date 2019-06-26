package icecube.daq.monitoring;

import icecube.daq.common.MockAppender;
import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.TimeCalib;
import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.juggler.alert.IAlertQueue;
import icecube.daq.payload.IUTCTime;
import icecube.daq.rapcal.BadTCalException;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.rapcal.Isochron;
import icecube.daq.util.DOMInfo;
import icecube.daq.util.IDOMRegistry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import static org.junit.Assert.*;

class MockAlertQueue
    implements IAlertQueue
{
    private volatile int numPushed;

    int getNumPushed()
    {
        return numPushed;
    }

    @Override
    public boolean isStopped()
    {
        return false;
    }

    @Override
    public void push(Map<String, Object> map)
        throws AlertException
    {
        throw new Error("Don't use this method");
    }

    @Override
    public void push(String varname, Alerter.Priority prio,
                     Map<String, Object> values)
        throws AlertException
    {
        push(varname, prio, null, values);
    }

    @Override
    public void push(String varname, Alerter.Priority prio, IUTCTime utcTime,
                     Map<String, Object> values)
        throws AlertException
    {
        numPushed++;
    }

    @Override
    public void start()
    {
        // do nothing
    }

    @Override
    public void stop()
    {
        // do nothing
    }
}

class MockDOMRegistry
    implements IDOMRegistry
{
    private HashMap<Long, DOMInfo> doms = new HashMap<Long, DOMInfo>();

    void addDom(long mbid, int string, int location)
    {
        doms.put(mbid, new DOMInfo(mbid, string, location));
    }

    @Override
    public Iterable<DOMInfo> allDOMs()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public double distanceBetweenDOMs(DOMInfo dom0, DOMInfo dom1)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public double distanceBetweenDOMs(short chan0, short chan1)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public short getChannelId(long mbid)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public DOMInfo getDom(long mbid)
    {
        return doms.get(mbid);
    }

    @Override
    public DOMInfo getDom(int major, int minor)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public DOMInfo getDom(short channelId)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public Set<DOMInfo> getDomsOnHub(int hubId)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public Set<DOMInfo> getDomsOnString(int string)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public String getName(long mbid)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public String getProductionId(long mbid)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int getStringMajor(long mbid)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int getStringMinor(long mbid)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int size()
    {
        throw new Error("Unimplemented");
    }
}

enum GPSQuality
{
    VERY_GOOD(' '),
    GOOD('.'),
    AVERAGE('*'),
    BAD('#'),
    VERY_BAD('?');

    private final byte val;

    private GPSQuality(char ch)
    {
        val = (byte) ch;
    }

    public byte getValue()
    {
        return val;
    }
}

public class RunMonitorTest
{
    private static final MockAppender appender = new MockAppender();
    private static final Charset US_ASCII = Charset.forName("US-ASCII");

    private static Random random = new Random(12345678L);

    @Rule
    public TestName testName = new TestName();

    @BeforeClass
    public static void setupClass()
    {
        // exercise logging calls, but output to nowhere
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);
    }

    @After
    public void teardown()
    {
        appender.assertNoLogMessages();
    }

    private void addRapcalLogMsg(List<String> expLog, List<DOMInfo> doms,
                                 long mbid, short[] waveform)
    {
        for (DOMInfo dom : doms) {
            if (dom.getNumericMainboardId() == mbid) {
                final String wfStr;
                if (waveform == null) {
                    wfStr = "";
                } else {
                    StringBuilder buf = new StringBuilder(" waveform[");
                    for (int i = 0; i < waveform.length; i++) {
                        if (i > 0) {
                            buf.append(' ');
                        }
                        buf.append(waveform[i]);
                    }
                    buf.append(']');
                    wfStr = buf.toString();
                }

                expLog.add("Exception for DOM " +
                           dom.getDeploymentLocation() + wfStr);
                return;
            }
        }

        throw new Error(String.format("Cannot find DOM %012x in dom list",
                                      mbid));
    }

    private void checkRapcalLogMsgs(List<String> expLogs)
    {
        try {
            for (int i = 0; i < expLogs.size(); i++) {
                appender.assertLogMessage(expLogs.get(i));
            }
            appender.assertNoLogMessages();
        } finally {
            appender.clear();
        }
    }

    private GPSInfo createGPSInfo(int day, int hour, int min, int sec,
                                  GPSQuality quality, long dorClock)
    {
        final byte SOH = (byte) 1;
        final String gpsStr =
            String.format("%03d:%02d:%02d:%02d", day, hour, min, sec);

        ByteBuffer buf = ByteBuffer.allocate(22);
        buf.put(SOH);
        buf.put(gpsStr.getBytes(US_ASCII));
        buf.put(quality.getValue());
        buf.putLong(dorClock);
        buf.flip();

        return new GPSInfo(buf, null);
    }

    private static final Isochron createIsochron(Isochron prev, long gpsOffset)
    {
        final long[] t0;
        if (prev == null) {
            t0 = fillTimeArray(0);
        } else {
            t0 = null;
        }

        final long[] t1;
        if (prev == null) {
            t1 = fillTimeArray(t0[t0.length - 1]);
        } else {
            t1 = fillTimeArray(prev.getDORReceiveTime());
        }

        Isochron iso;
        try {
            if (prev == null) {
                iso = new Isochron(t0, t1, gpsOffset);
            } else {
                iso = new Isochron(prev, t1, gpsOffset);
            }
        } catch (RAPCalException rex) {
            rex.printStackTrace();
            fail("Cannot build Isochron from " + t0 + " and " + t1);
            iso = null;
        }

        return iso;
    }

    private TimeCalib createTCal(short flags, long dorTx, long dorRx,
                                  short[] dorWaveform, long domRx, long domTx,
                                  short[] domWaveform)
    {
        if (dorWaveform.length != 64) {
            throw new Error("DOR waveform must be a 64-entry array");
        } else if (domWaveform.length != 64) {
            throw new Error("DOM waveform must be a 64-entry array");
        }

        ByteBuffer buf = ByteBuffer.allocate(292);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) buf.capacity());
        buf.putShort(flags);

        buf.putLong(dorTx);
        buf.putLong(dorRx);
        for (int i = 0; i < 64; i++) {
            buf.putShort(dorWaveform[i]);
        }

        buf.putLong(domRx);
        buf.putLong(domTx);
        for (int i = 0; i < 64; i++) {
            buf.putShort(domWaveform[i]);
        }

        buf.flip();

        return new TimeCalib(buf);
    }

    private static long[] fillTimeArray(long base)
    {
        final int timestep = 10000;

        long val = base + random.nextInt(timestep);

        long[] array = new long[4];
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                val += random.nextInt(timestep);
            }
            array[i] = val;
        }

        return array;
    }

    private void waitForRunSwitch(RunMonitor runMon, int runNum,
                                  int maxAttempts)
    {
        int numAttempts = 0;
        while (runMon.getRunNumber() != runNum) {
            if (numAttempts++ >= maxAttempts) {
                final String errmsg =
                    String.format("Run number did not switch to %s (now %d)",
                                  runNum, runMon.getRunNumber());
                throw new Error(errmsg);
            }

            Thread.yield();

            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
        }
    }

    private void waitForThreadStart(RunMonitor runMon)
    {
        int loopCount = 0;
        while (!runMon.isRunning()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }

            if (loopCount++ > 100) {
                fail("Thread never started");
            }
        }
    }

    @Test
    public void testOutsideRun()
        throws InterruptedException
    {
        final int string = 1;

        MockAlertQueue aq = new MockAlertQueue();
        RunMonitor runMon = new RunMonitor(string, aq);
        runMon.start();

        waitForThreadStart(runMon);

        runMon.pushGPSProcfileNotReady(string, 1);
        runMon.pushGPSProcfileNotReady(string, 2);

        runMon.stop();
        runMon.join();

        assertEquals("Received unexpected monitoring data",
                     0, aq.getNumPushed());
    }

    @Test
    public void testInAndOut()
        throws InterruptedException
    {
        final int string = 2;

        MockAlertQueue aq = new MockAlertQueue();
        RunMonitor runMon = new RunMonitor(string, aq);
        runMon.start();


        final long DOM0 = 111111111L;
        final long DOM1 = 123456789L;

        List<DOMInfo> cfgDOMList = new ArrayList<DOMInfo>();
        cfgDOMList.add(new DOMInfo(DOM0, string, 7));
        cfgDOMList.add(new DOMInfo(DOM1, string, 62));

        runMon.setConfiguredDOMs(cfgDOMList);
        waitForThreadStart(runMon);

        // push some data before the run
        runMon.pushGPSProcfileNotReady(string, 1);
        runMon.pushGPSProcfileNotReady(string, 2);

        int runNum;

        // start the run
        runNum = 123456;
        runMon.setRunNumber(runNum);
        waitForRunSwitch(runMon, runNum, 100);

        // push some data for the run
        runMon.pushGPSProcfileNotReady(string, 3);
        runMon.pushGPSProcfileNotReady(string, 4);

        // stop the run
        runMon.stop();
        runMon.join();

        // check alert counts for the run
        assertEquals("Did not receive monitoring data", 3, aq.getNumPushed());
    }

    @Test
    public void testMultiRun()
        throws InterruptedException
    {
        final int string = 3;

        MockAlertQueue aq = new MockAlertQueue();
        RunMonitor runMon = new RunMonitor(string, aq);
        runMon.start();

        final long DOM0 = 111111111L;
        final long DOM1 = 123456789L;

        List<DOMInfo> cfgDOMList = new ArrayList<DOMInfo>();
        cfgDOMList.add(new DOMInfo(DOM0, string, 7));
        cfgDOMList.add(new DOMInfo(DOM1, string, 62));

        runMon.setConfiguredDOMs(cfgDOMList);

        waitForThreadStart(runMon);

        int runNum;

        // start the first run
        runNum = 123456;
        runMon.setRunNumber(runNum);
        waitForRunSwitch(runMon, runNum, 100);

        // push some data the for the first run
        runMon.pushGPSProcfileNotReady(string, 1);
        runMon.pushGPSProcfileNotReady(string, 2);

        // switch to the second run
        runNum = 123457;
        runMon.setRunNumber(runNum);
        waitForRunSwitch(runMon, runNum, 100);

        // check alert count from the first run
        assertEquals("Did not receive monitoring data", 3, aq.getNumPushed());

        // push some data for the second run
        runMon.pushGPSProcfileNotReady(string, 3);
        runMon.pushGPSProcfileNotReady(string, 4);

        // start the third run
        runNum = 123458;
        runMon.setRunNumber(runNum);
        waitForRunSwitch(runMon, runNum, 100);

        // check alert count from the second run
        assertEquals("Did not receive monitoring data", 6, aq.getNumPushed());

        // push some data for the third run
        runMon.pushGPSProcfileNotReady(string, 5);
        runMon.pushGPSProcfileNotReady(string, 6);

        runMon.stop();
        runMon.join();

        // check alert count from the third run
        assertEquals("Did not receive monitoring data", 9, aq.getNumPushed());
    }

    @Test
    public void testStopStart()
        throws InterruptedException
    {
        final int string = 4;

        MockAlertQueue aq = new MockAlertQueue();
        RunMonitor runMon = new RunMonitor(string, aq);
        runMon.start();

        final long DOM0 = 111111111L;
        final long DOM1 = 123456789L;

        List<DOMInfo> cfgDOMList = new ArrayList<DOMInfo>();
        cfgDOMList.add(new DOMInfo(DOM0, string, 7));
        cfgDOMList.add(new DOMInfo(DOM1, string, 62));

        runMon.setConfiguredDOMs(cfgDOMList);

        waitForThreadStart(runMon);

        int runNum;

        // start the first run
        runNum = 123456;
        runMon.setRunNumber(runNum);
        waitForRunSwitch(runMon, runNum, 100);

        // push some data for the first run
        runMon.pushGPSProcfileNotReady(string, 1);
        runMon.pushGPSProcfileNotReady(string, 2);

        // switch to the second run
        runNum = 123457;
        runMon.setRunNumber(runNum);
        waitForRunSwitch(runMon, runNum, 100);

        // check alert count from the first run
        assertEquals("Did not receive monitoring data", 3, aq.getNumPushed());

        // push data for the second run
        runMon.pushGPSProcfileNotReady(string, 3);
        runMon.pushGPSProcfileNotReady(string, 4);

        // stop the second run
        runMon.stop();
        runMon.join();

        // check alert counts from the second run
        assertEquals("Did not receive monitoring data", 6, aq.getNumPushed());
        assertEquals("Run number should not be set", RunMonitor.NO_ACTIVE_RUN,
                     runMon.getRunNumber());

        // restart the thread
        runMon.start();
        waitForThreadStart(runMon);

        // push some pre-run data
        runMon.pushGPSProcfileNotReady(string, 5);
        runMon.pushGPSProcfileNotReady(string, 6);

        // start the third run
        runNum = 123458;
        runMon.setRunNumber(runNum);
        waitForRunSwitch(runMon, runNum, 100);

        // check alert counts from the second run
        assertEquals("Did not receive monitoring data", 6, aq.getNumPushed());

        // push data for the third run
        runMon.pushGPSProcfileNotReady(string, 7);
        runMon.pushGPSProcfileNotReady(string, 8);

        // stop the third run
        runMon.stop();
        runMon.join();

        // check alert counts from the third run
        assertEquals("Did not receive monitoring data", 9, aq.getNumPushed());
    }

    @Test
    public void testWildTCal()
        throws InterruptedException
    {
        final int string = 5;

        MockAlertQueue aq = new MockAlertQueue();
        RunMonitor runMon = new RunMonitor(string, aq);
        runMon.start();

        final long DOM0 = 111111111L;
        final long DOM1 = 123456789L;

        List<DOMInfo> cfgDOMList = new ArrayList<DOMInfo>();
        cfgDOMList.add(new DOMInfo(DOM0, string, 7));
        cfgDOMList.add(new DOMInfo(DOM1, string, 62));

        runMon.setConfiguredDOMs(cfgDOMList);

        waitForThreadStart(runMon);

        final int runNum = 123456;
        runMon.setRunNumber(runNum);
        waitForRunSwitch(runMon, runNum, 100);

        runMon.pushWildTCal(DOM0, 2.2, 3.3);
        runMon.pushWildTCal(DOM0, 4.4, 5.5);
        runMon.pushWildTCal(DOM1, 6.6, 7.7);

        runMon.stop();
        runMon.join();

        assertEquals("Did not receive monitoring data", 2, aq.getNumPushed());
    }

    @Test
    public void testGPSProblem()
        throws InterruptedException
    {
        final int string = 7;

        MockAlertQueue aq = new MockAlertQueue();
        RunMonitor runMon = new RunMonitor(string, aq);
        runMon.start();

        final long DOM0 = 111111111L;
        final long DOM1 = 123456789L;

        List<DOMInfo> cfgDOMList = new ArrayList<DOMInfo>();
        cfgDOMList.add(new DOMInfo(DOM0, string, 7));
        cfgDOMList.add(new DOMInfo(DOM1, string, 62));

        runMon.setConfiguredDOMs(cfgDOMList);

        final int CARD1 = 1;
        final int CARD5 = 5;

        waitForThreadStart(runMon);

        final int runNum = 123456;
        runMon.setRunNumber(runNum);
        waitForRunSwitch(runMon, runNum, 100);

        final int badString = string + 3;
        runMon.pushException(badString, CARD1, new GPSException("Bad1"));
        runMon.pushException(string, CARD1, new GPSException("Fake1"));
        runMon.pushException(string, CARD5, new GPSException("Fake5"));

        runMon.stop();
        runMon.join();

        assertEquals("Did not receive monitoring data", 3, aq.getNumPushed());

        try {
            for (int i = 0; i < 4; i++) {
                final String errmsg;
                if (i == 0) {
                    errmsg =
                        String.format("Expected data from string %d, not %d",
                                      string, badString);
                } else {
                    final int tmpStr;
                    if (i < 2) {
                        tmpStr = badString;
                    } else {
                        tmpStr = string;
                    }

                    final int card;
                    if (i < 3) {
                        card = CARD1;
                    } else {
                        card = CARD5;
                    }

                    errmsg = String.format("String %d card %d GPS exception",
                                           tmpStr, card);
                }

                appender.assertLogMessage(errmsg);
            }
            appender.assertNoLogMessages();
        } finally {
            appender.clear();
        }
    }

    @Test
    public void testBadTCalProblem()
        throws InterruptedException
    {
        final int string = 8;

        MockAlertQueue aq = new MockAlertQueue();
        RunMonitor runMon = new RunMonitor(string, aq);
        runMon.start();

        final long DOM0 = 111111111L;
        final long DOM1 = 123456789L;

        List<DOMInfo> cfgDOMList = new ArrayList<DOMInfo>();
        cfgDOMList.add(new DOMInfo(DOM0, string, 7));
        cfgDOMList.add(new DOMInfo(DOM1, string, 19));

        runMon.setConfiguredDOMs(cfgDOMList);

        final short flags = (short) 0;
        final short[] fakeWave = new short[64];
        final TimeCalib fakeTCal = createTCal(flags, 1L, 2L, fakeWave, 3L, 4L,
                                              fakeWave);

        waitForThreadStart(runMon);

        final int runNum = 123456;
        runMon.setRunNumber(runNum);
        waitForRunSwitch(runMon, runNum, 100);

        List<String> expLog = new ArrayList<String>();
        runMon.pushException(DOM0, new BadTCalException("Fake1", fakeWave),
                             fakeTCal);
        addRapcalLogMsg(expLog, cfgDOMList, DOM0, fakeWave);
        runMon.pushException(DOM1, new BadTCalException("Fake2", fakeWave),
                             fakeTCal);
        addRapcalLogMsg(expLog, cfgDOMList, DOM1, fakeWave);
        runMon.pushException(DOM1, new BadTCalException("Fake3", fakeWave),
                             fakeTCal);
        addRapcalLogMsg(expLog, cfgDOMList, DOM1, fakeWave);

        runMon.stop();
        runMon.join();

        assertEquals("Did not receive monitoring data", 2, aq.getNumPushed());

        checkRapcalLogMsgs(expLog);
    }

    @Test
    public void testRAPCalProblem()
        throws InterruptedException
    {
        final int string = 9;

        MockAlertQueue aq = new MockAlertQueue();
        RunMonitor runMon = new RunMonitor(string, aq);
        runMon.start();

        final long DOM0 = 111111111L;
        final long DOM1 = 123456789L;

        List<DOMInfo> cfgDOMList = new ArrayList<DOMInfo>();
        cfgDOMList.add(new DOMInfo(DOM0, string, 7));
        cfgDOMList.add(new DOMInfo(DOM1, string, 8));

        runMon.setConfiguredDOMs(cfgDOMList);

        final short flags = (short) 0;
        final short[] fakeWave = new short[64];
        final TimeCalib fakeTCal = createTCal(flags, 1L, 2L, fakeWave, 3L, 4L,
                                              fakeWave);

        waitForThreadStart(runMon);

        final int runNum = 123456;
        runMon.setRunNumber(runNum);
        waitForRunSwitch(runMon, runNum, 100);

        List<String> expLog = new ArrayList<String>();
        runMon.pushException(DOM0, new RAPCalException("Fake1"), fakeTCal);
        addRapcalLogMsg(expLog, cfgDOMList, DOM0, null);
        runMon.pushException(DOM1, new RAPCalException("Fake2"), fakeTCal);
        addRapcalLogMsg(expLog, cfgDOMList, DOM1, null);
        runMon.pushException(DOM1, new RAPCalException("Fake3"), fakeTCal);
        addRapcalLogMsg(expLog, cfgDOMList, DOM1, null);

        runMon.stop();
        runMon.join();

        assertEquals("Did not receive monitoring data", 2, aq.getNumPushed());

        checkRapcalLogMsgs(expLog);
    }

    @Test
    public void testGPSMisalignment()
        throws InterruptedException
    {
        final int string = 10;

        MockAlertQueue aq = new MockAlertQueue();
        RunMonitor runMon = new RunMonitor(string, aq);
        runMon.start();


        final long DOM0 = 111111111L;
        final long DOM1 = 123456789L;

        List<DOMInfo> cfgDOMList = new ArrayList<DOMInfo>();
        cfgDOMList.add(new DOMInfo(DOM0, string, 7));
        cfgDOMList.add(new DOMInfo(DOM1, string, 62));

        runMon.setConfiguredDOMs(cfgDOMList);
        waitForThreadStart(runMon);

        GPSInfo fakeInfo = createGPSInfo(1, 2, 3, 4, GPSQuality.VERY_GOOD,
                                         123456789L);

        // push some data before the run
        runMon.pushGPSMisalignment(string, 1, fakeInfo, fakeInfo);
        runMon.pushGPSMisalignment(string, 2, fakeInfo, fakeInfo);

        int runNum;

        // start the run
        runNum = 123456;
        runMon.setRunNumber(runNum);
        waitForRunSwitch(runMon, runNum, 100);

        // push some data for the run
        runMon.pushGPSMisalignment(string, 3, fakeInfo, fakeInfo);
        runMon.pushGPSMisalignment(string, 4, fakeInfo, fakeInfo);

        // stop the run
        runMon.stop();
        runMon.join();

        // check alert counts for the run
        assertEquals("Did not receive monitoring data", 3, aq.getNumPushed());
    }

    @Test
    public void testIsochron()
        throws InterruptedException
    {
        final int string = 10;

        MockAlertQueue aq = new MockAlertQueue();
        RunMonitor runMon = new RunMonitor(string, aq);
        runMon.start();

        final long DOM0 = 111111111L;
        final long DOM1 = 123456789L;

        List<DOMInfo> cfgDOMList = new ArrayList<DOMInfo>();
        cfgDOMList.add(new DOMInfo(DOM0, string, 1));
        cfgDOMList.add(new DOMInfo(DOM1, string, 2));

        runMon.setConfiguredDOMs(cfgDOMList);

        waitForThreadStart(runMon);

        final long gpsOffset = 0L;

        // push some data before the run
        runMon.push(DOM0, createIsochron(null, gpsOffset));

        int runNum;

        // start the run
        runNum = 123456;
        runMon.setRunNumber(runNum);
        waitForRunSwitch(runMon, runNum, 100);

        // push some data for the run
        Isochron prev0 = createIsochron(null, gpsOffset);
        Isochron prev1 = createIsochron(null, gpsOffset);
        for (int i = 0; i < 10; i++) {
            runMon.push(DOM0, createIsochron(prev0, gpsOffset));
            runMon.push(DOM1, createIsochron(prev1, gpsOffset));
        }

        // stop the run
        runMon.stop();
        runMon.join();

        // check alert counts for the run
        assertEquals("Did not receive monitoring data", 3, aq.getNumPushed());
    }

    @Test
    public void testHLCCount()
        throws InterruptedException
    {
        final int string = 5;

        MockAlertQueue aq = new MockAlertQueue();
        RunMonitor runMon = new RunMonitor(string, aq);
        runMon.start();

        final long DOM0 = 111111111L;
        final long DOM1 = 123456789L;

        List<DOMInfo> cfgDOMList = new ArrayList<DOMInfo>();
        cfgDOMList.add(new DOMInfo(DOM0, string, 7));
        cfgDOMList.add(new DOMInfo(DOM1, string, 62));

        runMon.setConfiguredDOMs(cfgDOMList);

        waitForThreadStart(runMon);

        final int runNum = 123456;
        runMon.setRunNumber(runNum);
        waitForRunSwitch(runMon, runNum, 100);

        runMon.countHLCHit(new long[]{DOM0, DOM0, DOM1},
                new long[] {123456L,234567L,123499L});

        runMon.stop();
        runMon.join();

        assertEquals("Did not receive monitoring data", 3, aq.getNumPushed());
    }
}
