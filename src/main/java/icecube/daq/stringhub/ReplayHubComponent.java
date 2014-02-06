package icecube.daq.stringhub;

import icecube.daq.io.HitSpoolReader;
import icecube.daq.io.SimpleOutputEngine;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQCompServer;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.juggler.mbean.MemoryStatistics;
import icecube.daq.juggler.mbean.SystemStatistics;
import icecube.daq.monitoring.MonitoringData;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.impl.ReadoutRequestFactory;
import icecube.daq.payload.impl.VitreousBufferCache;
import icecube.daq.sender.RequestReader;
import icecube.daq.sender.Sender;
import icecube.daq.util.DOMRegistry;
import icecube.daq.util.FlasherboardConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.xml.sax.SAXException;

/**
 * Keep track of DOM first and last times
 */
class DOMTimes
{
    private long firstTime = Long.MIN_VALUE;
    private long lastTime = Long.MIN_VALUE;

    /**
     * Add the next DOM time
     *
     * @param time DOM time
     */
    public void add(long time)
    {
        if (firstTime == Long.MIN_VALUE) {
            firstTime = time;
        }

        lastTime = time;
    }

    /**
     * Get the first DOM time
     *
     * @return first time
     */
    public long getFirstTime()
    {
        return firstTime;
    }

    /**
     * Get the last DOM time
     *
     * @return last time
     */
    public long getLastTime()
    {
        return lastTime;
    }
}

/**
 * Replay hitspooled data
 */
