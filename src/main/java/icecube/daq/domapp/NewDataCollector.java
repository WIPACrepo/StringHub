/* -*- mode: java; indent-tabs-mode:f; tab-width:4 -*- */

package icecube.daq.domapp;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.domapp.LocalCoincidenceConfiguration.RxMode;
import icecube.daq.domapp.dataacquisition.AcquisitionError;
import icecube.daq.domapp.dataacquisition.DataAcquisition;
import icecube.daq.domapp.dataprocessor.DataStats;
import icecube.daq.domapp.dataacquisition.Watchdog;
import icecube.daq.domapp.dataprocessor.DataProcessor;
import icecube.daq.domapp.dataprocessor.DataProcessorError;
import icecube.daq.domapp.dataprocessor.DataProcessorFactory;
import icecube.daq.domapp.dataprocessor.GPSProvider;
import icecube.daq.juggler.alert.Alerter.Priority;
import icecube.daq.livemoni.LiveTCalMoni;
import icecube.daq.time.gps.GPSService;
import icecube.daq.util.StringHubAlert;
import org.apache.log4j.Logger;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A data collection engine which drives the readout of the hits,
 * monitor, tcal, and supernova streams from a single DOM channel.
 * The object is a multi-threaded state machine.  The caller
 * commands certain state changes which trigger a response and
 * a state switch by the object's execution thread.  This allows
 * for non-blocking state switching on the caller's side.
 *
 * The output streams are passed into the object at construction
 * time and can be anything that supports the WritableByteChannel
 * interface.  The streaming data is output in 'TestDAQ' format
 * for all outputs with the structure:
 * <table summary="TestDAQ format">
 * <tr>
 * <th>Offset</th>
 * <th>Size</th>
 * <th>Data</th>
 * </tr>
 * <tr>
 * <td> 0 </td>
 * <td> 4 </td>
 * <td>Record length</td>
 * </tr>
 * <tr>
 * <td> 4 </td>
 * <td> 4 </td>
 * <td>Record ID</td>
 * </tr>
 * <tr>
 * <td> 8 </td>
 * <td> 8 </td>
 * <td>Mainboard ID</td>
 * </tr>
 * <tr>
 * <td> 16 </td>
 * <td> 8 </td>
 * <td>Reserved - must be 0</td>
 * </tr>
 * <tr>
 * <td> 24 </td>
 * <td> 8 </td>
 * <td>UT timestamp</td>
 * </tr>
 * </table> Supported records types are
 * <dl>
 * <dt>2</dt>
 * <dd>DOM engineering hit record</dd>
 * <dt>3</dt>
 * <dd>DOM delta-compressed hit records (including SLC hits)</dd>
 * <dt>102</dt>
 * <dd>DOM monitoring records</dd>
 * <dt>202</dt>
 * <dd>TCAL records</dd>
 * <dt>302</dt>
 * <dd>Supernova scaler records</dd>
 * </dl>
 *
 * @author krokodil
 *
 * Note:
 * Initial implementation was taken from DataCollector.java revision 15482.
 */
