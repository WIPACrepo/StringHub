package icecube.daq.domapp.test;

import static org.junit.Assert.*;

import icecube.daq.domapp.DeltaMCodec;
import icecube.daq.domapp.MonitorRecordFactory;

import java.nio.ByteBuffer;
import java.util.Random;

import junit.framework.JUnit4TestAdapter;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestDeltaMCodec 
{	
	private static final Logger logger = Logger.getLogger(TestDeltaMCodec.class);
	
	/**
	 * This should make the tests JUnit 3.8 compatible
	 * @return
	 */
	public static junit.framework.Test suite()
	{
		return new JUnit4TestAdapter(TestDeltaMCodec.class);
	}
	
	@BeforeClass
	public static void initLoggers()
	{
		//BasicConfigurator.configure();
		//Logger.getRootLogger().setLevel(Level.DEBUG);
	}
	
	@Before
	public void setUp() throws Exception 
	{
	}

	/**
	 * Test code / decode on static waveform-like vector
	 */
	@Test
	public void testWaveform() 
	{
		short[] vec = new short[] { 
				145, 146, 146, 145, 146, 146, 145, 145, 
				146, 158, 192, 200, 225, 224, 177, 147, 
				145, 146, 146, 144 };
		
		ByteBuffer buf = ByteBuffer.allocate(100);
		DeltaMCodec codec = new DeltaMCodec(buf);
		codec.encode(vec);
		logger.info(String.format("Compression ratio is %.1f%%", 
				(50.0 * buf.position()) / vec.length));
		buf.flip();
		short[] dec = codec.decode(vec.length);
		
		for (int i = 0; i < vec.length; i++)
		{
			assertEquals(vec[i], dec[i]);
		}
	}

	/**
	 * Test code / decode on random pattern
	 *
	 */
	@Test public void testRandomPattern()
	{
		Random r = new Random(581229607);
		for (int loop = 0; loop < 100; loop++)
		{
			short[] vec = new short[1000];
			ByteBuffer buf = ByteBuffer.allocate(5000);
			for (int i=0; i<vec.length; i++)
			{
				vec[i] = (short) (r.nextInt(900) + 120);
			}
			DeltaMCodec codec = new DeltaMCodec(buf);
			codec.encode(vec);
			logger.info(String.format("Compression ratio is %.1f%%", 
					(50.0 * buf.position()) / vec.length));
			buf.flip();
			short[] dec = codec.decode(vec.length);
			
			for (int i = 0; i < vec.length; i++)
			{
				assertEquals(vec[i], dec[i]);
			}
		}
	}
}
