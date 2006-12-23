package icecube.daq.domapp;

import java.nio.ByteBuffer;

public class SupernovaPacket 
{
	private int recl;
	private int fmtid;
	private long domClock;
	private byte[] counters;
	
	private SupernovaPacket() { }
	
	public static SupernovaPacket createFromBuffer(ByteBuffer buf)
	{
		SupernovaPacket sn = new SupernovaPacket();
		sn.recl = buf.getShort();
		sn.fmtid = buf.getShort();
		assert sn.fmtid == 300;
		sn.domClock = DOMAppUtil.decodeSixByteClock(buf);
		int n = sn.recl - 10;
		sn.counters = new byte[n];
		for (int i = 0; i < n; i++) sn.counters[i] = buf.get();
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
}
