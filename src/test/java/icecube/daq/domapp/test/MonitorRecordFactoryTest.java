package icecube.daq.domapp.test;

import static org.junit.Assert.*;

import icecube.daq.domapp.AsciiMonitorRecord;
import icecube.daq.domapp.DeltaCompressedHit;
import icecube.daq.domapp.MonitorRecord;
import icecube.daq.domapp.MonitorRecordFactory;

import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import junit.framework.JUnit4TestAdapter;

import org.junit.Before;
import org.junit.Test;

public class MonitorRecordFactoryTest {

	ByteBuffer monibuf;
	
	public MonitorRecordFactoryTest() throws Exception
	{
	}

	@Before public void setUp()
	{
		ReadableByteChannel channel =  Channels.newChannel(
				MonitorRecordFactoryTest.class.getResourceAsStream(
						"monitest.dat"
						)
					);
		assertNotNull("Couldn't get channel", channel);
		monibuf = ByteBuffer.allocate(5000);
		try
		{
			channel.read(monibuf);
			monibuf.flip();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			monibuf = null;
		}
	}
	
	@Test public void testCreateFromBuffer() 
	{
		
		while (monibuf.hasRemaining())
		{
			MonitorRecord rec = MonitorRecordFactory.createFromBuffer(monibuf);
			if (rec instanceof AsciiMonitorRecord) System.out.println(rec);
		}
		
	}
	
	public static junit.framework.Test suite()
	{
		return new JUnit4TestAdapter(MonitorRecordFactoryTest.class);
	}

}
