/* -*- mode: java; indent-tabs-mode:f; tab-width:4 -*- */

package icecube.daq.domapp;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.domapp.LocalCoincidenceConfiguration.RxMode;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.Driver;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.IDriver;
import icecube.daq.dor.TimeCalib;
import icecube.daq.juggler.alert.Alerter.Priority;
import icecube.daq.livemoni.LiveTCalMoni;
import icecube.daq.rapcal.RAPCal;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.rapcal.ZeroCrossingRAPCal;
import icecube.daq.time.gps.GPSService;
import icecube.daq.time.gps.GPSServiceError;
import icecube.daq.time.gps.IGPSService;
import icecube.daq.util.RealTimeRateMeter;
import icecube.daq.util.SimpleMovingAverage;
import icecube.daq.util.StringHubAlert;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.apache.log4j.Logger;

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
 * <table summary="TestDAQ hit format">
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
 */
public class LegacyDataCollector
    extends DataCollector
{
    private long                numericMBID;

    // the driver code used to rebuild a path on every call
    // get the File object ONCE
    private File tcalFile;

    private IDOMApp             app;
    private RAPCal              rapcal;
    private IDriver             driver;
    private volatile boolean    stop_thread;
    private final UTCMessageStream hitStream;
    private final UTCMessageStream moniStream;
    private final UTCMessageStream tcalStream;
    private final UTCMessageStream supernovaStream;

    /*
     * Message time-order is enforce within this epsilon, in 1/10 nano.
     * Defaults to 10 microseconds.
     */
    private final long MESSAGE_ORDERING_EPSILON =
            Integer.getInteger("icecube.daq.domapp.datacollector" +
                    ".message-ordering-epsilon",
                    10000);

    private final InterruptorTask watchdog = new InterruptorTask();

    private static final Logger         logger  = Logger.getLogger(LegacyDataCollector.class);

    private static final DecimalFormat  fmt     = new DecimalFormat("#0.000000000");

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSS");


    // TODO - replace these with properties-supplied constants
    // for now they are totally reasonable
    private long    threadSleepInterval   = 50;

    // Note: data, moni and supernova polling intervals are only
    // relevant when running in non-interval mode.
    private long nextSupernovaRead = 0;
    private long nextTcalRead = 0;
    private long nextMoniRead = 0;
    private long nextDataRead = 0;

    private long    dataReadInterval      = 10;
    private long    moniReadInterval      = 1000;
    private long    tcalReadInterval      = 1000;
    private long    supernovaReadInterval = 1000;

    // statistics on data packet size
    // welford's method
    private double data_m_n = 0;
    private double data_s_n = 0;
    private long data_count = 0;
    private long data_max = 0;
    private long data_min = 4092;
    private long data_zero_count = 0;
    private static final DecimalFormat  data_fmt = new DecimalFormat("0.###");

    private static final boolean ENABLE_STATS = Boolean.getBoolean(
	    "icecube.daq.domapp.datacollector.enableStats");

    // used to be set from a system property, now reads from the runconfig
    // intervals / enabled - True
    private boolean disable_intervals;
    private boolean supernova_disabled = true;

    private static final long INTERVAL_MIN_DOMAPP_PROD_VERSION = 445;
    private static final long BASE_TEST_VERSION = 4000;
    private static final long INTERVAL_MIN_DOMAPP_TEST_VERSION = 4477;

    private int     validRAPCalCount;
    private int     errorRAPCalCount;
    private double  cableLength;
    private double  epsilon;

    private int     numHits               = 0;
    private int     numMoni               = 0;
    private int     numSupernova          = 0;
    private int     loopCounter           = 0;
    private volatile long       lastTcalUT;
    private volatile long       runStartUT = 0L;
    private int     numLBMOverflows       = 0;

    private RealTimeRateMeter   rtHitRate, rtLCRate;

    private long        nextSupernovaDomClock;
    private HitBufferAB abBuffer;
    private int         numSupernovaGaps;


	private ByteBuffer intervalBuffer;

    /**
     * The waitForRAPCal flag if true will force the time synchronizer
     * to only output DOM timestamped objects when a RAPCal before and
     * a RAPCal after the object time have been registered.  This may
     * give some improvement to the reconstructed UTC time because
     * interpolation instead of extrapolation is used.
     */
    private final boolean       waitForRAPCal = Boolean.getBoolean(
            "icecube.daq.domapp.datacollector.waitForRAPCal");

    /**
     * The engineeringHit buffer magic number used internally by stringHub.
     */
    private final int           MAGIC_ENGINEERING_HIT_FMTID = 2;

    /**
     * The SLC / delta-compressed hit buffer magic number.
     */
    private final int MAGIC_COMPRESSED_HIT_FMTID  = 3;
    private final int MAGIC_MONITOR_FMTID         = 102;
    private final int MAGIC_TCAL_FMTID            = 202;
    private final int MAGIC_SUPERNOVA_FMTID       = 302;
    private boolean   latelyRunningFlashers;


    /**
     * A helper class to deal with the now-less-than-trivial
     * hit buffering which circumvents the hit out-of-order
     * issues.
     *
     * Also Feb-2008 added ability to buffer hits waiting for RAPCal
     *
     * @author kael
     *
     */
    private class HitBufferAB
    {
        private LinkedList<ByteBuffer> alist, blist;
        HitBufferAB()
        {
            alist = new LinkedList<ByteBuffer>();
            blist = new LinkedList<ByteBuffer>();
        }

        void pushA(ByteBuffer buf)
        {
            alist.addLast(buf);
        }

        void pushB(ByteBuffer buf)
        {
            blist.addLast(buf);
        }

        private ByteBuffer popA()
        {
            return alist.removeFirst();
        }

        private ByteBuffer popB()
        {
            return blist.removeFirst();
        }

        ByteBuffer pop()
        {
            /*
             * Handle the special cases where only one ATWD is activated
             * presumably because of broken hardware.
             */
            if (config.getAtwdChipSelect() == AtwdChipSelect.ATWD_A)
            {
                if (alist.isEmpty()) return null;
                return popA();
            }
            if (config.getAtwdChipSelect() == AtwdChipSelect.ATWD_B)
            {
                if (blist.isEmpty()) return null;
                return popB();
            }
            if (alist.isEmpty() || blist.isEmpty()) return null;
            long aclk = alist.getFirst().getLong(24);
            long bclk = blist.getFirst().getLong(24);
            if (aclk < bclk)
            {
                if (!waitForRAPCal || rapcal.laterThan(aclk))
                {
                    return popA();
                }
                else
                {
                    if (logger.isDebugEnabled()) logger.debug("Holding back A hit at " + aclk);
                    return null;
                }
            }
            else if (!waitForRAPCal || rapcal.laterThan(bclk))
            {
                return popB();
            }
            if (logger.isDebugEnabled()) logger.debug("Holding back B hit at " + bclk);
            return null;
        }
    }

    /**
     * Collects diagnostics pertaining to a single data
     * collection cycle.
     */
    private class CycleStats
    {

        final long sequence;

        final long lastCycleNanos;
        final long lastCycleDOMClk;

        long cycleStartNano;
        long cycleStopNano;

        long messagesAcquired;
        long bytesAcquired;
        long hitCount;

        long firstHitDOMClk = -1;
        long lastHitDOMClk = -1;

        //calculated at cycle completion
        long cycleDurationNanos;
        long cycleGapDurationNanos;
        long lastHitAgeNanos;
        long dataSpanNanos;
        long dataGapNanos;

        MessageStats currentMessage;
        private class MessageStats
        {
            final long cycleNumber;
            final long messageNumber;

            long messageReadStartNano;
            long messageRecvNano;
            long messageStopNano;

            int messageByteCount;
            int messageHitCount;

            long messageFirstHitDOMClk = -1;
            long messageLastHitDOMClk = -1;

            //calculated at message processing completion
            long messageReadDurationNanos;
            long messageDurationNano;
            long messageDataSpanNanos;

            public MessageStats(long cycleNumber, long messageNumber)
            {
                this.cycleNumber = cycleNumber;
                this.messageNumber = messageNumber;
            }

            final void reportHitData(final ByteBuffer hitBuf)
            {
                long domclk = hitBuf.getLong(24);

                messageLastHitDOMClk = domclk;
                if(messageFirstHitDOMClk == -1)
                {
                    messageFirstHitDOMClk = domclk;
                }
                messageHitCount++;
            }

            final void reportMessageDataReceived(ByteBuffer data)
            {
                messageRecvNano = now();
                messageByteCount = data.remaining();
            }

            final void reportMessageProcessingComplete(final long stopTimeNanos)
            {
                messageStopNano = stopTimeNanos;

                // system timer durations
                messageReadDurationNanos = messageRecvNano - messageReadStartNano;
                messageDurationNano = messageStopNano - messageReadStartNano;

                // data stream spans
                if (messageHitCount > 0)
                {
                    messageDataSpanNanos = (messageLastHitDOMClk - messageFirstHitDOMClk) * 25;
                }
                else
                {
                    messageDataSpanNanos = 0;
                }
            }

            final StringBuilder verbosePrint()
            {
                StringBuilder info = new StringBuilder(256);
                long durationMillis = messageDurationNano / 1000000;
                long readMillis = messageReadDurationNanos / 1000000;
                long processMillis = (messageDurationNano-messageReadDurationNanos) / 1000000;
                long dataMillisInMessage = (messageDataSpanNanos / 1000000);

                info.append("[").append(card).append(pair).append(dom).append
                        ("]")
                        .append(" message [").append(cycleNumber)
                        .append(".").append(messageNumber).append("]")
                        .append(" bytes [").append(messageByteCount)
                        .append(" b]")
                        .append(" hits [").append(messageHitCount).append("]")
                        .append(" duration [").append(durationMillis)
                        .append(" ms]")
                        .append(" read-time [").append(readMillis).append(" ms]")
                        .append(" process-time [").append(processMillis)
                        .append(" ms]")
                        .append(" data-span [").append(dataMillisInMessage)
                        .append(" ms]");

                return info;
            }
        }
        List<MessageStats> messageStatsList = new LinkedList<MessageStats>();


        private CycleStats(long sequence, long lastCycleNanos,
                           long lastCycleDOMClk)
        {
            this.sequence = sequence;

            //NOTE: carrying last cycle timing data forward makes
            //      gap calculations more compact.
            this.lastCycleNanos = lastCycleNanos;
            this.lastCycleDOMClk = lastCycleDOMClk;
        }


        final void reportCycleStart()
        {
            cycleStartNano = now();
        }

        /**
         * For tracking time spent in driver
         */
        final void initiateMessageRead()
        {
            // Arbitrary, but close. Using one timestamp
            // makes message durations contiguous.
            long newMessageStartTime = now();

            // manage previous message
            final long messageNumber;
            if(currentMessage != null)
            {
                currentMessage.reportMessageProcessingComplete(newMessageStartTime);
                messageStatsList.add(currentMessage);
                messageNumber = currentMessage.messageNumber + 1;
            }
            else
            {
                messageNumber = 0;
            }

            currentMessage = new MessageStats(sequence, messageNumber);
            currentMessage.messageReadStartNano = newMessageStartTime;
        }

        final void reportDataMessageRcv(ByteBuffer data)
        {
            currentMessage.reportMessageDataReceived(data);

            bytesAcquired += currentMessage.messageByteCount;
            messagesAcquired++;

            if(ENABLE_STATS)
            {
                trackMessageStats(data);
            }
        }

        final void reportHitData(ByteBuffer hitBuf)
        {

            long domclk = hitBuf.getLong(24);

            lastHitDOMClk = domclk;
            if(firstHitDOMClk == -1)
            {
                firstHitDOMClk = domclk;
            }
            hitCount++;

            currentMessage.reportHitData(hitBuf);
        }


        final void reportCycleStop()
        {
            cycleStopNano = now();

            //manage last message of cycle
            if(currentMessage != null)
            {
                currentMessage.reportMessageProcessingComplete(cycleStopNano);
                messageStatsList.add(currentMessage);
            }

            // system timer durations
            cycleDurationNanos = cycleStopNano - cycleStartNano;
            cycleGapDurationNanos = cycleStartNano -lastCycleNanos;

            // data stream spans
            if (hitCount > 0)
            {
                lastHitAgeNanos = currentMessage.messageRecvNano -
                        domToSystemTimer.translate(lastHitDOMClk);
                dataSpanNanos = (lastHitDOMClk - firstHitDOMClk) * 25;

                if(lastCycleDOMClk == -1)
                {
                    dataGapNanos = 0;
                }
                else
                {
                    dataGapNanos = (firstHitDOMClk -lastCycleDOMClk) * 25;
                }
            }
            else
            {
                lastHitAgeNanos = 0;
                dataSpanNanos = 0;
                dataGapNanos = 0;
            }

        }

        final void trackMessageStats(ByteBuffer msgBuffer)
        {
            // generate some stats as to the average size
            // of hit bytebuffers

            // Compute the mean and variance of data message sizes
            // This is an implementation of welfords method
            // http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance
            int remaining = msgBuffer.remaining();
            if(remaining>0) {
                double m_new = data_m_n + ( remaining - data_m_n) / ( data_count+1.0);
                data_s_n = data_s_n + ( remaining - data_m_n) * ( remaining - m_new);
                data_m_n = m_new;
                data_count++;
                if (remaining>data_max) {
                    data_max = remaining;
                }
                if (remaining<data_min) {
                    data_min = remaining;
                }
            } else {
                data_zero_count++;
            }
        }

        final long now()
        {
            return System.nanoTime();
        }

        final List<StringBuilder> verbosePrint()
        {
            List<StringBuilder> lines = new ArrayList<StringBuilder>
                    (INCLUDE_MESSAGE_DETAILS ? 25 : 1);

            if(INCLUDE_MESSAGE_DETAILS)
            {
                for (CycleStats.MessageStats messageStats: messageStatsList)
                {
                    lines.add(messageStats.verbosePrint());
                }
            }

            StringBuilder info = new StringBuilder(256);
            long durationMillis = cycleDurationNanos / 1000000;
            long millisSinceLastCycle = cycleGapDurationNanos /1000000;
            long dataMillisInCycle = (dataSpanNanos/ 1000000);
            long dataMillisSinceLastCycle = dataGapNanos / 1000000;
            long lbmBacklogMillis = lastHitAgeNanos / 1000000;

            info.append("[").append(card).append(pair).append(dom).append("]")
               .append(" cycle [").append(sequence).append("]")
               .append(" messages [").append(messagesAcquired)
               .append ("]")
               .append(" bytes [").append(bytesAcquired / 1024)
               .append(" kb]")
               .append(" hits [").append(hitCount).append("]")
               .append(" duration [").append(durationMillis)
               .append(" ms]")
               .append(" gap [").append(millisSinceLastCycle).append(" ms]")
               .append(" data-span [").append(dataMillisInCycle)
               .append(" ms]")
               .append(" data-gap [").append(dataMillisSinceLastCycle)
               .append(" ms]")
               .append(" lbm-backlog [").append(lbmBacklogMillis)
               .append(" ms]");

            lines.add(info);

            return lines;
        }

    }

    /**
     * Collects data acquisition diagnostics for all
     * data collection cycles in a run.
     */
    class CycleMonitor
    {
        long runStartTime; // local system clock
        long runStartNano; // system timer

        long sequence = -1;

        Queue<CycleStats> history = new LinkedList<CycleStats>();

        SimpleMovingAverage avgCycleDurationMillis =
                new SimpleMovingAverage(10);

        SimpleMovingAverage avgHitAcquisitionLatencyMillis =
                new SimpleMovingAverage(10);

        SimpleMovingAverage avgCycleDataSpanMillis = new SimpleMovingAverage
                (10);

        long lastCycleNanos;
        long lastCycleDOMClk = -1;

        boolean isLBMReported;

        final void reportRunStart()
        {
            runStartNano = now();
            runStartTime = System.currentTimeMillis();
            lastCycleNanos = runStartNano;
        }

        final CycleStats initiateCycle()
        {
            CycleStats cycleStats =
                    new CycleStats(++sequence, lastCycleNanos, lastCycleDOMClk);
            cycleStats.reportCycleStart();

            return cycleStats;
        }

        void reportLBMOverflow()
        {
            isLBMReported = true;
        }


        void completeCycle(final CycleStats cycle)
        {
            cycle.reportCycleStop();

            avgCycleDurationMillis.add(cycle.cycleDurationNanos / 1000000);
            avgCycleDataSpanMillis.add(cycle.dataSpanNanos / 1000000);
            lastCycleNanos = cycle.cycleStopNano;
            lastCycleDOMClk = cycle.lastHitDOMClk;

            //Note: We are really only interested in the
            //      age of the last hit in the cycle, the
            //      hits at the beginning have been intentionally
            //      buffered (via intervals, sleeps) to increase
            //      throughput. Increasing latency at the end
            //      of an acquisition cycle is an indication
            //      that the DOM LMB depth is increasing.
            //
            //      A value from a hit-less cycle is discarded.
            if(cycle.hitCount > 0)
            {
                avgHitAcquisitionLatencyMillis.add(cycle
                        .lastHitAgeNanos / 1000000);
            }

            history.add(cycle);
            if( history.size() > 10)
            {
                history.remove();
            }

            // we let the monitor handle LBM logging at the
            // end of the cycle rather than at lbm detection so that the
            // diagnostics include the current cycle
            if(VERBOSE_LBM_LOGGING && isLBMReported)
            {
                isLBMReported = false;
                List<StringBuilder> lines = logHistory();
                for(StringBuilder line: lines)
                {
                    logger.error(line);
                }
            }

            //debug mode, print details for every cycle
            if(VERBOSE_CYCLE_LOGGING)
            {
                List<StringBuilder> message = cycle.verbosePrint();
                for(StringBuilder line : message)
                {
                    logger.info(line);
                }
            }

        }

        final StringBuilder logAverages()
        {
            StringBuilder sb = new StringBuilder(128);
            sb.append("avg-lbm-backlog [").append
                    (avgHitAcquisitionLatencyMillis.getAverage())
                    .append(" ms]")
                    .append(" avg-cycle-duration [").append
                    (avgCycleDurationMillis.getAverage())
                    .append(" ms]")
                    .append(" avg-data-span [").append
                    (avgCycleDataSpanMillis.getAverage())
                    .append(" ms]");
            return sb;
        }

        final List<StringBuilder> logHistory()
        {
            final List<StringBuilder> lines = new ArrayList<StringBuilder>
                    (INCLUDE_MESSAGE_DETAILS ? 250 : 10);
            lines.add(logAverages());

            for(CycleStats cycleStats : history)
            {
                lines.addAll(cycleStats.verbosePrint());
            }

            return lines;
        }

        final long now()
        {
            return System.nanoTime();
        }
    }
    private CycleMonitor cycleMonitor = new CycleMonitor();

    // Log cycle diagnostics when LBM Overflow is reported
    private static final boolean VERBOSE_LBM_LOGGING = Boolean.getBoolean
            ("icecube.daq.domapp.datacollector.verbose-lbm-logging");

    // Log cycle diagnostics when acquisition is aborted by the watchdog
    private static final boolean VERBOSE_TIMEOUT_LOGGING = Boolean.getBoolean
            ("icecube.daq.domapp.datacollector.verbose-timeout-logging");

    // Log cycle diagnostics for every cycle
    private static final boolean VERBOSE_CYCLE_LOGGING = Boolean.getBoolean
            ("icecube.daq.domapp.datacollector.verbose-cycle-logging");

    // Log message diagnostics in addition to the cycle diagnostics
    private static final boolean INCLUDE_MESSAGE_DETAILS = Boolean.getBoolean
            ("icecube.daq.domapp.datacollector.include-message-details");



    /**
     * Provides a loosely calibrated mapping from the DOM clock
     * to the system monotonic clock.
     *
     * This mapping is for diagnostic use only as the quality of the
     * calibration is not guaranteed.
     *
     */
    private class DOMToSystemTimer
    {
        private long offsetNanos;

        void update(long domclk, long systemNanos)
        {
            offsetNanos = systemNanos - (domclk * 25);
        }

        long translate(long domclk)
        {
            return (domclk*25) + offsetNanos;
        }
    }
    private final DOMToSystemTimer domToSystemTimer = new
            DOMToSystemTimer();

    /**
     * Simple hits-only constructor (other channels suppressed)
     * @param chInfo structure containing DOM channel information
     * @param hitsTo BufferConsumer target for hits
     */
    public LegacyDataCollector(DOMChannelInfo chInfo, BufferConsumer hitsTo)
    {
        this(chInfo.card, chInfo.pair, chInfo.dom, null, hitsTo, null, null, null);
    }

    public LegacyDataCollector(
            int card, int pair, char dom,
            DOMConfiguration config,
            BufferConsumer hitsTo,
            BufferConsumer moniTo,
            BufferConsumer supernovaTo,
            BufferConsumer tcalTo)
	{
		// support class old signature
		// but default to disabling intervals
		this(card, pair, dom, null, config, hitsTo, moniTo, supernovaTo, tcalTo, false);
	}


    public LegacyDataCollector(
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

        //NOTE: Leave the initial value as null for clients without
        //      prior knowledge of the DOM mbid
        if(mbid != null)
        {
            setMainboardID(mbid);
        }

		//System.out.println("Enable stats: "+ENABLE_STATS+" intervals: "+ENABLE_INTERVAL);

        hitStream = new UTCMessageStream(hitsTo, "hit", MESSAGE_ORDERING_EPSILON);
        moniStream = new UTCMessageStream(moniTo, "moni", MESSAGE_ORDERING_EPSILON);
        tcalStream = new UTCMessageStream(tcalTo, "tcal", MESSAGE_ORDERING_EPSILON);
        supernovaStream = new UTCMessageStream(supernovaTo, "supernova", MESSAGE_ORDERING_EPSILON);

        this.driver = Driver.getInstance();

        // get and cache the gps file
        // and the tcal file
        tcalFile = this.driver.getTCALFile(card, pair, dom);

        String rapcalClass = System.getProperty(
                "icecube.daq.domapp.datacollector.rapcal",
                "icecube.daq.rapcal.ZeroCrossingRAPCal"
                );
        try
        {
            this.rapcal = (RAPCal) Class.forName(rapcalClass).newInstance();
        }
        catch (Exception ex)
        {
            logger.warn("Unable to load / instantiate RAPCal class " +
                    rapcalClass + ".  Loading ZeroCrossingRAPCal instead.");
            this.rapcal = new ZeroCrossingRAPCal();
        }
        this.app = null;
        this.config = config;

        runLevel = RunLevel.INITIALIZING;
        abBuffer  = new HitBufferAB();

        // Calculate 10-sec averages of the hit rate
        rtHitRate = new RealTimeRateMeter(100000000000L);
        rtLCRate  = new RealTimeRateMeter(100000000000L);
        latelyRunningFlashers = false;

		// byte buffer for messages read out with GetInterval
		intervalBuffer = ByteBuffer.allocateDirect(4092);

		// turn intervals on/off as requested
		disable_intervals = !enable_intervals;

        start();
    }

    public void close()
    {
        if (app != null) app.close();
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
     * @throws MessageException
     */
    private void configure(DOMConfiguration config) throws MessageException
    {
        if (logger.isDebugEnabled()) {
            logger.debug("Configuring DOM on " + canonicalName());
        }
        long configT0 = System.currentTimeMillis();

        app.setMoniIntervals(
                config.getHardwareMonitorInterval(),
                config.getConfigMonitorInterval(),
                config.getFastMonitorInterval()
                );

        if (config.isDeltaCompressionEnabled())
            app.setDeltaCompressionFormat();
        else
            app.setEngineeringFormat(config.getEngineeringFormat());

        if (config.getHV() >= 0)
        {
            app.enableHV();
            app.setHV(config.getHV());
        }
        else
        {
            app.disableHV();
        }

        // DAC setting
        for (byte dac_ch = 0; dac_ch < 16; dac_ch++)
            app.writeDAC(dac_ch, config.getDAC(dac_ch));
        app.setMux(config.getMux());
        app.setTriggerMode(config.getTriggerMode());

        // If trigger mode is Flasher then don't touch the pulser mode
        if (config.getTriggerMode() != TriggerMode.FB)
        {
            if (config.getPulserMode() == PulserMode.BEACON)
                app.pulserOff();
            else
                app.pulserOn();
        }

        // now step carefull around this - some old MB versions don't support the message
        try
        {
            if (config.isMinBiasEnabled())
                app.enableMinBias();
            else
                app.disableMinBias();
        }
        catch (MessageException mex)
        {
            logger.warn("Unable to configure MinBias");
        }

        app.setPulserRate(config.getPulserRate());
        LocalCoincidenceConfiguration lc = config.getLC();
        app.setLCType(lc.getType());
        app.setLCMode(lc.getRxMode());
        app.setLCTx(lc.getTxMode());
        app.setLCSource(lc.getSource());
        app.setLCSpan(lc.getSpan());
        app.setLCWindow(lc.getPreTrigger(), lc.getPostTrigger());
        app.setCableLengths(lc.getCableLengthUp(), lc.getCableLengthDn());
        if (config.isSupernovaEnabled()) {
			app.enableSupernova(config.getSupernovaDeadtime(), config.isSupernovaSpe());
			supernova_disabled=false;
		} else {
			app.disableSupernova();
			supernova_disabled=true;
			ByteBuffer eos = MultiChannelMergeSort.eos(numericMBID);
			try {
				supernovaStream.eos(eos.asReadOnlyBuffer());
			} catch (IOException iox) {
				logger.warn("Caught IO Exception trying to shut down unused SN channel");
			}
		}

        app.setScalerDeadtime(config.getScalerDeadtime());

        try
        {
            app.setAtwdReadout(config.getAtwdChipSelect());
        }
        catch (MessageException mex)
        {
            logger.warn("Unable to configure ATWD chip select");
        }

        // TODO figure out if we want this
        // app.setFastMoniRateType(FastMoniRateType.F_MONI_RATE_HLC);

        // Do the pedestal subtraction
        if (config.getPedestalSubtraction())
        {
            // WARN - this is done /w/ HV applied - probably need to
            // screen in DOMApp for spurious pulses
            app.collectPedestals(200, 200, 200, config.getAveragePedestals());
        }

        // set chargestamp source - again fail with WARNING if cannot get the
        // message through because of old mainboard release
        try
        {
            app.setChargeStampType(!config.isAtwdChargeStamp(),
                    config.isAutoRangeChargeStamp(),
                    config.getChargeStampChannel());
        }
        catch (MessageException mex)
        {
            logger.warn("Unable to configure chargestamp type");
        }

        // enable charge stamp histogramming
        try
        {
            app.histoChargeStamp(config.getHistoInterval(), config.getHistoPrescale());
        }
        catch (MessageException mex)
        {
            logger.warn("Unable to configure chargestamp histogramming");
        }


        if (logger.isDebugEnabled()) {
			long configT1 = System.currentTimeMillis();
            logger.debug("Finished DOM configuration - " + canonicalName() +
                        "; configuration took " + (configT1 - configT0) +
                        " milliseconds.");
        }
    }

    /**
     * binds a UTC time-adjusted buffer consumer to state about the stream of
     * messages being delivered
     */
    static class UTCMessageStream
    {

        private final BufferConsumer target;
        private final String type; //[hit | moni | sn

        private final long orderingEpsilon;

        private long lastDOMClock = 0;
        private long lastUTCClock = 0;

        // if the clock jumps significantly, we will see many out-of-order
        // messages, so throttle logging.
        public final int MAX_LOGGING = 10;
        int num_logged = 0;
        // A persistent, repeating every-other situation warrants additional
        // throttling mechanism.
        public final int LOGGED_OCCURRENCES_PERIOD = 1000;
        private int occurrence_count = 0;


        public UTCMessageStream(final BufferConsumer target, final String type,
                         final long orderingEpsilon)
        {
            this.target = target;
            this.type = type;
            this.orderingEpsilon = orderingEpsilon;
        }

        // used to truncate message processing, a cpu saver in
        // some configurations.
        public boolean hasConsumer()
        {
            return target != null;
        }

        public void eos(final ByteBuffer eos) throws IOException
        {
            if(target != null)
            {
                target.consume(eos);
            }
        }

        // we are passing rapcal as an argument to support unit testing this
        // class.
        public long dispatchBuffer(final RAPCal localRapCal,
                                    final ByteBuffer buf)
                throws
                IOException
        {
            long domclk = buf.getLong(24);
            long utc    = localRapCal.domToUTC(domclk).in_0_1ns();

            if(enforceOrdering(domclk, utc))
            {
                buf.putLong(24, utc);
                target.consume(buf);
            }
            else
            {
                 //todo  After in-field discovery, Increase logging and
                 //      consider dropping dom as the timestamp is off by more
                 //      than epsilon.
            }

            return utc;
        }

        /**
         * Enforce an ordering policy on the message stream.
         *
         * @return true if UTC time ordering is within some epsilon.
         */
        private boolean enforceOrdering(long domclk, long utc)
        {
            //handle out-of-order hits. This could originate at the DOM, or be
            // induced by tcal updates or errors.
            if (lastUTCClock > utc)
            {
                occurrence_count++;

                long utcClockBackStep_0_1_nanos = (lastUTCClock-utc);
                boolean accept = utcClockBackStep_0_1_nanos <= orderingEpsilon;

                // detect if the ordering violation initiated at the dom or
                // is a result of applying the rapcal.
                final String reason;
                if(lastDOMClock > domclk)
                {
                    reason = "Non-Contiguous " + type + " stream from DOM";
                }
                else
                {
                    reason = "Non-Contiguous rapcal for DOM";
                }

                if(num_logged < MAX_LOGGING)
                {
                    num_logged++;
                    String action = accept ? "deliver" : "drop";
                    logger.error("Out-of-order "+ type +": " +
                            "last-utc [" + lastUTCClock  + "]" +
                            " current-utc [" +utc +"]" +
                            " last-dom-clock [" + lastDOMClock + "]" +
                            " current-dom-clock [" + domclk + "]" +
                            " utc-diff [" + utcClockBackStep_0_1_nanos + "]" +
                            " (occurrence: " + occurrence_count +
                            ", reason: " + reason + ", action: "+ action + ")");

                    if(num_logged == MAX_LOGGING)
                    {
                        logger.error("Dampening Out-of-order logging.");
                    }
                }

                return accept;
            }
            else
            {
                //Note: last clocks refer to latest-in-time messages,
                //      we will log or  lgo/drop messages until the
                //      retrograde timestamp condition is over.
                lastDOMClock = domclk;
                lastUTCClock = utc;

                // For short periods we want detailed logging, but for
                // a persistent, repeating condition we want to dampen
                // the logging
                if(occurrence_count < LOGGED_OCCURRENCES_PERIOD ||
                        occurrence_count%LOGGED_OCCURRENCES_PERIOD == 0)
                {
                   num_logged=0;
                }
                return true;
            }

        }

    }

    private void dispatchHitBuffer(CycleStats cycleStats, int atwdChip,
                                   ByteBuffer hitBuf) throws IOException
    {
        cycleStats.reportHitData(hitBuf);

        if (atwdChip == 0)
            abBuffer.pushA(hitBuf);
        else
            abBuffer.pushB(hitBuf);
        while (true)
        {
            ByteBuffer buffer = abBuffer.pop();
            if (buffer == null) return;
            long utc = hitStream.dispatchBuffer(rapcal, buffer);

            // Collect HLC / SLC hit statistics ...
            switch ( buffer.getInt(4) )
            {
                case MAGIC_COMPRESSED_HIT_FMTID:
                    int flagsLC = (buffer.getInt(46) & 0x30000) >> 16;
                    if (flagsLC != 0) rtLCRate.recordEvent(utc);
                    // intentional fall-through
                case MAGIC_ENGINEERING_HIT_FMTID:
                    rtHitRate.recordEvent(utc);
                    lastHitTime = Math.max(lastHitTime, utc);
                    if (firstHitTime < 0L) firstHitTime = utc;
            }
        }
    }

    private void dataProcess(CycleStats cycleStats, ByteBuffer in) throws
            IOException
    {
        // TODO - I created a number of less-than-elegant hacks to
        // keep performance at acceptable level such as minimal
        // decoding of hit records. This should be cleaned up.

        if (!hitStream.hasConsumer()) return;

        int buffer_limit = in.limit();

        // create records from aggregate message data returned from DOMApp
        while (in.remaining() > 0)
        {
            int pos = in.position();
            short len = in.getShort(pos);
            short fmt = in.getShort(pos+2);

            int atwdChip;
            long domClock;
            ByteBuffer outputBuffer;

            switch (fmt)
            {
            case 0x01: /* Early engineering hit data format (pre-pDAQ, in fact) */
            case 0x02: /* Later engineering hit data format */
                numHits++;
                atwdChip = in.get(pos+4) & 1;
                domClock = DOMAppUtil.decodeClock6B(in, pos+10);
                in.limit(pos + len);
                outputBuffer = ByteBuffer.allocate(len + 32);
                outputBuffer.putInt(len + 32);
                outputBuffer.putInt(MAGIC_ENGINEERING_HIT_FMTID);
                outputBuffer.putLong(numericMBID);
                outputBuffer.putLong(0L);
                outputBuffer.putLong(domClock);
                outputBuffer.put(in).flip();
                in.limit(buffer_limit);

                ////////
                //
                // DO the A/B stuff
                //
                ////////
                dispatchHitBuffer(cycleStats, atwdChip, outputBuffer);

                break;

            case 0x90: /* SLC or other compressed hit format */
                // get unsigned MSB for clock
                int clkMSB = in.getShort(pos+4) & 0xffff;
                ByteOrder lastOrder = in.order();
                in.order(ByteOrder.LITTLE_ENDIAN);
                in.position(pos + 8);
                while (in.remaining() > 0)
                {
                    numHits++;
                    pos = in.position();
                    int word1 = in.getInt();
                    int word2 = in.getInt();
                    int word3 = in.getInt();
                    int hitSize = word1 & 0x7ff;
                    atwdChip = (word1 >> 11) & 1;
                    domClock = (((long) clkMSB) << 32) | (((long) word2) & 0xffffffffL);
                    if (logger.isDebugEnabled())
                    {
                        int trigMask = (word1 >> 18) & 0x1fff;
                        logger.debug("DELTA HIT - CLK: " + domClock + " TRIG: " + Integer.toHexString(trigMask));
                    }
                    short version = 0x02;
                    short fpq = (short) (
                            (config.getPedestalSubtraction() ? 1 : 0) |
                            (config.isAtwdChargeStamp() ? 2 : 0)
                            );
                    in.limit(pos + hitSize);
                    outputBuffer = ByteBuffer.allocate(hitSize + 42);
                    // Standard Header
                    outputBuffer.putInt(hitSize + 42);
                    outputBuffer.putInt(MAGIC_COMPRESSED_HIT_FMTID);
                    outputBuffer.putLong(numericMBID); // +8
                    outputBuffer.putLong(0L);    // +16
                    outputBuffer.putLong(domClock);         // +24
                    // Compressed hit extra info
                    // This is the 'byte order' word
                    outputBuffer.putShort((short) 1);  // +32
                    outputBuffer.putShort(version);    // +34
                    outputBuffer.putShort(fpq);        // +36
                    outputBuffer.putLong(domClock);    // +38
                    outputBuffer.putInt(word1);        // +46
                    outputBuffer.putInt(word3);        // +50
                    outputBuffer.put(in).flip();
                    in.limit(buffer_limit);
                    // DO the A/B stuff
                    dispatchHitBuffer(cycleStats, atwdChip, outputBuffer);
                }
                // Restore previous byte order
                in.order(lastOrder);
                break;

            default:
                logger.error("Unknown DOMApp format ID: " + fmt);
                in.position(pos + len);
            }
        }
    }

    private void moniProcess(ByteBuffer in) throws IOException
    {
        if (!moniStream.hasConsumer()) return;

        while (in.remaining() > 0)
        {
            MonitorRecord monitor = MonitorRecordFactory.createFromBuffer(in);
            if (monitor instanceof AsciiMonitorRecord)
            {
                String moniMsg = monitor.toString();
                if (moniMsg.contains("LBM OVERFLOW")) {
                    numLBMOverflows++;
                    logger.error("LBM Overflow [" + cycleMonitor.sequence +
                            "] [" + moniMsg +
                            "]");

                    cycleMonitor.reportLBMOverflow();

                } else if (logger.isDebugEnabled()) {
                    logger.debug(moniMsg);
                }
            }
            numMoni++;
            ByteBuffer moniBuffer = ByteBuffer.allocate(monitor.getLength()+32);
            moniBuffer.putInt(monitor.getLength()+32);
            moniBuffer.putInt(MAGIC_MONITOR_FMTID);
            moniBuffer.putLong(numericMBID);
            moniBuffer.putLong(0L);
            moniBuffer.putLong(monitor.getClock());
            moniBuffer.put(monitor.getBuffer());
            moniBuffer.flip();
            moniStream.dispatchBuffer(rapcal, moniBuffer);
        }
    }

    /**
     * Send the TCAL data out.
     * DOM clock timestamp assignment for TCAL messages is defined herein as
     * the DOR Tx time.
     * @param tcal the input {@link TimeCalib} object
     * @param gps the {@link GPSInfo} record is tacked onto the tail of the buffer
     * @throws IOException
     */
    private void tcalProcess(TimeCalib tcal, GPSInfo gps) throws IOException
    {
        if (!tcalStream.hasConsumer()) return;
        ByteBuffer tcalBuffer = ByteBuffer.allocate(500);
        tcalBuffer.putInt(0).putInt(MAGIC_TCAL_FMTID);
        tcalBuffer.putLong(numericMBID);
        tcalBuffer.putLong(0L);
        tcalBuffer.putLong(tcal.getDomTx().in_0_1ns() / 250L);
        tcal.writeUncompressedRecord(tcalBuffer);
        if (gps == null)
        {
            // Set this to the equivalent of 0 time in GPS
            tcalBuffer.put("\001001:00:00:00 \000\000\000\000\000\000\000\000".getBytes());
        }
        else
        {
            tcalBuffer.put(gps.getBuffer());
        }
        tcalBuffer.flip();
        tcalBuffer.putInt(0, tcalBuffer.remaining());
        tcalStream.dispatchBuffer(rapcal, tcalBuffer);
    }

    private void supernovaProcess(ByteBuffer in) throws IOException
    {
        while (in.remaining() > 0)
        {
            SupernovaPacket spkt = SupernovaPacket.createFromBuffer(in);
            // Check for gaps in SN data
            if ((nextSupernovaDomClock != 0L) && (spkt.getClock() != nextSupernovaDomClock) && numSupernovaGaps++ < 100)
            {
                long gapMillis = ((spkt.getClock() - nextSupernovaDomClock) *
                        25) / 1000000;
                logger.warn("Gap or overlap in SN rec: next = " + nextSupernovaDomClock
                        + " - current = " + spkt.getClock() + ", gap = " + gapMillis + " ms");
            }

            nextSupernovaDomClock = spkt.getClock() + (spkt.getScalers().length << 16);
            numSupernova++;
            if (supernovaStream.hasConsumer())
            {
                int len = spkt.getLength() + 32;
                ByteBuffer snBuf = ByteBuffer.allocate(len);
                snBuf.putInt(len).putInt(MAGIC_SUPERNOVA_FMTID).putLong(numericMBID).putLong(0L);
                snBuf.putLong(spkt.getClock()).put(spkt.getBuffer());
                snBuf.flip();
                supernovaStream.dispatchBuffer(rapcal, snBuf);
            }
        }
    }

    public void setLiveMoni(LiveTCalMoni moni)
    {
        rapcal.setMoni(moni);
    }

    public synchronized void signalShutdown()
    {
        stop_thread = true;
    }

    private void storeRunStartTime() throws InterruptedException
    {
        try
        {
            TimeCalib rst = driver.readTCAL(tcalFile);
            runStartUT = rapcal.domToUTC(rst.getDomTx().in_0_1ns() / 250L).in_0_1ns();
        }
        catch (IOException iox)
        {
            logger.warn("I/O error on TCAL read to determine run start time.");
        }
    }

    public String toString()
    {
        return getName();
    }

    private IGPSService execRapCal() throws GPSServiceError
    {
        try
        {
            IGPSService gps_serv = GPSService.getInstance();
            GPSInfo gps = gps_serv.getGps(card);

            TimeCalib tcal = driver.readTCAL(tcalFile);
            long tcalReceivedNanos = System.nanoTime();

            rapcal.update(tcal, gps.getOffset());
			nextTcalRead = System.currentTimeMillis() + tcalReadInterval;

            // Calibrating the local clock offset using these values
            // is the best we can do without relying on local time
            //  being synchronized.
            domToSystemTimer.update((tcal.getDomTx().in_0_1ns() / 250),
                    tcalReceivedNanos);

            validRAPCalCount++;
            cableLength = rapcal.cableLength();
            epsilon = rapcal.epsilon();

            if (getRunLevel().equals(RunLevel.RUNNING))
            {
                tcalProcess(tcal, gps);
            }
			return gps_serv;

        }
        catch (RAPCalException rcex)
        {
            errorRAPCalCount++;

            logger.warn("Got RAPCal exception", rcex);
        }
        catch (IOException iox)
        {
            logger.warn("Got IO exception", iox);
        }
        catch (InterruptedException intx)
        {
            watchdog.handleInterrupted(intx);
        }
	    return null;
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
	    lastTcalUT = 0L;
        nextSupernovaDomClock = 0L;
        numSupernovaGaps = 0;


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

    /**
     * Wrap up softboot -> domapp behavior
     * */
    private void softbootToDomapp() throws IOException, InterruptedException
    {
        /*
         * Based on discussion /w/ JEJ in Utrecht café, going for multiple retries here
         */
        for (int iBootTry = 0; iBootTry < 2; iBootTry++)
        {
            try
            {
                driver.commReset(card, pair, dom);
                watchdog.ping();
                driver.softboot (card, pair, dom);
                break;
            }
            catch (IOException iox)
            {
                logger.warn("Softboot attempt failed - retrying after 5 sec");
                watchdog.sleep(5000L);
            }
        }

        for (int i = 0; i < 2; i++)
        {
            watchdog.sleep(20);
            driver.commReset(card, pair, dom);
            watchdog.sleep(20);

            try
            {
                watchdog.ping();
                app = new DOMApp(this.card, this.pair, this.dom);
                watchdog.sleep(20);
                break;
            }
            catch (FileNotFoundException ex)
            {
                logger.error(
                        "Trial " + i + ": Open of " + card + "" + pair + dom + " " +
                        "failed! - comstats:\n" + driver.getComstat(card, pair, dom) +
                        "FPGA registers:\n" + driver.getFPGARegs(0));
                if (i == 1) throw ex;
            }
        }
        app.transitionToDOMApp();
    }


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

        driver.resetComstat(card, pair, dom);

        /*
         * I need the MBID right now just in case I have to shut this stream down.
         */
        setMainboardID(driver.getProcfileID(card, pair, dom));
        boolean needSoftboot = true;

        // Note: DOMApp implements its own access to the mbid, this will be
        //       checked after initialization to ensure agreement with the
        //       driver.
        String reportedMbid = mbid;
        if (!alwaysSoftboot)
        {
            logger.debug("Autodetecting DOMApp");
            try
            {
                app = new DOMApp(card, pair, dom);
                if (app.isRunningDOMApp())
                {
                    needSoftboot = false;
                    try
                    {
                        app.endRun();
                    }
                    catch (MessageException mex)
                    {
                        // this is normally what one would expect from a
                        // DOMApp not currently in running mode, ignore
                    }
                    reportedMbid = app.getMainboardID();
                }
                else
                {
                    app.close();
                    app = null;
                }
            }
            catch (Exception x)
            {
                logger.warn("DOM is not responding to DOMApp query - will attempt to softboot");
                // Clear this thread's interrupted status
                watchdog.ping();
                interrupted();
            }
        }

        if (needSoftboot)
        {
            for (int iTry = 0; iTry < 2; iTry++)
            {
                try
                {
                    softbootToDomapp();
                    reportedMbid = app.getMainboardID();
					break;
                }
                catch (Exception ex2)
                {
                    if (iTry == 1) throw ex2;
                    logger.error("Failure to softboot to DOMApp - will retry one time.");

                    //todo: Consider handling interrupt exception through the
                    //      watchdog for graceful abort during softboot.
                }
            }
        }
        setMainboardID(reportedMbid);

        setRunLevel(RunLevel.IDLE);

        watchdog.ping();
        if (logger.isDebugEnabled()) {
            logger.debug("Found DOM " + mbid + " running " + app.getRelease());
        }

        // prohibit running without a gps reading
        ensureGPSReady();


        // Grab 2 RAPCal data points to get started
        for (int nTry = 0; nTry < 10 && validRAPCalCount < 2; nTry++)
        {
            watchdog.sleep(100);
            execRapCal();
        }

        //todo Reconsider the interval setting override. A hard
        //     error may be preferable.
        //
		// determine if we should use get_intervals or
		// fallback to the original query algorithm
		String version = app.getRelease();
        if(!disable_intervals)
        {
            if(!version_supports_intervals(version))
            {
                logger.warn("Overriding interval setting, dom " +
                        "software version [" + version + "] does not support " +
                        "" +
                        "intervals");
                disable_intervals = true;
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Using data acquisition mode [" +
                    (disable_intervals ? "QUERY" : "INTERVAL") +
                    "]");
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
     * XXX This is massively over-engineered.  If you're reading this in 2012
     * and we're still using intervals, you should probably just assume that
     * we always want intervals and just rip all the code related to checking
     * the DOM-MB version.
     *
	 * @param versionStr DOM-MB version string
	 *
	 * @return <tt>true</tt> if the mainboard supports intervals
	 */
	private boolean version_supports_intervals(String versionStr) {
		Pattern pattern = Pattern.compile("DOM-MB-([0-9]+)");
		Matcher matcher = pattern.matcher(versionStr);

		boolean results = false;
		while(matcher.find()) {
			long version = Long.parseLong(matcher.group(1));

			if (version < BASE_TEST_VERSION) {
				if (version >= INTERVAL_MIN_DOMAPP_PROD_VERSION)
				{
					results=true;
					break;
				}
			} else if (version >= INTERVAL_MIN_DOMAPP_TEST_VERSION) {
				results=true;
				break;
			}
		}

		return results;
	}

    /**
     * The core acquisition loop.
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
                if (logger.isDebugEnabled()) logger.debug("Doing TCAL - runLevel is " + getRunLevel());
                execRapCal();
            }

			switch (getRunLevel())
            {
            case RUNNING:
                if(useIntervals)
                {
                    tired = doInterval();
                }
                else
                {
                    tired = doPolling(t);
                }
                break;

            case CONFIGURING:
                /* Need to handle a configure */
                logger.debug("Got CONFIGURE signal.");
                configure(config);
                logger.debug("DOM is configured.");
                setRunLevel(RunLevel.CONFIGURED);
                break;

            case STARTING:
                if (logger.isDebugEnabled()) {
                    logger.debug("Got START RUN signal " + canonicalName());
                }
                app.beginRun();
                cycleMonitor.reportRunStart();
                storeRunStartTime();
                logger.debug("DOM is running.");
                setRunLevel(RunLevel.RUNNING);
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
                    app.endRun();
                    setRunLevel(RunLevel.CONFIGURING);
                    latelyRunningFlashers = false;
                }
                if (flasherConfig != null)
                {
                    logger.info("Starting flasher subrun");
                    if (latelyRunningFlashers)
                    {
                        logger.info("Only changing flasher board configuration");
                        app.changeFlasherSettings(
                                (short) flasherConfig.getBrightness(),
                                (short) flasherConfig.getWidth(),
                                (short) flasherConfig.getDelay(),
                                (short) flasherConfig.getMask(),
                                (short) flasherConfig.getRate()
                                );
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
                        app.beginFlasherRun(
                                (short) flasherConfig.getBrightness(),
                                (short) flasherConfig.getWidth(),
                                (short) flasherConfig.getDelay(),
                                (short) flasherConfig.getMask(),
                                (short) flasherConfig.getRate()
                                );
                    }
                    latelyRunningFlashers = true;
                }
                else
                {
                    logger.info("Returning to non-flashing state");
                    configure(config);
                    app.beginRun();
                }
                storeRunStartTime();
                //todo consider how subruns should interact with
                //     the cycleMonitor
                // ??? cycleMonitor.reportRunStart();
                setRunLevel(RunLevel.RUNNING);
                break;

            case PAUSING:
                if (logger.isDebugEnabled()) {
                    logger.debug("Got PAUSE RUN signal " + canonicalName());
                }
                app.endRun();
                setRunLevel(RunLevel.CONFIGURED);
                break;

            case STOPPING:
                if (logger.isDebugEnabled()) {
                    logger.debug("Got STOP RUN signal " + canonicalName());
                }
                app.endRun();
                ByteBuffer eos = MultiChannelMergeSort.eos(numericMBID);
                hitStream.eos(eos.asReadOnlyBuffer());
                moniStream.eos(eos.asReadOnlyBuffer());
                tcalStream.eos(eos.asReadOnlyBuffer());
                supernovaStream.eos(eos.asReadOnlyBuffer());
                logger.debug("Wrote EOS to streams.");
                setRunLevel(RunLevel.CONFIGURED);

				/* output some statistics on data buffer size */
				if(ENABLE_STATS) {
					System.out.println(card+"/"+pair+"/"+dom+": rate: "+config.getPulserRate()+" max: "+data_max+" min:"+data_min+" mean: "+data_fmt.format(data_m_n)+" var: "+data_fmt.format((data_s_n / ( data_count - 1.0 )))+" count: "+data_count+" zero count: "+data_zero_count+ " lbm overflows: "+numLBMOverflows+" hit rate: "+rtHitRate.getRate()+" hit rate LC: "+rtLCRate.getRate());
				}
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


    /**
     * Execute a single polling data collection cycle.
     *
     * The guts of this method use the DOM query algorithm.
     * A 'GET_DATA' message is sent down to the dom every 10ms followed by
     * a get_moni and get_sn message wrapping up with a tcal every second.
     *
     * @return True if polling did not receive data, an indication for caller
     *         to throttle down.
     */
    private boolean doPolling(final long systemTimMillis) throws
            MessageException, IOException
    {
        boolean tired = true;

        CycleStats cycleStats = cycleMonitor.initiateCycle();

        try
        {
            // Time to do a data collection?
            if (systemTimMillis >= nextDataRead)
            {
                nextDataRead = systemTimMillis + dataReadInterval;

                try
                {
                    // Get debug information during Alpaca failures
                    cycleStats.initiateMessageRead();
                    ByteBuffer data = app.getData();
                    cycleStats.reportDataMessageRcv(data);

                    if (data.remaining() > 0) tired = false;

                    dataProcess(cycleStats, data);
                }
                catch (IllegalArgumentException ex)
                {
                    logger.error("Caught & re-raising IllegalArgumentException");
                    logger.error("Driver comstat for "+card+""+pair+dom+":\n"+driver.getComstat(card,pair,dom));
                    logger.error("FPGA regs for card "+card+":\n"+driver.getFPGARegs(card));
                    throw ex;
                }
            }

            // What about monitoring?
            if (systemTimMillis >= nextMoniRead)
            {
                nextMoniRead = systemTimMillis + moniReadInterval;

                cycleStats.initiateMessageRead();
                ByteBuffer moni = app.getMoni();
                cycleStats.reportDataMessageRcv(moni);
                if (moni.remaining() > 0)
                {
                    moniProcess(moni);
                    tired = false;
                }
            }

            if (systemTimMillis > nextSupernovaRead)
            {
                nextSupernovaRead = systemTimMillis + supernovaReadInterval;
                while (!supernova_disabled)
                {
                    cycleStats.initiateMessageRead();
                    ByteBuffer sndata = app.getSupernova();
                    cycleStats.reportDataMessageRcv(sndata);
                    if (sndata.remaining() > 0)
                    {
                        supernovaProcess(sndata);
                        tired = false;
                        break;
                    }
                }
            }
        }
        finally
        {
            cycleMonitor.completeCycle(cycleStats);
        }

        return tired;
    }

    /**
     * Execute a single interval data collection cycle.
     *
     * NOTES:
     *
     * Requires domapp version 445 or above. (test version 4477).
     *
     * Prior to domapp release 446, supernova messages must be enabled.
     *
     * HISTORY:
     * This is an updated version of the original polling method.
     *
     * Instead of sending query messages down to the dom every 10 ms one
     * "GET_INTERVAL" message is sent.  The results from that message are that all
     * data for the next second is returned followed by a moni and supernova message.
     *
     * In addition when statistics where run on the original method it appears that
     * the original code did not efficiently use the entire amount of space available
     * in messages.  Resulting in lots of small messages being returned from the dom.
     * The required domapp release 4477 packs the responses as tightly as possible
     * before sending the results to the stringhub for processing.  Further on
     * an LBM overflow the original code returned a zero length message to the surface
     * which causes the stringhub code to wait for one second before querying father.
     * This code is not effected by that decision.
     *
     * The following algorithm runs inside the domapp eventloop:
     *
     * if one second interval has expired, return any available data.
     * If the second has not expired and we have a full message send it
     * otherwise continue
     *
     * @return True if polling did not receive data, an indication for caller
     *         to throttle down.
     *
     */
    private boolean doInterval() throws MessageException, IOException
    {
        boolean tired = true;

        CycleStats cycleStats = cycleMonitor.initiateCycle();
        try
        {
            boolean done = false;
            app.getInterval();

            while (!done) {
                cycleStats.initiateMessageRead();
                ByteBuffer msg = app.recvMessage(intervalBuffer);
                cycleStats.reportDataMessageRcv(msg);

                watchdog.ping();

                byte msg_type = msg.get(0);
                byte msg_subtype = msg.get(1);

                // past the header
                msg.position(8);

                if(MessageType.GET_DATA.equals(msg_type, msg_subtype)) {
                    if (msg.remaining()>0) {
                        tired = false;
                    }

                    dataProcess(cycleStats, msg.slice());
                } else if(MessageType.GET_SN_DATA.equals(msg_type, msg_subtype)) {
                    if (msg.remaining()>0) {
                        supernovaProcess(msg.slice());
                        tired = false;
                    }
                    done = true;
                } else if(MessageType.GET_MONI.equals(msg_type, msg_subtype)) {
                    if (msg.remaining()>0) {
                        moniProcess(msg.slice());
                        tired = false;
                    }
                    // If we're not going to get a SN message, this marks the
                    // end of the interval
                    done = supernova_disabled;
                } else {
                    // assume a status of one
                    // as the recv code will have
                    // thrown an exception if that was not
                    // true
                    throw new MessageException(MessageType.GET_DATA,
                            msg_type, msg_subtype, 1);
                }

                if(tired) {
                    // sleep before next iteration
                    watchdog.sleep(threadSleepInterval);
                }
            }
        }
        finally
        {
            cycleMonitor.completeCycle(cycleStats);
        }

        return tired;
    }

    public long getRunStartTime()
    {
        return runStartUT;
    }

    public long getNumHits()
    {
        return numHits;
    }

    public long getNumMoni()
    {
        return numMoni;
    }

    public long getNumTcal()
    {
        return validRAPCalCount;
    }

    @Override
    public long getNumBadTcals()
    {
        return errorRAPCalCount;
    }

    @Override
    public double getCableLength()
    {
        // Note: can not qury rapcal directly since
        //       this method is called from mbean thread.
        return cableLength;
    }

    @Override
    public double getDOMFrequencySkew()
    {
        // Note: can not qury rapcal directly since
        //       this method is called from mbean thread.
        return epsilon;
    }

    public long getNumSupernova()
    {
        return numSupernova;
    }

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
    class InterruptorTask extends TimerTask
    {
        private volatile boolean pinged = false;
        private volatile boolean aborting = false;
        private volatile boolean abnormalExit = false;

        private volatile long lastPingNano = System.nanoTime();

        private final Timer watcher;

        InterruptorTask()
        {
            watcher = new Timer(LegacyDataCollector.this.getName() + "-timer");
        }

        public void enable()
        {
            watcher.schedule(this, 30000L, 5000L);
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
                    if (app != null) app.close();
                    LegacyDataCollector.this.interrupt();
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
            // Note: The watchgdog timer remains in effect within this method.
            //       If exit is blocked by the buffer consumers, the watchdog
            //       timer will free it with an interrupt.
            try
            {
                // clear interrupted flag if it is set
                Thread.interrupted();

                // Generate an alert if this was an unrequested exit
                if(aborting || abnormalExit)
                {
                    setRunLevel(RunLevel.ZOMBIE);
                    StringHubAlert.sendDOMAlert(alertQueue, Priority.EMAIL,
                            "Zombie DOM", card, pair, dom,
                            mbid, name, major, minor, runNumber,
                            lastHitTime);

                    //todo - squelch after mystery LBM overflows are solved
                    if(VERBOSE_TIMEOUT_LOGGING)
                    {
                        List<StringBuilder> lines = cycleMonitor.logHistory();
                        for(StringBuilder line : lines)
                        {
                            logger.error(line);
                        }
                    }
                }
                else
                {
                    //clean exit
                }

                // Make sure EoS (end-of-stream symbol) is written
                try
                {
                    ByteBuffer eos = MultiChannelMergeSort.eos(numericMBID);
                    hitStream.eos(eos.asReadOnlyBuffer());
                    moniStream.eos(eos.asReadOnlyBuffer());
                    tcalStream.eos(eos.asReadOnlyBuffer());
                    supernovaStream.eos(eos.asReadOnlyBuffer());
                    logger.info("Wrote EOS to streams.");
                }
                catch (IOException iox)
                {
                    logger.error("Error while writing EOS to consumers.", iox);
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
        return lastTcalUT;
    }

    public double getHitRate()
    {
        return rtHitRate.getRate();
    }

    public long getLBMOverflowCount()
    {
        return numLBMOverflows;
    }

    public String getMBID()
    {
        return mbid;
    }

    public String getRunState()
    {
        return getRunLevel().toString();
    }

    public double getHitRateLC()
    {
        return rtLCRate.getRate();
    }

    public long getAverageHitAcquisitionLatencyMillis()
    {
        return (long)cycleMonitor.avgHitAcquisitionLatencyMillis.getAverage();
    }

    //NOTE: This is based on the local system clock.
    public String getAcquisitionStartTime()
    {
        synchronized (dateFormat)
        {
            return dateFormat.format(new Date(cycleMonitor.runStartTime));
        }
    }

}
