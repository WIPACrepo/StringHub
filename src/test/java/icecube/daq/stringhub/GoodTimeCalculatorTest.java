package icecube.daq.stringhub;

import icecube.daq.domapp.AbstractDataCollector;
import icecube.daq.monitoring.TCalExceptionAlerter;

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

    public void close()
    {
        throw new Error("Unimplemented");
    }

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

    public long getNumHits()
    {
        throw new Error("Unimplemented");
    }

    public long getNumMoni()
    {
        throw new Error("Unimplemented");
    }

    public long getNumSupernova()
    {
        throw new Error("Unimplemented");
    }

    public long getNumTcal()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public boolean isZombie()
    {
        return false;
    }

    public void setTCalExceptionAlerter(TCalExceptionAlerter x0)
    {
        throw new Error("Unimplemented");
    }

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
        assertEquals("Bad earliest time", goodTime, calc.getTime());
    }

    private void checkLatest(String name, long goodTime)
    {
        GoodTimeCalculator calc = new GoodTimeCalculator(conn, false);
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
}
