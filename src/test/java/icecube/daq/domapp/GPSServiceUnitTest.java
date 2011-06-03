package icecube.daq.domapp;


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
import org.junit.Test;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.util.UTC;

import static org.junit.Assert.*;

/*class mockDriver implements IDriver
{
    public GPSInfo readGPS(int card)
    {
	throw  new Error("Unimplemented");
    }
       
    public TimeCalib readTCAL(int card, int pair, char dom)
    {
	throw  new Error("Unimplemented");
    }
        
    public void softboot(int card, int pair, char dom)
    {
	throw  new Error("Unimplemented");
    }

    public void commReset(int card, int pair, char dom)
    {
	throw  new Error("Unimplemented");
    }

    public void setBlocking(boolean block)
    {
	throw  new Error("Unimplemented");
    }

    public HashMap<String, Integer> getFPGARegisters(int card)
    {
	throw  new Error("Unimplemented");
    }

    public void resetComstat(int card, int pair, char dom)
    {
	throw  new Error("Unimplemented");
    }

    public String getComstat(int card, int pair, char dom)
    {
	throw  new Error("Unimplemented");
    }

    public String getFPGARegs(int card)
    {
	throw  new Error("Unimplemented");
    }
    
    public String getProcfileID(int card, int pair, char dom)
    {
	throw  new Error("Unimplemented");
    }
}*/

class mockBufferConsumer implements BufferConsumer
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
	final int hourGPS, hourGreg;
	final int minGPS, minGreg;
	final int secGPS, secGreg;
	final int dayGPS, dayGreg;
	boolean timeDiff = false;
	DOMChannelInfo chan = new DOMChannelInfo("056a7bb14cde", 1, 1, 'B');
	DOMConfiguration config = new DOMConfiguration();
        mockBufferConsumer hitsTo = new mockBufferConsumer();
	mockBufferConsumer moniTo = new mockBufferConsumer();
        mockBufferConsumer supernovaTo = new mockBufferConsumer();
        mockBufferConsumer tcalTo = new mockBufferConsumer();
	mockRAPCal rapcal = new mockRAPCal();
	Driver driver = Driver.getInstance();
	DataCollector dc = new DataCollector(chan.card, chan.pair, chan.dom, config, hitsTo, moniTo, supernovaTo, tcalTo, driver, rapcal);
	GPSService gps_serv = GPSService.getInstance();

	gps_serv.startService(driver, chan.card);
	GPSInfo newGPS = gps_serv.getGps( chan.card);
        GregorianCalendar calendar = new GregorianCalendar(
                new GregorianCalendar().get(GregorianCalendar.YEAR), 1, 1);
	calendar.add(GregorianCalendar.DAY_OF_YEAR, newGPS.getDay() - 1);
	dayGreg = calendar.get(Calendar.DAY_OF_YEAR);
	dayGPS = newGPS.getDay();
	hourGreg = calendar.get(Calendar.HOUR_OF_DAY);
	hourGPS = newGPS.getHour();
	minGreg = calendar.get(Calendar.MINUTE);
	minGPS = newGPS.getMin();
	secGreg = calendar.get(Calendar.SECOND);
	secGPS = newGPS.getSecond();	

	if(dayGreg != dayGPS)	{
	    timeDiff = true;
	}
	else	{
    	    if(hourGreg != hourGPS)   {
	        timeDiff = true;
            }
	    else    {
	        if(minGreg != minGPS)   {
	        timeDiff = true;
                }
	        else    {
		    if(secGreg > secGPS)   {
	            	if(secGreg - secGPS > 30)    {
			    timeDiff = true;
			}
                    }
		    else    {
			if(secGPS - secGreg > 30)    {
			    timeDiff = true;
			}
		    }
	        }
	    }
	}
	
    }
}
