package icecube.daq.monitoring;

import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.TimeCalib;
import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.juggler.alert.IAlertQueue;
import icecube.daq.rapcal.BadTCalException;
import icecube.daq.rapcal.Isochron;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.util.DeployedDOM;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TimeZone;

import org.apache.log4j.Logger;

/**
 * Manage, process, and send queued monitoring data.
 */
abstract class QueueConsumer<T>
{
    /** logging object */
    protected static final Logger LOG = Logger.getLogger(QueueConsumer.class);

    /** Parent which sends messages to Live */
    protected RunMonitor parent;

    /** Queue of data to be consumed */
    private LinkedList<T> queue = new LinkedList<T>();
    /** A single piece of data to be processed outside any locks */
    private T stashedValue;

    /**
     * Create a queue consumer
     *
     * @param parent main monitoring object
     */
    QueueConsumer(RunMonitor parent)
    {
        this.parent = parent;
    }

    /**
     * Is this consumer's queue empty?
     *
     * @return <tt>true</tt> if the list is empty
     */
    boolean isEmpty()
    {
        synchronized (queue) {
            return queue.size() == 0;
        }
    }

    /**
     * Process one value in the RunMonitor thread.
     * This is run outside any active RunMonitor lock.
     */
    abstract void process(T value);

    /**
     * Process the stashed value in the RunMonitor thread.
     * This is run outside any active lock.
     */
    void processStashedValue()
    {
        if (stashedValue != null) {
            try {
                process(stashedValue);
            } catch (Throwable thr) {
                LOG.error(getClass().getName() + " cannot process " +
                          stashedValue, thr);
            }
            stashedValue = null;
        }
    }

    /**
     * Push a value onto this consumer's queue.
     *
     * @param value new value
     */
    void push(T value)
    {
        synchronized (queue) {
            queue.addLast(value);
        }
    }

    /**
     * Reset everything back to initial conditions for the next run
     */
    abstract void reset();

    /**
     * Process everything in the queue.
     */
    void processAll()
    {
        synchronized (queue) {
            while (true) {
                // if we don't have a value, try to get one from the queue
                if (stashedValue == null) {
                    stashValue();

                    // if the queue is empty, we're done
                    if (stashedValue == null) {
                        break;
                    }
                }

                // process the next value
                try {
                    process(stashedValue);
                } catch (Throwable thr) {
                    LOG.error(getClass().getName() + " cannot process " +
                              stashedValue, thr);
                }
            }
        }
    }

    /**
     * Send per-run quantities.
     */
    abstract void sendRunData();

    /**
     * Stash a value for later processing.
     * This is run while the RunMonitor lock is active, so should do
     * as little as possible.
     * @return <tt>true</tt> if a value was stashed
     */
    boolean stashValue()
    {
        synchronized (queue) {
            if (stashedValue != null) {
                LOG.error("Cannot stash value; already have " +
                          stashedValue);
                return true;
            } else if (!isEmpty()) {
                stashedValue = queue.removeFirst();
                return true;
            }
        }
        return false;
    }
}

/**
 * Simple incremental counter
 */
class Counter
{
    /** Counter value */
    private int count;

    /**
     * Get the current value
     *
     * @return current value
     */
    int get()
    {
        return count;
    }

    /**
     * Increment the counter
     */
    void inc()
    {
        count++;
    }
}

/**
 * Consumer which counts the number of occurences of the key and sends
 * totals at the end of the run
 */
abstract class CountingConsumer<K, T>
    extends QueueConsumer<T>
{
    /** Map of keys to counters */
    HashMap<K, Counter> countMap = new HashMap<K, Counter>();

    /**
     * Create a counting consumer
     *
     * @param parent main monitoring object
     */
    CountingConsumer(RunMonitor parent)
    {
        super(parent);
    }

    /**
     *  Map keys to final counter values
     *
     * @return map of strings to counts
     */
    Map<String, Integer> getCountMap()
    {
        HashMap<String, Integer> counts = new HashMap<String, Integer>();
        for (Map.Entry<K, Counter> entry : countMap.entrySet()) {
            counts.put(entry.getKey().toString(), entry.getValue().get());
        }
        return counts;
    }

    /**
     * Increment the count
     *
     * @param key entry to increment
     */
    void inc(K key)
    {
        if (!countMap.containsKey(key)) {
            countMap.put(key, new Counter());
        }
        countMap.get(key).inc();
    }

    /**
     * Send the per-run totals to Live
     *
     * @param name quantity name
     * @param version quantity version
     * @param priority message priority
     */
    void sendRunData(final String name, final int version,
                     final Alerter.Priority priority)
    {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("version", version);

        map.put("counts", getCountMap());

        parent.sendMoni(name, priority, map);
    }

    /**
     * Reset everything back to initial conditions for the next run
     */
    @Override
    void reset()
    {
        countMap.clear();
    }
}

