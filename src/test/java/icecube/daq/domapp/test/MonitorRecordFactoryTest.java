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
		ReadableByteChannel channel =  Channels.newChannel(
				ClassLoader.getSystemResourceAsStream(
						"ic3/daq/domapp/test/monitest.dat"
						)
					);
		monibuf = ByteBuffer.allocate(5000);
		try
		{
			channel.read(monibuf);
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			throw ex;
		}
	}

	@Before public void setUp()
	{
		monibuf.flip();
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
