package icecube.daq.time.gps;

import icecube.daq.dor.GPSInfo;
import icecube.daq.monitoring.IRunMonitor;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * An implementation of the GPS service for use with test setups, such as
 * hubs without GPS hardware.
 * <p>
 * The service will provide GPS info with the time set to the ICL epoch of
 * Midnight Jan 1st of the current year and a DOR offset of zero.
 *
 */
class NullGPSService implements IGPSService
{

    private final static Charset US_ASCII = Charset.forName("US-ASCII");

    private final static GPSInfo NULL_GPS_INFO = buildGPSInfo();


    @Override
    public GPSInfo getGps(final int card)  throws GPSServiceError
    {
        return NULL_GPS_INFO;
    }

    @Override
    public void startService(final int card)
    {
        //nop
    }

    @Override
    public void shutdownAll()
    {
        //nop
    }

    @Override
    public boolean waitForReady(final int card, final int waitMillis)
            throws InterruptedException
    {
        return true;
    }

    @Override
    public void setRunMonitor(final IRunMonitor runMonitor)
    {
        //nop
    }

    @Override
    public void setStringNumber(final int string)
    {
        //nop
    }

    /**
     * Constructs the null GPSInfo.
     * @return A GPSInfo instance with default settings.
     */
    private static GPSInfo buildGPSInfo()
    {

        ByteBuffer buf = ByteBuffer.allocate(22);
        buf.put((byte)1);                            // SOH
        buf.put("001:00:00:00".getBytes(US_ASCII));  // GPS string
        buf.put((byte)' ');                          // Quality marker
        buf.putLong(0L);                             // DOR clock
        buf.flip();
        return new GPSInfo(buf, null);
    }

}
