package icecube.daq.monitoring;

import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.TimeCalib;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.payload.IUTCTime;
import icecube.daq.rapcal.Isochron;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.util.DOMInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertSame;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests BatchHLCReporter.java.
 */
public class BatchHLCReporterTest
{

    public static final int DEFAULT_MBID = 123;
    int BATCH_SIZE = 100;
    BatchHLCReporter subject;
    MockRunMonitor monitorMock;

    @Before
    public void setUp()
    {
        subject = new BatchHLCReporter(BATCH_SIZE);
        monitorMock = new MockRunMonitor();
        subject.setRunMonitor(monitorMock);
    }

    @Test
    public void testBatching()
    {
        //
        // test batching
        //
        for (int round = 1; round<10; round++)
        {
            int roundStart = 999 * round;
            for(int i=0; i< BATCH_SIZE-1; i++)
            {
                subject.reportHLCHit( i + DEFAULT_MBID , i + roundStart);
                assertEquals(monitorMock.reportedHitCount,((round-1) *100));
            }
            subject.reportHLCHit((BATCH_SIZE-1) + DEFAULT_MBID,
                                 (BATCH_SIZE-1) + roundStart);

            for(int i=0; i< BATCH_SIZE; i++)
            {
                assertEquals(monitorMock.lastHLCHitReport[i],
                        i + roundStart);
                assertEquals(monitorMock.lastDomIDReport[i],
                        i + DEFAULT_MBID);
            }
        }

    }

    @Test
    public void testFlushing()
    {
        //
        // test flushing
        //
        // flush per hit report
        for(int i=0; i< BATCH_SIZE * 5; i++)
        {
            subject.reportHLCHit(DEFAULT_MBID, i);
            subject.flush();
            assertEquals(monitorMock.reportedHitCount, i+1);
            assertArrayEquals(new long[]{i}, monitorMock.lastHLCHitReport);
        }

        // flush when empty
        long lastCount = monitorMock.reportedHitCount;
        subject.flush();
        Assert.assertEquals(lastCount, monitorMock.reportedHitCount);
        Assert.assertArrayEquals(new long[0], monitorMock.lastHLCHitReport);

        // flush at every point in a batch
        long reported = monitorMock.reportedHitCount;
        for(int i=0; i< BATCH_SIZE; i++)
        {
            long[] partial = new long[i];
            for(int j=0; j<i; j++)
            {
                subject.reportHLCHit(DEFAULT_MBID, j);
                partial[j] = j;
                reported++;
            }
            subject.flush();

            assertEquals(reported, monitorMock.reportedHitCount);
            assertArrayEquals(partial, monitorMock.lastHLCHitReport);
        }

    }


    /**
     * Tests DataStats.TimedAutoFlush
     */
    public static class TimedAutoFlushTest
    {

        int BATCH_SIZE = 100;
        int AUTO_FLUSH_INTERVAL = 1000;
        BatchHLCReporter.TimedAutoFlush subject;
        MockRunMonitor monitorMock;

        @Before
        public void setUp()
        {
            subject = new BatchHLCReporter.TimedAutoFlush(BATCH_SIZE,
                    AUTO_FLUSH_INTERVAL);
            monitorMock = new MockRunMonitor();
            subject.setRunMonitor(monitorMock);
        }

        @Test
        public void testBatching()
        {
            //
            // test batching
            //
            for (int round = 1; round<10; round++)
            {
                int roundStart = 999 * round;
                for(int i=0; i< BATCH_SIZE-1; i++)
                {
                    subject.reportHLCHit( i + DEFAULT_MBID , i + roundStart);
                    assertEquals(monitorMock.reportedHitCount,((round-1) *100));
                }
                subject.reportHLCHit((BATCH_SIZE-1) + DEFAULT_MBID,
                        (BATCH_SIZE-1) + roundStart);

                for(int i=0; i< BATCH_SIZE; i++)
                {
                    assertEquals(monitorMock.lastHLCHitReport[i],
                            i + roundStart);
                    assertEquals(monitorMock.lastDomIDReport[i],
                            i + DEFAULT_MBID);
                }
            }

        }

