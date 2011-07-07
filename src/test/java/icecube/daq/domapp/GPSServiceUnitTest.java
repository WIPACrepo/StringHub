package icecube.daq.domapp;

import java.lang.String;
import java.io.IOException;
import java.nio.ByteBuffer;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.Driver;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.GPSService;
import icecube.daq.dor.IDriver;
import icecube.daq.dor.IGPSService;
import java.util.concurrent.LinkedBlockingQueue;
import icecube.daq.dor.TimeCalib;
import icecube.daq.rapcal.RAPCal;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.bindery.BufferConsumer;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.lang.Character;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.util.UTC;

import static org.junit.Assert.*;


/*class mockBufferConsumer implements BufferConsumer
{
    LinkedBlockingQueue<ByteBuffer> q;

    public void consume(ByteBuffer buf) throws IOException
    {
        try
        {
            q.put(buf);
        }
        catch (InterruptedException intx)
        {
        
        }
    }
}
class mockRAPCal implements RAPCal
{
    public double clockRatio()
    {
	throw  new Error("Unimplemented");
    }

    public double cableLength()
    {
	throw  new Error("Unimplemented");
    }

    public boolean laterThan(long domclk)
    {
	throw new Error("Unimplemented");
    }

    public UTC domToUTC(long domclk)
    {
	throw new Error("Unimplemented");
    }

    public void update(TimeCalib tcal, UTC gpsOffset) throws RAPCalException
    {
	throw new Error("Unimplemented");
    }
}
*/

public class GPSServiceUnitTest implements IGPSService
{
 
    AbstractDataCollector dc;
    LinkedBlockingQueue<ByteBuffer> q;

    public GPSInfo getGps(int card)
    {
	throw new Error("Unimplemented");
    }
    
    public void startService(int card)
    {
	throw new Error("Unimplemented");
    }
    
    public void shutdownAll()
    {
	throw new Error("Unimplemented");
    }
	
    public void setAlerter(Alerter alerter)
    {
	throw new Error("Unimplemented");
    }

    @Test
    public void testGPS() throws Exception
    {
	
	ByteBuffer buf = ByteBuffer.allocate(22);
	
	byte startOfHeader = (byte) 1;
	String GPSTime = "000000000001";
	byte quality = (byte) 2;
	long dorClock = 1234567890L;

	buf.put(startOfHeader);
	buf.put(GPSTime.getBytes());
	buf.put(quality);
	buf.putLong(dorClock);
	buf.flip();

	GPSInfo newGPS = new GPSInfo(buf);
	
 	GPSService.GPSTest(newGPS);
	
    }
}
