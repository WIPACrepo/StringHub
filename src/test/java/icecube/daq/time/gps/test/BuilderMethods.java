package icecube.daq.time.gps.test;

import icecube.daq.dor.GPSInfo;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 *
 */
public class BuilderMethods
{
    private static final Charset US_ASCII = Charset.forName("US-ASCII");

    private static final byte SOH = (byte)0x01;

    public static final byte VERY_GOOD = (byte)' ';
    public static final byte GOOD = (byte)'.';
    public static final byte AVERAGE = (byte)'*';
    public static final byte BAD = (byte)'#';
    public static final byte VERY_BAD = (byte)'?';

    public static GPSInfo generateGPSInfo(String gps, long dorclock)
    {

        return generateGPSInfo(SOH, gps, VERY_GOOD, dorclock);
    }

    public static GPSInfo generateGPSInfo(final byte soh, final String gps,
                                   final byte quality, final long dorclock)
    {

        ByteBuffer bb = ByteBuffer.allocate(22);

        bb.put(soh);
        bb.put(gps.getBytes(US_ASCII));
        bb.put(quality);
        bb.putLong(dorclock);
        bb.flip();

        return new GPSInfo(bb, null);
    }
}