public class NewDataCollector
    extends DataCollector
{
    private static final Logger logger = Logger.getLogger(NewDataCollector.class);


    private long                numericMBID;

    private volatile boolean    stop_thread;


    /**
     * Acquisition and processing workload is separated to
     * support multi-threaded mode with processing offloaded
     * from the thread servicing the device.
     */
    private final DataAcquisition dataAcquisition;
    private final DataProcessor dataProcessor;

    /** Counters populated by the data processor. */
    private final DataStats dataStats;


    /** watchdog */
    private final InterruptorTask watchdog = new InterruptorTask();


    private long    threadSleepInterval   = 50;

    private long nextTcalRead = 0;
    private long    tcalReadInterval      = 1000;



    // used to be set from a system property, now reads from the runconfig
    // intervals / enabled - True
    private boolean disable_intervals;



    private int     loopCounter           = 0;
    private volatile long      runStartUT = 0L;


    private boolean   latelyRunningFlashers;


    private static final DecimalFormat  fmt
            = new DecimalFormat("#0.000000000");

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSS");


    // Log acquisition diagnostics when acquisition is aborted by the watchdog
    private static final boolean VERBOSE_TIMEOUT_LOGGING = Boolean.getBoolean
            ("icecube.daq.domapp.datacollector.verbose-timeout-logging");


    public NewDataCollector(
            int card, int pair, char dom,
            String mbid,
            DOMConfiguration config,
            BufferConsumer hitsTo,
            BufferConsumer moniTo,
            BufferConsumer supernovaTo,
            BufferConsumer tcalTo,
            boolean enable_intervals)
    {
        super(card, pair, dom);
        this.card = card;
        this.pair = pair;
        this.dom = dom;

        setMainboardID(mbid);

        this.config = config;

        // should already be set this way, but asserting it anyway.
        runLevel = RunLevel.INITIALIZING;


        // Note: At this point the dom config and mbid are disseminated
        //       to the processing stack. This is a departure from past
        //       implementations that supported late binding of these
        //       members.  If this use case re-emerges, processor
        //       will need to support setters for these members.
        dataProcessor = DataProcessorFactory.buildProcessor(
                (card+""+pair+dom),
                config,
                numericMBID,
                new GPSProvider(card),
                hitsTo, supernovaTo, moniTo, tcalTo);

        dataStats = dataProcessor.getDataCounters();

        dataAcquisition = new DataAcquisition(card, pair, dom, dataProcessor);


        latelyRunningFlashers = false;

        // turn intervals on/off as requested
        disable_intervals = !enable_intervals;

        start();
    }

    /**
     * Idempotent setter for the DOM mainboard ID related members.
     *
     * This method detects alterations of the mbid emanating from
     * a mis-behaving DOMApp.
     *
     */
    private void setMainboardID(String mainboardID)
    {
        if(mbid != null)
        {
            //once set, mbid values must be invariant
            if( !mbid.equals(mainboardID) )
            {
                throw new IllegalArgumentException("Attempt to change mbid" +
                        " from [" + mbid + "] to [" + mainboardID + "]");
            }
            else
            {
                // noop, the values agree
            }
        }
        else
        {
            mbid = mainboardID;
            numericMBID = Long.parseLong(mbid, 16);
        }
    }

    public void close()
    {
        dataAcquisition.doClose();
    }

    /**
     * It is polite to call datacollectors by name like [00A]
     * @return canonical name string
     */
    private String canonicalName()
    {
        return "[" + card + "" + pair + dom + "]";
    }

    /**
     * Applies the configuration in this.config to the DOM
     *
     * @throws icecube.daq.domapp.dataacquisition.AcquisitionError
     */
    private void configure(DOMConfiguration config) throws AcquisitionError
    {
       dataAcquisition.doConfigure(config);
    }

    public void setLiveMoni(LiveTCalMoni moni)
    {
        try
        {
            dataProcessor.setLiveMoni(moni);
        }
        catch (DataProcessorError dataProcessorError)
        {
            logger.error("Unable to set TCAL monitor", dataProcessorError);
        }
    }

    public synchronized void signalShutdown()
    {
        stop_thread = true;
    }

    public String toString()
    {
        return getName();
    }

    /**
     * Attempt a TCAL.
     *
     * The contract for this method is intentionally soft.  Errors encountered
     * on the acquisition side are logged, but suppressed.  This helps ride
     * out some temporary bad conditions.
     *
     * Errors encountered on the processing side are propagated.  A processing
     * error is generally fatal to the channel.
     *
     * @exception DataProcessorError An error occurred while passing the
     *            time calibration to the data processor.  This indicates
     *            a serious problem.
     */
    private void attemptRapCal() throws DataProcessorError
    {
        try
        {
            execRapCal();
        }
        catch (AcquisitionError acquisitionError)
        {
            logger.error("Ignoring tcal error", acquisitionError);
        }
    }

    /**
     * Execute a rapcal and queue the time calibration to the data processor.
     *
     * All errors, including potentially transient acquisition errors are
     * propagated. Most calling methods should be using attemptRapCal()
     * which silently ignors acquisition errors.
     *
     * @exception AcquisitionError An error occurred while acquiring the
     *            time calibration.  Often this is a transient condition.
     *
     * @exception DataProcessorError An error occurred while passing the
     *            time calibration to the data processor.  This indicates
     *            a serious problem.
     */
    private void execRapCal() throws DataProcessorError, AcquisitionError
    {
            dataAcquisition.doTCAL(watchdog);
            nextTcalRead = System.currentTimeMillis() + tcalReadInterval;
    }

    /**
     * The process is controlled by the runLevel state flag ...
     * <dl>
     * <dt>CONFIGURING (1)</dt>
     * <dd>signal a configure needed - successful configure will propagate the
     * state to CONFIGURED.</dd>
     * <dt>CONFIGURED (2)</dt>
     * <dd>the DOM is now configured and ready to start.</dd>
     * <dt>STARTING (3)</dt>
     * <dd>the DOM has received the start signal and is in process of starting
     * run.</dd>
     * <dt>RUNNING (4)</dt>
     * <dd>the thread is acquiring data.</dd>
     * <dt>STOPPING (5)</dt>
     * <dd>the DOM has received the stop signal and is in process of returning
     * to the CONFIGURED state.</dd>
     * </dl>
     */
    public void run()
    {


        // Defines the activation point of the data collector thread.
        // Arrange for a watchdog that monitors liveliness as well
        // as providing logic that reports unrequested exits and executes
        // cleanup.
        try
        {
            logger.debug("Begin data collection thread");
            watchdog.enable();
			launch_runcore();
        }
        catch (Throwable th)
        {
            watchdog.reportAbnormalExit(th);
        }
        finally
        {
            watchdog.handleExit();
            logger.info("End data collection thread.");
        }

    } /* END OF run() METHOD */

	/**
     * Initiate the core data acquisition run loop.
     *
     * NOTES:
     * If the user explicitly disables intervals setting
     * runConfig/Stringhub[id=X]/intervals/enable/false
     * or the domapp version is not high enough to support
     * intervals it will default to the query method.  Otherwise, intervals
     * will be used.
	*/
	private void launch_runcore() throws Exception
	{

        /*
         * I need the MBID right now just in case I have to shut this stream
          * down.
         */
        mbid = dataAcquisition.getMBID();
        numericMBID = Long.parseLong(mbid, 16);

        String reportedMBid =
                dataAcquisition.doInitialization(watchdog, alwaysSoftboot);

        // mbid reported from domapp messaging will be compared with mbid
        // obtained from proc file
        setMainboardID(reportedMBid);

        setRunLevel(RunLevel.IDLE);

        watchdog.ping();

//        if (logger.isDebugEnabled()) {
//            logger.debug("Found DOM " + mbid + " running " +
//                          app.getRelease());
//        }

        // prohibit running without a gps reading
        ensureGPSReady();

        // Grab 2 RAPCal data points to get started
        for (int nTry = 0;
             nTry < 10 && dataStats.getValidRAPCalCount() < 2; nTry++)
        {
            watchdog.sleep(100);
            attemptRapCal();
        }

        runcore(!disable_intervals);
	}

    /**
     * Wait for the gps service to obtain a valid reading. The expectation
     * for a well behaved hub is to be ready quickly, so this method warns
     * if it takes time.
     *
     * @throws Exception Exceeded the time allowed.  This can be judged by
     * the watchdog via interruption or by exceeding the limits
     * defined in the function.
     */
    private void ensureGPSReady() throws Exception
    {
        int attempts = 0;
        while (!GPSService.getInstance().waitForReady(card, 3000))
        {
            if( ++attempts> 4 )
            {
                throw new Exception("GPS service is not available.");
            }
            logger.warn("GPS service on card " + card +
                    " is slow to start, waiting...");
        }
    }

    /**
     * A wrapper around the setRunLevel for run level changes
     * initiated internally on the acquisition thread. This
     * method informs the data processor of state changes in series
     * with the data stream.
     */
    private void setRunLevelInternal(final RunLevel runLevel)
            throws DataProcessorError
    {
        setRunLevel(runLevel);
        dataProcessor.runLevel(runLevel);
    }

    /**
     * The core acquisition loop.
     *
     * NOTE: Generally during transitions, the data processor is not synced
     *       and the processor thread runs independently. Transitions from
     *       RUNNING to CONFIGURED require a sync with the processor thread.
     *       This sync is required to meet the contract that all data has
     *       been dispatched prior to returning to the configured state.
     *
     * @throws Exception
     */
    private void runcore(final boolean useIntervals) throws Exception
    {
        while (!stop_thread)
        {
            long t = System.currentTimeMillis();
            boolean tired = true;

            // Ping the watchdog task
            watchdog.ping();

            loopCounter++;

            /* Do TCAL and GPS -- this always runs regardless of the run state */
            if (t >= nextTcalRead)
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("Doing TCAL - runLevel is " + getRunLevel());
                }
                attemptRapCal();
            }

			switch (getRunLevel())
            {
            case RUNNING:
                if(useIntervals)
                {
                    tired = dataAcquisition.doInterval(watchdog);
                }
                else
                {
                    tired = dataAcquisition.doPolling(watchdog, t);
                }

                //todo This is not an optimal way to track this data. For the
                //     time being, it avoids a callback interface.
                this.firstHitTime = dataStats.getFirstHitTime();
                this.lastHitTime = dataStats.getLastHitTime();

                break;

            case CONFIGURING:
                /* Need to handle a configure */
                logger.debug("Got CONFIGURE signal.");
                configure(config);
                logger.debug("DOM is configured.");
                setRunLevelInternal(RunLevel.CONFIGURED);
                break;

            case STARTING:
                if (logger.isDebugEnabled()) {
                    logger.debug("Got START RUN signal " + canonicalName());
                }
                runStartUT = dataAcquisition.doBeginRun(watchdog);
                logger.debug("DOM is running.");
                setRunLevelInternal(RunLevel.RUNNING);
                break;

            case STARTING_SUBRUN:
                /*
                 * I must stop the current run unless I was just running a flasher run
                 * on this DOM and I am just changing the flasher parameters.
                 */
                logger.info("Starting subrun - flasher config is " +
                        (flasherConfig == null ? "not" : "") + " null / lately " +
                        (latelyRunningFlashers ? "" : "not") + " running flashers.");
                if (!(latelyRunningFlashers && flasherConfig != null))
                {
                    setRunLevel(RunLevel.STOPPING_SUBRUN);
                    dataAcquisition.doEndRun();
                    setRunLevelInternal(RunLevel.CONFIGURING);
                    latelyRunningFlashers = false;
                }
                if (flasherConfig != null)
                {
                    logger.info("Starting flasher subrun");
                    if (latelyRunningFlashers)
                    {
                        logger.info("Only changing flasher board configuration");
                        runStartUT = dataAcquisition.doChangeFlasherRun(flasherConfig);
                    }
                    else
                    {
                        DOMConfiguration tempConfig = new DOMConfiguration(config);
                        tempConfig.setHV(-1);
                        tempConfig.setTriggerMode(TriggerMode.FB);
                        LocalCoincidenceConfiguration lcX = new LocalCoincidenceConfiguration();
                        lcX.setRxMode(RxMode.RXNONE);
                        tempConfig.setLC(lcX);
                        tempConfig.setEngineeringFormat(
                                new EngineeringRecordFormat((short) 0, new short[] { 0, 0, 0, 64 })
                                );
                        tempConfig.setMux(MuxState.FB_CURRENT);
                        configure(tempConfig);
                        watchdog.sleep(new Random().nextInt(250));
                        logger.info("Beginning new flasher board run");
                        runStartUT = dataAcquisition.doBeginFlasherRun(flasherConfig);
                    }
                    latelyRunningFlashers = true;
                }
                else
                {
                    logger.info("Returning to non-flashing state");
                    configure(config);
                    runStartUT = dataAcquisition.doBeginRun(watchdog);

                }
                setRunLevelInternal(RunLevel.RUNNING);
                break;

            case PAUSING:
                if (logger.isDebugEnabled()) {
                    logger.debug("Got PAUSE RUN signal " + canonicalName());
                }
                dataAcquisition.doPauseRun();

                //NOTE: Data may be buffered waiting for bounding tcals.
                //      perform a final tcal to cause the release of this
                //      data. if this tcal fails, the contract of the
                //      CONFIGURED state will be violated. A poorly
                //      timed wild tcal dooms us here.
                execRapCal();

                dataProcessor.sync();
                setRunLevelInternal(RunLevel.CONFIGURED);
                break;

            case STOPPING:
                if (logger.isDebugEnabled()) {
                    logger.debug("Got STOP RUN signal " + canonicalName());
                }
                dataAcquisition.doEndRun();
                dataProcessor.eos();
                dataProcessor.sync();
                setRunLevelInternal(RunLevel.CONFIGURED);
                break;
            }

            if (tired)
            {
                if (logger.isDebugEnabled()) {
                    logger.debug("Runcore loop is tired - sleeping " +
                            threadSleepInterval + " ms.");
                }
                watchdog.sleep(threadSleepInterval);
            }
        } /* END RUN LOOP */
    } /* END METHOD */


    @Override
    public long getRunStartTime()
    {
        return runStartUT;
    }

    @Override
    public long getNumHits()
    {
        return dataStats.getNumHits();
    }

    @Override
    public long getNumMoni()
    {
        return dataStats.getNumMoni();
    }

    @Override
    public long getNumTcal()
    {
        return dataStats.getValidRAPCalCount();
    }

    @Override
    public long getNumBadTcals()
    {
        return dataStats.getErrorRAPCalCount();
    }

    @Override
    public double getCableLength()
    {
        return dataStats.getCableLength();
    }

    @Override
    public double getDOMFrequencySkew()
    {
        return dataStats.getDomFrequencySkew();
    }

    @Override
    public long getFirstDORTime()
    {
        return dataStats.getFirstDORTime();
    }

    @Override
    public long getLastDORTime()
    {
        return dataStats.getLastDORTime();
    }

    @Override
    public long getFirstDOMTime()
    {
        return dataStats.getFirstDOMTime();
    }

    @Override
    public long getLastDOMTime()
    {
        return dataStats.getLastDOMTime();
    }

    @Override
    public long getNumSupernova()
    {
        return dataStats.getNumSupernova();
    }

    @Override
    public long getAcquisitionLoopCount()
    {
        return loopCounter;
    }

    /**
     * A watchdog timer task to make sure data stream does not die.
     *
     * The Data Collector thread enables the watchdog at activation and
     * notifies at exit.  In addition, methods running on the collector
     * thread which are interruptable route InterruptedException handling
     * here.
     */
    class InterruptorTask extends TimerTask implements Watchdog
    {
        private volatile boolean pinged = false;
        private volatile boolean aborting = false;
        private volatile boolean abnormalExit = false;

        private volatile long lastPingNano = System.nanoTime();

        private final Timer watcher;

        private long DELAY =
                Integer.getInteger("icecube.daq.domapp.datacollector.watchdog-delay-millis", 30000);
        private long PERIOD =
                Integer.getInteger("icecube.daq.domapp.datacollector.watchdog-period-millis", 10000);

        InterruptorTask()
        {
            watcher = new Timer(NewDataCollector.this.getName() + "-timer");
        }

        public void enable()
        {
            watcher.schedule(this, DELAY, PERIOD);
        }

        public void run()
        {
            synchronized (this)
            {
                if (!pinged)
                {
                    long silentPeriodNano = System.nanoTime() - lastPingNano;

                    aborting = true;
                    stop_thread = true;

                    logger.error("data collection thread has become " +
                            "non-responsive for ["+(silentPeriodNano/1000000)+" " +
                            "ms] - aborting.");
                    dataAcquisition.doClose();
                    NewDataCollector.this.interrupt();
                }
                pinged = false;
            }
        }

        public void ping()
        {
            synchronized (this)
            {
                long now = System.nanoTime();
                if (logger.isDebugEnabled())
                {
                    long silentPeriodNano = now - lastPingNano;
                    logger.debug("pinged at " + fmt.format(silentPeriodNano * 1.0E-09));
                }
                pinged = true;
                lastPingNano = now;
            }
        }

        public boolean isAborting()
        {
            return aborting;
        }

        /**
         * Defines sleeping behavior for code subjected to watchdog
         * control.
         */
        public void sleep(final long millis)
        {
            try
            {
                Thread.sleep(millis);
            }
            catch (InterruptedException ie)
            {
                if(aborting)
                {
                    // expected
                }
                else
                {
                    logger.error("Unexpected Interrupt", ie);
                }
            }
        }

        /**
         * Defines interrupt logging behavior for code subjected to watchdog
         * control.
         */
        public void handleInterrupted(final InterruptedException ie)
        {
            if(aborting)
            {
                //expected
            }
            else
            {
                logger.error("Unexpected Interrupt", ie);
            }
        }

        /**
         * Reports abnormal data collector exit, resolving races between
         * watchdog abort and other exceptions in favor of the
         * watchdog.
         */
        public void reportAbnormalExit(Throwable reason)
        {
            if(aborting)
            {
                logger.error("DataCollector aborted at:", reason);
            }
            else
            {
                abnormalExit = true;
                logger.error("Intercepted error in DataCollector runcore",
                        reason);
            }
        }

        /**
         * Defines behavior at data collector exit.
         *
         * Handles the potential race conditions between the the data
         * collector thread, the watchdog and external control by
         * ensuring that any unrequested exit results in an alert
         * and a run level of ZOMBIE.
         */
        public void handleExit()
        {
            // Note: The watchdog timer remains in effect within this method.
            //       If exit is blocked by the buffer consumers, or by
            //       outstanding processing the watchdog timer will free
            //       it with an interrupt.
            try
            {
                // clear interrupted flag if it is set
                Thread.interrupted();
                ping();

                // Generate an alert if this was an unrequested exit
                if(aborting || abnormalExit)
                {
                    setRunLevel(RunLevel.ZOMBIE);
                    StringHubAlert.sendDOMAlert(alertQueue, Priority.EMAIL,
                            "Zombie DOM", card, pair, dom,
                            mbid, name, major, minor, runNumber,
                            lastHitTime);

                    //Note: recent acquisition performance may be useful
                    //      to diagnose unexplained timeouts, but is just
                    //      log file noise in most typical cases.
                    if(VERBOSE_TIMEOUT_LOGGING)
                    {
                        List<StringBuilder> lines = dataAcquisition.logHistory();
                        for(StringBuilder line : lines)
                        {
                            logger.error(line);
                        }
                    }
                }
                else
                {
                    //clean exit
                    // We remain in whatever state we were in when
                    // signalShutdown() took effect.
                }

                // shut down the processor
                try
                {
                    ping();
                    dataProcessor.shutdown();
                    logger.info("Data Processor shutdown for "
                            + canonicalName());
                }
                catch (DataProcessorError dpe)
                {
                    logger.error("Error while shutting down data processor for "
                            + canonicalName(), dpe);
                }

            }
            catch (Throwable th)
            {
                // This is a truly uncontrolled exit and should be considered
                // a coding error.
                logger.error("Error encountered while shutting down " +
                        "collection thread.", th);
            }
            finally
            {
                watcher.cancel();
            }
        }

    }

    @Override
    public synchronized long getLastTcalTime()
    {
        return dataStats.getLastTcalUT();
    }

    @Override
    public double getHitRate()
    {
        return dataStats.getHitRate();
    }

    @Override
    public double getHitRateLC()
    {
        return dataStats.getLCHitRate();
    }

    @Override
    public long getLBMOverflowCount()
    {
        return dataStats.getNumLBMOverflows();
    }

    @Override
    public String getRunState()
    {
        return getRunLevel().toString();
    }


    //todo - replace from processing or remove
    @Override
    public long getAverageHitAcquisitionLatencyMillis()
    {
        //return (long)cycleMonitor.avgHitAcquisitionLatencyMillis.getAverage();
        return -1;
    }

    @Override
    public String getAcquisitionStartTime()
    {
        //NOTE: This is based on the local system clock.
        synchronized (dateFormat)
        {
            return dateFormat.format(new Date(dataAcquisition.getRunStartSystemTime()));
        }
    }

}