public class ReplayHubComponent
    extends DAQComponent
    implements ReplayHubComponentMBean
{
    private static final Logger LOG =
        Logger.getLogger(ReplayHubComponent.class);

    private static final String COMPONENT_NAME = "replayHub";

    /** ID of this replay hub */
    private int hubId;
    /** Cache used to allocate new hit payloads */
    private IByteBufferCache cache;
    /** Hit sender */
    private Sender sender;

    /** directory which holds pDAQ run configuration files */
    private File configurationPath;

    /** Hit reader */
    private CachingHitSpoolReader payloadReader;

    /** Offset to apply to every hit time */
    private long timeOffset;
    /** File reader thread */
    private PayloadFileThread fileThread;
    /** Hit writer thread */
    private OutputThread outThread;

    /**
     * Create a replay component
     *
     * @param hubId hub ID
     *
     * @throws Exception if there's a problem
     */
    public ReplayHubComponent(int hubId)
        throws Exception
    {
        super(COMPONENT_NAME, hubId);

        this.hubId = hubId;

        addMBean("jvm", new MemoryStatistics());
        addMBean("system", new SystemStatistics());
        addMBean("stringhub", this);

        cache = new VitreousBufferCache("RHGen#" + hubId);
        addCache(cache);
        addMBean("GenericBuffer", cache);

        IByteBufferCache rdoutDataCache  =
            new VitreousBufferCache("SHRdOut#" + hubId);
        addCache(DAQConnector.TYPE_READOUT_DATA, rdoutDataCache);
        sender = new Sender(hubId, rdoutDataCache);

        if (LOG.isInfoEnabled()) {
            LOG.info("starting up ReplayHub component " + hubId);
        }

        /*
         * Component derives behavioral characteristics from
         * its 'minor ID' which is the low 3 (decimal) digits of
         * the hub component ID:
         *  (1) component x000        : amandaHub
         *  (2) component x001 - x199 : in-ice hub
         *      (79 - 86 are deep core but this currently doesn't
         *      mean anything)
         *  (3) component x200 - x299 : icetop
         */
        int minorHubId = hubId % 1000;

        SimpleOutputEngine hitOut;
        if (minorHubId == 0) {
            hitOut = null;
        } else {
            hitOut = new SimpleOutputEngine(COMPONENT_NAME, hubId, "hitOut");
            if (minorHubId > 199) {
                addMonitoredEngine(DAQConnector.TYPE_ICETOP_HIT, hitOut);
            } else {
                addMonitoredEngine(DAQConnector.TYPE_STRING_HIT, hitOut);
            }
            sender.setHitOutput(hitOut);
            sender.setHitCache(cache);
        }

        ReadoutRequestFactory rdoutReqFactory =
            new ReadoutRequestFactory(cache);

        RequestReader reqIn;
        try {
            reqIn = new RequestReader(COMPONENT_NAME, sender, rdoutReqFactory);
        } catch (IOException ioe) {
            throw new Error("Couldn't create RequestReader", ioe);
        }
        addMonitoredEngine(DAQConnector.TYPE_READOUT_REQUEST, reqIn);

        SimpleOutputEngine dataOut =
            new SimpleOutputEngine(COMPONENT_NAME, hubId, "dataOut");
        addMonitoredEngine(DAQConnector.TYPE_READOUT_DATA, dataOut);

        sender.setDataOutput(dataOut);

        MonitoringData monData = new MonitoringData();
        monData.setSenderMonitor(sender);
        addMBean("sender", monData);

        // monitoring output stream
/*
        IByteBufferCache moniBufMgr =
            new VitreousBufferCache("RHMoni#" + hubId);
        addCache(DAQConnector.TYPE_MONI_DATA, moniBufMgr);

        SimpleOutputEngine moniOut =
            new SimpleOutputEngine(COMPONENT_NAME, hubId, "moniOut");
        addMonitoredEngine(DAQConnector.TYPE_MONI_DATA, moniOut);

        // time calibration output stream
        IByteBufferCache tcalBufMgr =
            new VitreousBufferCache("RHTCal#" + hubId);
        addCache(DAQConnector.TYPE_TCAL_DATA, tcalBufMgr);

        SimpleOutputEngine tcalOut =
            new SimpleOutputEngine(COMPONENT_NAME, hubId, "tcalOut");
        addMonitoredEngine(DAQConnector.TYPE_TCAL_DATA, tcalOut);

        // supernova output stream
        IByteBufferCache snBufMgr = new VitreousBufferCache("RHSN#" + hubId);
        addCache(DAQConnector.TYPE_SN_DATA, snBufMgr);

        SimpleOutputEngine snOut =
            new SimpleOutputEngine(COMPONENT_NAME, hubId, "snOut");
        addMonitoredEngine(DAQConnector.TYPE_SN_DATA, snOut);
*/
    }

    /**
     * Configure component.
     *
     * @param configName configuration name
     *
     * @throws DAQCompException if there is a problem
     */
    @SuppressWarnings("unchecked")
    public void configuring(String configName)
        throws DAQCompException
    {
        if (configurationPath == null) {
            throw new DAQCompException("Global configuration directory has" +
                                       " not been set");
        }

        // clear hit file name
        payloadReader = null;

        Document doc = loadXMLDocument(configurationPath, configName);

        // extract replayFiles element tree
        String replayFilesStr = "runConfig/replayFiles";
        Element replayFiles = (Element) doc.selectSingleNode(replayFilesStr);
        if (replayFiles == null) {
            throw new DAQCompException("No replayFiles entry found in " +
                                       configName);
        }

        // save base directory name
        String baseDir;
        Attribute bdAttr = replayFiles.attribute("baseDir");
        if (bdAttr == null) {
            baseDir = null;
        } else {
            baseDir = bdAttr.getValue();
        }

        // extract this hub's entry
        String hubNodeStr = replayFilesStr + "/hits[@hub='" + hubId + "']";
        Element hubNode = (Element) doc.selectSingleNode(hubNodeStr);
        if (hubNode == null) {
            throw new DAQCompException("No replayFiles entry for hub#" +
                                       hubId + " found in " +
                                       configName);
        }

        // get file paths
        File hitFile = getFile(baseDir, hubNode, "source");

        // done configuring
        if (LOG.isInfoEnabled()) {
            LOG.info("Hub#" + hubId + ": " + hitFile);
        }

        try {
            payloadReader = new CachingHitSpoolReader(hitFile, hubId);
        } catch (IOException ioe) {
            throw new DAQCompException("Cannot open " + hitFile, ioe);
        }
    }

    /**
     * Get the payload buffer cache
     *
     * @return buffer cache
     */
    public IByteBufferCache getCache()
    {
        return cache;
    }

    /**
     * Return the time when the first of the channels to stop has stopped.
     * @return the DAQ time (1E10 ticks/sec) of the hit which fulfills this
     *         condition.
     */
    public long getEarliestLastChannelHitTime()
    {
        if (fileThread == null) {
            return 0L;
        }

        return fileThread.getEarliestLastChannelHitTime();
    }

    /**
     * Report the total hit rate ( in Hz )
     * @return total hit rate in Hz
     */
    public double getHitRate()
    {
        return 0.0;
    }

    /**
     * Report the lc hit rate ( in Hz )
     * @return lc hit rate in Hz
     */
    public double getHitRateLC()
    {
        return 0.0;
    }

    /**
     * Return the time when the last of the channels to report hits has
     * finally reported
     * @return the DAQ time (1E10 ticks/sec) of the hit which fulfills this
     *         condition
     */
    public long getLatestFirstChannelHitTime()
    {
        if (fileThread == null) {
            return 0L;
        }

        return fileThread.getLatestFirstChannelHitTime();
    }

    /**
     * Return the number of requests queued for writing.
     *
     * @return output queue size
     */
    public long getNumQueued()
    {
        if (outThread == null) {
            return 0L;
        }

        return outThread.getNumQueued();
    }

    /**
     * Return an array of the number of active doms and the number of total
     * doms packed into an integer array to avoid 2 xmlrpc calls from the
     * ActiveDOMsTask
     *
     * @return [0] = number of active doms, [1] = total number of doms
     */
    public int[] getNumberOfActiveAndTotalChannels()
    {
        return new int[] { 60, 60 };
    }

    /**
     * Report number of functioning DOM channels under control of stringHub.
     * @return number of DOMs
     */
    public int getNumberOfActiveChannels()
    {
        return 60;
    }

    /**
     * Return the number of non-zombie DOMs for this hub
     *
     * @return number of non-zombies
     */
    public int getNumberOfNonZombies()
    {
        return 0;
    }

    /**
     * Get the time of the first hit being replayed.
     *
     * @return UTC time of first hit
     *
     * @throws DAQCompException if component is not a replay hub
     */
    public long getReplayStartTime()
        throws DAQCompException
    {
        if (payloadReader == null) {
            throw new DAQCompException("HitSpoolReader is null");
        }

        final long time = payloadReader.peekTime();
        if (time == Long.MIN_VALUE) {
            throw new DAQCompException("Cannot get next payload time");
        }

        return time;
    }

    /**
     * Get the sender object
     *
     * @return sender
     */
    public Sender getSender()
    {
        return sender;
    }

    /**
     * Get the current gap between DAQ time and system time
     *
     * @return gap between DAQ and system time
     */
    public double getTimeGap()
    {
        if (fileThread == null) {
            return 0L;
        }

        return fileThread.getTimeGap();
    }

    /**
     * Report time of the most recent hit object pushed into the HKN1
     * @return 0
     */
    public long getTimeOfLastHitInputToHKN1()
    {
        return 0L;
    }

    /**
     * Report time of the most recent hit object output from the HKN1
     * @return 0
     */
    public long getTimeOfLastHitOutputFromHKN1()
    {
        return 0L;
    }

    /**
     * Return the number of LBM overflows inside this string
     * @return 0
     */
    public long getTotalLBMOverflows()
    {
        return 0L;
    }

    /**
     * Return this component's svn version id as a String.
     *
     * @return svn version id as a String
     */
    public String getVersionInfo()
    {
        return "$Id$";
    }

    /**
     * Set the path for global configuration directory.
     *
     * @param dirName global configuration directory
     */
    @Override
    public void setGlobalConfigurationDir(String dirName)
    {
        super.setGlobalConfigurationDir(dirName);

        configurationPath = new File(dirName);
        if (!configurationPath.exists()) {
            throw new Error("Configuration directory \"" + configurationPath +
                            "\" does not exist");
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("Setting the ueber configuration directory to " +
                     configurationPath);
        }

        // load DOM registry and pass it to the sender
        DOMRegistry domRegistry;
        try {
            domRegistry = DOMRegistry.loadRegistry(configurationPath);
            sender.setDOMRegistry(domRegistry);
        } catch (ParserConfigurationException e) {
            LOG.error("Cannot load DOM registry", e);
        } catch (SAXException e) {
            LOG.error("Cannot load DOM registry", e);
        } catch (IOException e) {
            LOG.error("Cannot load DOM registry", e);
        }
    }

    /**
     * Set the offset applied to each hit being replayed.
     *
     * @param offset offset to apply to hit times
     */
    public void setReplayOffset(long offset)
    {
        timeOffset = offset;
    }

    /**
     * Start sending data.
     *
     * @throws DAQCompException if there is a problem
     */
    public void starting()
        throws DAQCompException
    {
        sender.reset();

        if (payloadReader == null) {
            throw new DAQCompException("Hit reader has not been initialized");
        }

        outThread = new OutputThread("OutputThread#" + hubId, sender);
        outThread.start();

        fileThread = new PayloadFileThread("ReplayThread#" + hubId,
                                           payloadReader, timeOffset,
                                           outThread);
        fileThread.start();
    }

    /**
     * Start subrun.
     *
     * @param flasherConfigs list of flasher config data
     *
     * @return nothing
     *
     * @throws DAQCompException always, because this is not yet implemented!
     */
    public long startSubrun(List<FlasherboardConfiguration> flasherConfigs)
        throws DAQCompException
    {
        throw new DAQCompException("Cannot yet replay flasher runs!!!");
    }

    /**
     * Stop sending data.
     */
    public void stopping()
    {
        fileThread.stopping();
    }

    /**
     * Main program.
     *
     * @param args command-line arguments
     *
     * @throws Exception if there is a problem
     */
    public static void main(String[] args)
        throws Exception
    {
        int hubId = Integer.getInteger("icecube.daq.stringhub.componentId");
        if (hubId == 0) {
            System.err.println("Hub ID not set, specify with" +
                               " -Dicecube.daq.stringhub.componentId=#");
            System.exit(1);
        }

        DAQCompServer srvr;
        try {
            srvr = new DAQCompServer(new ReplayHubComponent(hubId), args);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
            // avoid uninitialized 'srvr' warning
            return;
        }
        srvr.startServing();
    }
}