/**
 * Consumer which counts the number of occurences of each configured DOM
 * and sends totals at the end of the run, using each DOM's "string-position"
 * as the label for its count
 */
abstract class DOMCountingConsumer<T>
    extends CountingConsumer<Long, T>
{
    /**
     * Create a counting consumer
     *
     * @param parent main monitoring object
     */
    DOMCountingConsumer(RunMonitor parent)
    {
        super(parent);
    }

    /**
     * Build a hashmap of DOM "string-position" to associated counts (or zero
     * if a DOM has no counts)
     *
     * @return map of all configured DOMs to associated counts
     */
    Map<String, Integer> getCountMap()
    {
        HashMap<DeployedDOM, Integer> domMap =
            new HashMap<DeployedDOM, Integer>();
        for (DeployedDOM dom : parent.getConfiguredDOMs()) {
            final Long key = Long.valueOf(dom.getNumericMainboardId());
            if (countMap.containsKey(key)) {
                domMap.put(dom, countMap.get(key).get());
            } else {
                domMap.put(dom, Integer.valueOf(0));
            }
        }

        HashMap<String, Integer> counts = new HashMap<String, Integer>();
        for (Map.Entry<DeployedDOM, Integer> entry : domMap.entrySet()) {
            final Integer key =
                Integer.valueOf(entry.getKey().getStringMinor());
            counts.put(key.toString(), entry.getValue());
        }
        return counts;
    }
}

/**
 * Consume misaligned GPS data
 */
class GPSMisalignmentConsumer
    extends CountingConsumer<Integer, GPSMisalignmentConsumer.Data>
{
    /**
     * GPS misalignment data
     */
    class Data
    {
        /** card number */
        int card;
        /** previous GPS information */
        GPSInfo oldGPS;
        /** new, bad GPS information */
        GPSInfo newGPS;

        /**
         * Create GPS misalignment data
         *
         * @param card card number
         * @param oldGPS previous GPS information
         * @param oldGPS new, bad GPS information
         */
        Data(int card, GPSInfo oldGPS, GPSInfo newGPS)
        {
            this.card = card;
            this.oldGPS = oldGPS;
            this.newGPS = newGPS;
        }
    }

    /** Live quantity name */
    public static final String NAME = "card_gps_misalignment";
    /** Live quantity version */
    public static final int VERSION = 1;

    /** Live message priority */
    private static final Alerter.Priority PRIORITY = Alerter.Priority.EMAIL;

    /**
     * Create a GPS misalignment consumer
     *
     * @param parent main monitoring object
     */
    GPSMisalignmentConsumer(RunMonitor parent)
    {
        super(parent);
    }

    /**
     * Process a single piece of data
     *
     * @param data data being processed
     */
    @Override
    void process(Data data)
    {
        inc(Integer.valueOf(data.card));
    }

    /**
     * Push the data onto this consumer's queue
     *
     * @param string string numer
     * @param card card number
     * @param oldGPS previous GPS information
     * @param newGPS new, problematic GPS information
     */
    void pushData(int string, int card, GPSInfo oldGPS, GPSInfo newGPS)
    {
        if (parent.getString() != string) {
            LOG.error("Expected data from string " + parent.getString() +
                      ", not " + string);
        }

        push(new Data(card, oldGPS, newGPS));
    }

    /**
     * Send per-run quantities.
     */
    @Override
    void sendRunData()
    {
        sendRunData(NAME, VERSION, PRIORITY);
    }
}

/**
 * Consume GPS exceptions
 */
