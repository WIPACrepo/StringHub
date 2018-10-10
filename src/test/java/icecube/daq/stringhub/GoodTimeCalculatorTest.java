package icecube.daq.stringhub;

import icecube.daq.domapp.AbstractDataCollector;
import icecube.daq.monitoring.IRunMonitor;

import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

class MockDataCollector
    extends AbstractDataCollector
{
    private long time;

    MockDataCollector(int card, int pair, char dom, long time)
    {
        super(card, pair, dom);

        this.time = time;
    }

    @Override
    public void close()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public long getAcquisitionLoopCount()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public long getFirstHitTime()
    {
        return time;
    }

    @Override
    public long getLastHitTime()
    {
        return time;
    }

    @Override
    public long getNumHits()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public long getNumMoni()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public long getNumSupernova()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public long getNumTcal()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public boolean isZombie()
    {
        return false;
    }

    @Override
    public void setRunMonitor(IRunMonitor x0)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void signalShutdown()
    {
        throw new Error("Unimplemented");
    }
}

public class GoodTimeCalculatorTest
{
    private static final long ONE_SECOND = 10000000000L;

    private DOMConnector conn;

    private void checkEarliest(String name, long goodTime)
    {
        GoodTimeCalculator calc = new GoodTimeCalculator(conn, true);
        // debug // calc.dump();
        assertEquals("Bad earliest time", goodTime, calc.getTime());
    }

    private void checkLatest(String name, long goodTime)
    {
        GoodTimeCalculator calc = new GoodTimeCalculator(conn, false);
        // debug // calc.dump();
        assertEquals("Bad latest time", goodTime, calc.getTime());
    }

    @Before
    public void setUp()
    {
        conn = new DOMConnector(64);
    }

    @Test
    public void testEarliestEmpty()
    {
        checkEarliest("EarliestEmpty", 0L);
    }

    @Test
    public void testLatestEmpty()
    {
        checkLatest("LatestEmpty", Long.MAX_VALUE);
    }

    @Test
    public void testEarliestOne()
    {
        final long time = 123456789L;

        conn.add(new MockDataCollector(0, 0, 'A', time));

        checkEarliest("EarliestOne", time);
    }

    @Test
    public void testLatestOne()
    {
        final long time = 123456789L;

        conn.add(new MockDataCollector(0, 0, 'A', time));

        checkLatest("LatestOne", time);
    }

    @Test
    public void testEarliestMany()
    {
        Random rand = new Random();

        long firstTime = Long.MAX_VALUE;
        long lastTime = Long.MIN_VALUE;

        long tmpTime = 60 * ONE_SECOND;
        for (int i = 0; i < 60; i++) {
            tmpTime += Math.abs(rand.nextLong() % ONE_SECOND);

            if (firstTime == Long.MAX_VALUE) {
                firstTime = tmpTime;
            }
            lastTime = tmpTime;

            conn.add(new MockDataCollector(0, i, 'A', tmpTime));
        }

        checkEarliest("EarliestMany", lastTime);
    }

    @Test
    public void testLatestMany()
    {
        Random rand = new Random();

        long firstTime = Long.MAX_VALUE;
        long lastTime = Long.MIN_VALUE;

        long tmpTime = 60 * ONE_SECOND;
        for (int i = 0; i < 60; i++) {
            tmpTime += Math.abs(rand.nextLong() % ONE_SECOND);

            if (firstTime == Long.MAX_VALUE) {
                firstTime = tmpTime;
            }
            lastTime = tmpTime;

            conn.add(new MockDataCollector(0, i, 'A', tmpTime));
        }

        checkLatest("LatestMany", firstTime);
    }

    @Test
    public void testEarliestSplitFront()
    {
        Random rand = new Random();

        long firstTime = Long.MAX_VALUE;
        long lastTime = Long.MIN_VALUE;

        long tmpTime = 60 * ONE_SECOND;
        for (int i = 0; i < 60; i++) {
            tmpTime += Math.abs(rand.nextLong() % ONE_SECOND);

            if (i == 15) {
                // jump ahead two minutes
                tmpTime += 120 * ONE_SECOND;
                firstTime = Long.MAX_VALUE;
            }

            if (firstTime == Long.MAX_VALUE) {
                firstTime = tmpTime;
            }
            lastTime = tmpTime;

            conn.add(new MockDataCollector(0, i, 'A', tmpTime));
        }

        checkEarliest("EarliestSplitFront", lastTime);
    }

    @Test
    public void testLatestSplitFront()
    {
        Random rand = new Random();

        long firstTime = Long.MAX_VALUE;
        long lastTime = Long.MIN_VALUE;

        long tmpTime = 60 * ONE_SECOND;
        for (int i = 0; i < 60; i++) {
            tmpTime += Math.abs(rand.nextLong() % ONE_SECOND);

            if (i == 15) {
                // jump ahead two minutes
                tmpTime += 120 * ONE_SECOND;
                firstTime = Long.MAX_VALUE;
            }

            if (firstTime == Long.MAX_VALUE) {
                firstTime = tmpTime;
            }
            lastTime = tmpTime;

            conn.add(new MockDataCollector(0, i, 'A', tmpTime));
        }

        checkLatest("LatestSplitFront", firstTime);
    }

    @Test
    public void testEarliestSplitBack()
    {
        Random rand = new Random();

        long firstTime = Long.MAX_VALUE;
        long lastTime = Long.MIN_VALUE;

        long tmpTime = 60 * ONE_SECOND;
        for (int i = 0; i < 60; i++) {
            tmpTime += Math.abs(rand.nextLong() % ONE_SECOND);

            if (i == 45) {
                // jump ahead two minutes
                tmpTime += 120 * ONE_SECOND;
            } else if (i < 45) {
                if (firstTime == Long.MAX_VALUE) {
                    firstTime = tmpTime;
                }
                lastTime = tmpTime;
            }

            conn.add(new MockDataCollector(0, i, 'A', tmpTime));
        }

        checkEarliest("EarliestSplitBack", lastTime);
    }

