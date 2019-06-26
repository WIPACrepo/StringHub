package icecube.daq.domapp;

import static org.junit.Assert.*;

import icecube.daq.common.MockAppender;
import icecube.daq.domapp.DeltaMCodec;
import icecube.daq.domapp.MonitorRecordFactory;

import java.nio.ByteBuffer;
import java.util.Random;

import junit.framework.JUnit4TestAdapter;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestDeltaMCodec 
{	
	private static final Logger logger = Logger.getLogger(TestDeltaMCodec.class);
	private static final MockAppender appender = new MockAppender();

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
		BasicConfigurator.resetConfiguration();
		BasicConfigurator.configure(appender);
		//appender.setVerbose(true).setLevel(Level.INFO);
	}
	
	@Before
	public void setUp() throws Exception 
	{
	}
	
	@After
	public void tearDown() throws Exception 
	{
		appender.assertNoLogMessages();
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
		if (logger.isInfoEnabled())
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
		Random r = new Random();
        short[] vec = new short[256];
        ByteBuffer buf = ByteBuffer.allocate(1000);
		for (int loop = 0; loop < 10000; loop++)
		{
		    buf.clear();
            for (int i=0; i<vec.length; i++)
			{
				vec[i] = (short) (r.nextInt(900) + 120);
			}
			DeltaMCodec codec = new DeltaMCodec(buf);
			codec.encode(vec);
			if (loop % 1000 == 0) 
			if (logger.isInfoEnabled())
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
