package icecube.daq.domapp;


import static org.junit.Assert.assertEquals;
import icecube.daq.domapp.DeltaCompressedHit;
import icecube.daq.stringhub.test.MockAppender;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import junit.framework.JUnit4TestAdapter;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class DeltaCompressedHitTest 
{
	private DeltaCompressedHit hit;
	private static final Logger logger = Logger.getLogger(DeltaCompressedHitTest.class);
	private static final MockAppender appender = new MockAppender();
	
	/**
	 * This should make the tests JUnit 3.8 compatible
	 * @return
	 */
	public static junit.framework.Test suite()
	{
		return new JUnit4TestAdapter(DeltaCompressedHit.class);
	}
	
	@BeforeClass public static void initialize() throws Exception
	{
		// Init the logging system
		BasicConfigurator.resetConfiguration();
		BasicConfigurator.configure(appender);
		//appender.setVerbose(true).setLevel(Level.INFO);
	}
	
	@Before
	public void setUp() throws Exception
	{
		ReadableByteChannel ch = Channels.newChannel(
				DeltaCompressedHitTest.class.getResourceAsStream("domapp-delta.dat")
			);
		ByteBuffer iobuf = ByteBuffer.allocateDirect(1000);
		try
		{
			int n = ch.read(iobuf);
			logger.info("Read " + n + " bytes.");
			iobuf.flip();
			iobuf.order(ByteOrder.BIG_ENDIAN);
			int recl = iobuf.getShort();
			int fmtid = iobuf.getShort();
			assert fmtid == 0x90;
			iobuf.order(ByteOrder.LITTLE_ENDIAN);
			int clkMSB = iobuf.getShort();
			logger.debug("New buffer - recl: " + recl + " fmtid: " 
					+ fmtid + " clock MSB: " + Integer.toHexString(clkMSB));
			iobuf.getShort();
			hit = DeltaCompressedHit.decodeBuffer(iobuf, clkMSB);
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			throw ex;
		}

	}

	@After
	public void tearDown() throws Exception 
	{
		assertEquals("Bad number of log messages",
			     0, appender.getNumberOfMessages());
	}

	@Test 
	public void testClock()
	{
		assertEquals(0x01da7340000eL, hit.getClock());
	}
	
	@Test
	public void testAtwd()
	{
		short[] atwd = { 
			125, 128, 127, 126, 127, 128, 128, 127, 127, 126,
			124, 125, 123, 124, 124, 124, 124, 123, 124, 124,
			122, 123, 122, 121, 120, 119, 118, 117, 118, 118,
			118, 116, 118, 118, 116, 114, 115, 113, 116, 112,
			112, 114, 114, 118, 124, 139, 148, 134, 136, 136,
			134, 134, 134, 132, 134, 136, 134, 136, 134, 133,
			135, 134, 135, 135, 133, 135, 134, 134, 134, 135,
			134, 133, 134, 134, 132, 135, 133, 134, 132, 131,
			129, 135, 135, 133, 132, 136, 134, 135, 133, 134,
			132, 133, 133, 134, 131, 134, 133, 133, 135, 133,
			134, 134, 133, 133, 134, 131, 134, 134, 135, 135,
			138, 136, 137, 138, 140, 145, 152, 165, 187, 230,
			304, 353, 258, 138, 135, 134, 134, 134
		};
		
		short[] deco = hit.getATWD()[0];
		for (int i = 0; i < 128; i++)
			assertEquals(atwd[i], deco[i]);
		
	}
	
	@Test
	public void testFadc()
	{
		short[] fadc = {
			141, 141, 141, 141, 141, 143, 165, 205, 189, 160,
			153, 149, 145, 142, 144, 143, 142, 141, 136, 125,
			119, 121, 123, 125, 129, 130, 133, 135, 137, 137,
			139, 139, 140, 140, 140, 140, 141, 142, 144, 143,
			142, 142, 143, 142, 142, 142, 142, 143, 143, 144,
			143, 142, 142, 142, 142, 142, 142, 142, 142, 142,
			141, 142, 142, 142, 142, 142, 142, 142, 142, 143,
			142, 142, 142, 141, 142, 142, 142, 142, 143, 143,
			142, 142, 141, 142, 141, 142, 142, 142, 141, 142,
			142, 141, 142, 143, 143, 143, 142, 143, 143, 143,
			142, 142, 142, 142, 142, 143, 142, 142, 142, 143,
			142, 142, 142, 142, 141, 142, 142, 143, 142, 143,
			142, 143, 142, 142, 142, 142, 143, 141, 143, 142,
			142, 142, 142, 142, 142, 142, 142, 143, 143, 142,
			143, 143, 142, 142, 141, 141, 141, 141, 142, 142,
			141, 142, 142, 141, 142, 141, 142, 142, 141, 141,
			141, 141, 142, 141, 142, 143, 142, 142, 142, 142,
			143, 143, 143, 142, 142, 142, 142, 142, 142, 143,
			142, 142, 142, 143, 142, 142, 142, 142, 142, 143,
			143, 142, 142, 142, 143, 142, 142, 142, 142, 142,
			143, 143, 143, 143, 143, 141, 141, 142, 142, 143,
			142, 142, 142, 142, 141, 142, 143, 143, 141, 142,
			142, 143, 143, 142, 142, 142, 142, 142, 142, 143,
			142, 142, 142, 141, 142, 142, 142, 143, 142, 143, 
			142, 142, 142, 142, 143, 142, 142, 142, 142, 142, 
			144, 142, 142, 142, 143, 143
		};
		
		short[] deco = hit.getFADC();
		for (int i = 0; i < 256; i++)
			assertEquals(fadc[i], deco[i]);
		
	}
	
	@Test
	public void testChargeStamp()
	{
		assertEquals(7, hit.getPeakPosition());
		short[] cs = hit.getChargeStamp();
		assertEquals((short) 165, cs[0]);
		assertEquals((short) 205, cs[1]);
		assertEquals((short) 189, cs[2]);
	}

}
