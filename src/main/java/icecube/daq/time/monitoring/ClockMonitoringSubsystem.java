package icecube.daq.time.monitoring;

import icecube.daq.juggler.alert.AlertQueue;
import org.apache.log4j.Logger;


/**
 * A facade to the clock monitoring subsystem, providing an entry
 * point for configuration and interaction from the rest of the system.
 *<p>
 * The subsystem interface is always available, but by default no
 * monitoring will take place. Monitoring must be enabled explicitly
 * by configuration.
 * <pre>
 *
 *    icecube.daq.time.monitoring.enable-clock-monitoring = [false]
 *
 *        Enable/Disable clock monitoring.
 *
 *</pre><p>
 * If monitoring is enabled, a reachable NTP server must be defined.
 *<pre>
 *
 *   icecube.daq.time.monitoring.ntp-host = ntp1
 *
 *       Defines the NTP server to be queried.
 *
 *</pre><p>
 * If monitoring is enabled, clock alert notifications will be issued.
 *<pre>
 *
 *   icecube.daq.time.monitoring.alert-email = "foo@icecube.wisc.edu"
 *
 *       An email addresses that will receive notifications.
 *
 *   icecube.daq.time.monitoring.alert-should-page = false
 *
 *       When true, the alert will also request a page.
 *
 *</pre><p>
 * There are additional optional configurations for the enabled mode:
 *<pre>
 *
 *    icecube.daq.time.monitoring.ntp-poll-seconds = [300]<dt></dt>
 *
 *       Defines the polling period of NTP server queries.</dt>
 *
 *    icecube.daq.time.monitoring.max-tcal-duration-millis = [50]
 *
 *       Defines na upper bound on the execution time of a tcal, measurements
 *       exceeding this time are considered outliers and dropped.
 *
 *    icecube.daq.time.monitoring.max-ntp-duration-millis = [25]
 *
 *       Defines an upper bound on the execution time of a tcal, measurements
 *       exceeding this time are considered outliers and dropped.
 *
 *    icecube.daq.time.monitoring.max-consec-ntp-rejects = [20]
 *
 *       After this number of consecutive NTP queries are rejected due to
 *       execution time, an alert will be issued.
 *
 *    icecube.daq.time.monitoring.system-clock-alert-threshold-millis = [100]
 *
 *       The offset between NTP and system time that initiates an alert.
 *
 *    icecube.daq.time.monitoring.master-clock-alert-threshold-millis = [100]
 *
 *       The offset between NTP and master clock time that initiates an alert.
 *
 *    icecube.daq.time.monitoring.local-clock-sample-window = [5]
 *
 *       The number of successive over-threshold local clock readings that
 *       must occur to trigger an alert.
 *
 *    icecube.daq.time.monitoring.master-clock-sample-window = [5]
 *
 *       The number of successive over-threshold master clock readings that
 *       must occur to trigger an alert.
 *
 *    icecube.daq.time.monitoring.alert-interval-minutes = [60]
 *
 *       The minimum interval between two successive alerts of the same type.
 *
 *
 *</pre><p>
 * Design Notes:
 *<p>
 * In enabled mode, the NTP query and the clock reading processing are
 * done on independent threads. This minimizes delay on the data collection
 * threads and simplifies package-internal synchronization. Note that
 * package-internal classes assume this and are not thread-safe.
 *
 */
public interface ClockMonitoringSubsystem extends ClockProcessor
{

    static final Logger logger =
            Logger.getLogger(ClockMonitoringSubsystem.class);

    /**
     * Monitoring must be explicitly enabled by configuration.
     */
    public static final  boolean CLOCK_MONITORING_ENABLED =
            Boolean.getBoolean(
                    "icecube.daq.time.monitoring.enable-clock-monitoring");
    /**
     * The NTP clock that the master clock and system clock will be
     * compared against.
     */
    public static final  String NTP_HOST =
            System.getProperty("icecube.daq.time.monitoring.ntp-host");