class GPSProblemConsumer
    extends CountingConsumer<Integer, Integer>
{
    /** Live quantity name */
    public static final String NAME = "card_gps_problem";
    /** Live quantity version */
    public static final int VERSION = 1;

    /** logging object */
    protected static final Logger LOG =
        Logger.getLogger(GPSProblemConsumer.class);

    /** Live message priority */
    private static final Alerter.Priority PRIORITY = Alerter.Priority.EMAIL;

    /**
     * Create a GPS exception consumer
     *
     * @param parent main monitoring object
     */
    GPSProblemConsumer(RunMonitor parent)
    {
        super(parent);
    }

    /**
     * Process a single piece of data
     *
     * @param key card number
     */
    @Override
    void process(Integer key)
    {
        inc(key);
    }

    /**
     * Push the data onto this consumer's queue
     *
     * @param string string number
     * @param card card number
     * @param exception GPS exception
     */
    void pushData(int string, int card, GPSException exception)
    {
        if (parent.getString() != string) {
            LOG.error("Expected data from string " + parent.getString() +
                      ", not " + string);
        }

        LOG.error(String.format("String %d card %d GPS exception", string,
                                card), exception);

        push(Integer.valueOf(card));
    }

    /**
     * Send per-run quantities.
     */
    @Override
    void sendRunData()
    {
        sendRunData(NAME, VERSION, PRIORITY);
    }
}
/**
 * Consume isochrons
 */
class IsoConsumer
    extends QueueConsumer<IsoConsumer.Data>
{
    /**
     * Isochron data
     */
    class Data
    {
        /** DOM mainboard ID */
        long mbid;
        /** isochron data */
        Isochron isochron;

        /**
         * Create isochron data
         *
         * @param mbid DOM mainboard ID
         * @param isochron isochron
         */
        Data(long mbid, Isochron isochron)
        {
            this.mbid = mbid;
            this.isochron = isochron;
        }
    }

    class CableHisto
    {
        /** Multiplier used to cast the cable length into a reasonable range */
        private static final double MULTIPLIER = 5E9;

        /** Number of bins per histogram */
        public static final int BINS = 100;

        /** Half the range, used to check that values are in-bounds */
        private static final double HALF_RANGE = (double) BINS / 2.0;

        /** The DOM being histogrammed */
        private DeployedDOM dom;

        /** Initial cache of seed values */
        private double[] cache = new double[5];
        /** Number of cached values */
        private int cached = 0;

        /** Set to <tt>true</tt> when we're taking data */
        private boolean initialized;
        /** Histogram base value */
        private double minValue;

        /** Count of values too small for the histogram */
        private int underflow;
        /** Count of values too large for the histogram */
        private int overflow;
        /** Histogrammed values */
        private int[] histogram = new int[BINS];

        /**
         * Create a cable length histogram
         *
         * @param dom DOM being tracked
         */
        CableHisto(DeployedDOM dom)
        {
            this.dom = dom;
        }

        /**
         * Add a value to the histogram
         *
         * @param value value being added
         */
        private void addValue(double value)
        {
            int index = (int) (value - minValue);
            if (index < 0) {
                underflow++;
            } else if (index >= histogram.length) {
                overflow++;
            } else {
                histogram[index]++;
            }
        }

        /**
         * Get the minimum bin value
         *
         * @return minimum value
         */
        double getMinValue()
        {
            return minValue / MULTIPLIER;
        }

        /**
         * Get the maximum bin value
         *
         * @return maximum value
         */
        double getMaxValue()
        {
            return (minValue + BINS) / MULTIPLIER;
        }

        /**
         * Get the `string-position` string
         *
         * @return OM string
         */
        String getOMString()
        {
            return dom.getDeploymentLocation();
        }

        /**
         * Get the list of histogram bins
         *
         * @return list of histogram bin counts
         */
        int[] getHistogram()
        {
            return histogram;
        }

        /**
         * Get the count of entries too large for the histogram
         *
         * @param count of extra-large entries
         */
        int getOverflow()
        {
            return overflow;
        }

        /**
         * Get the count of entries too small for the histogram
         *
         * @param count of extra-small entries
         */
        int getUnderflow()
        {
            return underflow;
        }

        /**
         * Use accumulated data to establish histogram bounds and start filling
         * histogram bins from initial data.
         */
        private void initialize()
        {
            // get the mean of the cached values
            double average = 0.0;
            for (int i = 0; i < cache.length; i++) {
                average += cache[i];
            }
            average /= (double) cache.length;

            // determine the first value in the histogram
            minValue = average - HALF_RANGE;

            // add cached values to the histogram
            for (int i = 0; i < cache.length; i++) {
                addValue(cache[i]);
            }

            // free the cache!
            cache = null;
        }

        /**
         * Have we accumulated enough data to start filling bins?
         *
         * @return <tt>true</tt> if we're taking data
         */
        boolean isInitialized()
        {
            return initialized;
        }

        /**
         * Process the latest Isochron
         *
         * @param iso isochron
         */
        void process(Isochron iso)
        {
            final double cableLength = iso.getCableLength() * MULTIPLIER;

            if (!initialized) {
                if (cached < cache.length) {
                    cache[cached++] = cableLength;
                    return;
                }

                initialize();

                // ...and we're ready to histogram!
                initialized = true;
            }

            addValue(cableLength);
        }

        public String toString()
        {
            return String.format("%s: under %d over %d",
                                 dom.getDeploymentLocation(),
                                 underflow, overflow);
        }
    }

    /** Live quantity name */
    public static final String NAME = "dom_tcal_histogram";
    /** Live quantity version */
    public static final int VERSION = 1;

    /** Live message priority */
    private static final Alerter.Priority PRIORITY = Alerter.Priority.EMAIL;

    /** Map mainboard IDs to histogram generators */
    private HashMap<Long, CableHisto> histograms =
        new HashMap<Long, CableHisto>();

    /**
     * Create an isochron consumer
     *
     * @param parent main monitoring object
     */
    IsoConsumer(RunMonitor parent)
    {
        super(parent);
    }

    /**
     * Process a single piece of data
     *
     * @param data data being processed
     */
    @Override
    void process(Data data)
    {
        if (!histograms.containsKey(data.mbid)) {
            DeployedDOM dom = parent.getDom(data.mbid);
            if (dom == null) {
                LOG.error(String.format("Ignoring Isochron for bad DOM %012x",
                                        data.mbid));
                return;
            }

            CableHisto ch = new CableHisto(dom);
            histograms.put(data.mbid, ch);
        }

        histograms.get(data.mbid).process(data.isochron);
    }

    /**
     * Push the data onto this consumer's queue
     *
     * @param mbid DOM mainboard ID
     * @param isochron the next isochron value
     */
    void pushData(long mbid, Isochron isochron)
    {
        push(new Data(mbid, isochron));
    }

    /**
     * Reset everything back to initial conditions for the next run
     */
    @Override
    void reset()
    {
    }

    /**
     * Send per-run quantities.
     */
    @Override
    void sendRunData()
    {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("version", VERSION);

        HashMap<String, Object> data = new HashMap<String, Object>();
        for (CableHisto h : histograms.values()) {
            if (!h.isInitialized()) {
                continue;
            }

            ArrayList<Integer> values = new ArrayList<Integer>();
            for (int val : h.getHistogram()) {
                values.add(val);
            }

            HashMap<String, Object> histo = new HashMap<String, Object>();
            histo.put("binContents", values);
            histo.put("xmin", h.getMinValue());
            histo.put("xmax", h.getMaxValue());
            histo.put("underflow", h.getUnderflow());
            histo.put("overflow", h.getOverflow());

            data.put(h.getOMString(), histo);
        }

        map.put("histograms", data);
        map.put("xlabel", "cable length");
        map.put("ylabel", "nentries");
        map.put("nentries", CableHisto.BINS);
        map.put("runNumber", parent.getRunNumber());
        map.put("recordingStartTime", parent.getStartTimeString());
        map.put("recordingStopTime", parent.getStopTimeString());

        parent.sendMoni(NAME, PRIORITY, map);
    }
}

