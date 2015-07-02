package icecube.daq.dor;

import icecube.daq.util.Leapseconds;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests GPS Info
 */
public class GPSInfoTest
{

    final static Charset US_ASCII = Charset.forName("US-ASCII");

    byte SOH = (byte)0x01;

    byte QUALITY_VERY_GOOD = ' ';
    byte GOOD = '.';
    byte AVERAGE = '*';
    byte BAD = '#';
    byte VERY_BAD = '?';

    long TENTH_NANO_PER_SECOND = 10000000000L;
    long TENTH_NANO_PER_DOR = 500L;


    @Test
    public void testConstructFromBuffer()
    {
        //
        // test the construction of GPSInfo instances from a
        // buffer.
        //
        long DOR = 1234566L;
        String GPS_STRING = "001:02:03:04";
        long ICL_TICKS = (((((0*24) + 2) * 60 + 3) * 60) + 4) * TENTH_NANO_PER_SECOND;

        ByteBuffer originalBuffer = generateGPSBuffer(SOH, GPS_STRING,
                QUALITY_VERY_GOOD, DOR);
        GPSInfo subject = new GPSInfo(originalBuffer, null);

        // test parsed fields
        assertEquals("day", 1, subject.getDay());
        assertEquals("hour", 2, subject.getHour());
        assertEquals("min", 3, subject.getMin());
        assertEquals("sec", 4, subject.getSecond());

        assertEquals("quality", QUALITY_VERY_GOOD, subject.getQuality());

        assertEquals("DOR", DOR, subject.getDorclk());
        assertEquals("GPS", GPS_STRING, subject.getTimestring());
        assertEquals("LEAP", 0, subject.getLeapSecondAdjustment());

        //test offset calculation
        long PREDICTED = ICL_TICKS - (DOR * TENTH_NANO_PER_DOR);
        assertEquals("offset", PREDICTED, subject.getOffset().in_0_1ns());


        //test value out
        ByteBuffer roundTripBuffer = subject.getBuffer();
        originalBuffer.rewind();
        assertTrue("Round trip buffer", originalBuffer.equals(roundTripBuffer));
    }


    @Test
    public void testBadInput()
    {
        //
        // Test input data that should except in construction
        //

        // test bad SOH marks
        for(int i=0; i<256; i++)
        {
            byte soh = (byte)(i&0xff);
            if(soh == SOH){continue;}
            try
            {
                ByteBuffer buf = generateGPSBuffer(soh, "001:02:03:04",
                        QUALITY_VERY_GOOD, 51251245L);
                GPSInfo subject = new GPSInfo(buf, null);
                fail("Accepted bad SOH mark [" + soh + "]");
            }
            catch (Exception e)
            {
                //desired
            }
        }

        //test bad gps strings
        String[] badGPSStrings = {
                "",
                "xxx:yy:zz:aa",
                "123:45:55:1a",
                "111:22:3b:44",
                "001:0c:33:44",
                "123:45:55:6",
                "12:34:56:55"
        };
        for (int i = 0; i < badGPSStrings.length; i++)
        {
            try
            {
                String GPS = badGPSStrings[i];
                ByteBuffer buf = generateGPSBuffer(SOH, GPS,
                        QUALITY_VERY_GOOD, 51251245L);
                GPSInfo subject = new GPSInfo(buf, null);
                fail("Accepted bad GPS [" + GPS + "]");
            }
            catch (Exception e)
            {
                //desired
            }
        }
    }

