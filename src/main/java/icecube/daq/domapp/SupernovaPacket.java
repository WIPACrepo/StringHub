package icecube.daq.domapp;

import java.nio.ByteBuffer;

public final class SupernovaPacket
{
	private int recl;
	//private int fmtid;
	private long domClock;
	private byte[] counters;
	private ByteBuffer buffer;

	private SupernovaPacket() { }

	public static SupernovaPacket createFromBuffer(ByteBuffer buf)
	{
		int limit = buf.limit();
		int pos = buf.position();
		SupernovaPacket sn = new SupernovaPacket();
		sn.recl = buf.getShort(pos);
		//sn.fmtid = buf.getShort(pos+2);
		sn.domClock = DOMAppUtil.decodeClock6B(buf, pos+4);
		int n = sn.recl - 10;
		sn.counters = new byte[n];
		for (int i = 0; i < n; i++) sn.counters[i] = buf.get(pos+10+i);
		buf.limit(pos + sn.recl);
		sn.buffer = ByteBuffer.allocate(sn.recl);
		sn.buffer.put(buf);
		sn.buffer.flip();
		buf.limit(limit);
		return sn;
	}

	/**
	 * Get the DOM clock.
	 * @return the DOM clock at the left edge of the first bin
	 * in the array of scalers.
	 */
	public long getClock() { return domClock; }

	/**
	 * Get the record length.
	 * @return the record length in bytes.
	 */
	public int getLength() { return recl; }

	/**
	 * Get direct access to the array of SN scalers for this record.
	 * @return byte array of SN scalers - each in range [0:15] where
	 * 15 means 15 or greater (overflow).
	 */
	public byte[] getScalers() { return counters; }

	public ByteBuffer getBuffer() {
		// TODO Auto-generated method stub
		return buffer;
	}
}