    /** Controls the frequency of NTP polling. */
    public static final int NTP_QUERY_PERIOD_SECONDS =
            Integer.getInteger(
                    "icecube.daq.time.monitoring.ntp-poll-seconds", 300);


    /** Number of milliseconds that bound a tcal sample duration. */
    public static final int MAX_TCAL_SAMPLE_MILLIS =
            Integer.getInteger(
                    "icecube.daq.time.monitoring.max-tcal-duration-millis", 50);

    /** Number of nanos that bound an NTP Query duration. */
    public static final int MAX_NTP_SAMPLE_MILLIS =
            Integer.getInteger(
                    "icecube.daq.time.monitoring.max-ntp-duration-millis", 25);

    /**
     * The number of consecutively rejected NTP readings that triggers an
     * alert.
     */
    public static final int MAX_CONSECUTIVE_NTP_REJECTS =
            Integer.getInteger(
                    "icecube.daq.time.monitoring.max-consec-ntp-rejects", 20);


    /** Millisecond offset of the local clock which triggers an alert. */
    public static final int SYSTEM_CLOCK_ALERT_THRESHOLD_MILLIS =
            Integer.getInteger(
            "icecube.daq.time.monitoring.system-clock-alert-threshold-millis",
                    100);

    /** Millisecond offset of the master clock which triggers an alert. */
    public static final int MASTER_CLOCK_ALERT_THRESHOLD_MILLIS =
            Integer.getInteger(
              "icecube.daq.time.monitoring.master-clock-alert-threshold-millis",
                    100);

    /**
     * The number of successive over-threshold local clock samples required
     * to trigger an alert.
     */
    public static final int SYSTEM_CLOCK_SAMPLE_WINDOW =
            Integer.getInteger(
                    "icecube.daq.time.monitoring.system-clock-sample-window",
                    5);

    /**
     * The number of successive over-threshold master clock samples required
     * to trigger an alert.
     */
    public static final int MASTER_CLOCK_SAMPLE_WINDOW =
            Integer.getInteger(
                    "icecube.daq.time.monitoring.master-clock-sample-window",
                    5);

    /** The minimum duration between two successive alerts of the same type. */
    public static final int ALERT_INTERVAL_MINUTES =
            Integer.getInteger(
                    "icecube.daq.time.monitoring.alert-interval-minutes",
                    60);

    /** An email address that will receive alerts. */
    public static final String ALERT_EMAIL =
            System.getProperty("icecube.daq.time.monitoring.alert-email", "");

    /** When true, the alerts will request a page. */
    public static final boolean ALERT_SHOULD_PAGE =
            Boolean.getBoolean("icecube.daq.time.monitoring.alert-should-page");


    /**
     * Starts the subsystem. Must be called prior to submitting clock
     * measurements.
     *
     * @param alerter Handles alert delivery.
     *
     * @return An MBean for monitoring the subsystem.
     */
    public Object startup(AlertQueue alerter);


    /**
     * Stops the subsystem.
     */
    public void shutdown();


    /**
     * Provides client access to public subsystem interfaces.
     */
    public static class Factory
    {
        private static final ClockMonitoringSubsystem singleton;
        static
        {
            if(CLOCK_MONITORING_ENABLED)
            {
                singleton = new EnabledMonitor();
            }
            else
            {
                singleton = new DisabledMonitor();
            }
        }

        public static ClockMonitoringSubsystem subsystem()
        {
            return singleton;
        }

        public static ClockProcessor processor()
        {
            return singleton;
        }

    }


    /**
     * A Null implementation provided to clients when monitoring is disabled.
     */
    class DisabledMonitor extends NullProcessor
            implements ClockMonitoringSubsystem
    {
        @Override
        public Object startup(final AlertQueue alerter)
        {
            return null;
        }

        @Override
        public void shutdown()
        {
        }
    }


