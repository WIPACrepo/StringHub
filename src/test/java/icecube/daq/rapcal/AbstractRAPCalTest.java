package icecube.daq.rapcal;

import icecube.daq.dor.TimeCalib;
import icecube.daq.util.UTC;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

class MyRAPCal
    extends AbstractRAPCal
{
    MyRAPCal()
    {
        super();
    }

    double getFineTimeCorrection(short[] w)
        throws RAPCalException
    {
        return 0.0;
    }
}

public class AbstractRAPCalTest
{
    public static final long ONE_SECOND = 12345678901234L;

    TimeCalib buildTimeCalib(long dorTx, long dorRx, long domRx, long domTx)
    {
        ByteBuffer buf = ByteBuffer.allocate(292);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        final short pad = (short) 0xff;
        buf.putShort(pad);
        buf.putShort(pad);

        buf.putLong(dorTx);
        buf.putLong(dorRx);
        for (int i = 0; i < 64; i++) {
            buf.putShort(pad);
        }

        buf.putLong(domRx);
        buf.putLong(domTx);
        for (int i = 0; i < 64; i++) {
            buf.putShort(pad);
        }

        buf.flip();

        return new TimeCalib(buf);
    }

    @BeforeClass
    public static void setupLogging()
    {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
    }

    @Test
    public void testDefault()
    {
        AbstractRAPCal rc = new MyRAPCal();

        assertTrue("Unexpected average cable length " +
                   rc.getAverageCableLength(),
                   Double.isNaN(rc.getAverageCableLength()));
        assertEquals("Unexpected last cable length " + rc.getLastCableLength(),
                     rc.getLastCableLength(), 0.0, 0.000000001);
        assertTrue("Unexpected cable length " + rc.cableLength(),
                   Double.isNaN(rc.cableLength()));
        assertTrue("Unexpected clock ratio " + rc.clockRatio(),
                   Double.isNaN(rc.clockRatio()));

        long domclk = 12345678900000L;
        assertFalse("Unexpected laterThan(" + domclk + ")",
                    rc.laterThan(domclk));

        UTC utc = rc.domToUTC(domclk);
        assertNull("domToUTC(" + domclk + ") returned " + utc +
                   " instead of null", utc);
    }

    @Test
    public void testUpdate()
        throws RAPCalException
    {
        AbstractRAPCal rc = new MyRAPCal();

        final long oneHundredDays = 100 * 24 * 3600 * ONE_SECOND;

        long dorTx = oneHundredDays;
        long domRx = dorTx + 4444L;
        long domTx = domRx + 123L;
        long dorRx = domTx + 3690L;

        TimeCalib tcal;
        long domclk;
        UTC utc;

        tcal = buildTimeCalib(dorTx, dorRx, domRx, domTx);

        rc.update(tcal, new UTC(0L));

        assertTrue("Unexpected average cable length " +
                   rc.getAverageCableLength(),
                   Double.isNaN(rc.getAverageCableLength()));
        assertEquals("Unexpected last cable length " + rc.getLastCableLength(),
                     rc.getLastCableLength(), 0.0, 0.000000001);
        assertTrue("Unexpected cable length " + rc.cableLength(),
                   Double.isNaN(rc.cableLength()));
        assertTrue("Unexpected clock ratio " + rc.clockRatio(),
                   Double.isNaN(rc.clockRatio()));

        assertFalse("Unexpected laterThan(" + (dorTx - ONE_SECOND) + ")",
                    rc.laterThan((dorTx - ONE_SECOND) / 250L));
        assertFalse("Unexpected laterThan(" + (dorRx + ONE_SECOND) + ")",
                    rc.laterThan((dorRx + ONE_SECOND) / 250L));

        utc = rc.domToUTC(dorRx);
        assertNull("domToUTC(" + dorRx + ") returned " + utc +
                   " instead of null", utc);

        long dorTx2 = dorRx + ONE_SECOND * 30;
        long domRx2 = dorTx2 + 4444L;
        long domTx2 = domRx2 + 123L;
        long dorRx2 = domTx2 + 3690L;

        tcal = buildTimeCalib(dorTx2, dorRx2, domRx2, domTx2);

        rc.update(tcal, new UTC(0L));

        assertEquals("Unexpected average cable length " +
                     rc.getAverageCableLength(),
                     rc.getAverageCableLength(), 0.00020335, 0.00000001);
        assertEquals("Unexpected last cable length " + rc.getLastCableLength(),
                     rc.getLastCableLength(),    0.00020335, 0.00000001);
        assertEquals("Unexpected cable length " + rc.cableLength(),
                     rc.cableLength(), 0.00020335, 0.00000001);
        assertEquals("Unexpected clock ratio " + rc.clockRatio(),
                     rc.clockRatio(), 0.0, 0.00000001);

        domclk = dorRx2 + 10000000000L;
        assertFalse("Unexpected laterThan(" + domclk + ")",
                    rc.laterThan(domclk));
        assertTrue("Unexpected laterThan(" + dorTx + ")",
                    rc.laterThan(dorTx));

        utc = rc.domToUTC(domclk);
        assertNotNull("domToUTC(" + dorRx + ") returned null instead of " +
                      utc, utc);
    }
}
