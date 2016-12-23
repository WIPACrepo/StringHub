package icecube.daq.time.gps;

import icecube.daq.dor.Driver;
import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.GPSNotReady;
import icecube.daq.dor.IDriver;
import icecube.daq.dor.TimeCalib;
import icecube.daq.monitoring.IRunMonitor;
import icecube.daq.time.monitoring.ClockMonitoringSubsystem;
import icecube.daq.time.monitoring.ClockProcessor;
import icecube.daq.util.Leapseconds;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implements the production GPS Service backed by the GPS DSB card.
 * <p>
 * When active, the service polls the gpssync proc file at regular
 * intervals and provides client access to the latest reading.
 * <p>
 * Certain Error conditions such as unstable GPS/DOR offsets, or repeated
 * errors from the driver will result in the service being placed in a
 * failure state. Clients accessing readings from a service in a failure
 * state will receive an error.  Through this mechanism, DOMS serviced by a
 * DOR card with an faulty or unstable GPS will be dropped.
 * <p>
 * Note:
 *    Initial code moved from GPSService.java.
 *
 */
public class DSBGPSService implements IGPSService
{

    private Logger logger = Logger.getLogger(DSBGPSService.class);

    /** Driver. */
    private IDriver driver;

    /** Per-card collector thread. */
    private GPSCollector[] coll;

    /** String number of this hub. */
    private int string;

    /**
     * Null-proof notifier.
     */
    private static class MoniGuard
    {
        private IRunMonitor runMonitor;
        void push(final int string, final int card,
                  final GPSException exception)
        {
            if(runMonitor != null)
            {
                runMonitor.pushException(string, card, exception);
            }
        }
        void push(final int string, final int card, final GPSInfo oldGPS,
                  final GPSInfo newGPS)
        {
            if(runMonitor != null)
            {
                runMonitor.pushGPSMisalignment(string, card, oldGPS, newGPS);
            }
        }
        void push(final int string, final int card)
        {
            if(runMonitor != null)
            {
                runMonitor.pushGPSProcfileNotReady(string, card);
            }
        }
    }
    private final MoniGuard moniGuard = new MoniGuard();



    /**
     * Package protected.
     *
     * Note: Only one service should be instantiated on a hub. The service
     *       requires exclusive use of the syncgps procfile.
     */
    DSBGPSService()
    {
        this(Driver.getInstance());
    }

    /**
     * Package protected.
     *
     * Note: Only one service should be instantiated on a hub. The service
     *       requires exclusive use of the syncgps procfile.
     */
    DSBGPSService(IDriver driver)
    {
        this.driver = driver;
        this.coll = new GPSCollector[8];
    }

    @Override
    public GPSInfo getGps(int card) throws GPSServiceError
    {
        if(coll[card] != null)
        {
            return coll[card].getGps();
        }
        else
        {
            throw new GPSServiceError("Attempt to get GPS from" +
                    " card [" + card + "] before starting service.");
        }
    }

    @Override
    public boolean waitForReady(int card, int waitMillis)
            throws InterruptedException
    {
        return coll[card].waitForReady(waitMillis);
    }

    @Override
    public void startService(final int card)
    {
        if (coll[card] == null) { coll[card] = new GPSCollector(driver, card); }
        if (!coll[card].isRunning()) coll[card].startup();
    }

    @Override
    public void shutdownAll()
    {
        for (int i = 0; i < 8; i++)
            if (coll[i] != null && coll[i].isRunning()) coll[i].shutdown();
    }

    @Override
    public void setRunMonitor(final IRunMonitor runMonitor)
    {
        moniGuard.runMonitor = runMonitor;
    }

    @Override
    public void setStringNumber(final int string)
    {
        this.string = string;
    }

    /**
     * The per-card thread that continuously polls the
     * gpssync file.
     */
    class GPSCollector extends Thread
    {
        /** Number of consecutive missed reads to allow before logging.*/
        private final int EXPECTED_CONSEC_READ_MISSES = 10;

        /** Number of consecutive missed reads to allow without failing.*/
        private  final int MAX_CONSEC_READ_MISSES;

        /** Number of consecutive errors to allow before failing. */
        private  final int MAX_CONSEC_READ_ERRORS;


        /** Details the source of the GPS data. */
        private final int card;
        private IDriver driver;
        private File gpsFile;

