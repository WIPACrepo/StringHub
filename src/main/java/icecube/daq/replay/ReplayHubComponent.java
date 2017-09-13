package icecube.daq.replay;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.io.HitSpoolReader;
import icecube.daq.io.PayloadByteReader;
import icecube.daq.io.SimpleOutputEngine;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQCompServer;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.juggler.mbean.MemoryStatistics;
import icecube.daq.juggler.mbean.SystemStatistics;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.impl.ReadoutRequestFactory;
import icecube.daq.payload.impl.VitreousBufferCache;
import icecube.daq.sender.RequestReader;
import icecube.daq.sender.SenderSubsystem;
import icecube.daq.util.DOMRegistryException;
import icecube.daq.util.DOMRegistryFactory;
import icecube.daq.util.IDOMRegistry;
import icecube.daq.util.FlasherboardConfiguration;
import icecube.daq.util.JAXPUtil;
import icecube.daq.util.JAXPUtilException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

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

    /** maximum number of huge time gaps allowed before the run is killed */
    private static final int MAX_GAPS = 20;

    /** ID of this replay hub */
    private int hubId;
    /** Cache used to allocate new hit payloads */
    private IByteBufferCache cache;
    /** Hit sender */
    private SenderSubsystem sender;
    /** DOM database */
    private IDOMRegistry domRegistry;

    /** directory which holds pDAQ run configuration files */
    private File configurationPath;

    /** Hit reader */
    private CachingPayloadReader hitReader;
    /** time of first hit */
    private long firstTime;

    /** List of data stream output processors */
    private HandlerOutputProcessor[] outputProc =
        new HandlerOutputProcessor[4];
    /** List of data stream handlers */
    private FileHandler[] handlers = new FileHandler[4];

    //todo: Remove after rhinelander release
    /**
     * Configuration directive to fall back to the legacy HitSpool/Sender
     * implementations;
     */
    public static final boolean USE_LEGACY_SENDER =
    Boolean.getBoolean("icecube.daq.sender.SenderSubsystem.use-legacy-sender");

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
    }

    public void initialize()
    {
        addMBean("jvm", new MemoryStatistics());
        addMBean("system", new SystemStatistics());
        addMBean("stringhub", this);

        cache = new VitreousBufferCache("RHGen#" + hubId);
        addCache(cache);
        addMBean("GenericBuffer", cache);

        IByteBufferCache rdoutDataCache  =
            new VitreousBufferCache("SHRdOut#" + hubId);
        addCache(DAQConnector.TYPE_READOUT_DATA, rdoutDataCache);

        try
        {
            if(USE_LEGACY_SENDER)
            {
                sender =
                   SenderSubsystem.Factory.REPLAY_COMPONENT.createLegacy(hubId,
                        cache, rdoutDataCache, domRegistry);
            }
            else
            {
                sender = SenderSubsystem.Factory.REPLAY_COMPONENT.create(hubId,
                        cache, rdoutDataCache, domRegistry);
            }
        }
        catch (IOException ioe)
        {
            throw new Error("Couldn't create hub#" + hubId + " Sender",
                    ioe);
        }

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
        }

        IByteBufferCache rdoutReqCache  =
            new VitreousBufferCache("SHRReq#" + hubId);
        addCache(DAQConnector.TYPE_READOUT_REQUEST, rdoutReqCache);
        ReadoutRequestFactory rdoutReqFactory =
            new ReadoutRequestFactory(rdoutReqCache);

        RequestReader reqIn;
        try {
            reqIn = new RequestReader(COMPONENT_NAME,
                    sender.getReadoutRequestHandler(), rdoutReqFactory);
        } catch (IOException ioe) {
            throw new Error("Couldn't create hub#" + hubId + " RequestReader",
                            ioe);
        }
        addMonitoredEngine(DAQConnector.TYPE_READOUT_REQUEST, reqIn);

        SimpleOutputEngine dataOut =
            new SimpleOutputEngine(COMPONENT_NAME, hubId, "dataOut");
        addMonitoredEngine(DAQConnector.TYPE_READOUT_DATA, dataOut);

        sender.setDataOutput(dataOut);

        addMBean("sender", sender.getMonitor());

        // monitoring output stream
        SimpleOutputEngine moniOut =
            new SimpleOutputEngine(COMPONENT_NAME, hubId, "moniOut");
        addMonitoredEngine(DAQConnector.TYPE_MONI_DATA, moniOut);

        // time calibration output stream
        SimpleOutputEngine tcalOut =
            new SimpleOutputEngine(COMPONENT_NAME, hubId, "tcalOut");
        addMonitoredEngine(DAQConnector.TYPE_TCAL_DATA, tcalOut);

        // supernova output stream
        SimpleOutputEngine snOut =
            new SimpleOutputEngine(COMPONENT_NAME, hubId, "snOut");
        addMonitoredEngine(DAQConnector.TYPE_SN_DATA, snOut);

        // initialize output processors
        for (DataStreamType dst : DataStreamType.values()) {
            HandlerOutputProcessor hout;
            switch (dst) {
            case HIT:
                outputProc[dst.index()] =
                        new HitOutputProcessor(sender.getHitInput());
                break;
            case MONI:
                outputProc[dst.index()] = new StreamOutputProcessor(moniOut);
                break;
            case SN:
                outputProc[dst.index()] = new StreamOutputProcessor(snOut);
                break;
            case TCAL:
                outputProc[dst.index()] = new StreamOutputProcessor(tcalOut);
                break;
            }
        }
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
                                       " not been set for hub#" + hubId);
        }

        Document doc;
        try {
            doc = JAXPUtil.loadXMLDocument(configurationPath, configName);
        } catch (JAXPUtilException jux) {
            throw new DAQCompException("Hub#" + hubId + " config failed", jux);
        }

        final String fwdProp = "sender/forwardIsolatedHitsToTrigger";
        try {
            final String fwdText = JAXPUtil.extractText(doc, fwdProp);
            if (fwdText.equalsIgnoreCase("true")) {
                LOG.error("Enabled hit forwarding");
                sender.forwardIsolatedHitsToTrigger();
            }
        } catch (JAXPUtilException jux) {
            throw new DAQCompException("Hub#" + hubId + " config failed", jux);
        }

        final String replayFilesStr = "runConfig/replayFiles";

        // extract replayFiles element tree
        Element replayFiles;
        try {
            replayFiles =
                (Element) JAXPUtil.extractNode(doc, replayFilesStr);
        } catch (JAXPUtilException jux) {
            throw new DAQCompException("Hub#" + hubId +
                                       " <replayFiles> parse failed", jux);
        }
        if (replayFiles == null) {
            throw new DAQCompException("No <replayFiles> entry found for" +
                                       "  hub#" + hubId + " in " + configName);
        }

        // extract this hub's entry
        Element hubNode;

        final String dataNodeStr =
            replayFilesStr + "/data[@hub='" + hubId + "']";
        try {
            hubNode = (Element) JAXPUtil.extractNode(doc, dataNodeStr);
        } catch (JAXPUtilException jux) {
            throw new DAQCompException("Hub#" + hubId +
                                       " <data> parse failed", jux);
        }
        if (hubNode == null) {
            final String hitNodeStr =
                replayFilesStr + "/hit[@hub='" + hubId + "']";
            try {
                hubNode = (Element) JAXPUtil.extractNode(doc, hitNodeStr);
            } catch (JAXPUtilException jux) {
                throw new DAQCompException("Hub#" + hubId +
                                           " <hit> parse failed", jux);
            }

            throw new DAQCompException("No <data> or <hit> entry for hub#" +
                                       hubId + " found in " + configName);
        }

        File dataDir;
        String topdir;
        String subdir;

        // try to get file path
        topdir = replayFiles.getAttribute("dir");
        if (topdir != null && topdir.length() > 0) {
            // build subdirectory name
            if (hubId < 200) {
                subdir = String.format("ichub%02d", hubId);
            } else {
                subdir = String.format("ithub%02d", hubId - 200);
            }
        } else {
            // try to get old-style file path
            topdir = replayFiles.getAttribute("baseDir");
            if (topdir == null || topdir.length() == 0) {
                throw new DAQCompException("Neither 'dir' nor 'baseDir'" +
                                           " attribute found for hub#" +
                                           hubId + " <replayFiles> entry in " +
                                           configName);
            }

            // get subdirectory name
            subdir = hubNode.getAttribute("source");
            if (subdir == null || subdir == "") {
                throw new DAQCompException("Hub#" + hubId + " <replayFiles>" +
                                           " does not specify 'source'" +
                                           " attribute");
            }
        }

        // build path to replay directory
        if (topdir == null) {
            dataDir = new File(subdir);
        } else {
            dataDir = new File(topdir, subdir);
        }

        // make sure path exists
        if (!dataDir.exists()) {
            // it could be a compressed file
            File dd2 = new File(topdir, subdir + ".gz");
            if (dd2.exists()) {
                dataDir = dd2;
            } else {
                String hostname;
                try {
                    hostname = InetAddress.getLocalHost().getHostName();
                } catch (Exception ex) {
                    hostname = "unknown";
                }
                throw new DAQCompException(dataDir.toString() +
                                           " does not exist on " + hostname);
            }
        }

        try {
            hitReader = new CachingPayloadReader(dataDir, hubId);
        } catch (IOException ioe) {
            throw new DAQCompException("Cannot open " + dataDir, ioe);
        }

        // save first time so CnCServer can calculate the run start time
        firstTime = hitReader.peekTime();
        if (firstTime == Long.MIN_VALUE) {
            throw new DAQCompException("Cannot get first payload time" +
                                       " for hub#" + hubId);
        }

        for (DataStreamType dst : DataStreamType.values()) {
            final Iterator<ByteBuffer> fileReader;
            if (dst == DataStreamType.HIT) {
                fileReader = hitReader;
            } else {
                File f = new File(dataDir, dst.filename() + "-0.dat");
                if (!f.exists()) {
                    LOG.warn("No " + dst.filename() +
                             " data available for hub#" + hubId + " (" + f +
                             "); closing stream");
                    outputProc[dst.index()].stop();
                    handlers[dst.index()] = null;
                    continue;
                }

                try {
                    fileReader = new PayloadByteReader(f, cache);
                } catch (IOException ioe) {
                    throw new DAQCompException("Cannot create " + dst +
                                               " file handler", ioe);
                }
            }

            handlers[dst.index()] = new FileHandler(hubId, dst, fileReader);
            if (dst == DataStreamType.HIT &&
                MAX_GAPS != FileHandler.MAX_HUGE_GAPS)
            {
                handlers[dst.index()].setMaximumNumberOfHugeTimeGaps(MAX_GAPS);
            }
        }

        // done configuring
        if (LOG.isInfoEnabled()) {
            LOG.info("Hub#" + hubId + ": " + dataDir);
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
        final int idx = DataStreamType.HIT.index();
        if (handlers[idx] == null) {
            return 0L;
        }

        return handlers[idx].getEarliestLastChannelHitTime();
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
        final int idx = DataStreamType.HIT.index();
        if (handlers[idx] == null) {
            return 0L;
        }

        return handlers[idx].getLatestFirstChannelHitTime();
    }

    public int getNumFiles()
    {
        if (hitReader != null) {
            return hitReader.getNumberOfFiles();
        }

        return 0;
    }

    /**
     * Return the number of payloads queued for reading.
     *
     * @return input queue size
     */
    public long getNumInputsQueued()
    {
        final int idx = DataStreamType.HIT.index();
        if (handlers[idx] == null) {
            return 0L;
        }

        return handlers[idx].getNumInputsQueued();
    }

    /**
     * Return the number of payloads queued for writing.
     *
     * @return output queue size
     */
    public long getNumOutputsQueued()
    {
        final int idx = DataStreamType.HIT.index();
        if (handlers[idx] == null) {
            return 0L;
        }

        return handlers[idx].getNumOutputsQueued();
    }

    /**
     * Return an array of the number of active doms and the number of total
     * doms packed into an integer array to avoid 2 rpc calls from the
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
        return firstTime;
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
     * Get the total time (in nanoseconds) behind the DAQ time.
     *
     * @return total nanoseconds behind the current DAQ time
     */
    public long getTotalBehind()
    {
        final int idx = DataStreamType.HIT.index();
        if (handlers[idx] == null) {
            return 0L;
        }

        return handlers[idx].getTotalBehind();
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
        final int idx = DataStreamType.HIT.index();
        if (handlers[idx] == null) {
            return 0L;
        }

        return handlers[idx].getTotalPayloads();
    }

    /**
     * Get the total time (in nanoseconds) spent sleeping in order to
     * match DAQ time to system time
     *
     * @return total nanoseconds spent sleeping
     */
    public long getTotalSleep()
    {
        final int idx = DataStreamType.HIT.index();
        if (handlers[idx] == null) {
            return 0L;
        }

        return handlers[idx].getTotalSleep();
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
                            "\" does not exist for hub#" + hubId);
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("Setting the ueber configuration directory to " +
                     configurationPath);
        }

        // load DOM registry
        try {
            domRegistry = DOMRegistryFactory.load(configurationPath);
        } catch (DOMRegistryException dre) {
            LOG.error("Cannot load hub#" + hubId + " DOM registry", dre);
        }
    }

    /**
     * Set the offset applied to each hit being replayed.
     *
     * @param offset offset to apply to hit times
     */
    public void setReplayOffset(long offset)
    {
        for (DataStreamType dst : DataStreamType.values()) {
            if (handlers[dst.index()] != null) {
                handlers[dst.index()].setReplayOffset(offset);
            }
        }
    }

    /**
     * Start sending data.
     *
     * @throws DAQCompException if there is a problem
     */
    @Override
    public void starting(int runNumber)
        throws DAQCompException
    {
        sender.startup();

        for (int i = 0; i < outputProc.length; i++) {
            if (handlers[i] != null) {
                handlers[i].startThreads(outputProc[i]);
            }
        }
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
    @Override
    public long startSubrun(List<FlasherboardConfiguration> flasherConfigs)
        throws DAQCompException
    {
        throw new DAQCompException("Cannot yet replay flasher runs!!!");
    }

    /**
     * Stop sending data.
     */
    @Override
    public void stopping()
    {
        for (DataStreamType dst : DataStreamType.values()) {
            if (handlers[dst.index()] != null) {
                handlers[dst.index()].stopThreads();
            }
        }
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
        ConsoleAppender appender = new ConsoleAppender();
        appender.setWriter(new PrintWriter(System.out));
        appender.setLayout(new PatternLayout("%p[%t] %L - %m%n"));
        appender.setName("console");
        Logger.getRootLogger().addAppender(appender);

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
 * Decorator which adds peek method to HitSpoolReader
 */
class CachingPayloadReader
    extends HitSpoolReader
{
    /** error logger */
    private static final Logger LOG =
        Logger.getLogger(CachingPayloadReader.class);

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
    CachingPayloadReader(File payFile, int hubId)
        throws IOException
    {
        super(payFile, hubId);
    }

    /**
     * Get the total number of hits read from the file
     *
     * @return number of hits
     */
    @Override
    public int getNumberOfPayloads()
    {
        final int num = super.getNumberOfPayloads();
        if (cachedBuf != null) {
            // don't count the cached hit
            return num - 1;
        }

        return num;
    }

    /**
     * Is there another hit?
     *
     * @return <tt>true</tt> if there's another hit
     */
    @Override
    public boolean hasNext()
    {
        if (cachedBuf != null) {
            return true;
        }

        return super.hasNext();
    }

    /**
     * Get the next hit
     *
     * @return next hit
     */
    @Override
    public ByteBuffer next()
    {
        if (cachedBuf != null) {
            ByteBuffer tmp = cachedBuf;
            cachedBuf = null;
            return tmp;
        }

        return super.next();
    }

    /**
     * Return the time of the next hit
     *
     * @return next hit time (Long.MIN_VALUE if there's no next hit)
     */
    long peekTime()
    {
        if (cachedBuf == null) {
            cachedBuf = super.next();
        }

        return BBUTC.get(cachedBuf);
    }
}

class HitOutputProcessor
    implements HandlerOutputProcessor
{
    private BufferConsumer sender;

    HitOutputProcessor(BufferConsumer sender)
    {
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

    public void send(ByteBuffer buf)
    {
        try
        {
            sender.consume(buf);
        }
        catch (IOException e)
        {
           throw new Error(e);
        }
    }

    public void stop()
    {
        try
        {
            sender.consume(buildStopMessage());
        }
        catch (IOException e)
        {
            throw new Error(e);
        }
    }
}

class StreamOutputProcessor
    implements HandlerOutputProcessor
{
    private SimpleOutputEngine out;

    StreamOutputProcessor(SimpleOutputEngine out)
    {
        this.out = out;
    }

    public void send(ByteBuffer buf)
    {
        out.getChannel().receiveByteBuffer(buf);
    }

    public void stop()
    {
        out.sendLastAndStop();
    }
}
