package icecube.daq.domapp;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.lang.Math;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class DeltaCompressedHit 
{
	private EnumSet<TriggerBit> triggerMask;
	private boolean fadcAvailable;
	private boolean atwdAvailable;
	private int	atwdChip;
	private long    domClock;
	/** The SLC charge stamp - 3 fADC samples around the peak */
	private short[] chargeStamp;
	/** This is the chargestamp peak sample index */
	private int	peakPosition;
	private short[] fADC;
	private short[][] atwd;
	
	private static final Logger logger = Logger.getLogger(DeltaCompressedHit.class);
	
	private DeltaCompressedHit() 
	{
		triggerMask = EnumSet.noneOf(TriggerBit.class);
		chargeStamp = new short[3];
		fADC = new short[0];
		atwd = new short[4][];
	}
	
	/**
	 * Decode the delta hit inside the ByteBuffer
	 * @param buf the ByteBuffer object holding the object to
	 * be decoded.  The buffer position is affected by the
	 * decode operation
	 * @param clockMSB the MSB of the clockword - gotten from
	 * the buffer header.
	 * @return DeltaCompressedHit object
	 */
	public static DeltaCompressedHit decodeBuffer(ByteBuffer buf, int clockMSB)
	{
		DeltaCompressedHit hit = new DeltaCompressedHit();
		int pos = buf.position();
		int word1 = buf.getInt();
		logger.debug("DeltaHit word0: " + Integer.toHexString(word1));
		assert (word1 & 0x80000000) != 0;
		if ((word1 & 0x00040000) != 0) hit.triggerMask.add(TriggerBit.SPE);
		if ((word1 & 0x00080000) != 0) hit.triggerMask.add(TriggerBit.MPE);
		if ((word1 & 0x00100000) != 0) hit.triggerMask.add(TriggerBit.CPU);
		if ((word1 & 0x00200000) != 0) hit.triggerMask.add(TriggerBit.PULSER);
		if ((word1 & 0x00400000) != 0) hit.triggerMask.add(TriggerBit.LED);
		if ((word1 & 0x00800000) != 0) hit.triggerMask.add(TriggerBit.FLASHER);
		
		hit.fadcAvailable = (word1 & 0x8000) != 0;
		hit.atwdAvailable = (word1 & 0x4000) != 0;
		hit.atwdChip = (word1 & 0x800) >> 11;
		int natwd    = (word1 & 0x3000) >> 12;
		int hitSize  = word1 & 0x7ff;
		hit.domClock = ((long) (clockMSB) << 32) | ((long) buf.getInt() & 0xffffffffL);
		int word2 = buf.getInt();
		boolean peakShift = (word2 & 0x80000000) != 0;
		hit.peakPosition = (word2 >>> 27) & 0x0f;
		hit.chargeStamp[0] = (short) ((word2 >>> 18) & 0x1ff);
		hit.chargeStamp[1] = (short) ((word2 >>> 9) & 0x1ff);
		hit.chargeStamp[2] = (short) (word2 & 0x1ff);
		if (peakShift)
		{
			hit.chargeStamp[0] <<= 1;
			hit.chargeStamp[1] <<= 1;
			hit.chargeStamp[2] <<= 1;
		}
		DeltaMCodec codec = new DeltaMCodec(buf);
		if (hit.fadcAvailable) hit.fADC =codec.decode(256);
		if (hit.atwdAvailable) 
		{
			for (int i = 0; i < natwd+1; i++) hit.atwd[i] = codec.decode(128);
		}
		codec.alignBuffer();
		return hit;
	}
	
	public long getClock() { return domClock; }
	public EnumSet<TriggerBit> triggers() { return triggerMask; }
	public boolean hasFADC() { return fadcAvailable; }
	public boolean hasATWD() { return atwdAvailable; }
	
	public short[] getFADC()
	{
		return fADC;
	}
	
	public short[][] getATWD()
	{
		return atwd;
	}
	
	public int getChip() { return atwdChip; }
	
	public short[] getChargeStamp()
	{
		return chargeStamp;
	}
	
	public int getPeakPosition()
	{
		return peakPosition;
	}
}

/**
 * The 'M' (1-2-3-6-11) delta decoder/encoder
 * @author krokodil
 *
 */
class DeltaMCodec
{
	ByteBuffer buf;
	int bitsPerWord = 3;
	int bitBoundary = 1;
	int bvalid = 0;
	int pos;
	int reg = 0;
	
	static final Logger logger = Logger.getLogger(DeltaMCodec.class);
	
	public DeltaMCodec(ByteBuffer buf)
	{
		this.buf = buf;
		pos = buf.position();
	}

	/**
	 * Decode the next vector of N short integers from the 
	 * compressed buffer.
	 * @param samples length of vector to decode
	 * @return decompressed vector of short ints
	 */
	short[] decode(int samples)
	{
		short last = 0;
		short[] out = new short[samples];
		
		// must reset bit logic
		bitsPerWord = 3;
		bitBoundary = 1;
		
		for (int i = 0; i < samples; i++)
		{
			int word;
			while (true) 
			{
				word = getBits();
				if (word != (1 << (bitsPerWord-1))) break;
				shiftUp();
			}
			if (Math.abs(word) < bitBoundary) 
				shiftDown();
			last += word;
			out[i] = last;
		}
		return out;
	}

	/** 
	 * Align the input ByteBuffer to next 32-bit boundary (relative).
	 * The encoder aligns on 32-bit words - clean up trailing padding.
	 */
	public void alignBuffer()
	{
		// Temp a no-op
		// buf.position(pos + (buf.position() - pos + 3) / 4 * 4);
	}
	
	private void shiftUp() 
	{
		switch (bitsPerWord)
		{
		case 1:
			bitsPerWord = 2;
			bitBoundary = 1;
			break;
		case 2:
			bitsPerWord = 3;
			bitBoundary = 2;
			break;
		case 3:
			bitsPerWord = 6;
			bitBoundary = 4;
			break;
		case 6:
			bitsPerWord = 11;
			bitBoundary = 32;
			break;
		case 11: // null-op
			break;
		}
		logger.debug("shift up - bpw = " + bitsPerWord);
	}

	private void shiftDown()
	{
		switch (bitsPerWord)
		{
		case 1: // null-op
			break;
		case 2:	
			bitsPerWord = 1;
			bitBoundary = 0;
			break;
		case 3:	
			bitsPerWord = 2;
			bitBoundary = 1;
			break;
		case 6:	
			bitsPerWord = 3; 
			bitBoundary = 2;
			break;
		case 11: 
			bitsPerWord = 6;
			bitBoundary = 4;
			break;
		}
		logger.debug("shift down - bpw = " + bitsPerWord);
	}
	
	private int getBits()
	{
		// refresh the working register by bringing in a new byte
		while (bvalid < bitsPerWord) 
		{
			int nextByte = buf.get() & 0xff;
			reg |= nextByte << bvalid;
			bvalid += 8;
			logger.debug("get next byte '" + Integer.toBinaryString(nextByte) + "'b");
		}
		int val = reg & ((1 << bitsPerWord) - 1);
		StringBuffer registerBinaryString = new StringBuffer(Integer.toBinaryString(reg));
		while (registerBinaryString.length() < bvalid) registerBinaryString.insert(0, "0");
		logger.debug("register = '" + registerBinaryString + "'b");
		if (val > (1 << (bitsPerWord-1)))
		{
			// It's actually a negative number
			val -= (1 << bitsPerWord);
		}
		reg >>>= bitsPerWord; bvalid -= bitsPerWord;
		return (short) val;
	}
	
	
}
