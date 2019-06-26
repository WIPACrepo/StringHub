package icecube.daq.time.monitoring;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.NtpV3Packet;
import org.apache.commons.net.ntp.TimeInfo;
import org.apache.log4j.Logger;

import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
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
     * The period to poll the NTP server, in seconds.
     */
    private final int pollingPeriodSeconds;

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
     * @param pollingPeriodSeconds The period to poll the server, in seconds.
     * @param target NTP samples will be forwarded here.
     * @param alerter Faults requiring an alert are routed here.
     */
    NTPService(final String ntpHostname, final int pollingPeriodSeconds,
               final ClockProcessor target, final ClockAlerter alerter)
    {
        this.ntpHostname = ntpHostname;
        this.pollingPeriodSeconds = pollingPeriodSeconds;
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

                // stagger the start time to prevent hubs synchronizing
                // their query period and loading the server
                long startDelay = (long) (Math.random() * 60);
                executor = Executors.newScheduledThreadPool(1,
                        new NTPThreadFactory(ntpHostname));
                executor.scheduleWithFixedDelay(job,
                        startDelay, pollingPeriodSeconds, TimeUnit.SECONDS);
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


                long before;
                long after;
                TimeInfo time;

                // Query the NTP server allowing for a few timeouts
                // before alerting.
                final int MAX_ATTEMPTS = 3;
                int attempt = 0;
                while(true)
                {
                    attempt++;
                    try
                    {
                        before = System.nanoTime();
                        time = ntpClient.getTime(resolved);
                        after = System.nanoTime();
                        break;
                    }
                    catch (SocketTimeoutException ste)
                    {
                        if(attempt< MAX_ATTEMPTS)
                        {
                            // assume a transient issue, repeat query
                            logger.warn("NTP Query Timeout, attempt "
                                    + attempt + " of " + MAX_ATTEMPTS,
                                    ste);
                            Thread.sleep(10000);
                        }
                        else
                        {
                            throw new Error("Too many query timeouts", ste);
                        }
                    }
                }

                processNTPQuery(time, before, after);

                // Note: Shoehorn alert.
                //
                // Note: Leap seconds present an issue for recipients
                //       of NTP measurements. If a leap second is pending,
                //       and the current day is the last day of the month,
                //       and we are one polling interval from midnight
                //       attempt to get a fresh reading immediately after
                //       the leap second is due to occur.
                //
                //       The idea here is that a fresh reading post leap
                //       second, combined with the hysteresis of the clock
                //       monitor, should prevent alerts from being issued
                //       as the various clocks apply the leap second.
                int leapIndicator = time.getMessage().getLeapIndicator();


                if(leapIndicator == NtpV3Packet.LI_LAST_MINUTE_HAS_61_SECONDS ||
                        leapIndicator == NtpV3Packet.LI_LAST_MINUTE_HAS_59_SECONDS )
                {

                    Date ntpTimeAsDate =
                            time.getMessage().getTransmitTimeStamp().getDate();

                    boolean isLastDayOfMonth =
                            UtilityMethods.isLastDayOfMonth(ntpTimeAsDate);

                    if(isLastDayOfMonth)
                    {
                        long millisToLeap =
                                UtilityMethods.millisUntilMidnight(ntpTimeAsDate);

                        // if this is the last scheduled reading before the leap
                        // second. Sleep until after the leap second and grab
                        // a makeup reading.
                        if(millisToLeap < (pollingPeriodSeconds * 1000) )
                        {
                            long sleepMillis = millisToLeap + 250;
                            logger.warn("Leap second pending, sleeping ["
                                    + sleepMillis + " ms] to aquire" +
                                    " a fresh NTP timestamp");
                            Thread.sleep(sleepMillis);

                            long before2 = System.nanoTime();
                            TimeInfo time2 = ntpClient.getTime(resolved);
                            long after2 = System.nanoTime();

                            processNTPQuery(time2, before2, after2);

                            //did we sleep enough?
                            int leapIndicator2 =
                                    time.getMessage().getLeapIndicator();
                            if(leapIndicator2 == NtpV3Packet.LI_LAST_MINUTE_HAS_61_SECONDS ||
                                    leapIndicator2 == NtpV3Packet.LI_LAST_MINUTE_HAS_59_SECONDS )
                            {
                                logger.warn("Leap second still pending," +
                                        " sleep failed.");
                            }
                            else
                            {
                                logger.warn("Leap second was applied to NTP.");
                            }
                        }
                    }

                }

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

    /**
     * Holds utility methods.  These methods are enclosed in a
     * package visible static class to support testing.
     */
    static class UtilityMethods
    {

        /**
         * Determines if the Date object holds a time that is the
         * last day of the month.  This is meaningful for anticipating
         * whan a leap second will be applied.
         *
         * @param time A time from the ntp server.
         * @return True if the time is from the last day of the month.
         */
        static boolean isLastDayOfMonth(final Date time)
        {

            Calendar ntpNow = new GregorianCalendar();
            ntpNow.setTimeZone(TimeZone.getTimeZone("GMT"));
            ntpNow.setTime(time);

            final int currentDayOfMonth = ntpNow.get(Calendar.DAY_OF_MONTH);

            final int lastDayOfMonth =
                    ntpNow.getActualMaximum(Calendar.DAY_OF_MONTH);

            return currentDayOfMonth == lastDayOfMonth;
        }


        /**
         * Calculate the number of milliseconds fron a specific time until
         * midnight that day.
         *
         * @param time A time info from the ntp server.
         */
        static long millisUntilMidnight(final Date time)
        {
            Calendar ntpNow = new GregorianCalendar();
            ntpNow.setTimeZone(TimeZone.getTimeZone("GMT"));
            ntpNow.setTime(time);

            Calendar ntpMidnight = new GregorianCalendar();
            ntpMidnight.setTimeZone(TimeZone.getTimeZone("GMT"));
            ntpMidnight.setTime(ntpNow.getTime());
            ntpMidnight.add(Calendar.DATE, 1);
            ntpMidnight.set(Calendar.HOUR_OF_DAY, 0);
            ntpMidnight.set(Calendar.MINUTE, 0);
            ntpMidnight.set(Calendar.SECOND, 0);

            long millisUntilMidnight = ntpMidnight.getTimeInMillis()
                    - ntpNow.getTimeInMillis();

            return millisUntilMidnight;
        }
    }


}
