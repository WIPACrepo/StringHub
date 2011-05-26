package icecube.daq.dor;

import java.util.GregorianCalendar;
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

    private Logger logger = Logger.getLogger(GPSService.class);
    private Alerter alerter;

    private class GPSCollector extends Thread
    {
        private Driver driver;
        private int card;
        private int cons_gpsx_count;
        private GPSInfo gps;
        private int gps_error_count;
        private AtomicBoolean running;
        
        void startup(int card)
        {
            driver = Driver.getInstance();
            this.card = card;
            cons_gpsx_count = 0;
            gps_error_count = 0;
            gps = null;
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
                    Thread.sleep(240L);
                    GPSInfo newGPS = driver.readGPS(card);
                    
                    GregorianCalendar calendar = new GregorianCalendar(
                            new GregorianCalendar().get(GregorianCalendar.YEAR), 1, 1);
                    
                    cons_gpsx_count = 0;
                    if (!(gps == null || newGPS.getOffset().equals(gps.getOffset())))
                    {
                        logger.error(
                                "GPS offset mis-alignment detected - old GPS: " +
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
                catch (InterruptedException intx)
                {
                    return;
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
                }
                catch (GPSException gps_ex)
                {
                    gps_ex.printStackTrace();
                    logger.warn("Got GPS exception - time translation to UTC will be incomplete");
                    StringHubAlert.sendDOMAlert(
                            alerter, "GPS Error", "SyncGPS procfile I/O error",
                            card, 0, '-', "000000000000", "GPS", 0, 0);
                    gps_error_count++;
                }
            }
        }
        
        synchronized GPSInfo getGps() { return gps; }

        public boolean isRunning()
        {
            return running.get();
        }
    }
    
    private GPSCollector[] coll;
    private static final GPSService instance = new GPSService();
    
    private GPSService()
    {
        coll = new GPSCollector[8];
    }
    
    public static GPSService getInstance() { return instance; }
    
    public GPSInfo getGps(int card) { return coll[card].getGps(); } 
    
    public void startService(int card) 
    {
        if (coll[card] == null) { coll[card] = new GPSCollector(); }
        if (!coll[card].isRunning()) coll[card].startup(card); 
    }
    
    public void shutdownAll() 
    {
        for (int i = 0; i < 8; i++) coll[i].shutdown();
    }
    
    public void setAlerter(Alerter alerter) { this.alerter = alerter; }
}