/**
 * Consume procfile-not-ready messages
 */
class ProcfileConsumer
    extends CountingConsumer<Integer, Integer>
{
    /** Live quantity name */
    public static final String NAME = "card_procfile_not_ready";
    /** Live quantity version */
    public static final int VERSION = 1;

    /** Live message priority */
    private static final Alerter.Priority PRIORITY = Alerter.Priority.EMAIL;

    /**
     * Create a procfile error consumer
     *
     * @param parent main monitoring object
     */
    ProcfileConsumer(RunMonitor parent)
    {
        super(parent);
    }

    /**
     * Process a single piece of data
     *
     * @param data data being processed
     */
    @Override
    void process(Integer key)
    {
        inc(key);
    }

    /**
     * Push the data onto this consumer's queue
     *
     * @param string string number
     * @param card card number
     */
    void pushData(int string, int card)
    {
        if (parent.getString() != string) {
            LOG.error("Expected data from string " + parent.getString() +
                      ", not " + string);
        }

        push(Integer.valueOf(card));
    }

    /**
     * Send per-run quantities.
     */
    @Override
    void sendRunData()
    {
        sendRunData(NAME, VERSION, PRIORITY);
    }
}

/**
 * Consume RAPCal exceptions
 */
class RAPCalProblemConsumer
    extends DOMCountingConsumer<Long>
{
    /** Live quantity name */
    public static final String NAME = "dom_rapcal_exception";
    /** Live quantity version */
    public static final int VERSION = 1;

    /** Live message priority */
    private static final Alerter.Priority PRIORITY = Alerter.Priority.SCP;

    /**
     * Create a RAPCal exception consumer
     *
     * @param parent main monitoring object
     */
    RAPCalProblemConsumer(RunMonitor parent)
    {
        super(parent);
    }

    /**
     * Log details of this exception
     *
     * @param mbid mainboard ID
     * @param exception exception to log
     */
    private void logException(long mbid, RAPCalException exception)
    {
        DeployedDOM dom = parent.getDom(mbid);

        final String domStr;
        if (dom == null) {
            domStr = String.format("unconfigured DOM %012x", mbid);
        } else {
            if (dom.getName() == null) {
                domStr = "DOM " + dom.getDeploymentLocation();
            } else {
                domStr = "DOM " + dom.getDeploymentLocation() + " (" +
                    dom.getName() + ")";
            }
        }

        final String wfStr;
        if (!(exception instanceof BadTCalException)) {
            wfStr = "";
        } else {
            StringBuilder buf = new StringBuilder(" waveform[");
            final short[] waveform =
                ((BadTCalException) exception).getWaveform();
            for (int i = 0; i < waveform.length; i++) {
                if (i > 0) {
                    buf.append(' ');
                }
                buf.append(waveform[i]);
            }
            buf.append(']');
            wfStr = buf.toString();
        }

        LOG.error("Exception for " + domStr + wfStr, exception);
    }

    /**
     * Process a single piece of data
     *
     * @param data data being processed
     */
    @Override
    void process(Long mbid)
    {
        // count this exception
        inc(mbid);
    }

    /**
     * Push the data onto this consumer's queue
     *
     * @param mbid DOM mainboard ID
     * @param exception RAPCal exception
     * @param tcal time calibration data which caused this exception
     */
    void pushData(long mbid, RAPCalException exception, TimeCalib tcal)
    {
        // log details
        logException(mbid, exception);

        // count DOM later
        push(Long.valueOf(mbid));
    }

    /**
     * Send per-run quantities.
     */
    @Override
    void sendRunData()
    {
        sendRunData(NAME, VERSION, PRIORITY);
    }
}

