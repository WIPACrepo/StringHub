package icecube.daq.dor;

import java.util.GregorianCalendar;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

import icecube.daq.juggler.alert.Alerter;
import icecube.daq.util.StringHubAlert;

/**
 * This class exists to centralize the GPS information collection
 * and hand it out in a more orderly manner to the clients that
 * need it - mostly a gang of DataCollectors who want the GPS
 * offset information.  The GPS procfile only exists on the card
 * level so there must be a one-to-many handoff of this offset info.
 * @author kael
 *
 */

public class GPSService
{

    private static Logger logger = Logger.getLogger(GPSService.class);
    private Alerter alerter;
    private static int countFalse = 0;
    private static final int maxFalse = 0;

    private class GPSCollector extends Thread
    {
        private Driver driver;
        private int card;
        private int cons_gpsx_count;
        private IGPSInfo gps;
        private int gps_error_count;
        private AtomicBoolean running;

        GPSCollector(Driver driver, int card)
        {
            this.driver = driver;
            this.card = card;
            cons_gpsx_count = 0;
            gps_error_count = 0;
            gps = null;
            running = new AtomicBoolean(false);
        }

        void startup()
        {
            running.set(true);
            this.start();
        }

        void shutdown()
        {
            running.set(false);
        }

        public void run()
        {
            while (running.get())
            {
                try
                {
                    Thread.sleep(740L);
                }
                catch (InterruptedException intx)
                {
                    return;
                }

                IGPSInfo newGPS;
                try {
                    newGPS = driver.readGPS(card);
                }
                catch (GPSNotReady gps_not_ready)
                {
                    if (cons_gpsx_count++ > 10)
                    {
                        logger.warn("GPS not ready.");
                        StringHubAlert.sendDOMAlert(
                                alerter, "GPS Error", "SyncGPS procfile not ready",
                                card, 0, '-', "000000000000", "GPS", 0, 0);
                    }
                    continue;
                }
                catch (GPSException gps_ex)
                {
                    gps_ex.printStackTrace();
                    logger.warn("Got GPS exception - time translation to UTC will be incomplete");
                    StringHubAlert.sendDOMAlert(
                            alerter, "GPS Error", "SyncGPS procfile I/O error",
                            card, 0, '-', "000000000000", "GPS", 0, 0);
                    gps_error_count++;
                    continue;
                }

                // got GPS info, clear error counter
                cons_gpsx_count = 0;

                if (!(gps == null || newGPS.getOffset().equals(gps.getOffset())))
                {
                    logger.error("GPS offset mis-alignment detected - old GPS: " +
                                 gps + " new GPS: " + newGPS);
                    StringHubAlert.sendDOMAlert(
                            alerter, "GPS Error", "GPS Offset mis-match",
                            card, 0, '-', "000000000000", "GPS", 0, 0);
                }
                else
                {
                    synchronized (this) { gps = newGPS; }
                }

            }
        }

	synchronized IGPSInfo getGps() { return gps; }

        public boolean isRunning()
        {
            return running.get();
        }
    }

    private GPSCollector[] coll;
    private static final GPSService instance = new GPSService();
    private static final int maxDiff = 5;

    private GPSService()
    {
        coll = new GPSCollector[8];
    }

    public static GPSService getInstance() { return instance; }

    public IGPSInfo getGps(int card) { return coll[card].getGps(); }

    public void startService(Driver drv, int card)
    {
        if (coll[card] == null) { coll[card] = new GPSCollector(drv, card); }
        if (!coll[card].isRunning()) coll[card].startup();
    }

    public static void GPSTest(GPSInfo newGPS)    {
	if(!testGPS(newGPS)) {
            countFalse++;
            if(countFalse > maxFalse)
            {
            GregorianCalendar calendar = new GregorianCalendar();
            logger.error("GPS clock " + newGPS +
                " differs from system clock " + calendar);
            }
        }
    }

    public static boolean testGPS(GPSInfo gps)
    {
	final int hourGPS, hourGreg;
	final int minGPS, minGreg;
	final int secGPS, secGreg;
	final int dayGPS, dayGreg;

	GregorianCalendar calendar = new GregorianCalendar(
            new GregorianCalendar().get(GregorianCalendar.YEAR), 1, 1);

	calendar.add(GregorianCalendar.DAY_OF_YEAR, gps.getDay() - 1);
	dayGreg = calendar.get(Calendar.DAY_OF_YEAR);
	dayGPS = gps.getDay();
	hourGreg = calendar.get(Calendar.HOUR_OF_DAY);
	hourGPS = gps.getHour();
	minGreg = calendar.get(Calendar.MINUTE);
	minGPS = gps.getMin();
	secGreg = calendar.get(Calendar.SECOND);
	secGPS = gps.getSecond();
	if(dayGreg != dayGPS)	{
	    return false;
	}
	else	{
    	    if(hourGreg != hourGPS)   {
	        return false;
            }
	    else    {
	        if(minGreg != minGPS)   {
	            return false;
                }
	        else    {
		    if(secGreg > secGPS)    {
			if(secGreg - secGPS > maxDiff)
			    return false;
		    }
		    else    {
			if(secGPS - secGreg > maxDiff)
			    return false;
		    }


	        }
	    }
	}
	return true;

    }

    public void shutdownAll()
    {
        for (int i = 0; i < 8; i++)
            if (coll[i] != null && coll[i].isRunning()) coll[i].shutdown();
    }

    public void setAlerter(Alerter alerter) { this.alerter = alerter; }
}