    @Test
    public void testLatestSplitBack()
    {
        Random rand = new Random();

        long firstTime = Long.MAX_VALUE;
        long lastTime = Long.MIN_VALUE;

        long tmpTime = 60 * ONE_SECOND;
        for (int i = 0; i < 60; i++) {
            tmpTime += Math.abs(rand.nextLong() % ONE_SECOND);

            if (i == 45) {
                // jump ahead two minutes
                tmpTime += 120 * ONE_SECOND;
            } else if (i < 45) {
                if (firstTime == Long.MAX_VALUE) {
                    firstTime = tmpTime;
                }
                lastTime = tmpTime;
            }

            conn.add(new MockDataCollector(0, i, 'A', tmpTime));
        }

        checkLatest("LatestSplitBack", firstTime);
    }

    @Test
    public void testEarliestMultiSplitFront()
    {
        Random rand = new Random();

        long firstTime = Long.MAX_VALUE;
        long lastTime = Long.MIN_VALUE;

        long tmpTime = 60 * ONE_SECOND;
        for (int i = 0; i < 60; i++) {
            tmpTime += Math.abs(rand.nextLong() % ONE_SECOND);

            if (i == 15) {
                // jump ahead five minutes
                tmpTime += 300 * ONE_SECOND;
                firstTime = Long.MAX_VALUE;
            }

            if (i == 20) {
                // jump ahead two minutes
                tmpTime += 120 * ONE_SECOND;
                firstTime = Long.MAX_VALUE;
            }

            if (firstTime == Long.MAX_VALUE) {
                firstTime = tmpTime;
            }
            lastTime = tmpTime;

            conn.add(new MockDataCollector(0, i, 'A', tmpTime));
        }

        checkEarliest("EarliestMultiSplitFront", lastTime);
    }

    @Test
    public void testLatestMultiSplitFront()
    {
        Random rand = new Random();

        long firstTime = Long.MAX_VALUE;
        long lastTime = Long.MIN_VALUE;

        long tmpTime = 60 * ONE_SECOND;
        for (int i = 0; i < 60; i++) {
            tmpTime += Math.abs(rand.nextLong() % ONE_SECOND);

            if (i == 15) {
                // jump ahead five minutes
                tmpTime += 300 * ONE_SECOND;
                firstTime = Long.MAX_VALUE;
            }

            if (i == 20) {
                // jump ahead two minutes
                tmpTime += 120 * ONE_SECOND;
                firstTime = Long.MAX_VALUE;
            }

            if (firstTime == Long.MAX_VALUE) {
                firstTime = tmpTime;
            }
            lastTime = tmpTime;

            conn.add(new MockDataCollector(0, i, 'A', tmpTime));
        }

        checkLatest("LatestSplitFront", firstTime);
    }

    @Test
    public void testEarliestMultiSplitBack()
    {
        Random rand = new Random();

        long firstTime = Long.MAX_VALUE;
        long lastTime = Long.MIN_VALUE;

        long tmpTime = 60 * ONE_SECOND;
        for (int i = 0; i < 60; i++) {
            tmpTime += Math.abs(rand.nextLong() % ONE_SECOND);

            if (i == 45) {
                // jump ahead five minutes
                tmpTime += 300 * ONE_SECOND;
            } else if (i == 55) {
                // jump ahead two minutes
                tmpTime += 120 * ONE_SECOND;
            } else if (i < 45) {
                if (firstTime == Long.MAX_VALUE) {
                    firstTime = tmpTime;
                }
                lastTime = tmpTime;
            }

            conn.add(new MockDataCollector(0, i, 'A', tmpTime));
        }

        checkEarliest("EarliestSplitBack", lastTime);
    }

    @Test
    public void testLatestMultiSplitBack()
    {
        Random rand = new Random();

        long firstTime = Long.MAX_VALUE;
        long lastTime = Long.MIN_VALUE;

        long tmpTime = 60 * ONE_SECOND;
        for (int i = 0; i < 60; i++) {
            tmpTime += Math.abs(rand.nextLong() % ONE_SECOND);

            if (i == 45) {
                // jump ahead five minutes
                tmpTime += 300 * ONE_SECOND;
            } else if (i == 55) {
                // jump ahead two minutes
                tmpTime += 120 * ONE_SECOND;
            } else if (i < 55) {
                if (firstTime == Long.MAX_VALUE) {
                    firstTime = tmpTime;
                }
                lastTime = tmpTime;
            }

            conn.add(new MockDataCollector(0, i, 'A', tmpTime));
        }

        checkLatest("LatestSplitBack", firstTime);
    }

    public void testRun131585()
    {
        // test the fix for the bad end run time
        // discovered in run 131585

        // Two drops 11 hours apart
        conn.add(new MockDataCollector(0,0,'A', 239065550000000000L));  // 2018-10-04 16:42:35.000 (Mnemophobia)
        conn.add(new MockDataCollector(0,0,'A', 239469990000000000L));  // 2018-10-05 03:56:39.000 (Itchetiky)

        // The non-dropped DOMS at end of run 7 hours later
        Random rand = new Random();
        long endOfRunTime = 239727620000000000L; //2018-10-05 11:06:02.000 (End of Run)
        for(int i=0; i< 56; i++)
        {
            conn.add(new MockDataCollector(0,0,'A', endOfRunTime));
            endOfRunTime += Math.abs(rand.nextLong() % ONE_SECOND);
        }

        checkLatest("Run131585", endOfRunTime);
    }

}
