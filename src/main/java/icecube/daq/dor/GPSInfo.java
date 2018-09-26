package icecube.daq.dor;

import icecube.daq.util.UTC;
import icecube.daq.util.Leapseconds;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

public class GPSInfo {
    private final String timestring;
    private final int day, hour, min, sec;
    private final int leapSecondAdjustment;
    private final int quality;
    private final long dorclk;
    private final UTC offset;
    private final ByteBuffer record;
    private static final Logger logger = Logger.getLogger(GPSInfo.class);

    public GPSInfo(ByteBuffer buf, Leapseconds leapObj) {
	buf.mark();
	byte[] timestringbytes = new byte[12];
	if (buf.get() != 1) throw new IllegalArgumentException("GPS record does not begin with SOH");
	buf.get(timestringbytes);
	timestring = new String(timestringbytes);
	try
	    {
		day  = Integer.parseInt(timestring.substring(0, 3));
		hour = Integer.parseInt(timestring.substring(4, 6));
		min  = Integer.parseInt(timestring.substring(7, 9));
		sec  = Integer.parseInt(timestring.substring(10, 12));
	    }
	catch (NumberFormatException nex)
	    {
		logger.warn("Failed to parse GPS timestring " + timestring);
		throw nex;
	    }

    // Determine if a leap second adjustment needs to be added into
    // the offset. In years with a leap second, the ICL point-in-time
    // (tenths of nanos since the start of the year) of a gps timestring
    // is one second later for all time strings occurring after the leap
    // second.
	if (leapObj!=null) {
        leapSecondAdjustment = leapObj.getLeapOffset(day);
	}
        else
    {
        leapSecondAdjustment = 0;
    }

	quality = buf.get();
	dorclk  = buf.getLong();
	offset = new UTC(10000000000L * (60 * (60 * (24 * (day-1) + hour) + min) + sec + leapSecondAdjustment) - 500 * dorclk);
	int limit = buf.limit();
	buf.limit(buf.position());
	buf.reset();
	record = ByteBuffer.allocate(buf.remaining());
	record.put(buf);
	record.flip();
	buf.limit(limit);
    }

    /**
     * Usage Note: Day, hour, min and second some directly from the
     *             master clock. Their value is not modified
     *             by the leap second adjustments.
     *
     *             Notably, you can not use these fields to directly
     *             calculate an ICL point-in-time. To calculate an ICL
     *             point in time from (day,hour,min,second) you will
     *             need to use (day,hour,min,sec,leapSecondAdjustment)
     *
     */
    public int getDay() { return day; }
    public int getHour() { return hour; }
    public int getMin() { return min; }
    public int getSecond() { return sec; }
    public int getLeapSecondAdjustment() { return leapSecondAdjustment; }

    public int getQuality() { return quality; }

    public UTC getOffset() { return offset; }

    /** provide access to raw internals. */
    public String getTimestring() { return timestring; }
    public long getDorclk() { return dorclk; }

    /**
     * This method returns a read-only view of the
     * underlying 22-byte GPS record.
     * @return readonly ByteBuffer
     */
    public ByteBuffer getBuffer() { return record.asReadOnlyBuffer(); }

    public String toString() 
    {
	return timestring + " : Quality = " + quality +
	    " DOR clk: " + dorclk + " GPS offset: " + offset;
    }

}
