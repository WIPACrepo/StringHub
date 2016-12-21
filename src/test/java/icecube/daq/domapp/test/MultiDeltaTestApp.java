package icecube.daq.domapp.test;

import icecube.daq.domapp.DeltaCompressedHit;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * This class tests the DOM FPGA delta compression fidelity en masse.
 * The program is fed a dump of the DOMs LBM in the format
 *    0 .. 2047 Raw 0 record
 * 2048 .. 4095 Compressed 0 record
 * 4096 .. xxxx Raw 1 record
 * xxxx .. 8191 Compressed 1 record,
 * and so on.  Invocation goes like:
 *   java ic3.daq.domapp.test.MultiDeltaTest <i>lbm-dump</i>
 * @author krokodil
 *
 */
public class MultiDeltaTestApp 
{
	private short[] fadcRaw;
	private short[][] atwdRaw;
	private DeltaCompressedHit hit;
	
	public static void main(String[] args) throws Exception
	{
		BasicConfigurator.configure();
		Logger.getRootLogger().setLevel(Level.INFO);
		
		File file = new File(args[0]);
		ReadableByteChannel ch = Channels.newChannel(new FileInputStream(file));
		ByteBuffer buf = ByteBuffer.allocate((int) file.length());
		ch.read(buf);
		buf.flip();
		buf.order(ByteOrder.LITTLE_ENDIAN);
		while (buf.remaining() > 0)
		{
			MultiDeltaTestApp test = new MultiDeltaTestApp(buf);
			if (test.check())
				System.err.println("Buffer validated.");
		}
	}
	
	private MultiDeltaTestApp(ByteBuffer buf)
	{
		// allocate memory
		fadcRaw = new short[256];
		atwdRaw = new short[4][];
		for (int ch = 0; ch < 4; ch++) atwdRaw[ch] = new short[128];
		
		int pos = buf.position();
		decode_raw(buf);

		// advance to next LBM record boundary
		buf.position(pos + 0x800);
		decode_cmp(buf);
		buf.position(pos + 0x1000);
	}
	
	private void decode_raw(ByteBuffer buf)
	{
		// read the raw record
		int word0 = buf.getInt();
		int clkLo = word0 & 0xffff;
		assert (word0 >> 16 & 0xffff) == 1;
		int clkHi = buf.getInt();
		int word2 = buf.getInt();
		int word3 = buf.getInt();
		ShortBuffer fadcBuf = buf.asShortBuffer();
		for (int i = 0; i < 256; i++) fadcRaw[i] = fadcBuf.get();
		for (int ch = 0; ch < 4; ch++)
			for (int i = 0; i < 128; i++)
				atwdRaw[ch][i] = fadcBuf.get();
	}
	
	private void decode_cmp(ByteBuffer buf)
	{
		int word0 = buf.getInt();
		hit = DeltaCompressedHit.decodeBuffer(buf, word0 & 0xffff);
	}
	
	private boolean check()
	{
		short[] dfadc = hit.getFADC();
		short[][] datwd = hit.getATWD();
		
		for (int i = 0; i < 256; i++)
			if (dfadc[i] != fadcRaw[i])
			{
				System.err.println("FADC[" + i + "] does not match.  "
						+ "Expected " + fadcRaw[i] + " got " + dfadc[i]);
				return false;
			}
		
		for (int ch = 0; ch < 4; ch++)
		{
			if (datwd[ch] == null) break;
			for (int i = 0; i < 128; i++)
				if (datwd[ch][i] != atwdRaw[ch][i])
				{
					System.err.println("ATWD[" + ch + "][" + i + "] does not match.  "
							+ "Expected " + atwdRaw[ch][i] + " got " + datwd[ch][i]);
					return false;
				}
		}
		return true;
	}
}