        /** The latest GPS info. **/
        private GPSInfo gps;

        /** number of consecutive missed reads. */
        private int missedReadCount;

        /** number of consecutive error conditions. */
        private int errorCount;

        /** Flag indicating activation/deactivation. */
        private AtomicBoolean running;

        /** Flag indicating service failed. */
        private boolean isFailed;
        private String failureReason;

        /** support clients waiting for an available reading. */
        private CountDownLatch initializationLatch = new CountDownLatch(1);

        /** monitoring consumer. */
        private ClockProcessor gpsConsumer =
                ClockMonitoringSubsystem.Factory.processor();

        /**
         * Normal constructor with appropriate error tolerance.
         *
         * @param driver The backing source of GPSInfo reads.
         * @param card The source DOR card.
         */
        GPSCollector(IDriver driver, int card)
        {
            this(driver, card, 3600, 10);
        }

        /**
         * Supports testing with tighter tolerances
         * @param driver The backing source of GPSInfo reads.
         * @param card The source DOR card.
         * @param maxMissedReads The maximum number of consecutive missed reads
         *                       before failing the service.
         * @param maxErrors The maximum number of consecutive read errors
         *                       before failing the service.
         */
        GPSCollector(IDriver driver, int card, int maxMissedReads,
                     int maxErrors)
        {
            this.card = card;
            this.driver = driver;
            this.gpsFile = driver.getGPSFile(card);
            missedReadCount = 0;
            errorCount = 0;
            MAX_CONSEC_READ_MISSES = maxMissedReads;
            MAX_CONSEC_READ_ERRORS = maxErrors;
            gps = null;
            isFailed = false;
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
            this.interrupt();
        }

        synchronized GPSInfo getGps() throws GPSServiceError
        {
            if(isFailed)
            {
                throw new GPSServiceError(failureReason);
            }
            if(!running.get())
            {
                throw new GPSServiceError("Service not running for" +
                        " card " + card);
            }
            if(gps == null)
            {
                throw new GPSServiceError("Service not initialized for" +
                        " card " + card);
            }
            return gps;
        }

        boolean waitForReady(long waitMillis)
                throws InterruptedException
        {
            return initializationLatch.await(waitMillis, TimeUnit.MILLISECONDS);
        }

        public boolean isRunning()
        {
            return running.get();
        }