/**
 * Consume wild TCal data
 */
class WildTCalConsumer
    extends DOMCountingConsumer<WildTCalConsumer.Data>
{
    /** Wild time calibration data */
    class Data
    {
        /** DOM mainboard ID */
        long mbid;
        /** bad cable length */
        double cableLength;
        /** average cable length */
        double averageLen;

        /**
         * Create wild time calibration data
         *
         * @param mbid DOM mainboard ID
         * @param cableLength bad cable length
         * @param averageLen average cable length
         */
        Data(long mbid, double cableLength, double averageLen)
        {
            this.mbid = mbid;
            this.cableLength = cableLength;
            this.averageLen = averageLen;
        }
    }

    /** Live quantity name */
    public static final String NAME = "dom_wild_tcal_count";
    /** Live quantity version */
    public static final int VERSION = 1;

    /** Live message priority */
    private static final Alerter.Priority PRIORITY = Alerter.Priority.EMAIL;

    /**
     * Create a wild TCal consumer
     *
     * @param parent main monitoring object
     */
    WildTCalConsumer(RunMonitor parent)
    {
        super(parent);
    }

    /**
     * Process a single piece of data
     *
     * @param data data being processed
     */
    @Override
    void process(Data data)
    {
        inc(data.mbid);
    }

    /**
     * Push the data onto this consumer's queue
     *
     * @param mbid DOM mainboard ID
     * @param cableLength bad cable length
     * @param averageLen average cable length
     */
    void pushData(long mbid, double cableLength, double averageLen)
    {
        push(new Data(mbid, cableLength, averageLen));
    }

    /**
     * Send per-run quantities.
     */
    @Override
    void sendRunData()
    {
        sendRunData(NAME, VERSION, PRIORITY);
    }
}

/**
 * Thread daemon managing a set of consumers which produce monitoring messages
 */
