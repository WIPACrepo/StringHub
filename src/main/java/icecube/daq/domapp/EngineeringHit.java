package icecube.daq.domapp;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

public class EngineeringHit {

	private int 	format;
	private int 	atwdChip;
	private int 	triggerFlags;
	private long 	domclk;
	private short[] fadc;
	private short[][] atwd;

	public enum Trigger { TEST, CPU, SPE, FLASHER }

	private static final Logger logger = Logger.getLogger(EngineeringHit.class);

	/**
	 * Decode an engineering hit from a byte buffer
	 * @param buf
	 */
	public EngineeringHit(ByteBuffer buf) {
	    int pos = buf.position();
		short len = buf.getShort(pos);
		format = buf.getShort(pos+2);
		atwdChip = buf.get(pos+4);
		EngineeringRecordFormat engRecFmt = new EngineeringRecordFormat(
			buf.get(pos+5), buf.get(pos+6), buf.get(pos+7)
			);
		if (logger.isDebugEnabled())
			logger.debug("Decode recl = " + len + " (" + engRecFmt.fadcSamples() + ", "
				+ engRecFmt.atwdSamples(0) + ", " + engRecFmt.atwdSamples(1) + ", "
				+ engRecFmt.atwdSamples(2) + ", " + engRecFmt.atwdSamples(3) + ")");
		triggerFlags = buf.get(pos+8);
		domclk = DOMAppUtil.decodeClock6B(buf, pos+10);
		buf.position(pos+16);
		fadc = new short[engRecFmt.fadcSamples()];
		atwd = new short[4][];

		for (int i = 0; i < engRecFmt.fadcSamples(); i++) fadc[i] = buf.getShort();
		for (int ch = 0; ch < 4; ch++) 
		{
			atwd[ch] = new short[engRecFmt.atwdSamples(ch)];
			for (int i = 0; i < engRecFmt.atwdSamples(ch); i++) 
			{
				if (engRecFmt.atwdWordsize(ch) == 1) 
				{
					atwd[ch][i] = buf.get();
					if (atwd[ch][i] < 0) atwd[ch][i] += 256;
				}
				else
				{
					atwd[ch][i] = buf.getShort();
				}
			}
		}
	}

	/**
	 * Get the 48-bit dom clock value
	 * @return 48-bit dom clock - 25 ns ticks.
	 */
	public long getClock() { return domclk; }

	public Trigger getTrigger() { return Trigger.values()[triggerFlags & 3]; }

	public short[] getAtwd(int ch) { return atwd[ch]; }

	public short[] getFadc() { return fadc; }

	public int getChip() { return atwdChip; }

}
