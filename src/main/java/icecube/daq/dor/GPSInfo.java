package icecube.daq.dor;

import icecube.daq.util.UTC;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

public class GPSInfo {
	private String timestring;
	private int day, hour, min, sec;
	private int quality;
	private long dorclk;
	private UTC offset;
	private ByteBuffer record;
	private final static Logger logger = Logger.getLogger(GPSInfo.class);
	
	public GPSInfo(ByteBuffer buf) {
		buf.mark();
		byte[] timestringbytes = new byte[12];
		if (buf.get() != 1) throw new IllegalArgumentException("GPS record does not begin with SOH");
		buf.get(timestringbytes);
		timestring = new String(timestringbytes);
		try
		{
			day  = Integer.valueOf(timestring.substring(0, 3)).intValue();
			hour = Integer.valueOf(timestring.substring(4, 6)).intValue();
			min  = Integer.valueOf(timestring.substring(7, 9)).intValue();
			sec  = Integer.valueOf(timestring.substring(10, 12)).intValue();
		}
		catch (NumberFormatException nex)
		{
			logger.warn("Failed to parse GPS timestring " + timestring);
			throw nex;
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
	
	public int getQuality() { return quality; }
	
	public UTC getOffset() { return offset; }
	
	/**
	 * This method returns a read-only view of the 
	 * underlying 22-byte GPS record.
	 * @return readonly ByteBuffer
	 */
	public ByteBuffer getBuffer() { return record.asReadOnlyBuffer(); }
	
	public String toString() {
		return timestring + " : Quality = " + quality + "  : " + offset;
	}
	
}
