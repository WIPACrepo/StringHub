package icecube.daq.dor;

import icecube.daq.util.UTC;
import icecube.daq.util.Leapseconds;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

public class GPSInfo {
    private String timestring;
    private int day, hour, min, sec;
    private int quality;
    private long dorclk;
    private UTC offset;
    private ByteBuffer record;
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

	if (leapObj!=null) {
	    sec = sec + (int)leapObj.get_leap_offset(day);
	}
	quality = buf.get();
	dorclk  = buf.getLong();
	offset = new UTC(10000000000L * (60 * (60 * (24 * (day-1) + hour) + min) + sec) - 500 * dorclk);
	int limit = buf.limit();
	buf.limit(buf.position());
	buf.reset();
	record = ByteBuffer.allocate(buf.remaining());
	record.put(buf);
	record.flip();
	buf.limit(limit);
    }

    public int getDay() { return day; }
    public int getHour() { return hour; }
    public int getMin() { return min; }
    public int getSecond() { return sec; }
    
    public int getQuality() { return quality; }
    
    public UTC getOffset() { return offset; }
    
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
