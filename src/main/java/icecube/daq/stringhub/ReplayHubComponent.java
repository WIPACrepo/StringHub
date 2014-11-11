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
import icecube.daq.util.JAXPUtil;
import icecube.daq.util.JAXPUtilException;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.xml.sax.SAXException;

/**
 * Keep track of DOM first and last times
 */
class DOMTimes
{
    private static final Logger LOG = Logger.getLogger(DOMTimes.class);

    private String dom;
    private long firstTime = Long.MIN_VALUE;
    private long lastTime = Long.MIN_VALUE;

    DOMTimes(long mbid)
    {
        this.dom = String.format("%012x", mbid);
    }

    /**
     * Add the next DOM time
     *
     * @param time DOM time
     */
    public void add(long time)
    {
        if (firstTime == Long.MIN_VALUE) {
            firstTime = time;
        } else if (time < firstTime) {
            LOG.error("Reset " + dom + " first time from " + firstTime +
                      " to " + time);
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

        Document doc;
        try {
            doc = JAXPUtil.loadXMLDocument(configurationPath, configName);
        } catch (JAXPUtilException jux) {
            throw new DAQCompException(jux);
        }

        final String replayFilesStr = "runConfig/replayFiles";

        // extract replayFiles element tree
        Element replayFiles;
        try {
            replayFiles =
                (Element) JAXPUtil.extractNode(doc, replayFilesStr);
        } catch (JAXPUtilException jux) {
            throw new DAQCompException(jux);
        }
        if (replayFiles == null) {
            throw new DAQCompException("No replayFiles entry found in " +
                                       configName);
        }

        // save base directory name
        String baseDir = replayFiles.getAttribute("baseDir");

        // extract this hub's entry
        String hubNodeStr = replayFilesStr + "/hits[@hub='" + hubId + "']";
        Element hubNode;
        try {
            hubNode = (Element) JAXPUtil.extractNode(doc, hubNodeStr);
        } catch (JAXPUtilException jux) {
            throw new DAQCompException(jux);
        }
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
            LOG.error("No active thread for getEarliestLastChannelHitTime");
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
            LOG.error("No active thread for getLatestFirstChannelHitTime");
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
        return 60;
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
            LOG.error("No active thread for getTimeGap");
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
     * Get the total number of payloads read.
     *
     * @return total payloads
     */
    public long getTotalPayloads()
    {
        if (fileThread == null) {
            LOG.error("No active thread for getTotalPayloads");
            return 0L;
        }

        return fileThread.getTotalPayloads();
    }

    /**
     * Get the total time (in nanoseconds) spent sleeping in order to
     * match DAQ time to system time
     *
     * @return total nanoseconds spent sleeping
     */
    public long getTotalSleep()
    {
        if (fileThread == null) {
            LOG.error("No active thread for getTotalSleep");
            return 0L;
        }

        return fileThread.getTotalSleep();
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
        if (fileThread == null) {
            LOG.error("No active thread for stopping");
        }

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

    /** Nanoseconds per second */
    private static final long NS_PER_SEC = 1000000000L;

    /** hit spool reader */
    private CachingHitSpoolReader rdr;
    /** Offset to apply to every hit time */
    private long timeOffset;
    /** hit writer thread */
    private OutputThread outThread;

    /** The actual thread object */
    private Thread realThread;
    /** 'true' if this thread has been started */
    private boolean started;
    /** 'true' if this thread is stopping */
    private boolean stopping;

    /** first and last times for every DOM */
    private HashMap<Long, DOMTimes> domTimes =
        new HashMap<Long, DOMTimes>();

    /** Gap between DAQ time and system time */
    private long timeGap;
    /** Total time spent sleeping so payload time matches system time */
    private long totalSleep;
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
        long latestFirst = Long.MIN_VALUE;
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

        if (!found || latestFirst < 0L) {
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
        return ((double) timeGap) / ((double) NS_PER_SEC);
    }

    /**
     * Get the total number of payloads read.
     *
     * @return total payloads
     */
    public long getTotalPayloads()
    {
        return totPayloads;
    }

    /**
     * Get the total time (in nanoseconds) spent sleeping in order to
     * match DAQ time to system time
     *
     * @return total nanoseconds spent sleeping
     */
    public long getTotalSleep()
    {
        return totalSleep;
    }

    private void process()
    {
        boolean firstPayload = true;
        TimeKeeper sysTime = new TimeKeeper(true);
        TimeKeeper daqTime = new TimeKeeper(false);

        long numHits = 0;

        int gapCount = 0;
        while (!stopping) {
            if (!rdr.hasNext()) break;

            ByteBuffer buf = rdr.next();
            if (buf == null) {
                break;
            }

            numHits++;
            totPayloads++;

            final long rawTime = rdr.getUTCTime(buf);
            if (rawTime == Long.MIN_VALUE) {
                final String fmtStr =
                    "Ignoring short hit buffer#%d (%d bytes)";
                LOG.error(String.format(fmtStr, numHits, buf.limit()));
                continue;
            }

            // set the DAQ time
            if (!daqTime.set(rawTime + timeOffset, numHits)) {
                // if the current time if before the previous time, skip it
                continue;
            }

            // update the raw buffer's hit time
            if (timeOffset != 0) {
                rdr.setUTCTime(buf, daqTime.get());
            }

            // set system time
            if (!sysTime.set(System.nanoTime(), numHits)) {
                // if the current time if before the previous time, skip it
                continue;
            }

            if (firstPayload) {
                // don't need to recalibrate the first payload
                firstPayload = false;
            } else {
                // try to deliver payloads at the rate they were created

                // get the difference the current system time and
                //  the next payload time
                timeGap = daqTime.baseDiff() - sysTime.baseDiff();

                // whine if the time gap is greater than one second
                if (timeGap > NS_PER_SEC * 2) {
                    if (rdr.getNumberOfPayloads() < 10) {
                        // minimize gap for first few payloads
                        timeGap = NS_PER_SEC / 10L;
                    } else {
                        // complain about gap
                        final String fmtStr =
                            "Huge time gap (%.2f sec) for payload #%d from %s";
                        LOG.error(String.format(fmtStr, getTimeGap(),
                                                rdr.getNumberOfPayloads(),
                                                rdr.getFile()));
                        if (++gapCount > 20) {
                            LOG.error("Too many huge gaps ... aborting");
                            break;
                        }
                    }

                    // reset base times
                    sysTime.setBase(timeGap);
                    daqTime.setBase(0L);
                }

                // if we're sending payloads too quickly, wait a bit
                if (timeGap > NS_PER_SEC) {
                    totalSleep += timeGap;

                    try {
                        final long ns_per_ms = 1000000L;
                        final long sleepMS = timeGap / ns_per_ms;
                        final int sleepNS = (int) (timeGap % ns_per_ms);
                        Thread.sleep(sleepMS, sleepNS);
                    } catch (InterruptedException ie) {
                        // ignore interrupts
                    }
                }
            }

            // record the DAQ time for this DOM
            long mbid = buf.getLong(8);
            if (!domTimes.containsKey(mbid)) {
                domTimes.put(mbid, new DOMTimes(mbid));
            }
            domTimes.get(mbid).add(daqTime.get());

            buf.flip();

            outThread.push(buf);

            // don't overwhelm other threads
            Thread.yield();
        }

        outThread.stop();

        try {
            rdr.close();
        } catch (IOException ioe) {
            // ignore errors on close
        }

        LOG.error("Finished queuing " + numHits + " hits");
    }

    /**
     * Main file writer loop.
     */
    public void run()
    {
        try {
            process();
        } catch (Throwable thr) {
            LOG.error("Processing failed after " + totPayloads + " hits");
        }

        finishThreadCleanup();
    }

    /**
     * Start the thread.
     */
    public void start()
    {
        if (started) {
            throw new Error("Thread has already been started!");
        }

        realThread.start();
        started = true;
    }

    /**
     * Notify the thread that it should stop
     */
    public void stopping()
    {
        if (!started) {
            throw new Error("Thread has not been started!");
        }

        stopping = true;
        realThread.interrupt();
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
     * Build DOM stop message.
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
        // sleep for a second so detector has a chance to get first good time
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            LOG.error("Initial output thread sleep interrupted", ie);
        }

        ByteBuffer buf;
        while (!stopping || outputQueue.size() > 0) {
            synchronized (outputQueue) {
                if (!stopping && outputQueue.size() == 0) {
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

        sender.consume(buildStopMessage());

        stopped = true;

        LOG.error("Finished writing hits");
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
 * Track various times.
 */
class TimeKeeper
{
    private static final Logger LOG = Logger.getLogger(TimeKeeper.class);

    private boolean isSystemTime;
    private boolean initialized;
    private long firstTime;
    private long baseTime;
    private long lastTime;

    /**
     * Create a time keeper
     *
     * @param isSystemTime - <tt>true</tt> if this is for the system time,
     *                       <tt>false</tt> for DAQ time
     */
    TimeKeeper(boolean isSystemTime)
    {
        this.isSystemTime = isSystemTime;
    }

    /**
     * Return the difference between the last time and the base time (in ns).
     *
     * @return difference in nanoseconds
     */
    long baseDiff()
    {
        long diff = lastTime - baseTime;
        if (!isSystemTime) {
            // convert DAQ time (10ths of ns) to system time
            diff /= 10L;
        }
        return diff;
    }

    /**
     * Return the difference between the last time and the first time (in ns).
     *
     * @return difference in nanoseconds
     */
    long realDiff()
    {
        long diff = lastTime - firstTime;
        if (!isSystemTime) {
            // convert DAQ time (10ths of ns) to system time
            diff /= 10L;
        }
        return diff;
    }

    /**
     * Get the most recent time
     *
     * @return time (ns for system time, 10ths of ns for DAQ time)
     */
    long get()
    {
        return lastTime;
    }

    /**
     * Record the next time.
     *
     * @param time next time
     * @param hitNum sequential hit number to use in error reporting
     *
     * @return <tt>false</tt> if <tt>time</tt> preceeds the previous time
     */
    boolean set(long time, long hitNum)
    {
        if (!initialized) {
            firstTime = time;
            baseTime = time;
            initialized = true;
        } else if (time < lastTime) {
            String timeType;
            if (isSystemTime) {
                timeType = "System";
            } else {
                timeType = "DAQ";
            }

            final String fmtStr =
                "%s hit#%d went back %d in time! (cur %d, prev %d)";
            LOG.error(String.format(fmtStr, hitNum, lastTime - time, time,
                                    lastTime));
            return false;
        }

        lastTime = time;
        return true;
    }

    /**
     * Set the base time as <tt>offset</tt> from the most recent time
     *
     * @param offset time offset
     */
    void setBase(long offset)
    {
        baseTime = lastTime + offset;
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
