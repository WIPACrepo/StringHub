package icecube.daq.domapp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Date;
import icecube.daq.dor.IDriver;
import icecube.daq.bindery.BufferConsumer;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.Driver;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.GPSService;
import icecube.daq.dor.TimeCalib;
import icecube.daq.rapcal.RAPCal;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.util.UTC;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.BeforeClass;
import java.util.Calendar;
import java.util.GregorianCalendar;
//import java.lang.reflect.InvocationTargetException;
//import java.lang.reflect.Method;
import org.junit.Test;

import static org.junit.Assert.*;

class BufConsumer implements BufferConsumer
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
class rapcal implements RAPCal
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

public class SimDataCollectorUnitTest implements BufferConsumer
{
    private IDriver driver;
    AbstractDataCollector dc;
    LinkedBlockingQueue<ByteBuffer> q;
    
    public SimDataCollectorUnitTest()
    {
        q = new LinkedBlockingQueue<ByteBuffer>(1000);
    }
    
    @BeforeClass
    public static void setupLogging()
    {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
    }
    
    @Before
    public void setUp() throws InterruptedException
    {
        DOMChannelInfo chan = new DOMChannelInfo("056a7bb14cde", 0, 1, 'B');
        DOMConfiguration config = new DOMConfiguration();
        config.setSimNoiseRate(1000.0);
        config.setSimHLCFrac(0.25);
        q.clear();
        dc = new SimDataCollector(chan, config, this, null, null, null, false);
        dc.signalConfigure();
        while (!dc.isConfigured()) Thread.sleep(50);
        dc.signalStartRun();
        while (!dc.isRunning()) Thread.sleep(50);
        Thread.sleep(500);
        dc.signalStopRun();
        while (!dc.isConfigured()) Thread.sleep(50);
        dc.signalShutdown();
    }

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
    
    @Test
    public void testLCSim() throws Exception
    {
        boolean sawHLC = false, sawSLC = false;
        while (true)
        {
            ByteBuffer buf = q.take();
            if (buf.getInt(0) == 32 && buf.getLong(24) == Long.MAX_VALUE) break;
            boolean isHLC = (buf.getInt(46) & 0x30000) != 0;
            if (isHLC) sawHLC = true;
            if (!isHLC) sawSLC = true;
        }
        assertTrue(sawHLC);
        assertTrue(sawSLC);
    }
    @Test
    public void testGPS() throws Exception
    {
	final int dayGPS;
	final int dayCal;
	final int hourGPS;
	final int hourCal;
	final int minGPS;
	final int minCal;
	final int secGPS;
	final int secCal;
	DOMChannelInfo chan = new DOMChannelInfo("056a7bb14cde", 1, 1, 'B');
	DOMConfiguration config = new DOMConfiguration();
        BufConsumer hitsTo = new BufConsumer();
	BufConsumer moniTo = new BufConsumer();
        BufConsumer supernovaTo = new BufConsumer();
        BufConsumer tcalTo = new BufConsumer();
	rapcal rapcal = new rapcal();
		//DataCollector dc = new DataCollector(chan.card, chan.pair, chan.dom, config, hitsTo, moniTo, supernovaTo, tcalTo, driver, rapcal);
		//GPSService gps_serv = GPSService.getInstance();

	DataCollector privateObject = new DataCollector(chan.card, chan.pair, chan.dom, config, hitsTo, moniTo, supernovaTo, tcalTo, driver, rapcal);
	Method privateStringMethod = PrivateObject.class.
            getDeclaredMethod("execRapCal", null);
	privateStringMethod.setAccessible(true);
        GPSService gps_serv = (GPSService)privateStringMethod.invoke(privateObject, null);

	gps_serv.startService(chan.card);
	GPSInfo newGPS = gps_serv.getGps( chan.card);
        GregorianCalendar calendar = new GregorianCalendar(
                new GregorianCalendar().get(GregorianCalendar.YEAR), 1, 1);
	calendar.add(GregorianCalendar.DAY_OF_YEAR, newGPS.getDay() - 1);
	dayCal = calendar.get(Calendar.DAY_OF_WEEK);
	dayGPS = newGPS.getDay();
	hourCal = calendar.get(Calendar.HOUR_OF_DAY);
	hourGPS = newGPS.getDay();
	minCal = calendar.get(Calendar.MINUTE);
	minGPS = newGPS.getDay();
	secCal = calendar.get(Calendar.SECOND);
	secGPS = newGPS.getDay();
	if(dayGPS != dayCal)
	{
	    throw new Error("Unsynchronized");
	}
	else if(hourGPS != hourCal)
	{
	    throw new Error("Unsynchronized");
	}
	else if(minGPS != minCal)
	{
	    throw new Error("Unsynchronized");
	}
	else
	{
	    if(secGPS > secCal)
	    {
		if(secGPS-secCal > 4)
		{
		    throw new Error("Unsynchronized");
		}
    	    }
	    else
	    {
		if(secCal-secGps > 4)
		{
		    throw new Error("Unsynchronized");
		}
	    }
	}
	
    }
}