abstract class ThreadDaemon
    implements Runnable
{
    /** logging object */
    private static final Logger LOG = Logger.getLogger(ThreadDaemon.class);

    /** thread name */
    private String threadName;
    /** thread (can be null between runs) */
    private Thread thread;
    /** Lock used to control access to thread-related attributes */
    private Object threadLock = new Object();
    /** <tt>true</tt> when the thread has started */
    private volatile boolean started;
    /** <tt>true</tt> is thread should stop itself */
    private volatile boolean stopping;

    /**
     * Create a thread daemon.
     *
     * @param name thread name
     */
    ThreadDaemon(String name)
    {
        threadName = name;
    }

    /**
     * Is the associated thread running?
     *
     * @return <tt>true</tt> if the thread is running.
     */
    public boolean isRunning()
    {
        synchronized (threadLock) {
            return started && !stopping && thread != null && thread.isAlive();
        }
    }

    /**
     * Should the thread be stopped?
     *
     * @return <tt>true</tt> if the main loop should stop
     */
    boolean isStopping()
    {
        return stopping;
    }

    /**
     * If the thread is running, wait for it to die.
     *
     * @throws InterruptedException if the join was interrupted
     */
    public void join()
        throws InterruptedException
    {
        synchronized (threadLock) {
            if (thread != null) {
                if (!stopping) {
                    stop();
                }
            }
        }

        if (thread != null) {
            try {
                thread.join();
            } catch (NullPointerException npe) {
                // must have lost the race
            }
            thread = null;
            stopping = false;
        }
    }

    /**
     * Main processing loop
     */
    abstract void mainloop();

    /**
     * Notify the main thread loop that the thread state has changed.
     * This method should handle any inter-loop locking.
     */
    abstract void notifyThread();

    /**
     * Main thread loop which catches unexpected errors and sets the
     * internal <tt>stopping</tt> flag on exit.
     */
    public void run()
    {
        started = true;
        try {
            mainloop();
        } catch (Throwable thr) {
            String tmpName;
            if (thread != null) {
                tmpName = thread.getName();
            } else if (threadName != null) {
                tmpName = threadName;
            } else {
                tmpName = getClass().getName();
            }
            LOG.error("Yikes, " + tmpName + " thread died!", thr);
        } finally {
            synchronized (threadLock) {
                stopping = true;
            }
            started = false;
        }
    }

    /**
     * Start the thread.
     *
     * @throws Error if the thread is already running
     */
    public void start()
    {
        synchronized (threadLock) {
            if (isRunning()) {
                throw new Error("Thread is already running!");
            }

            thread = new Thread(this);
            thread.setName(threadName);
            thread.start();
        }
    }

    /**
     * Notify the main loop that it should stop.
     */
    public void stop()
    {
        synchronized (threadLock) {
            if (isRunning()) {
                stopping = true;
                notifyThread();
            }
        }
    }
}

/**
 * Monitor run-related quantities
 */