/**
 * Payload file writer thread.
 */
class PayloadFileThread
    implements Runnable
{
    /** error logger */
    private static final Logger LOG =
        Logger.getLogger(PayloadFileThread.class);

    /** hit spool reader */
    private CachingHitSpoolReader rdr;
    /** Offset to apply to every hit time */
    private long timeOffset;
    /** hit writer thread */
    private OutputThread outThread;

    /** The actual thread object */
    private Thread realThread;
    /** 'true' if this thread is stopping */
    private boolean stopping;

    /** first and last times for every DOM */
    private HashMap<Long, DOMTimes> domTimes =
        new HashMap<Long, DOMTimes>();

    /** time of the most recently read hit */
    private long prevTime = Long.MIN_VALUE;

    /** Gap between DAQ time and system time */
    private double gapSecs;
    /** total number of payloads read */
    private long totPayloads;

    /**
     * Create payload file writer thread.
     *
     * @param name thread name
     */
    PayloadFileThread(String name, CachingHitSpoolReader rdr, long timeOffset,
                      OutputThread outThread)
    {
        this.rdr = rdr;
        this.timeOffset = timeOffset;
        this.outThread = outThread;

        realThread = new Thread(this);
        realThread.setName(name);
    }

    /**
     * Build DOM stop message.
     *
     * @param stopBuff byte buffer to use (in not <tt>null</tt>)
     *
     * @return stop message
     */
    private ByteBuffer buildStopMessage()
    {
        final int stopLen = 32;

        ByteBuffer stopBuf = ByteBuffer.allocate(stopLen);

        stopBuf.putInt(0, stopLen);
        stopBuf.putLong(24, Long.MAX_VALUE);

        stopBuf.position(0);

        return stopBuf;
    }

    /**
     * No cleanup is needed.
     */
    private void finishThreadCleanup()
    {
    }

    /**
     * Return the time when the first of the channels to stop has stopped.
     * @return the DAQ time (1E10 ticks/sec) of the hit which fulfills this
     *         condition.
     */
    public long getEarliestLastChannelHitTime()
    {
        long earliestLast = Long.MAX_VALUE;
        boolean found = true;

        for (Long mbid : domTimes.keySet()) {
            long val = domTimes.get(mbid).getLastTime();
            if (val < 0L) {
                found = false;
                break;
            } else if (val < earliestLast) {
                earliestLast = val;
            }
        }

        if (!found) {
            return 0L;
        }

        return earliestLast;
    }

    /**
     * Return the time when the last of the channels to report hits has
     * finally reported
     * @return the DAQ time (1E10 ticks/sec) of the hit which fulfills this
     *         condition
     */
    public long getLatestFirstChannelHitTime()
    {
        long latestFirst = Long.MAX_VALUE;
        boolean found = true;

        for (Long mbid : domTimes.keySet()) {
            long val = domTimes.get(mbid).getFirstTime();
            if (val < 0L) {
                found = false;
                break;
            } else if (val > latestFirst) {
                latestFirst = val;
            }
        }

        if (!found) {
            return 0L;
        }

        return latestFirst;
    }

    /**
     * Get the current gap between DAQ time and system time
     *
     * @return gap between DAQ and system time
     */
    public double getTimeGap()
    {
        return gapSecs;
    }

    private void process()
    {
        boolean timeInit = false;
        long startSysTime = 0L;
        long startDAQTime = 0L;

        long numHits = 0;

        int gapCount = 0;
        while (!stopping && rdr.hasNext()) {
            ByteBuffer buf = rdr.next();
            if (buf == null) {
                break;
            }

            numHits++;

            final long daqTime = rdr.getUTCTime(buf);
            if (daqTime <= Long.MIN_VALUE) {
                final String fmtStr =
                    "Ignoring short hit buffer#%d (%d bytes)";
                LOG.error(String.format(fmtStr, numHits, buf.limit()));
                continue;
            }

            if (daqTime < prevTime) {
                final String fmtStr =
                    "Hit#%d went back %d in time! (cur %d, prev %d)";
                LOG.error(String.format(fmtStr, numHits, prevTime - daqTime,
                                        daqTime, prevTime));
                continue;
            }
            prevTime = daqTime;

            // get system time
            final long sysTime = System.nanoTime();

            // try to deliver payloads at the rate they were created
            if (!timeInit) {
                startSysTime = sysTime;
                startDAQTime = daqTime;
                timeInit = true;
            } else {
                // DAQ time is 0.1 ns so divide by 10L to match nanoTime()
                final long daqDiff =
                    (daqTime - startDAQTime) / 10L;
                final long sysDiff = sysTime - startSysTime;

                long sleepTime = daqDiff - sysDiff;

                // track the number of seconds' gap between system and DAQ time
                final long ns_per_sec = 1000000000L;
                gapSecs = ((double) sleepTime) / ((double) ns_per_sec);

                // whine if the time gap is greater than one second
                if (sleepTime > ns_per_sec * 2) {
                    if (rdr.getNumberOfPayloads() < 10) {
                        // minimize gap for first few payloads
                        sleepTime = ns_per_sec / 10L;
                    } else {
                        // complain about gap
                        final String fmtStr =
                            "Huge time gap (%.2f sec) for payload #%d from %s";
                        LOG.error(String.format(fmtStr, gapSecs,
                                                rdr.getNumberOfPayloads(),
                                                rdr.getFile()));
                        if (++gapCount > 20) {
                            LOG.error("Too many huge gaps ... aborting");
                            break;
                        }
                    }

                    // reset base times
                    startSysTime = sysTime + sleepTime;
                    startDAQTime = daqTime;
                }

                // if we're sending payloads too quickly, wait a bit
                if (sleepTime > ns_per_sec) {
                    try {
                        final long ns_per_ms = 1000000L;
                        final long sleepMS = sleepTime / ns_per_ms;
                        final int sleepNS = (int) (sleepTime % ns_per_ms);
                        Thread.sleep(sleepMS, sleepNS);
                    } catch (InterruptedException ie) {
                        // ignore interrupts
                    }
                }
            }

            // record the DAQ time for this DOM
            long mbid = buf.getLong(8);
            if (!domTimes.containsKey(mbid)) {
                domTimes.put(mbid, new DOMTimes());
            }
            domTimes.get(mbid).add(daqTime);

            if (timeOffset != 0) {
                rdr.setUTCTime(buf, daqTime + timeOffset);
            }

            buf.flip();

            outThread.push(buf);

            // don't overwhelm other threads
            Thread.yield();

        }

        LOG.error("Not writing STOP payload");

        try {
            rdr.close();
        } catch (IOException ioe) {
            // ignore errors on close
        }

        totPayloads += numHits;
        LOG.error("Finished writing " + numHits + " hits");
    }

    /**
     * Main file writer loop.
     */
    public void run()
    {
        process();

        outThread.push(buildStopMessage());

        finishThreadCleanup();
    }

    /**
     * Start the thread.
     */
    public void start()
    {
        if (realThread == null) {
            throw new Error("Thread has already been started!");
        }

        realThread.start();
        realThread = null;
    }

    /**
     * Notify the thread that it should stop
     */
    public void stopping()
    {
        stopping = true;
    }
}