        @Test
        public void testFlushing()
        {
            // flush per hit report
            for(int i=0; i< BATCH_SIZE * 5; i++)
            {
                subject.reportHLCHit(DEFAULT_MBID, i);
                subject.flush();
                assertEquals(monitorMock.reportedHitCount, i+1);
                assertArrayEquals(new long[]{i}, monitorMock.lastHLCHitReport);
            }

            // flush when empty
            long lastCount = monitorMock.reportedHitCount;
            subject.flush();
            Assert.assertEquals(lastCount, monitorMock.reportedHitCount);
            Assert.assertArrayEquals(new long[0], monitorMock.lastHLCHitReport);

            // flush at every point in a batch
            long reported = monitorMock.reportedHitCount;
            for(int i=0; i< BATCH_SIZE; i++)
            {
                long[] partial = new long[i];
                for(int j=0; j<i; j++)
                {
                    subject.reportHLCHit(DEFAULT_MBID, j);
                    partial[j] = j;
                    reported++;
                }
                subject.flush();

                assertEquals(reported, monitorMock.reportedHitCount);
                assertArrayEquals(partial, monitorMock.lastHLCHitReport);
            }

        }

        @Test
        public void testAutoFlushing()
        {
            //
            // test time based auto flush
            //

            // auto lush at every point in a batch
            long reported = monitorMock.reportedHitCount;
            for(int i=2; i< BATCH_SIZE; i++)
            {
                long[] partial = new long[i];
                long beforeCount = monitorMock.reportedHitCount;
                long[] beforeReport = monitorMock.lastHLCHitReport;
                for(int j=0; j<i; j++)
                {
                    assertEquals(beforeCount, monitorMock.reportedHitCount);
                    assertSame(beforeReport, monitorMock.lastHLCHitReport);

                    long val;
                    if(j == (i-1))
                    {
                        val = AUTO_FLUSH_INTERVAL;
                    }
                    else
                    {
                        val = j;
                    }
                    subject.reportHLCHit(DEFAULT_MBID, val);
                    partial[j] = val;
                    reported++;
                }

                assertEquals(reported, monitorMock.reportedHitCount);
                assertArrayEquals(partial, monitorMock.lastHLCHitReport);
            }
        }

    }


    static class MockRunMonitor implements IRunMonitor
    {

        long[] lastDomIDReport;
        long[] lastHLCHitReport;
        long reportedHitCount;

        @Override
        public void countHLCHit(final long domID[], final long[] utc)
        {
            assertTrue(domID.length == utc.length);

            reportedHitCount+= utc.length;
            lastDomIDReport = domID;
            lastHLCHitReport = utc;
        }

        @Override
        public Iterable<DOMInfo> getConfiguredDOMs()
        {
            return null;
        }

        @Override
        public DOMInfo getDom(final long mbid)
        {
            return null;
        }

        @Override
        public String getStartTimeString()
        {
            return null;
        }

        @Override
        public String getStopTimeString()
        {
            return null;
        }

        @Override
        public int getString()
        {
            return 0;
        }

        @Override
        public boolean isRunning()
        {
            return false;
        }

        @Override
        public void join() throws InterruptedException
        {
        }

        @Override
        public void push(final long mbid, final Isochron isochron)
        {
        }

        @Override
        public void pushException(final int string, final int card,
                                  final GPSException exception)
        {
        }

        @Override
        public void pushException(final long mbid, final RAPCalException exc,
                                  final TimeCalib tcal)
        {
        }

        @Override
        public void pushGPSMisalignment(final int string, final int card,
                                        final GPSInfo oldGPS,
                                        final GPSInfo newGPS)
        {
        }

        @Override
        public void pushGPSProcfileNotReady(final int string, final int card)
        {
        }

        @Override
        public void pushWildTCal(final long mbid, final double cableLength,
                                 final double averageLen)
        {
        }

        @Override
        public void sendMoni(final String varname,
                             final Alerter.Priority priority,
                             final IUTCTime utc,
                             final Map<String, Object> map,
                             final boolean addString)
        {
        }

        @Override
        public void setConfiguredDOMs(final Collection<DOMInfo> configuredDOMs)
        {
        }

        @Override
        public void setRunNumber(final int runNumber)
        {
        }

        @Override
        public void start()
        {
        }

        @Override
        public void stop()
        {
        }

    }
}