        @Override
        public void run()
        {

            // Eat through the up to 11 buffered GPS
            // snaps in the DOR card. These records
            // are arbitrarily stale and may not be
            // suitable for establishing the current
            // offset.
            try
            {
                for (int i = 0; i < 11; i++)
                    driver.readGPS(gpsFile);
            }
            catch (GPSNotReady nr)
            {
                // OK
            }
            catch (GPSException gpsx)
            {
                // Probably not OK, but defer serious consequences until
                // the next read(s).
                logger.warn("Ignoring GPS exception " + gpsx.getMessage());
            }


            // Poll the syncgps file at a rate slightly higher than the
            // expected rate of 1 Hz. This prevents accruing scheduling
            // delays creating a backlog of snapshots in the buffer.
            while (running.get())
            {
                try
                {
                    Thread.sleep(740L);
                    GPSInfo newGPS = driver.readGPS(gpsFile);

                    missedReadCount = 0;
                    errorCount = 0;

                    if(gps == null)
                    {
                        // initial value
                        updateGPS(newGPS);
                        initializationLatch.countDown();
                    }
                    else
                    {
                        // enforce stable offset
                        boolean isStable =
                                newGPS.getOffset().equals(gps.getOffset());
                        if(isStable)
                        {
                            updateGPS(newGPS);
                        }
                        else
                        {

                            //NOTE: Special handling code for the 2016 leap
                            //      second. We anticipate another errant
                            //      leap second from the ET6000 in which
                            //      case we want to ignore the change in
                            //      offset alignment and continue to run
                            //      while ignoring the mis-aligned GPS snaps
                            long delta = gps.getOffset().in_0_1ns() -
                                    newGPS.getOffset().in_0_1ns();
                            if(delta == 10000000000L)
                            {
                                // This happens after the so-called fake leap
                                // second, an unscheduled leap second emitted
                                // by the ET6000 24 hours before the scheduled
                                // leap second.
                                //
                                // The operational procedure is to keep the
                                // system running while we reset the backup
                                // master clock.
                                //
                                // Do not fail the service, just continue to
                                // serve the previous GPSInfo which contains
                                // the correct dor/utc offset.

                                // reduce logging to once per 10 minutes
                                if( (newGPS.getSecond() == 0) &&
                                    ((newGPS.getMin() % 10) == 0) )
                                {
                                    final String errmsg =
                                            "GPS offset mis-alignment detected - old GPS: " +
                                                    gps + " new GPS: " + newGPS;
                                    logger.error(errmsg);
                                    logger.warn("Ignoring GPS offset change and" +
                                            " throttling logging, scenario matches" +
                                            " false leap second phenomenon");
                                }

                            }
                            else
                            {
                                // look for the end of year rollover, allowing
                                // a slop second for the potential false
                                // leap second.
                                Leapseconds oracle = Leapseconds.getInstance();
                                int yearOfOperation = oracle.defaultYear;
                                long DAQ_TICKS_IN_YEAR = 10000000000L *
                                        oracle.seconds_in_year(yearOfOperation);
                                if( ((DAQ_TICKS_IN_YEAR - delta) == 0) ||
                                    (Math.abs(DAQ_TICKS_IN_YEAR - delta) == 10000000000L) )
                                {
                                    // This happens at the end of the year when
                                    // the master clock rolls over from
                                    // 365:23:59:60 to 001:00:00:00.
                                    //
                                    // Ignore this offset alignment change to
                                    // permit operating (a few seconds) through
                                    // the end of year

                                    final String errmsg =
                                            "GPS offset mis-alignment detected - old GPS: " +
                                                    gps + " new GPS: " + newGPS;
                                    logger.error(errmsg);
                                    logger.warn("Ignoring GPS offset change," +
                                            " scenario matches end-of-year");
                                }
                                else
                                {
                                    final String errmsg =
                                            "GPS offset mis-alignment detected - old GPS: " +
                                                    gps + " new GPS: " + newGPS;
                                    logger.error(errmsg);
                                    setFailed(errmsg);
                                }
                            }

                            moniGuard.push(string, card, gps, newGPS);
                        }
                    }
                }
                catch (InterruptedException intx)
                {
                    if(!running.get())
                    {
                        // a reasonable way to shutdown
                        return;
                    }
                    else
                    {
                        // unexpected, but comply
                        setFailed("Unexpected interruption");
                        return;
                    }
                }
                catch (GPSNotReady gps_not_ready)
                {
                    // Indicates no snaps available in the buffer. Since
                    // we poll more frequently than 1Hz, we expect this
                    // condition to occur once per period of 10-20 polling
                    // attempts. A sustained series indicates a problem.
                    if (missedReadCount++ > EXPECTED_CONSEC_READ_MISSES)
                    {
                        logger.warn("GPS not ready.");
                        moniGuard.push(string, card);
                    }

                    // After a certain period, fail the service. Most likely
                    // another process is reading the gpssync file. This
                    // is tolerable for a short time, but deprives data
                    // acquisition from monitoring the GPS offset.
                    if(missedReadCount > MAX_CONSEC_READ_MISSES)
                    {
                        setFailed("GPS service is not receiving readings" +
                                " from gpssync file");
                    }
                }
                catch (GPSException gps_ex)
                {
                    // indicates an issue with the driver, report always
                    // but give it a few chances to correct itself
                    logger.warn("Got GPS exception - time translation" +
                            " to UTC will be incomplete", gps_ex);
                    moniGuard.push(string, card, gps_ex);

                    if (errorCount++ > MAX_CONSEC_READ_ERRORS)
                    {
                        setFailed("GPS service received "
                                + MAX_CONSEC_READ_ERRORS +
                                " consecutive errors reading gpssync file.");
                    }
                }
                catch (Throwable th)
                {
                    logger.error("GPS service on card " + card +
                            " encountered an error", th);
                    setFailed("GPS service encountered an error " +
                            th.getMessage());
                }
            }
        }

        private void updateGPS(GPSInfo latestGPS)
        {
            synchronized (this)
            {
                gps = latestGPS;
                gpsConsumer.process(new ClockProcessor.GPSSnapshot(latestGPS,
                        card));
            }
        }

        private void setFailed(final String reason)
        {
            isFailed = true;
            failureReason = reason;
            running.set(false);
        }


    }


}