    @Test
    public void testLeapAdjustments()
    {
        Leapseconds leapseconds = new MyLeapseconds(2015);

        long DOR = 32887251245124L;
//        long DOR = 0L;

        long LEAP_TICKS = 10000000000L;
        long ONE_SECOND_DOR_TICKS = 20000000L;

        String GPS_BEFORE_LEAP = "181:23:59:59";
        long ICL_BEFORE_LEAP_TICKS = (((((180*24) + 23) * 60 + 59) * 60) + 59) * TENTH_NANO_PER_SECOND;

        String GPS_AT_LEAP = "181:23:59:60";
        String GPS_AFTER_LEAP = "182:00:00:00";


        // test that the offset remained constant through the leap second
        {
        long PREDICTED = ICL_BEFORE_LEAP_TICKS - (DOR * TENTH_NANO_PER_DOR);
        GPSInfo gpsInfo = new GPSInfo(generateGPSBuffer(SOH, GPS_BEFORE_LEAP,
                QUALITY_VERY_GOOD, DOR), leapseconds);
        assertEquals("unexpected adjustment", 0, gpsInfo.getLeapSecondAdjustment());
        assertEquals("before leap offset", PREDICTED, gpsInfo.getOffset().in_0_1ns());

        DOR += ONE_SECOND_DOR_TICKS;
        gpsInfo = new GPSInfo(generateGPSBuffer(SOH, GPS_AT_LEAP,
                QUALITY_VERY_GOOD, DOR), leapseconds);
        assertEquals("unexpected adjustment", 0, gpsInfo.getLeapSecondAdjustment());
        assertEquals("at leap offset", PREDICTED, gpsInfo.getOffset().in_0_1ns());

        DOR += ONE_SECOND_DOR_TICKS;
        gpsInfo = new GPSInfo(generateGPSBuffer(SOH, GPS_AFTER_LEAP,
                QUALITY_VERY_GOOD, DOR), leapseconds);
        assertEquals("expected adjustment", 1, gpsInfo.getLeapSecondAdjustment());
        assertEquals("at leap offset", PREDICTED, gpsInfo.getOffset().in_0_1ns());
        }

        // run without the leapsecond nist adjustment and verify that the
        // offset shifts by one second.
        //
        // this test "tests the test"
        {
        long PREDICTED = ICL_BEFORE_LEAP_TICKS - (DOR * TENTH_NANO_PER_DOR);
        GPSInfo gpsInfo = new GPSInfo(generateGPSBuffer(SOH, GPS_BEFORE_LEAP,
                QUALITY_VERY_GOOD, DOR), null);
        assertEquals("unexpected adjustment", 0, gpsInfo.getLeapSecondAdjustment());
        assertEquals("before leap offset", PREDICTED, gpsInfo.getOffset().in_0_1ns());

        DOR += ONE_SECOND_DOR_TICKS;
        gpsInfo = new GPSInfo(generateGPSBuffer(SOH, GPS_AT_LEAP,
                QUALITY_VERY_GOOD, DOR), null);
        assertEquals("unexpected adjustment", 0, gpsInfo.getLeapSecondAdjustment());
        assertEquals("at leap offset", PREDICTED, gpsInfo.getOffset().in_0_1ns());

        DOR += ONE_SECOND_DOR_TICKS;
        gpsInfo = new GPSInfo(generateGPSBuffer(SOH, GPS_AFTER_LEAP,
                QUALITY_VERY_GOOD, DOR), null);
        assertEquals("expected adjustment", 0, gpsInfo.getLeapSecondAdjustment());
        assertEquals("at leap offset", PREDICTED-LEAP_TICKS, gpsInfo.getOffset().in_0_1ns());
        }


    }

    /**
     * generate a buffer containing the input for a GPSInfo instance.
     */
    static ByteBuffer generateGPSBuffer(final byte SOH,
                                        final String GPSString,
                                        byte quality,
                                        long dorclock)
    {
        ByteBuffer buf = ByteBuffer.allocate(22);
        buf.put(SOH);                          // SOH
        buf.put(GPSString.getBytes(US_ASCII)); // ddd:hh:mm:ss
        buf.put(quality);                      // Quality
        buf.asLongBuffer().put(dorclock);      // dor clock
        buf.position(buf.position() + 8);
        buf.flip();

        return buf;
    }

    /**
     * Leapseconds wrapper to generate a leapsecond instance
     * for a targeted year.
     */
    private static class MyLeapseconds extends Leapseconds
    {
        private static String NIST_FILE =
                Leapseconds.getConfigDirectory() + "/nist/leapseconds-latest";
        private MyLeapseconds(final int year) throws IllegalArgumentException
        {
            super(NIST_FILE, year);
        }
    }

}
