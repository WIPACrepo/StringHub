package icecube.daq.domapp;

import java.nio.ByteBuffer;

/**
 * Utility functions for DOMApp quantities.
 * @author krokodil
 */
public class DOMAppUtil 
{
	/**
	 * Utility for decoding 6-byte clock word.  These words are 
	 * found among DOM-timestamped records - they are stored as
	 * 6-byte quantities instead of standard 8-byte longs in
	 * order to save space.
	 * @param buf the input ByteBuffer.  The next 6 bytes at
	 * the current buffer position will be decoded. Note that 
	 * the Buffer position will be advanced by 6 units.
	 * @return 48-bit clock word (high 2 bytes are 0)
	 */
	static long decodeSixByteClock(ByteBuffer buf)
	{
		long domclk = 0L;
		for (int itb = 0; itb < 6; itb++) 
		{
			int x = ((int) buf.get()) & 0xff;
			domclk = (domclk << 8) | x;
		}
		return domclk;
	}
}
