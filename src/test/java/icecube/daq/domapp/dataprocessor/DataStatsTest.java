package icecube.daq.domapp.dataprocessor;

import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.TimeCalib;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.monitoring.IRunMonitor;
import icecube.daq.payload.IUTCTime;
import icecube.daq.rapcal.Isochron;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.util.DOMInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertSame;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Tests DataStats.java
 */
public class DataStatsTest
{

    DataStats subject;
    long MBID = 123;

    @Before
    public void setUp()
    {
        subject = new DataStats(MBID);
    }

    @Test
    public void testCounters()
    {
        assertEquals(MBID, subject.getMainboardID());
        assertEquals(0, subject.getNumHits());
        assertEquals(-1, subject.getFirstHitTime());
        assertEquals(-1, subject.getLastHitTime());
        assertEquals(0.0, subject.getLCHitRate());
        assertEquals(0.0, subject.getHitRate());
        assertEquals(0, subject.getNumLBMOverflows());
        assertEquals(0, subject.getNumMoni());
        assertEquals(0, subject.getNumSupernova());
        assertEquals(0, subject.getValidRAPCalCount());
        assertEquals(0, subject.getErrorRAPCalCount());
        assertEquals(0.0, subject.getCableLength());
        assertEquals(0.0, subject.getDomFrequencySkew());
        assertEquals(-1, subject.getFirstDORTime());
        assertEquals(-1, subject.getFirstDOMTime());
        assertEquals(-1, subject.getLastDORTime());
        assertEquals(-1, subject.getLastDOMTime());
        assertEquals(0, subject.getLastTcalUT());
        assertEquals(0, subject.getProcessorQueueDepth());
        assertEquals(0, subject.getMaxProcessorQueueDepth());
        assertEquals(0, subject.getDispatcherQueueDepth());
        assertEquals(0, subject.getMaxDispatcherQueueDepth());
        assertEquals(0.0, subject.getAvgHitAcquisitionLatencyMillis());

        long TCAL_UTC = 123456789L;
        double CABLE_LENGTH = 1.34E-7;
        double FREQ_SKEW = 7.95E-14;


        subject.reportLBMOverflow();
        subject.reportClockRelationship(321842112, 123443255243L);
        subject.reportTCAL(buildTimeCalib(24767312917086L, 24767312917465L,
                14613698885L, 14613699497L,
                new short[64], new short[64]),
                TCAL_UTC, CABLE_LENGTH, FREQ_SKEW);

        subject.reportTCALError();
        subject.reportTCALError();

        subject.reportMoni();
        subject.reportMoni();
        subject.reportMoni();
        subject.reportMoni();

        subject.reportSupernova();
        subject.reportSupernova();
        subject.reportSupernova();
        subject.reportSupernova();
        subject.reportSupernova();
        subject.reportSupernova();
        subject.reportSupernova();

        subject.reportHit(true, 321847312, 7324923749234L);
        subject.reportHit(false, 321857312, 7324923849234L);
        subject.reportHit(false, 321867312, 7324923949234L);
        subject.reportHit(true, 321877312, 7324924049234L);
        subject.reportHit(false, 321887312, 7324924149234L);

        subject.reportProcessorQueueDepth(111);
        subject.reportProcessorQueueDepth(5893);
        subject.reportProcessorQueueDepth(4555);
        subject.reportProcessorQueueDepth(19);

        subject.reportDispatcherQueueDepth(1);
        subject.reportDispatcherQueueDepth(4444);
        subject.reportDispatcherQueueDepth(9999);
        subject.reportDispatcherQueueDepth(3);


        assertEquals(MBID, subject.getMainboardID());
        assertEquals(5, subject.getNumHits());
        assertEquals(7324923749234L, subject.getFirstHitTime());
        assertEquals(7324924149234L, subject.getLastHitTime());
        assertEquals(0.0, subject.getLCHitRate());
        assertEquals(0.0, subject.getHitRate());
        assertEquals(1, subject.getNumLBMOverflows());
        assertEquals(4, subject.getNumMoni());
        assertEquals(7, subject.getNumSupernova());
        assertEquals(1, subject.getValidRAPCalCount());
        assertEquals(2, subject.getErrorRAPCalCount());
        assertEquals(CABLE_LENGTH, subject.getCableLength());
        assertEquals(FREQ_SKEW, subject.getDomFrequencySkew());
        assertEquals(24767312917086L, subject.getFirstDORTime());
        assertEquals(14613699497L, subject.getFirstDOMTime());
        assertEquals(24767312917086L, subject.getLastDORTime());
        assertEquals(14613699497L, subject.getLastDOMTime());
        assertEquals(123456789, subject.getLastTcalUT());
        assertEquals(19, subject.getProcessorQueueDepth());
        assertEquals(5893, subject.getMaxProcessorQueueDepth());
        assertEquals(3, subject.getDispatcherQueueDepth());
        assertEquals(9999, subject.getMaxDispatcherQueueDepth());

        // can't test exactly
        assertNotEquals(0.0, subject.getAvgHitAcquisitionLatencyMillis());

    }

    static TimeCalib buildTimeCalib(long dorTx, long dorRx,
                                    long domRx, long domTx,
                                    short[] dorwf, short[] domwf)
    {
        ByteBuffer buf = ByteBuffer.allocate(292);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        final short pad = (short) 0xff;
        buf.putShort(pad);
        buf.putShort(pad);

        buf.putLong(dorTx);
        buf.putLong(dorRx);
        for (int i = 0; i < 64; i++) {
            buf.putShort(dorwf[i]);
        }

        buf.putLong(domRx);
        buf.putLong(domTx);
        for (int i = 0; i < 64; i++) {
            buf.putShort(domwf[i]);
        }

        buf.flip();

        return new TimeCalib(buf);
    }


}