    /**
     * The full-blown heavy clock monitor.
     *
     * Launches threads for clock reading processing and NTP querying.
     */
    class EnabledMonitor implements ClockMonitoringSubsystem
    {
        /**
         * The recipient of clock readings from client code. The
         * Null implementation will be replaced after startup to
         * begrudgingly support a mis-ordered startup (GPS started
         * before clock monitor).
         */
        private ClockProcessor reactor = new NullProcessor();

        /** Generates NTP times by polling the NTP server*/
        private NTPService ntpService;

        /** running status. */
        private boolean running = false;


        /** MBean */
        private ClockMonitorMBean mbean;


        /**
         * Startup the clock monitoring subsystem.
         */
        public Object startup(AlertQueue alerter)
        {
            synchronized (this)
            {
                if(!running)
                {
                    // initialize monitor objects
                    ClockAlerter clockAlerter = new ClockAlerter(alerter,
                            ALERT_EMAIL, ALERT_SHOULD_PAGE,
                            ALERT_INTERVAL_MINUTES);

                    ClockMonitor delegate =
                            new ClockMonitor(clockAlerter,
                                    MAX_TCAL_SAMPLE_MILLIS,
                                    MAX_NTP_SAMPLE_MILLIS,
                                    SYSTEM_CLOCK_ALERT_THRESHOLD_MILLIS,
                                    MASTER_CLOCK_ALERT_THRESHOLD_MILLIS,
                                    SYSTEM_CLOCK_SAMPLE_WINDOW,
                                    MASTER_CLOCK_SAMPLE_WINDOW,
                                    MAX_CONSECUTIVE_NTP_REJECTS);

                    //activate the new threads atomically
                    atomicStartup(clockAlerter, delegate);

                    running = true;

                    // stash the clock monitor as the mbean
                    mbean = delegate;
                }
                else
                {
                    // be idempotent, but complain
                    logger.warn("Redundent attempt to start the clock monitor" +
                            " by thread " + Thread.currentThread().getName());
                }
                return mbean;
            }

        }

        /**
         * Prevent half-way startup.  This is most likely when the
         * NTP host is not resolvable.
         */
        private void atomicStartup(final ClockAlerter clockAlerter,
                                   final ClockProcessor delegate)
        {
            try
            {
                //start up the reactor
                reactor = new ClockReactor(delegate);
                ((ClockReactor)reactor).startup();

                // start the NTP monitor
                ntpService = new NTPService(NTP_HOST, NTP_QUERY_PERIOD_SECONDS,
                        reactor, clockAlerter);
                ntpService.startup();
            }
            catch (Throwable th)
            {
                if(reactor != null && ((ClockReactor)reactor).isRunning())
                {
                    ((ClockReactor)reactor).shutdown();
                }

                if(ntpService != null && ntpService.isRunning())
                {
                    ntpService.shutdown();
                }

                throw new Error(th);
            }
        }

        /**
         * Shutdown the clock monitoring subsystem.
         */
        public void shutdown()
        {
            synchronized (this)
            {
                if(running)
                {
                    ((ClockReactor)reactor).shutdown();
                    ntpService.shutdown();
                }
            }
        }

        @Override
        public void process(final ClockProcessor.GPSSnapshot gpssnap)
        {
            reactor.process(gpssnap);
        }

        @Override
        public void process(final ClockProcessor.TCALMeasurement tcal)
        {
            reactor.process(tcal);
        }

        @Override
        public void process(final ClockProcessor.NTPMeasurement ntp)
        {
            reactor.process(ntp);
        }
    }

    /**
     * Null processor.
     */
    class NullProcessor implements ClockProcessor
    {
        @Override
        public final void process(final GPSSnapshot gpssnap)
        {
        }

        @Override
        public final void process(final TCALMeasurement tcal)
        {
        }

        @Override
        public final void process(final NTPMeasurement ntp)
        {
        }
    }

}