/**
 * Class which writes payloads to an output channel.
 */
class OutputThread
    implements Runnable
{
    private static final Logger LOG = Logger.getLogger(OutputThread.class);

    private Thread thread;
    private boolean waiting;
    private boolean stopping;
    private boolean stopped;

    /** Output queue. */
    private Deque<ByteBuffer> outputQueue =
        new ArrayDeque<ByteBuffer>();

    /** Hit sender */
    private Sender sender;

    /**
     * Create and start output thread.
     *
     * @param name thread name
     * @param srcId trigger handler ID (used when creating merged triggers)
     * @param sender hit sender
     */
    public OutputThread(String name, Sender sender)
    {
        thread = new Thread(this);
        thread.setName(name);

        this.sender = sender;
    }

    /**
     * Return the number of requests queued for writing.
     *
     * @return output queue size
     */
    public long getNumQueued()
    {
        return outputQueue.size();
    }

    public boolean isStopped()
    {
        return stopped;
    }

    public boolean isWaiting()
    {
        return waiting;
    }

    public void notifyThread()
    {
        synchronized (outputQueue) {
            outputQueue.notify();
        }
    }

    public void push(ByteBuffer buf)
    {
        if (buf != null) {
            synchronized (outputQueue) {
                outputQueue.addLast(buf);
                outputQueue.notify();
            }
        }
    }

    /**
     * Main output loop.
     */
    public void run()
    {
        ByteBuffer buf;
        while (!stopping || outputQueue.size() > 0) {
            synchronized (outputQueue) {
                if (outputQueue.size() == 0) {
                    try {
                        waiting = true;
                        outputQueue.wait();
                    } catch (InterruptedException ie) {
                        LOG.error("Interrupt while waiting for output" +
                                  " queue", ie);
                    }
                    waiting = false;
                }

                if (outputQueue.size() == 0) {
                    buf = null;
                } else {
                    buf = outputQueue.removeFirst();
                }
            }

            if (buf == null) {
                continue;
            }

            sender.consume(buf);
        }

        stopped = true;
    }

    public void start()
    {
        thread.start();
    }

    public void stop()
    {
        synchronized (outputQueue) {
            stopping = true;
            outputQueue.notify();
        }
    }
}

