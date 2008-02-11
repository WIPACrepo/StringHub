package icecube.daq.domapp;

import java.nio.ByteBuffer;

/**
 * Base monitor record class.
 * @author krokodil
 */
public class MonitorRecord {

	protected short recl;
	protected short fmtId;
	protected long domclk;
	protected ByteBuffer record;

	public MonitorRecord(ByteBuffer buf)
	{
		buf.mark();
		int pos = buf.position();
		int limit = buf.limit();
		recl = buf.getShort();
		fmtId = buf.getShort();
		domclk = DOMAppUtil.decodeSixByteClock(buf);
		record = ByteBuffer.allocate(recl);
		buf.reset();
		buf.limit(pos + recl);
		record.put(buf).flip();
		buf.limit(limit);
	}

	public int getLength() { return recl; }
	public int getType() { return fmtId; }
	public long getClock() { return domclk; }
	public ByteBuffer getBuffer() { return (ByteBuffer) record; }

}
