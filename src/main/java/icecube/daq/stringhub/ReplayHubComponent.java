package icecube.daq.stringhub;

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
import icecube.daq.util.FlasherboardConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.List;

import org.apache.log4j.Logger;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

public class ReplayHubComponent
    extends DAQComponent
{
    private static final Logger LOG =
        Logger.getLogger(ReplayHubComponent.class);

    private static final String COMPONENT_NAME = "replayHub";

    private int hubId;
    private Sender sender;

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

        IByteBufferCache genMgr = new VitreousBufferCache("RHGen#" + hubId);
        addCache(genMgr);
        addMBean("GenericBuffer", genMgr);

        IByteBufferCache rdoutDataCache  =
            new VitreousBufferCache("SHRdOut#" + hubId);
        addCache(DAQConnector.TYPE_READOUT_DATA, rdoutDataCache);
        sender = new Sender(hubId, rdoutDataCache);

        if (LOG.isInfoEnabled()) {
            LOG.info("starting up ReplayHub component " + hubId);
        }

        // Component derives behavioral characteristics from
        // its 'minor ID' - they are ...
        // (1) component xx81 - xx99 : icetop
        // (2) component xx01 - xx80 : in-ice
        // (3) component xx00        : amandaHub
        int minorHubId = hubId % 100;

        SimpleOutputEngine hitOut;
        if (minorHubId == 0) {
            hitOut = null;
        } else {
            hitOut = new SimpleOutputEngine(COMPONENT_NAME, hubId, "hitOut");
            if (minorHubId > 80)
                addMonitoredEngine(DAQConnector.TYPE_ICETOP_HIT, hitOut);
            else
                addMonitoredEngine(DAQConnector.TYPE_STRING_HIT, hitOut);
            sender.setHitOutput(hitOut);
        }

        ReadoutRequestFactory rdoutReqFactory =
            new ReadoutRequestFactory(genMgr);

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

        // read in config XML
        Document doc;
        try {
            doc = new SAXReader().read(fis);
        } catch (DocumentException de) {
            throw new DAQCompException("Couldn't read " + masterConfigFile +
                                       ": " + de.getMessage());
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
    }

    /**
     * Start sending data.
     *
     * @throws DAQCompException if there is a problem
     */
    public void starting()
        throws DAQCompException
    {
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
        ByteBuffer buildStopMessage()
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
        long getPayloadTime(ByteBuffer buf)
        {
            return buf.getLong(24);
        }

        /**
         * No cleanup is needed.
         */
        void finishThreadCleanup()
        {
        }

        /**
         * Main file writer loop.
         */
        public void run()
        {
            stopped = false;

            FileInputStream in;
            try {
                in = new FileInputStream(dataFile);
            } catch (IOException ioe) {
                LOG.error("Couldn't open " + dataFile, ioe);
                return;
            }

            ReadableByteChannel chan = in.getChannel();

            ByteBuffer lenBuf = ByteBuffer.allocate(4);

            boolean timeInit = false;
            long startSysTime = 0L;
            long startDAQTime = 0L;

            int numPayloads = 0;
            while (!stopped) {
                lenBuf.rewind();
                int numBytes;
                try {
                    numBytes = chan.read(lenBuf);
                } catch (IOException ioe) {
                    LOG.error("Couldn't read length of payload #" +
                              numPayloads + " from " + dataFile, ioe);
                    break;
                }

                // end of file
                if (numBytes < 0) {
                    LOG.error("Saw end-of-file at payload #" + numPayloads);
                    break;
                }

                if (numBytes < 4) {
                    LOG.error("Only got " + numBytes +
                              " of length for payload #" + numPayloads +
                              " in " + dataFile);
                    break;
                }

                // get payload length
                int len = lenBuf.getInt(0);
                if (len < 4) {
                    LOG.error("Bad length " + len + " for payload #" +
                              numPayloads + " in " + dataFile);
                    break;
                }

                // Sender expects a separate buffer for each payload
                ByteBuffer buf = ByteBuffer.allocate(len);
                buf.limit(len);
                buf.putInt(len);

                // read the rest of the payload
                int lenIn;
                try {
                    lenIn = chan.read(buf);
                } catch (IOException ioe) {
                    LOG.error("Couldn't read " + len +
                              " data bytes for payload #" + numPayloads +
                              " from " + dataFile, ioe);
                    break;
                }

                if (lenIn != len - 4) {
                    throw new Error("Expected to read " + (len - 4) +
                                    " bytes, not " + lenIn + " for payload #" +
                                    numPayloads + " from " + dataFile);
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
                                    numPayloads + " from " + dataFile, ioe);
                }

                // don't overwhelm other threads
                Thread.yield();

                numPayloads++;
            }

            stopped = true;

            try {
                chan.close();
            } catch (IOException ioe) {
                // ignore errors on close
            }

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
        void write(ByteBuffer buf)
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
