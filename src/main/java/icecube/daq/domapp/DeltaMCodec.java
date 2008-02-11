package icecube.daq.domapp;

import java.nio.ByteBuffer;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * The 'M' (1-2-3-6-11) delta decoder/encoder
 * @author krokodil
 *
 */
public class DeltaMCodec
{
	private ByteBuffer buf;
	private int bitsPerWord;
	private int bitBoundary;
	private int bvalid = 0;
	private int pos;
	private int reg = 0;

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
	public short[] decode(int samples)
	{
		short last = 0;
		short[] out = new short[samples];

		// must reset bit logic -- note virtual bit register is _not_ reset
		bitsPerWord = 3; bitBoundary = 2;

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
	 * Delta encoder
	 * @param vec
	 */
	public void encode(short[] vec)
	{
		short last = 0;
		bitsPerWord = 3; bitBoundary = 2;
		for (int i = 0; i < vec.length; i++)
		{
			int delta = vec[i] - last;
			last = vec[i];
			int abs = Math.abs(delta);
			if (abs < (1 << (bitsPerWord-1)))
			{
				putBits(delta);
				if (abs < bitBoundary) shiftDown();
			}
			else
			{
				do
				{
					putBits((1 << (bitsPerWord-1)));
					shiftUp();
				}
				while (abs >= (1 <<(bitsPerWord-1)));
				putBits(delta);
			}
		}
		flush();
	}

	private void flush()
	{
		while (bvalid > 0)
		{
			int nb = bvalid > 8 ? 8 : bvalid;
			buf.put((byte) (reg & 0xff));
			reg >>>= nb;
			bvalid -= nb;
		}
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

	/**
	 * Supply continual bit stream from the byte buffer supplied
	 * up construction of this class instance
	 * @param bits - word to shift out
	 */
	private void putBits(int bits)
	{
		reg |= ( bits & (1 << bitsPerWord) - 1 ) << bvalid;
		bvalid += bitsPerWord;
		logger.debug("putBits(" + bits + ")");
		registerLog();
		while (bvalid > 7)
		{
			buf.put((byte) (reg & 0xff));
			reg >>>= 8;
			bvalid -= 8;
		}
	}

	/**
	 * Read in continual bit stream from the byte buffer supplied
	 * upon construction of this class instance.
	 * @return
	 */
	private int getBits()
	{
		// refresh the working register by bringing in a new byte
		while (bvalid < bitsPerWord)
		{
			int nextByte = buf.get() & 0xff;
			reg |= nextByte << bvalid;
			bvalid += 8;
			logger.debug("get next byte " + Integer.toBinaryString(nextByte & 0xff));
		}
		int val = reg & ((1 << bitsPerWord) - 1);
		registerLog();
		if (val > (1 << (bitsPerWord-1)))
		{
			// It's actually a negative number
			val -= (1 << bitsPerWord);
		}
		reg >>>= bitsPerWord; bvalid -= bitsPerWord;
		return (short) val;
	}

	private void registerLog()
	{
		if (logger.getEffectiveLevel().isGreaterOrEqual(Level.DEBUG))
		{
			StringBuffer bstr = new StringBuffer(Integer.toBinaryString(reg));
			while (bstr.length() < bvalid) bstr.insert(0, "0");
			logger.debug("bit register = B\"" + bstr + "\"");
		}
	}


}