/**
 * Decorator which adds peek method to HitSpoolReader
 */
class CachingHitSpoolReader
{
    /** error logger */
    private static final Logger LOG =
        Logger.getLogger(CachingHitSpoolReader.class);

    /** actual reader */
    private HitSpoolReader rdr;
    /** buffer cache */
    private ByteBuffer cachedBuf;

    /**
     * Create the reader
     *
     * @param payFile hitspool file
     * @param hubId this hub's ID
     *
     * @throws IOException if there is a problem opening the file
     */
    CachingHitSpoolReader(File payFile, int hubId)
        throws IOException
    {
        rdr = new HitSpoolReader(payFile, hubId);
    }

    /**
     * Close the file
     *
     * @throws IOException if there is an error
     */
    void close()
        throws IOException
    {
        rdr.close();
    }

    /**
     * Get the hit time from the buffer
     */
    static long getUTCTime(ByteBuffer buf)
    {
        if (buf.limit() < 32) {
            return Long.MIN_VALUE;
        }

        return buf.getLong(24);
    }

    /**
     * Return the file being read
     *
     * @return current file
     */
    File getFile()
    {
        return rdr.getFile();
    }

    /**
     * Get the total number of hits read from the file
     *
     * @return number of hits
     */
    int getNumberOfPayloads()
    {
        if (cachedBuf == null) {
            // if there's no cached hit, we've read all the hits
            return rdr.getNumberOfPayloads();
        }

        // don't count the cached hit
        return rdr.getNumberOfPayloads() - 1;
    }

    /**
     * Is there another hit?
     *
     * @return <tt>true</tt> if there's another hit
     */
    boolean hasNext()
    {
        if (cachedBuf != null) {
            return true;
        }

        return rdr.hasNext();
    }

    /**
     * Get the next hit
     *
     * @return next hit
     */
    ByteBuffer next()
    {
        if (cachedBuf != null) {
            ByteBuffer tmp = cachedBuf;
            cachedBuf = null;
            return tmp;
        }

        return rdr.next();
    }

    /**
     * Return the time of the next hit
     *
     * @return next hit time (Long.MIN_VALUE if there's no next hit)
     */
    long peekTime()
    {
        if (cachedBuf == null) {
            cachedBuf = rdr.next();
        }

        return getUTCTime(cachedBuf);
    }

    /**
     * Set the hit time
     *
     * @param buf hit buffer
     * @param nextTime time to write to hit buffer
     */
    static void setUTCTime(ByteBuffer buf, long newTime)
    {
        if (buf.limit() < 32) {
            LOG.error(String.format("Cannot modify %d byte buffer",
                                    buf.limit()));
        } else {
            buf.putLong(24, newTime);
        }
    }
}