public class RunMonitor
    extends ThreadDaemon
    implements IRunMonitor
{
    /** Value which indicates that no run is active */
    public static final int NO_ACTIVE_RUN = Integer.MIN_VALUE;

    /** logging object */
    private static final Logger LOG = Logger.getLogger(RunMonitor.class);

    /** String number */
    private int string;
    /** Alert queue which send messages to Live */
    private IAlertQueue alertQueue;
    /** Map mainboard IDs to DOMs on this string */
    private Map<Long, DeployedDOM> mbidMap;

    /** Current run number */
    private int runNumber = NO_ACTIVE_RUN;
    /** If not equal to <tt>runNumber</tt>, the run has changed */
    private int nextNumber = NO_ACTIVE_RUN;
    /** Lock which controls access to the consumer data */
    private Object queueLock = new Object();

    /** Start of this data period */
    private Date startTime;
    /** End of this data period */
    private Date stopTime;
    /** Date string formatter */
    private final SimpleDateFormat dateFormat;

    // This is ugly but I can't think of a better way to do it!
    /** GPS misalignment consumer */
    private GPSMisalignmentConsumer alignConsumer;
    /** GPS exception consumer */
    private GPSProblemConsumer gpsexConsumer;
    /** Isochron consumer */
    private IsoConsumer isoConsumer;
    /** Procfile error consumer */
    private ProcfileConsumer pfileConsumer;
    /** RAPCal exception consumer */
    private RAPCalProblemConsumer rapexcConsumer;
    /** Wild TCal consumer */
    private WildTCalConsumer wildConsumer;

    /** List of active consumers */
    private ArrayList<QueueConsumer> consumers =
        new ArrayList<QueueConsumer>();

    /**
     * Create a run monitor
     *
     * @param alertQueue object which sends messages to Live
     */
    public RunMonitor(int string, IAlertQueue alertQueue)
    {
        super("RunMonitor");

        this.string = string;
        this.alertQueue = alertQueue;

        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * Finish processing and sending all data from the current run
     */
    private void finishRun()
    {
        // record the end time for this run
        stopTime = new Date();

        for (QueueConsumer consumer : consumers) {
            consumer.processAll();
            consumer.sendRunData();
            consumer.reset();
        }
    }

    /**
     * Set the list of DOMs configured for this string
     *
     * @param configuredDOMs map of mainboard ID -> deployed DOM data
     */
    public Iterable<DeployedDOM> getConfiguredDOMs()
    {
        return mbidMap.values();
    }

    /**
     * Get DOM information
     *
     * @param mbid DOM mainboard ID
     *
     * @returns dom information
     */
    DeployedDOM getDom(long mbid)
    {
        if (mbidMap == null) {
            throw new Error("List of configured DOMs has not been set");
        }

        return mbidMap.get(mbid);
    }

    /**
     * Get the current run number
     *
     * @return run number
     */
    public int getRunNumber()
    {
        return runNumber;
    }

    /**
     * Get the string representation of the ending time for this run
     *
     * @return ending time
     */
    String getStopTimeString()
    {
        return dateFormat.format(stopTime);
    }

    /**
     * Get the string representation of the starting time for this run
     *
     * @return starting time
     */
    String getStartTimeString()
    {
        return dateFormat.format(startTime);
    }

    /**
     * Get this string's number
     *
     * @return string number
     */
    public int getString()
    {
        return string;
    }

    /**
     * Is there an active run?
     *
     * @return <tt>true</tt> if the hub is running
     */
    private boolean hasRunNumber()
    {
        return runNumber != NO_ACTIVE_RUN;
    }

    /**
     * Are all consumer queues empty?
     *
     * @return <tt>true</tt> if any consumer has queued data
     */
    private boolean isEmpty()
    {
        synchronized (queueLock) {
            for (QueueConsumer consumer : consumers) {
                if (!consumer.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Join with the consumer thread, then unset the run number.
     */
    @Override
    public void join()
        throws InterruptedException
    {
        super.join();

        runNumber = NO_ACTIVE_RUN;
        nextNumber = NO_ACTIVE_RUN;
    }

    /**
     * Main thread loop
     */
    @Override
    void mainloop()
    {
        ArrayList<QueueConsumer> stashed = new ArrayList<QueueConsumer>();

        while (true) {
            synchronized (queueLock) {
                boolean empty = isEmpty();

                // if all queues are empty and there's a new run number...
                if (empty && runNumber != nextNumber) {
                    finishRun();

                    // ...switch to the new number
                    runNumber = nextNumber;
                }

                if (empty && !isStopping()) {
                    // if all queues are empty, wait for more data
                    try {
                        queueLock.wait();
                    } catch (InterruptedException ie) {
                        LOG.error("Interrupt while waiting for" +
                                  " monitoring data", ie);
                        // go back to the top of the loop
                        // to check if we're stopping
                        continue;
                    }

                    // did we get more data?
                    empty = isEmpty();
                }

                // if there's no data and we've been asked to stop, do it now
                if (empty && isStopping()) {
                    break;
                }

                // stash a value while we're inside the lock
                stashed.clear();
                for (QueueConsumer consumer : consumers) {
                    if (consumer.stashValue()) {
                        stashed.add(consumer);
                    }
                }
            }

            // now that we're outside the lock, process stashed values
            for (QueueConsumer consumer : stashed) {
                consumer.processStashedValue();
            }
        }

        finishRun();
    }

    /**
     * Notify the thread that something needs its attention
     */
    @Override
    void notifyThread()
    {
        synchronized (queueLock) {
            queueLock.notify();
        }
    }

    /**
     * Push isochron data onto the consumer's queue
     *
     * @param mbid DOM mainboard ID
     * @param isochron isochron
     */
    @Override
    public void push(long mbid, Isochron isochron)
    {
        synchronized (queueLock) {
            if (hasRunNumber()) {
                if (isoConsumer == null) {
                    isoConsumer = new IsoConsumer(this);
                    consumers.add(isoConsumer);
                }
                isoConsumer.pushData(mbid, isochron);
                queueLock.notify();
            }
        }
    }

    /**
     * Push GPS exception data onto the consumer's queue
     *
     * @param string string number
     * @param card card number
     * @param exception GPS exception
     */
    @Override
    public void pushException(int string, int card, GPSException exception)
    {
        synchronized (queueLock) {
            if (hasRunNumber()) {
                if (gpsexConsumer == null) {
                    gpsexConsumer = new GPSProblemConsumer(this);
                    consumers.add(gpsexConsumer);
                }
                gpsexConsumer.pushData(string, card, exception);
                queueLock.notify();
            }
        }
    }

    /**
     * Push RAPCal exception data onto the consumer's queue
     *
     * @param mbid DOM mainboard ID
     * @param exception RAPCal exception
     * @param tcal time calibration data which caused this exception
     */
    @Override
    public void pushException(long mbid, RAPCalException exception,
                              TimeCalib tcal)
    {
        synchronized (queueLock) {
            if (hasRunNumber()) {
                if (rapexcConsumer == null) {
                    rapexcConsumer = new RAPCalProblemConsumer(this);
                    consumers.add(rapexcConsumer);
                }
                rapexcConsumer.pushData(mbid, exception, tcal);
                queueLock.notify();
            }
        }
    }

    /**
     * Push GPS misalignment data onto the consumer's queue
     *
     * @param card card number
     * @param oldGPS previous GPS information
     * @param newGPS new, problematic GPS information
     */
    @Override
    public void pushGPSMisalignment(int string, int card, GPSInfo oldGPS,
                                    GPSInfo newGPS)
    {
        synchronized (queueLock) {
            if (hasRunNumber()) {
                if (alignConsumer == null) {
                    alignConsumer = new GPSMisalignmentConsumer(this);
                    consumers.add(alignConsumer);
                }
                alignConsumer.pushData(string, card, oldGPS, newGPS);
                queueLock.notify();
            }
        }
    }

    /**
     * Push GPS procfile error data onto the consumer's queue
     *
     * @param string string number
     * @param card card number
     */
    @Override
    public void pushGPSProcfileNotReady(int string, int card)
    {
        synchronized (queueLock) {
            if (hasRunNumber()) {
                if (pfileConsumer == null) {
                    pfileConsumer = new ProcfileConsumer(this);
                    consumers.add(pfileConsumer);
                }
                pfileConsumer.pushData(string, card);
                queueLock.notify();
            }
        }
    }

    /**
     * Push wild TCal error data onto the consumer's queue
     *
     * @param mbid DOM mainboard ID
     * @param cableLength bad cable length
     * @param averageLen average cable length
     */
    @Override
    public void pushWildTCal(long mbid, double cableLength, double averageLen)
    {
        synchronized (queueLock) {
            if (hasRunNumber()) {
                if (wildConsumer == null) {
                    wildConsumer = new WildTCalConsumer(this);
                    consumers.add(wildConsumer);
                }
                wildConsumer.pushData(mbid, cableLength, averageLen);
                queueLock.notify();
            }
        }
    }

    /**
     * Send monitoring message to Live
     *
     * @param varname quantity name
     * @param priority message priority
     * @param map field->value map
     */
    void sendMoni(String varname, Alerter.Priority priority,
                  Map<String, Object> map)
    {
        map.put("runNumber", runNumber);
        if (!map.containsKey("string")) {
            map.put("string", string);
        }

        try {
            alertQueue.push(varname, priority, map);
        } catch (AlertException ae) {
            LOG.error("Cannot push " + varname, ae);
        }
    }

    /**
     * Set the list of DOMs configured for this string
     *
     * @param configuredDOMs list of configured DOMs
     */
    @Override
    public void setConfiguredDOMs(Collection<DeployedDOM> configuredDOMs)
    {
        mbidMap = new HashMap<Long, DeployedDOM>();

        for (DeployedDOM dom : configuredDOMs) {
            mbidMap.put(dom.getNumericMainboardId(), dom);
        }
    }

    /**
     * Set the run number
     *
     * @param runNumber new run number
     */
    @Override
    public void setRunNumber(int runNumber)
    {
        synchronized (queueLock) {
            nextNumber = runNumber;

            // record the starting time for this run
            startTime = new Date();
            stopTime = null;
            queueLock.notify();
        }
    }
}
