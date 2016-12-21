package icecube.daq.time.monitoring;

import icecube.daq.dor.GPSInfo;
import icecube.daq.util.Leapseconds;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Utility classes and methods for testing the clock monitor.
 */
public class TestUtilities
{
    final static Charset US_ASCII = Charset.forName("US-ASCII");

    /**
     * Number of DOR ticks in a millisecond period
     */
    static long millisAsDor(long millis)
    {
        return millis * 20000;
    }

    /**
     * Number of millis in a nanosecond period
     */
    static long millisAsNano(long millis)
    {
        return millis * 1000000;
    }

    /**
     * Discovers the current year.
     * @return The current year.
     */
    static int discoverCurrentYear()
    {
        TimeZone utc_zone = TimeZone.getTimeZone("GMT");
        Calendar now = Calendar.getInstance(utc_zone);
        int year = now.get(Calendar.YEAR);
        return year;
    }

    /**
     * A point in time of the current year that also provides a coincident
     * representation in milliseconds since the unix epoch.
     */
    static class PointInTime
    {
        final long epochTimeMillis;
        final String GPSString;

        /**
         * Make a point in time in the current year.
         */
        PointInTime(int day, int hour, int min, int sec)
        {
            this(discoverCurrentYear(), day, hour, min, sec);
        }

        /**
         * Make a point in time in a specific year.
         */
        PointInTime(int year, int day, int hour, int min, int sec)
        {
            GPSString = fill("000", day) + ":" + fill("00", hour) +
                    ":" + fill("00", min) + ":" + fill("00", sec);

            GregorianCalendar now =
                    new GregorianCalendar(year, Calendar.JANUARY, 1, 0, 0, 0);
            now.setTimeZone(TimeZone.getTimeZone("GMT"));
            now.set(GregorianCalendar.MONTH, 0);
            now.set(GregorianCalendar.DAY_OF_MONTH, 1);
            now.set(GregorianCalendar.HOUR_OF_DAY, 0);
            now.set(GregorianCalendar.MINUTE, 0);
            now.set(GregorianCalendar.SECOND, 0);

            now.add(Calendar.DAY_OF_MONTH, day - 1);
            now.add(Calendar.HOUR_OF_DAY, hour);
            now.add(Calendar.MINUTE, min);
            now.add(Calendar.SECOND, sec);

            epochTimeMillis = now.getTime().getTime();
        }

        private String fill(String base, int data)
        {
            byte[] target = base.getBytes(US_ASCII);
            byte[] source = Integer.toString(data).getBytes(US_ASCII);

            int c = 1;
            for (int i = source.length - 1; i >= 0; i--)
            {
                target[target.length - c] = source[i];
                c++;
            }

            return new String(target);
        }
    }

    static ClockProcessor.NTPMeasurement generateNTPMeasurement(
            long ntpSystemTime,
            long monotonicTime)
    {
        return generateNTPMeasurement(ntpSystemTime,
                monotonicTime,
                0,
                50);
    }

    static ClockProcessor.NTPMeasurement generateNTPMeasurement(
            long ntpSystemTime,
            long monotonicTime,
            double sytstemClockOffset
            )
    {
        return generateNTPMeasurement(ntpSystemTime,
            monotonicTime,
            sytstemClockOffset,
            50);
    }

    static ClockProcessor.NTPMeasurement generateNTPMeasurement(
            long ntpSystemTime,
            long monotonicTime,
            double sytstemClockOffset,
            long executionNanos
    )
    {
        return new ClockProcessor.NTPMeasurement("test",
                ntpSystemTime,
                sytstemClockOffset,
                monotonicTime,
                executionNanos);
    }

    /**
     * make a gps snapshot without a leap. The monitoring package should
     * not be affected by the internal leap second handling.
     */
    static ClockProcessor.GPSSnapshot generateGPSSnapshot(
            int card, final String GPSString, byte quality, long dorclock)
    {
        return generateGPSSnapshot(card, GPSString, quality, dorclock, null);
    }

    /**
     * make a gps snapshot with a leapsecond object. The monitoring package
     * should not be affected by the internal leap second handling, but this
     * is tested explicitly in some tests.
     */
    static ClockProcessor.GPSSnapshot generateGPSSnapshot(
            int card, final String GPSString, byte quality, long dorclock,
            Leapseconds leapseconds)
    {
        ByteBuffer buf = ByteBuffer.allocate(22);
        buf.put((byte)0x01);                   // SOH
        buf.put(GPSString.getBytes(US_ASCII)); // ddd:hh:mm:ss
        buf.put(quality);                      // Quality
        buf.asLongBuffer().put(dorclock);      // dor clock
        buf.position(buf.position() + 8);
        buf.flip();

        GPSInfo gpsInfo = new GPSInfo(buf, leapseconds);
        return new ClockProcessor.GPSSnapshot(gpsInfo, card);
    }

    static ClockProcessor.TCALMeasurement generateTCALMeasurement(
            long tcalPointDor, long tcal_point_nano, int card, String cwd )
    {
        return generateTCALMeasurement(tcalPointDor,
                tcal_point_nano, card, cwd, 50000000);
    }

    static ClockProcessor.TCALMeasurement generateTCALMeasurement(
            long tcalPointDor, long tcal_point_nano, int card, String cwd,
            long executuionNanos)
    {
        return new ClockProcessor.TCALMeasurement(tcalPointDor,
                tcal_point_nano, executuionNanos, card, cwd);
    }


    public static void main(String[] args)
    {
        PointInTime t = new PointInTime(11, 3, 59, 03);
        System.out.println(t.GPSString);
        System.out.println(t.epochTimeMillis);
        System.out.println(new Date(t.epochTimeMillis).toGMTString());

        generateGPSSnapshot(1, t.GPSString, (byte)32, 214124);


        t = new PointInTime(2009, 11, 3, 59, 03);
        System.out.println(t.GPSString);
        System.out.println(t.epochTimeMillis);
        System.out.println(new Date(t.epochTimeMillis));
        System.out.println(new Date(t.epochTimeMillis).toGMTString());
    }
}