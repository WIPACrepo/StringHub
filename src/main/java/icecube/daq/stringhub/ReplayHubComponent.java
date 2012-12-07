package icecube.daq.stringhub;

import icecube.daq.io.PayloadByteReader;
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.xml.sax.SAXException;

public class ReplayHubComponent
    extends DAQComponent
    implements ReplayHubComponentMBean
{
    private static final Logger LOG =
        Logger.getLogger(ReplayHubComponent.class);

    private static final String COMPONENT_NAME = "replayHub";

    private int hubId;
    private IByteBufferCache cache;
    private Sender sender;
    private DOMRegistry domRegistry;

    private String configurationPath;

    private File hitFile;

    private boolean stopped;

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
            if (minorHubId > 199)
                addMonitoredEngine(DAQConnector.TYPE_ICETOP_HIT, hitOut);
            else
                addMonitoredEngine(DAQConnector.TYPE_STRING_HIT, hitOut);
            sender.setHitOutput(hitOut);
            sender.setHitCache(cache);
        }

        ReadoutRequestFactory rdoutReqFactory =
            new ReadoutRequestFactory(cache);

        RequestReader reqIn;
        try
        {
            reqIn = new RequestReader(COMPONENT_NAME, sender, rdoutReqFactory);
        }
        catch (IOException ioe)
        {
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
     */
    @SuppressWarnings("unchecked")
    public void configuring(String configName)
        throws DAQCompException
    {
        // clear hit file name
        hitFile = null;

        // make sure to add '.xml' if missing
        String extension;
        if (configName.endsWith(".xml")) {
            extension = "";
        } else {
            extension = ".xml";
        }

        // build config file path
        File masterConfigFile =
            new File(configurationPath, configName + extension);

        // open config file
        FileInputStream fis;
        try {
            fis = new FileInputStream(masterConfigFile);
        } catch (FileNotFoundException fnfe) {
            throw new DAQCompException("Couldn't open " + masterConfigFile);
        }

	Document doc;
	try {
	    // read in config XML
	    try {
		doc = new SAXReader().read(fis);
	    } catch (DocumentException de) {
		throw new DAQCompException("Couldn't read " + masterConfigFile +
					   ": " + de.getMessage());
	    }
	} finally {
	    // done with the fileinputstream
	    try {
		fis.close();
	    } catch (IOException e) {
		throw new DAQCompException("Could not close the master config file input stream");
	    }
	}

        // extract hubFiles element tree
        String hubFilesStr = "runConfig/hubFiles";
        Element hubFiles = (Element) doc.selectSingleNode(hubFilesStr);
        if (hubFiles == null) {
            throw new DAQCompException("No hubFiles entry found in " +
                                       masterConfigFile);
        }

        // save base directory name
        String baseDir;
        Attribute bdAttr = hubFiles.attribute("baseDir");
        if (bdAttr == null) {
            baseDir = null;
        } else {
            baseDir = bdAttr.getValue();
        }

        // extract this hub's entry
        String hubNodeStr = hubFilesStr + "/hub[@id='" + hubId + "']";
        Element hubNode = (Element) doc.selectSingleNode(hubNodeStr);
        if (hubNode == null) {
            throw new DAQCompException("No hubFiles entry for hub#" + hubId +
                                       " found in " + masterConfigFile);
        }

        // get file paths
        hitFile = getFile(baseDir, hubNode, "hitFile");

        // done configuring
        if (LOG.isInfoEnabled()) {
            LOG.info("Hub#" + hubId + ": " + hitFile);
        }
    }

    public IByteBufferCache getCache()
    {
        return cache;
    }

    /**
     * Get the file associated with the specified attribute.
     *
     * @param dataDir data directory
     * @param elem XML element describing this hub's input files
     * @param attrName name of file attribute
     *
     * @return data file
     *
     * @throws DAQCompException if the attribute or file cannot be found
     */
    private File getFile(String dataDir, Element elem, String attrName)
        throws DAQCompException
    {
        // get attribute
        Attribute fileAttr = elem.attribute(attrName);
        if (fileAttr == null) {
            return null;
        }

        // get attribute value
        String name = fileAttr.getValue();

        // build path for attribute
        File file;
        if (dataDir == null) {
            file = new File(name);
        } else {
            file = new File(dataDir, name);
        }

        // make sure path exists
        if (!file.exists()) {
            throw new DAQCompException("Replay " + attrName + " " + file +
                                       " does not exist");
        }

        // return file path
        return file;
    }

    /**
     * Return the time when the first of the channels to stop has stopped.
     * @return the DAQ time (1E10 ticks/sec) of the hit which fulfills this
     *         condition.
     */
    public long getEarliestLastChannelHitTime()
    {
        return 0L;
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
        return 0L;
    }

    /**
     * Return an array of the number of active doms and the number of total doms
     * Packed into an integer array to avoid 2 xmlrpc calls from the ActiveDOMsTask
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

    public Sender getSender()
    {
        return sender;
    }

    /**
     * Report time of the most recent hit object pushed into the HKN1
     * @return
     */
    public long getTimeOfLastHitInputToHKN1()
    {
        return 0L;
    }

    /**
     * Report time of the most recent hit object output from the HKN1
     * @return
     */
    public long getTimeOfLastHitOutputFromHKN1()
    {
        return 0L;
    }

    /**
     * Return the number of LBM overflows inside this string
     * @return  a long value representing the total lbm overflows in this string
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

        configurationPath = dirName;
        if (LOG.isInfoEnabled()) {
            LOG.info("Setting the ueber configuration directory to " +
                     configurationPath);
        }

        // get a reference to the DOM registry - useful later
        try {
            domRegistry = DOMRegistry.loadRegistry(configurationPath);
        } catch (ParserConfigurationException e) {
            LOG.error("Cannot load DOM registry", e);
        } catch (SAXException e) {
            LOG.error("Cannot load DOM registry", e);
        } catch (IOException e) {
            LOG.error("Cannot load DOM registry", e);
        }

        sender.setDOMRegistry(domRegistry);
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

        if (hitFile == null) {
            throw new DAQCompException("Hit file location has not been set");
        }

        PayloadFileThread thread =
            new PayloadFileThread("ReplayThread#" + hubId, hitFile);
        thread.start();
    }

    /**
     * Start subrun.
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
        stopped = true;
    }

    class OrderedFileException
        extends Exception
    {
        OrderedFileException(String msg)
        {
            super(msg);
        }
    }

    class OrderedFile
        implements Comparable<OrderedFile>
    {
        private File f;
        private int num;

        OrderedFile(File f)
            throws OrderedFileException
        {
            this.f = f;
            num = getFileNumber();
        }

        public int compareTo(OrderedFile of)
        {
            return num - of.num;
        }

        File getFile()
        {
            return f;
        }

        private int getFileNumber()
            throws OrderedFileException
        {
            String name = f.getName();

            int end = name.length();
            if (name.endsWith(".dat")) {
                end -= 4;
            }

            int idx;
            for (idx = end;
                 idx > 0 && Character.isDigit(name.charAt(idx - 1));
                 idx--);

            if (idx == end) {
                throw new OrderedFileException("Expected numbers before" +
                                               " \".dat\" at end of \"" +
                                               name + "\"");
            }

            String substr = name.substring(idx, end);
            try {
                return Integer.parseInt(substr);
            } catch (NumberFormatException nfe) {
                throw new OrderedFileException("Cannot extract number" +
                                               " from \"" + name + "\"");
            }
        }

        public String toString()
        {
            return f.toString();
        }
    }

    /**
     * Payload file writer thread.
     */
    class PayloadFileThread
        implements Runnable
    {
        // system time is 10^-4 secs, DAQ time is 10^-11 secs,
        // so DAQ multiplier is 10^7
        private static final long DAQ_MULTIPLIER = 10000000L;

        private File dataFile;
        private Thread realThread;

        private long totPayloads;

        private boolean timeInit;
        private long startSysTime;
        private long startDAQTime;

        /**
         * Create payload file writer thread.
         *
         * @param name thread name
         */
        PayloadFileThread(String name, File dataFile)
        {
            this.dataFile = dataFile;

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
         * Get the time for this payload.
         *
         * @param buf ByteBuffer containing the payload
         *
         * @return time in milliseconds
         */
        private long getPayloadTime(ByteBuffer buf)
        {
            return buf.getLong(24);
        }

        /**
         * No cleanup is needed.
         */
        private void finishThreadCleanup()
        {
        }

        private void processDirectory(File dataDir)
        {
            File[] dirList = dataDir.listFiles();

            ArrayList<OrderedFile> list = new ArrayList<OrderedFile>();
            for (int i = 0; i < dirList.length; i++) {
                try {
                    list.add(new OrderedFile(dirList[i]));
                } catch (OrderedFileException ofe) {
                    LOG.error("Bad ordered file", ofe);
                }
            }

            Collections.sort(list);

            for (OrderedFile f : list) {
                if (stopped) {
                    break;
                }

                LOG.error("Processing " + f);
                processFile(f.getFile());
            }
        }

        private void processFile(File payFile)
        {
            boolean timeInit = false;
            long startSysTime = 0L;
            long startDAQTime = 0L;

            PayloadByteReader rdr;
            try {
                rdr = new PayloadByteReader(payFile);
            } catch (IOException ioe) {
                LOG.error("Cannot open payFile", ioe);
                return;
            }

            while (rdr.hasNext()) {
                ByteBuffer buf = rdr.next();
                if (buf == null) {
                    break;
                }

                // try to deliver payloads at the rate they were created
                if (!timeInit) {
                    startSysTime = System.currentTimeMillis();
                    startDAQTime = getPayloadTime(buf);
                    timeInit = true;
                } else {
                    final long daqTime = getPayloadTime(buf);

                    long sysTime = System.currentTimeMillis();

                    final long daqDiff =
                        (daqTime - startDAQTime) / DAQ_MULTIPLIER;
                    final long sysDiff = sysTime - startSysTime;

                    // if we're sending payloads too quickly, wait a bit
                    final long sleepTime = daqDiff - sysDiff;
                    if (sleepTime > 5000L) {
                        LOG.error("Huge time gap (" + (sleepTime / 1000L) +
                                  ") for payload #" +
                                  rdr.getNumberOfPayloads() + " from " +
                                  payFile);
                        break;
                    }

                    if (sleepTime > 10L) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException ie) {
                            // ignore interrupts
                        }
                    }
                }

                buf.flip();

                try {
                    write(buf);
                } catch (IOException ioe) {
                    throw new Error("Sender did not consume payload #" +
                                    rdr.getNumberOfPayloads() + " from " +
                                    payFile, ioe);
                }

                // don't overwhelm other threads
                Thread.yield();

            }

            try {
                rdr.close();
            } catch (IOException ioe) {
                // ignore errors on close
            }

            totPayloads += rdr.getNumberOfPayloads();
        }

        /**
         * Main file writer loop.
         */
        public void run()
        {
            stopped = false;

            if (dataFile.isDirectory()) {
                processDirectory(dataFile);
            } else {
                processFile(dataFile);
            }

            stopped = true;

            ByteBuffer stopBuf = buildStopMessage();
            try {
                write(stopBuf);
            } catch (IOException ioe) {
                throw new Error("Couldn't write " + dataFile + " stop message",
                                ioe);
            }

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
         * Write payload to Sender.
         *
         * @param buf payload
         *
         * @throws IOException should never be thrown
         */
        private void write(ByteBuffer buf)
            throws IOException
        {
            sender.consume(buf);
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
        int hubId = 0;
        try
        {
            hubId = Integer.getInteger("icecube.daq.stringhub.componentId");
        } catch (Exception ex) {
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
            return; // without this, compiler whines about uninitialized 'srvr'
        }
        srvr.startServing();
    }
}
