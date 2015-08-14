package icecube.daq.rapcal;

import icecube.daq.dor.TimeCalib;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Utility methods for static import into tests.
 */
public class BuildTCalMethods
{
    static TimeCalib buildTimeCalib(long[] tcal)
    {
        return buildTimeCalib(tcal[0], tcal[1], tcal[2], tcal[3]);
    }

    static TimeCalib buildTimeCalib(long[] tcal, short[] dorwf, short[] domwf)
    {
        return buildTimeCalib(tcal[0], tcal[1], tcal[2], tcal[3], dorwf, domwf);
    }

    static TimeCalib buildTimeCalib(long dorTx, long dorRx,
                                    long domRx, long domTx)
    {
        short[] padded = new short[64];
        Arrays.fill(padded, (short) 0xff);
        return buildTimeCalib(dorTx, dorRx, domRx, domTx, padded, padded);
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
