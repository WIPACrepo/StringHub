package icecube.daq.sender.test;

import icecube.daq.bindery.StreamBinder;
import icecube.daq.common.DAQCmdInterface;
import icecube.daq.common.DAQComponentProcessManager;
import icecube.daq.domapp.AbstractDataCollector;
import icecube.daq.domapp.BadEngineeringFormat;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.domapp.DataCollector;
import icecube.daq.domapp.EngineeringRecordFormat;
import icecube.daq.domapp.IDataCollectorFactory;
import icecube.daq.domapp.MessageException;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.IDriver;
import icecube.daq.eventbuilder.IReadoutDataPayload;
import icecube.daq.io.DAQComponentInputProcessor;
import icecube.daq.io.DAQComponentOutputProcess;
import icecube.daq.io.PayloadDestinationOutputEngine;
import icecube.daq.io.PayloadInputEngine;
import icecube.daq.io.PayloadOutputEngine;
import icecube.daq.payload.ByteBufferCache;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IPayloadDestinationCollection;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.MasterPayloadFactory;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.splicer.Payload;
import icecube.daq.sender.RequestInputEngine;
import icecube.daq.sender.Sender;
import icecube.daq.trigger.IHitPayload;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.DataFormatException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;

public class SenderTest
    extends TestCase
{
    private static Log logger = LogFactory.getLog(SenderTest.class);

    private static Level logLevel = Level.ERROR;

    /** input buffer used inside readData() method */
    private ByteBuffer dataBuf;

    private IByteBufferCache bufMgr;
    private MasterPayloadFactory factory;
    private long prevTime;

    private static void addInputChannel(ReadableByteChannel chan,
                                        IByteBufferCache bufMgr,
                                        DAQComponentInputProcessor proc)
        throws IOException
    {
/*
        final int id = 0;

        DAQdataChannelMBean dcInputSink =
            new DAQdataChannelMBean(null, nearType, id, null, null);

        dcInputSink.setFarEndType(farType);
        dcInputSink.setFarEndID(id);

        final String chanType = DAQdataChannelMBean.DAQ_READ_CHANNEL;
        dcInputSink.setChannelType(chanType);

        dcInputSink.setSelectableChannel(chan);
*/

        try {
            proc.addDataChannel(chan, bufMgr);
        } catch (Exception ex) {
            throw new IOException("Couldn't add input channel");
        }
    }

    private static void addOutputChannel(WritableByteChannel chan,
                                         IByteBufferCache bufMgr,
                                         DAQComponentOutputProcess proc)
        throws IOException
    {
/*
        final int id = 0;

        DAQdataChannelMBean dcInputSink =
            new DAQdataChannelMBean(null, nearType, id, null, null);

        dcInputSink.setFarEndType(farType);
        dcInputSink.setFarEndID(id);

        final String chanType = DAQdataChannelMBean.DAQ_WRITE_CHANNEL;
        dcInputSink.setChannelType(chanType);

        dcInputSink.setSelectableChannel(chan);
*/

        try {
            proc.addDataChannel(chan, bufMgr);
        } catch (Exception ex) {
            throw new IOException("Couldn't add input channel");
        }
    }

    private PayloadDestinationOutputEngine buildDataOutput(String compName,
                                                           int compId,
                                                           Sender sender,
                                                           WritableByteChannel chan)
        throws Exception
    {
        PayloadDestinationOutputEngine dataOut =
            new PayloadDestinationOutputEngine(compName, compId, "dataOut");
        dataOut.registerBufferManager(bufMgr);
        addOutputChannel(chan, bufMgr, dataOut);

        IPayloadDestinationCollection dataColl =
            dataOut.getPayloadDestinationCollection();
        sender.setDataOutputDestination(dataColl);
        dataOut.start();

        dataOut.startProcessing();

        return dataOut;
    }

    private RequestInputEngine buildRequestInput(String compName, int compId,
                                                 Sender sender,
                                                 ReadableByteChannel chan)
        throws Exception
    {
        RequestInputEngine reqIn =
            new RequestInputEngine(compName, compId, "reqIn",
                                   sender, bufMgr, factory);

        addInputChannel(chan, bufMgr, reqIn);
        reqIn.start();

        reqIn.startProcessing();

        return reqIn;
    }

    private static final String getComponentName()
    {
        return DAQCmdInterface.DAQ_STRING_HUB;
    }

    private boolean isHubSending(List sources)
    {
        for (Iterator iter = sources.iterator(); iter.hasNext(); ) {
            MockDOMApp da = (MockDOMApp) iter.next();

            if (da.hasMoreData()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Process command-line arguments.
     *
     * @param args list of arguments
     */
    private static void processArgs(String[] args)
    {
        boolean usage = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].charAt(0) == '-') {
                if (args[i].charAt(1) == 'l') {
                    i++;
                    if (!setLogLevel(args[i])) {
                        System.err.println("Bad log level '" + args[i] +
                                           "'");
                        usage = true;
                    }
                } else {
                    System.err.println("Unknown option '" + args[i] + "'");
                    usage = true;
                }
            } else {
                System.err.println("Unknown argument '" + args[i] + "'");
                usage = true;
            }
        }

        if (usage) {
            System.err.println("java SenderTest" +
                               " [-l logLevel]" +
                               "");
            System.exit(1);
        }
    }

    private boolean readData(ReadableByteChannel chan)
        throws IOException
    {
        boolean running = true;

        dataBuf.clear();

        int nr = chan.read(dataBuf);
        logger.debug("Read " + nr + " bytes from data source.");

        dataBuf.flip();
        while (true) {
            final int bufLimit = dataBuf.limit();

            final int len = dataBuf.getInt(0);
            if (len == 4) {
                running = false;
                break;
            }

            if (len > bufLimit) {
                logger.error("Bad length " + len + ", limit is " +
                             bufLimit);
                break;
            }

            if (len < 16) {
                logger.error("Short payload (" + len + " bytes)");
                break;
            }

            final int type = dataBuf.getInt(4);
            if (type != PayloadRegistry.PAYLOAD_ID_READOUT_DATA) {
                logger.error("Expected readout data payload," +
                             " got type #" + type);
            } else {
                dataBuf.limit(len);

                IReadoutDataPayload rdp;
                try {
                    Payload payload = factory.createPayload(0, dataBuf);
                    payload.loadPayload();
                    rdp = (IReadoutDataPayload) payload;
                } catch (DataFormatException dfe) {
                    logger.error("Couldn't create ReadoutDataPayload",
                                 dfe);
                    rdp = null;
                }

                List hitList;
                if (rdp == null) {
                    hitList = null;
                } else {
                    hitList = rdp.getHitList();
                }

                if (hitList == null) {
                    IUTCTime firstTime = rdp.getFirstTimeUTC();
                    IUTCTime lastTime = rdp.getLastTimeUTC();

                    if (firstTime == null || lastTime == null) {
                        logger.error("No hits or times found" +
                                     " in ReadoutDataPayload");
                    } else {
                        logger.error("No hits found" +
                                     " in ReadoutDataPayload [" +
                                     firstTime.getUTCTimeAsLong() +
                                     "-" +
                                     lastTime.getUTCTimeAsLong() +
                                     "]");
                    }
                } else {
                    Iterator hitIter = hitList.iterator();
                    while (hitIter.hasNext()) {
                        IHitPayload hit = (IHitPayload) hitIter.next();

                        long time =
                            hit.getHitTimeUTC().getUTCTimeAsLong();
                        logger.debug("Time: " + time);

                        assertTrue("Time went backwards (time=" +
                                   time + " last=" + prevTime + ")",
                                   time >= prevTime);

                        prevTime = time;
                    }
                }
            }

            if (len >= bufLimit) {
                break;
            }

            dataBuf.position(len);
            dataBuf.limit(bufLimit);
            dataBuf.compact();
        }

        return running;
    }

    public static final boolean setLogLevel(String levelStr)
    {
        if (levelStr.equalsIgnoreCase("off") ||
            levelStr.equalsIgnoreCase("none"))
        {
            logLevel = Level.OFF;
        } else if (levelStr.equalsIgnoreCase("fatal")) {
            logLevel = Level.FATAL;
        } else if (levelStr.equalsIgnoreCase("error")) {
            logLevel = Level.ERROR;
        } else if (levelStr.equalsIgnoreCase("warn")) {
            logLevel = Level.WARN;
        } else if (levelStr.equalsIgnoreCase("info")) {
            logLevel = Level.INFO;
        } else if (levelStr.equalsIgnoreCase("debug")) {
            logLevel = Level.DEBUG;
        } else if (levelStr.equalsIgnoreCase("all")) {
            logLevel = Level.ALL;
        } else {
            return false;
        }

        return true;
    }

    private void startStringHub(List collectors)
    {
        for (int i = 0; i < 2; i++) {
            int expLevel;
            if (i == 0) {
                expLevel = DataCollector.CONFIGURING;
            } else {
                expLevel = DataCollector.STARTING;
            }

            for (Iterator iter = collectors.iterator(); iter.hasNext(); ) {
                DataCollector dc = (DataCollector) iter.next();

                if (i == 0) {
                    dc.signalConfigure();
                } else {
                    dc.signalStartRun();
                }
            }

            for (Iterator iter = collectors.iterator(); iter.hasNext(); ) {
                DataCollector dc = (DataCollector) iter.next();

                int reps = 0;
                while (true) {
                    final int curLevel = dc.queryDaqRunLevel();
                    if (curLevel > expLevel) {
                        break;
                    }

                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        // do nothing
                    }

                    if (reps++ > 20) {
                        final String opName =
                            DataCollector.STATE_NAMES[expLevel];

                        throw new Error(dc.toString() + " never reached" +
                                        " expected run level #" + expLevel +
                                        " " + opName);
                    }
                }
            }
        }
    }

    private void stopStringHub(List collectors)
    {
        String opName = "STOPPING";

        for (Iterator iter = collectors.iterator(); iter.hasNext(); ) {
            DataCollector dc = (DataCollector) iter.next();

            int reps = 0;
            while (true) {
                final int curLevel = dc.queryDaqRunLevel();
                if (curLevel == DataCollector.RUNNING)
                {
                    break;
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    // do nothing
                }

                if (reps++ > 20) {
                    throw new Error(dc.toString() + " never reached" +
                                    " expected run level while " +
                                    opName);
                }
            }
        }

        for (Iterator iter = collectors.iterator(); iter.hasNext(); ) {
            DataCollector dc = (DataCollector) iter.next();

            dc.signalStopRun();
        }
    }

    private void shutDownStringHub(List collectors)
    {
        String opName = "SHUTTING DOWN";

        for (Iterator iter = collectors.iterator(); iter.hasNext(); ) {
            DataCollector dc = (DataCollector) iter.next();

            int reps = 0;
            while (true) {
                final int curLevel = dc.queryDaqRunLevel();
                if (curLevel <= DataCollector.CONFIGURED)
                {
                    break;
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    // do nothing
                }

                if (reps++ > 20) {
                    throw new Error(dc.toString() + " never reached" +
                                    " expected run level while " +
                                    opName);
                }
            }
        }

        for (Iterator iter = collectors.iterator(); iter.hasNext(); ) {
            DataCollector dc = (DataCollector) iter.next();

            dc.signalShutdown();
        }
    }

    protected void setUp()
    {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(new MockAppender(logLevel));

        bufMgr = new ByteBufferCache(256, 50000000, 50000000, "StreamBinder");
        factory = new MasterPayloadFactory(bufMgr);

        dataBuf = ByteBuffer.allocate(100000);

        prevTime = 0L;
    }

    public static Test suite()
    {
        return new TestSuite(SenderTest.class);
    }

    public void testFull()
        throws IOException
    {
        final int numDoms = 2;
        final double simulationTime = 1.0;
        final int numRequests = 5;
        final int rate = 5;

        final int stringHubId = 12;
        Sender sender = new Sender(stringHubId, factory);

        // XXX not writing hits to triggers

        final String compName = getComponentName();
        final int compId = 0;

        Pipe hitPipe = Pipe.open();
        hitPipe.sink().configureBlocking(false);
        hitPipe.source().configureBlocking(false);

        PayloadDestinationOutputEngine hitOut =
            new PayloadDestinationOutputEngine(compName, compId, "hitOut");
        hitOut.registerBufferManager(bufMgr);
        addOutputChannel(hitPipe.sink(), bufMgr, hitOut);

        IPayloadDestinationCollection hitColl =
            hitOut.getPayloadDestinationCollection();
        sender.setHitOutputDestination(hitColl);
        hitOut.start();

        try {
            hitOut.startProcessing();
        } catch (Exception ex) {
            logger.fatal("Couldn't start hit output engine", ex);
            System.exit(1);
        }

        Pipe reqPipe = Pipe.open();
        reqPipe.sink().configureBlocking(false);
        reqPipe.source().configureBlocking(false);

        RequestInputEngine reqIn;
        try {
            reqIn =
                buildRequestInput(compName, compId, sender, reqPipe.source());
        } catch (Exception ex) {
            logger.fatal("Couldn't start request engine", ex);
            System.exit(1);
            return;
        }

        Pipe dataPipe = Pipe.open();
        dataPipe.sink().configureBlocking(false);
        //dataPipe.source().configureBlocking(false);

        PayloadDestinationOutputEngine dataOut;
        try {
            dataOut =
                buildDataOutput(compName, compId, sender, dataPipe.sink());
        } catch (Exception ex) {
            logger.fatal("Couldn't start data output engine", ex);
            System.exit(1);
            return;
        }

        DAQComponentProcessManager pm = null;
        //registerInputCallbacks(pm, reqIn, "IC3_reqInput");
        //registerOutputCallbacks(pm, dataOut, "IC3_dataOutput");

        sender.reset();

        StreamBinder bind = new StreamBinder(numDoms, sender);
        bind.start();

        ArrayList sources = new ArrayList();
        ArrayList collectors = new ArrayList();

        IDataCollectorFactory dcFactory =
            new MockDataCollectorFactory(simulationTime, 10.0);

        boolean failed = false;
        for (int i = 0; i < numDoms; i++) {

            Pipe p = Pipe.open();
            p.source().configureBlocking(false);

            final long mbId = (long) i + 1;
            DOMChannelInfo chInfo =
                new DOMChannelInfo(Long.toHexString(mbId), (i / 4),
                                   (i / 2) & 1, ((i & 1) == 0 ? 'A' : 'B'));

            AbstractDataCollector dc;
            try {
                dc = dcFactory.create(chInfo, p.sink());
            } catch (IOException ioe) {
                logger.fatal("Couldn't create DataCollector", ioe);
                failed = true;
                break;
            } catch (MessageException me) {
                logger.fatal("Couldn't create DataCollector", me);
                failed = true;
                break;
            }

            collectors.add(dc);

            // connect this object to the stream binder
            bind.register(p.source(), chInfo.mbid);
        }

        if (failed) {
            System.exit(1);
        }


        for (Iterator iter = collectors.iterator(); iter.hasNext(); ) {
            DataCollector dc = (DataCollector) iter.next();

            DOMConfiguration config = new DOMConfiguration();

            EngineeringRecordFormat engFmt;
            try {
                engFmt = new EngineeringRecordFormat((short) 0,
                                                     new short[] {
                                                         0, 0, 0, 0,
                                                     });
            } catch (BadEngineeringFormat bef) {
                logger.fatal("Couldn't create engineering record format", bef);
                failed = true;
                break;
            }

            config.setEngineeringFormat(engFmt);
            config.setPulserRate(rate);

            dc.setConfig(config);
            dc.start();
        }

        startStringHub(collectors);

        RequestGenerator reqGen =
            new RequestGenerator(reqPipe.sink(), simulationTime, numRequests);
        reqGen.start();

        Selector sel = Selector.open();

        Pipe.SourceChannel dataSrc = dataPipe.source();
        dataSrc.configureBlocking(false);

        dataSrc.register(sel, dataSrc.validOps());

        boolean stopping = false;
        boolean stopped = false;
        while (!stopped) {
            try {
                sel.select(500);
            } catch (IOException ioe) {
                logger.error("Couldn't get channel", ioe);
                break;
            }

            Iterator iter = sel.selectedKeys().iterator();
            while (iter.hasNext()) {
                SelectionKey key = (SelectionKey) iter.next();
                iter.remove();

                if (!readData((ReadableByteChannel) key.channel())) {
                    logger.info("Got STOP signal");
                    stopped = true;
                    break;
                }
            }

            if (!stopping && !isHubSending(sources)) {
                stopping = true;
                stopStringHub(collectors);
            }
        }

        while (!hitOut.isStopped() || !reqIn.isStopped() ||
               !dataOut.isStopped())
        {
            String runEng = "";
            if (!hitOut.isStopped()) {
                if (runEng.length() > 0) {
                    runEng += ", ";
                }
                runEng += "hitOut";
            }
            if (!reqIn.isStopped()) {
                if (runEng.length() > 0) {
                    runEng += ", ";
                }
                runEng += "reqIn";
            }
            if (!dataOut.isStopped()) {
                if (runEng.length() > 0) {
                    runEng += ", ";
                }
                runEng += "dataOut";
            }

            System.err.println("Waiting for " + runEng);
            try {
                Thread.sleep(500);
            } catch (Exception ex) {
                // ignore exceptions
            }
        }

        bind.shutdown();
        shutDownStringHub(collectors);

        try {
            hitOut.destroyProcessor();
        } catch (Exception ex) {
            fail("Couldn't destroy hit output engine: " + ex);
        }
        try {
            reqIn.destroyProcessor();
        } catch (Exception ex) {
            fail("Couldn't destroy request input engine: " + ex);
        }
        try {
            dataOut.destroyProcessor();
        } catch (Exception ex) {
            fail("Couldn't destroy data output engine: " + ex);
        }
    }

    public static final void main(String[] args)
        throws IOException
    {
        processArgs(args);

        junit.textui.TestRunner.run(suite());
    }
}
