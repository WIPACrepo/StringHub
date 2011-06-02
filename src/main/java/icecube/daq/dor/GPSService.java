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
        private IGPSInfo gps;
        private int gps_error_count;
        private AtomicBoolean running;

        GPSCollector(int card)
        {
            driver = Driver.getInstance();
            this.card = card;
            cons_gpsx_count = 0;
            gps_error_count = 0;
            gps = null;
            running = new AtomicBoolean(false);
        }

        void startup()
        {
            running.set(true);
            start();
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
                }
                catch (InterruptedException intx)
                {
                    return;
                }

                IGPSInfo newGPS;
                try
                {
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

                GregorianCalendar calendar = new GregorianCalendar();
                int year = calendar.get(GregorianCalendar.YEAR);
                GregorianCalendar jan1 = new GregorianCalendar(year, 1, 1);

                cons_gpsx_count = 0;
                if (!(gps == null || newGPS.getOffset().equals(gps.getOffset())))
                {
                    logger.error("GPS offset mis-alignment detected -" +
                                 " old GPS: " + gps + " new GPS: " + newGPS);
                    StringHubAlert.sendDOMAlert(alerter, "GPS Error",
                                                "GPS Offset mis-match",
                                                card, 0, '-', "000000000000",
                                                "GPS", 0, 0);
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

    private GPSService()
    {
        coll = new GPSCollector[8];
    }

    public static GPSService getInstance() { return instance; }

    public IGPSInfo getGps(int card) { return coll[card].getGps(); }

    public void startService(int card)
    {
        if (coll[card] == null) { coll[card] = new GPSCollector(card); }
        if (!coll[card].isRunning()) coll[card].startup();
    }

    public void shutdownAll()
    {
        for (int i = 0; i < 8; i++)
            if (coll[i] != null && coll[i].isRunning()) coll[i].shutdown();
    }

    public void setAlerter(Alerter alerter) { this.alerter = alerter; }
}
