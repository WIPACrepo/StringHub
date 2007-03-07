package icecube.daq.sender.test;

import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.IDriver;
import icecube.daq.dor.TimeCalib;

import java.io.IOException;
import java.nio.ByteBuffer;

class MockDriver
    implements IDriver
{
    private int day, hour, min, sec, dorClock;

    MockDriver()
    {
    }

    public GPSInfo readGPS(int card)
        throws GPSException
    {
        final byte startOfHeader = 1;
        final byte quality = 1;

        StringBuffer buf = new StringBuffer();

        String[] timeStr = new String[] {
            Integer.toString(day),
            Integer.toString(hour),
            Integer.toString(min),
            Integer.toString(sec),
        };

        for (int i = 0; i < timeStr.length; i++) {
            final int endLen = (i + 1) * 3;

            final int addChars = endLen - (buf.length() + timeStr[i].length());
            for (int j = 0; j < addChars; j++) {
                char ch;
                if (i == 0 || j > 0) {
                    buf.append('0');
                } else if (i < 2) {
                    buf.append(' ');
                } else {
                    buf.append(':');
                }
            }

            buf.append(timeStr[i]);
        }

        ByteBuffer bb = ByteBuffer.allocate(22);
        bb.put(startOfHeader);
        bb.put(buf.toString().getBytes());
        bb.put(quality);
        bb.putLong(dorClock);

        bb.flip();

        return new GPSInfo(bb);
    }

    public TimeCalib readTCAL(int card, int pair, char dom)
        throws IOException, InterruptedException
    {
        return null;
    }

    public void softboot(int card, int pair, char dom)
        throws IOException
    {
        // do nothing
    }
}
