package icecube.daq.time.monitoring;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.apache.log4j.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Provides the clock processor with a stream of NTP readings from
 * an external NTP source.
 *
 * Design Notes:
 *    This class active object. An execution thread is created at startup
 *    and will run until shutdown.  An NTP query is executed periodically
 *    on this thread with results passed to the clock processor.
 *
 *    If an execution fails to produce an offset, the alerter is invoked.
 *
 *    The hostname is resolved for each execution.
 *
 *    This class uses NTPUDPClient from the apache-commons-net library
 *    to query the server.  An alternative implementation can be substituted
 *    here without affecting the design.
 */
class NTPService
{

    /** Logger. */
    private final Logger logger = Logger.getLogger(NTPService.class);

    /**
     * The hostname of the NTP server.
     */
    private final String ntpHostname;

    /**
     * The period to poll the NTP server
     */
    private final int pollingPeriod;

    /**
     * The recipient of NTP readings.
     */
    private final ClockProcessor target;

    /**
     * Provides scheduled execution.
     */
    private ScheduledExecutorService executor;

    /**
     * Object that handles alerts.
     */
    private ClockAlerter alerter;


    /** running status. */
    private volatile boolean running = false;


    /**
     * Create an NTP service. Must call startup to activate.
     *
     * @param ntpHostname The hostname of the NTP server.
     * @param pollingPeriod The period to poll the server..
     * @param target NTP samples will be forwarded here.
     * @param alerter Faults requiring an alert are routed here.
     */
    NTPService(final String ntpHostname, final int pollingPeriod,
               final ClockProcessor target, final ClockAlerter alerter)
    {
        this.ntpHostname = ntpHostname;
        this.pollingPeriod = pollingPeriod;
        this.target = target;
        this.alerter = alerter;
    }

    /**
     * Start the service. Match with a call to shutdown.
     *
     * @throws Exception The NTP server could not be resolved.
     */
    void startup() throws Exception
    {
        synchronized (this)
        {
            logger.info("Starting NTP Service for [" + ntpHostname + "]");
            if(!running)
            {
                NTPQueryJob job = new NTPQueryJob(ntpHostname,
                        target);

                executor = Executors.newScheduledThreadPool(1,
                        new NTPThreadFactory(ntpHostname));
                executor.scheduleWithFixedDelay(job,
                        0, pollingPeriod, TimeUnit.SECONDS);
                running = true;
            }
            else
            {
                // be idempotent, but complain
                logger.warn("Redundent attempt to start the ntp service" +
                        " by thread " + Thread.currentThread().getName());
            }
        }
    }

    /**
     * Stops the service.
     */
    public void shutdown()
    {
        synchronized(this)
        {
            if(running)
            {
                if(executor != null)
                {
                    executor.shutdownNow();
                    try
                    {
                        executor.awaitTermination(5, TimeUnit.SECONDS);
                    }
                    catch (InterruptedException e)
                    {
                        logger.error("Could not shut down NTP service thread");
                    }
                    finally
                    {
                        running = false;
                    }
                }
            }
        }
    }

    /**
     * Provides the running state of the service.
     *
     * @return  True if the service is running.
     */
    boolean isRunning()
    {
        synchronized (this)
        {
            return running;
        }
    }


    /**
     * Holds the task that is scheduled to run periodically. Each
     * run will query the NTP server an forward the reading to the
     * clock processor.
     */
    private class NTPQueryJob implements Runnable
    {
        final String hostname;
        final InetAddress ntpHost;
        final NTPUDPClient ntpClient;
        final ClockProcessor target;

        private NTPQueryJob(final String hostname,
                            final ClockProcessor target)
        {
            this.hostname = hostname;
            this.target = target;
            this.ntpClient = new NTPUDPClient();
            ntpClient.setDefaultTimeout(5000);
        }

        @Override
        public void run()
        {
            // Called periodically via the scheduled executor,
            // each run performs one NTP query and passes the
            // result to the clock processor.
            try
            {
                InetAddress resolved =
                        InetAddress.getByName(ntpHostname);

                long before = System.nanoTime();
                TimeInfo time = ntpClient.getTime(resolved);
                long after = System.nanoTime();

                processNTPQuery(time, before, after);

            }
            catch (UnknownHostException e)
            {
                //alert, but do not fail
                logger.warn("NTP Lookup Error", e);
                alerter.alertNTPServer("Could not resolve NTP server"
                        , ntpHostname);
            }
            catch (Throwable th)
            {
                //alert, but do not fail
                logger.warn("NTP Query Error", th);
                alerter.alertNTPServer(th.getMessage(), ntpHostname);
            }
        }

        private void processNTPQuery(TimeInfo timeInfo, long beforeNano,
                                     long afterNano)
        {
            // ntp library delays offset calculation
            timeInfo.computeDetails();

            // See: RFC-1305
            //
            // Estimate the NTP clock time corresponding to the point-in-time
            // just after the completion of the NTP query.
            Long offset = timeInfo.getOffset();
            if(offset != null)
            {
                long ntp_time_unix = timeInfo.getReturnTime() + offset;
                ClockProcessor.NTPMeasurement reading =
                        new ClockProcessor.NTPMeasurement(hostname,
                                ntp_time_unix, offset,
                                afterNano, (afterNano-beforeNano));
                target.process(reading);
            }
            else
            {
                // the ntp exchange did not produce an offset, there are
                // various conditions that cause this. The NTPClient
                // library attempts to relay the cause via string comments.
                List<String> comments = timeInfo.getComments();
                for (int i = 0; i < comments.size(); i++)
                {
                    logger.warn("NTP Query Failed: " + comments.get(i));
                }

                alerter.alertNTPServer("Bad NTP Query", ntpHostname);
            }
        }

    }


    /**
     * Factory for defining the ntp service thread.
     */
    private static class NTPThreadFactory implements ThreadFactory
    {
        final String name;

        private NTPThreadFactory(final String host)
        {
            this.name = "NTP Service-" + host;
        }

        @Override
        public Thread newThread(final Runnable runnable)
        {
            Thread thread = new Thread(runnable);
            thread.setName(name);
            return thread;
        }

    }


}
