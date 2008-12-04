/* -*- mode: java; indent-tabs-mode:f; tab-width:4 -*- */

package icecube.daq.domapp;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.bindery.StreamBinder;
import icecube.daq.domapp.LocalCoincidenceConfiguration.RxMode;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.Driver;
import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.IDriver;
import icecube.daq.dor.TimeCalib;
import icecube.daq.rapcal.RAPCal;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.rapcal.ZeroCrossingRAPCal;
import icecube.daq.util.RealTimeRateMeter;
import icecube.daq.util.UTC;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedByInterruptException;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * <table>
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
public class DataCollector 
    extends AbstractDataCollector
    implements DataCollectorMBean 
{
    private int                 card;
    private int                 pair;
    private char                dom;
    private long                numericMBID;
    private IDOMApp             app;
    private GPSInfo             gps;
    private UTC                 gpsOffset;
    private RAPCal              rapcal;
    private IDriver             driver;
    private boolean             stop_thread;
    private BufferConsumer      hitsConsumer;
    private BufferConsumer      moniConsumer;
    private BufferConsumer      tcalConsumer;
    private BufferConsumer      supernovaConsumer;

    private static final Logger         logger  = Logger.getLogger(DataCollector.class);
    private static final DecimalFormat  fmt     = new DecimalFormat("#0.000000000");

    // TODO - replace these with properties-supplied constants
    // for now they are totally reasonable
    private long                threadSleepInterval   = 50;
    private long                lastDataRead          = 0;
    private long                dataReadInterval      = 10;
    private long                lastMoniRead          = 0;
    private long                moniReadInterval      = 1000;
    private long                lastTcalRead          = 0;
    private long                tcalReadInterval      = 1000;
    private long                lastSupernovaRead     = 0;
    private long                supernovaReadInterval = 1000;

    private int                 rapcalExceptionCount  = 0;
    private int                 validRAPCalCount;

    private int                 numHits               = 0;
    private int                 numMoni               = 0;
    private int                 numSupernova          = 0;
    private int                 loopCounter           = 0;
    private long                lastTcalUT;
    private volatile long       runStartUT = 0L;
    private int                 numLBMOverflows       = 0;
    
    private RealTimeRateMeter   rtHitRate;

    private long                nextSupernovaDomClock;
    private HitBufferAB         abBuffer;
    private int                 numSupernovaGaps;
    private final boolean       waitForRAPCal = Boolean.getBoolean(
            "icecube.daq.domapp.datacollector.waitForRAPCal");

    private final int           MAGIC_ENGINEERING_HIT_FMTID = 2;
    private final int           MAGIC_COMPRESSED_HIT_FMTID  = 3;
    private final int           MAGIC_MONITOR_FMTID         = 102;
    private final int           MAGIC_TCAL_FMTID            = 202;
    private final int           MAGIC_SUPERNOVA_FMTID       = 302;
    
    
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
     * Simple hits-only constructor (other channels suppressed)
     * @param chInfo structure containing DOM channel information
     * @param hitsTo BufferConsumer target for hits
     * @throws IOException
     * @throws MessageException
     */
    public DataCollector(DOMChannelInfo chInfo, BufferConsumer hitsTo) 
    throws IOException, MessageException
    {
        this(chInfo.card, chInfo.pair, chInfo.dom, null, hitsTo, null, null, null, null, null, null);
    }

    public DataCollector(
            int card, 
            int pair, 
            char dom, 
            DOMConfiguration config,
            BufferConsumer hitsTo,
            BufferConsumer moniTo,
            BufferConsumer supernovaTo,
            BufferConsumer tcalTo,
            IDriver driver, 
            RAPCal rapcal, 
            IDOMApp app) throws IOException, MessageException
    {
        super(card, pair, dom);
        this.card = card;
        this.pair = pair;
        this.dom = dom;
        hitsConsumer = hitsTo;
        moniConsumer = moniTo;
        tcalConsumer = tcalTo;
        supernovaConsumer = supernovaTo;
        
        if (driver != null)
            this.driver = driver;
        else
            this.driver = Driver.getInstance();
        
        if (rapcal != null)
        {
            this.rapcal = rapcal;
        }
        else
        {
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
        }
        this.app = app;
        this.config = config;

        gps = null;

        runLevel = RunLevel.INITIALIZING;
        gpsOffset = new UTC(0L);
        abBuffer  = new HitBufferAB();
        
        // Calculate 10-sec averages of the hit rate
        rtHitRate = new RealTimeRateMeter(100000000000L);
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
        if (logger.isInfoEnabled()) {
            logger.info("Configuring DOM on " + canonicalName());
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

        if (config.isMinBiasEnabled()) 
            app.enableMinBias();
        else
            app.disableMinBias();
        
        app.setPulserRate(config.getPulserRate());
        LocalCoincidenceConfiguration lc = config.getLC();
        app.setLCType(lc.getType());
        app.setLCMode(lc.getRxMode());
        app.setLCTx(lc.getTxMode());
        app.setLCSource(lc.getSource());
        app.setLCSpan(lc.getSpan());
        app.setLCWindow(lc.getPreTrigger(), lc.getPostTrigger());
        app.setCableLengths(lc.getCableLengthUp(), lc.getCableLengthDn());
        app.enableSupernova(config.getSupernovaDeadtime(), config.isSupernovaSpe());
        app.setScalerDeadtime(config.getScalerDeadtime());

        // Do the pedestal subtraction
        if (config.getPedestalSubtraction())
        {
            // WARN - this is done /w/ HV applied - probably need to
            // screen in DOMApp for spurious pulses
            app.collectPedestals(200, 200, 200);
        }
        
        // set chargestamp source
        app.setChargeStampType(!config.isAtwdChargeStamp(), 
                config.isAutoRangeChargeStamp(), 
                config.getChargeStampFixedChannel());
        
        // enable charge stamp histogramming
        app.histoChargeStamp(config.getHistoInterval(), config.getHistoPrescale());
        
        long configT1 = System.currentTimeMillis();
        if (logger.isInfoEnabled()) {
            logger.info("Finished DOM configuration - " + canonicalName() +
                        "; configuration took " + (configT1 - configT0) +
                        " milliseconds.");
        }
    }

    private long dispatchBuffer(ByteBuffer buf, BufferConsumer target) throws IOException
    {
        long domclk = buf.getLong(24);
        long utc    = rapcal.domToUTC(domclk).in_0_1ns();
        buf.putLong(24, utc);
        int fmtId = buf.getInt(4);
        if (fmtId == MAGIC_ENGINEERING_HIT_FMTID || fmtId == MAGIC_COMPRESSED_HIT_FMTID) rtHitRate.recordEvent(utc);
        target.consume(buf);
        return utc;
    }
    
    private void dispatchHitBuffer(int atwdChip, ByteBuffer hitBuf) throws IOException
    {
        if (atwdChip == 0)
            abBuffer.pushA(hitBuf);
        else
            abBuffer.pushB(hitBuf);
        while (true) 
        {
            ByteBuffer buffer = abBuffer.pop();
            if (buffer == null) return;
            dispatchBuffer(buffer, hitsConsumer);
        }
    }
    

    private void dataProcess(ByteBuffer in) throws IOException
    {
        // TODO - I created a number of less-than-elegant hacks to
        // keep performance at acceptable level such as minimal
        // decoding of hit records. This should be cleaned up.

        if (hitsConsumer == null) return;
        
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
                dispatchHitBuffer(atwdChip, outputBuffer);
                
                break;
                
            case 0x90: /* SLC or other compressed hit format */
                int clkMSB = in.getShort(pos+4);
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
                    short version = 0x01;
                    short pedestal = config.getPedestalSubtraction() ? (short) 0x01 : (short) 0x00;
                    in.limit(pos + hitSize);
                    outputBuffer = ByteBuffer.allocate(hitSize + 42);
                    // Standard Header
                    outputBuffer.putInt(hitSize + 42); 
                    outputBuffer.putInt(MAGIC_COMPRESSED_HIT_FMTID);
                    outputBuffer.putLong(numericMBID); // +8
                    outputBuffer.putLong(0L);          // +16
                    outputBuffer.putLong(domClock);    // +24
                    // Compressed hit extra info
                    // This is the 'byte order' word
                    outputBuffer.putShort((short) 1);  // +32
                    outputBuffer.putShort(version);    // +34
                    outputBuffer.putShort(pedestal);   // +36
                    outputBuffer.putLong(domClock);    // +38
                    outputBuffer.putInt(word1);        // +46
                    outputBuffer.putInt(word3);        // +50
                    outputBuffer.put(in).flip();
                    in.limit(buffer_limit);
                    
                    ////////
                    //
                    // DO the A/B stuff
                    //
                    ////////
                    dispatchHitBuffer(atwdChip, outputBuffer);
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
        if (moniConsumer == null) return;
        
        while (in.remaining() > 0)
        {
            MonitorRecord monitor = MonitorRecordFactory.createFromBuffer(in);
            if (monitor instanceof AsciiMonitorRecord)
            {
                String moniMsg = monitor.toString();
                if (logger.isDebugEnabled()) logger.debug(moniMsg);
                if (moniMsg.contains("LBM Overflow")) numLBMOverflows++;
            }
            numMoni++;
            ByteBuffer moniBuffer = ByteBuffer.allocate(monitor.getLength()+32);
            moniBuffer.putInt(monitor.getLength()+32);
            moniBuffer.putInt(MAGIC_MONITOR_FMTID);
            moniBuffer.putLong(numericMBID);
            moniBuffer.putLong(0L);
            moniBuffer.putLong(monitor.getClock());
            moniBuffer.put(monitor.getBuffer());
            dispatchBuffer((ByteBuffer) moniBuffer.flip(), moniConsumer);
        }
    }

    /**
     * Send the TCAL data out.  UTC time for TCAL is defined herein as the DOR Tx time.
     * Note that the {@link #dispatchBuffer} method is not called since the domClock to
     * UTC mapping does not take place for these types of record.
     * @param tcal the input {@link TimeCalib} object
     * @param gps the {@link GPSInfo} record is tacked onto the tail of the buffer 
     * @throws IOException
     */
    private void tcalProcess(TimeCalib tcal, GPSInfo gps) throws IOException
    {
        if (tcalConsumer == null) return;
        ByteBuffer tcalBuffer = ByteBuffer.allocate(500);
        tcalBuffer.putInt(0).putInt(MAGIC_TCAL_FMTID);
        tcalBuffer.putLong(numericMBID);
        tcalBuffer.putLong(0L);
        tcalBuffer.putLong(tcal.getDomTx().in_0_1ns());
        tcal.writeUncompressedRecord(tcalBuffer);
        tcalBuffer.put(gps.getBuffer()).flip();
        tcalBuffer.putInt(0, tcalBuffer.remaining());
        dispatchBuffer(tcalBuffer, tcalConsumer);
    }

    private void supernovaProcess(ByteBuffer in) throws IOException
    {
        while (in.remaining() > 0)
        {
            SupernovaPacket spkt = SupernovaPacket.createFromBuffer(in);
            // Check for gaps in SN data
            if ((nextSupernovaDomClock != 0L) && (spkt.getClock() != nextSupernovaDomClock) && numSupernovaGaps++ < 100)
                logger.warn("Gap or overlap in SN rec: next = " + nextSupernovaDomClock
                        + " - current = " + spkt.getClock());

            nextSupernovaDomClock = spkt.getClock() + (spkt.getScalers().length << 16);
            numSupernova++;
            if (supernovaConsumer != null)
            {
                int len = spkt.getLength() + 32;
                ByteBuffer snBuf = ByteBuffer.allocate(len);
                snBuf.putInt(len).putInt(MAGIC_SUPERNOVA_FMTID).putLong(numericMBID).putLong(0L);
                snBuf.putLong(spkt.getClock()).put(spkt.getBuffer());
                dispatchBuffer((ByteBuffer) snBuf.flip(), supernovaConsumer);
            }
        }
    }

    public synchronized void signalShutdown()
    {
        stop_thread = true;
    }

    private void storeRunStartTime() throws InterruptedException
    {
        try
        {
            TimeCalib rst = driver.readTCAL(card, pair, dom);
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

    private void execRapCal()
    {
        try
        {
            gps = driver.readGPS(card);
            gpsOffset = gps.getOffset();
            TimeCalib tcal = driver.readTCAL(card, pair, dom);
            rapcal.update(tcal, gpsOffset);

            validRAPCalCount++;

            if (getRunLevel().equals(RunLevel.RUNNING))
            {
                tcalProcess(tcal, gps);
            }

        }
        catch (RAPCalException rcex)
        {
            rapcalExceptionCount++;
            rcex.printStackTrace();
            logger.warn("Got RAPCal exception");
        }
        catch (GPSException gpsx)
        {
            gpsx.printStackTrace();
            logger.warn("Got GPS exception");
        }
        catch (IOException iox)
        {
            iox.printStackTrace();
            logger.warn(iox);
        }
        catch (InterruptedException intx)
        {
            intx.printStackTrace();
            logger.warn("Got interrupted exception");
        }
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

        logger.info("Begin data collection thread");

        // Create a watcher timer
        Timer watcher = new Timer(getName() + "-timer");
        InterruptorTask intTask = new InterruptorTask();
        watcher.schedule(intTask, 30000L, 5000L);
        try
        {
            runcore(intTask);
        }
        catch (Exception x)
        {
            x.printStackTrace();
            logger.error("Intercepted error in DataCollector runcore: " + x);
            /*
             * TODO cleanup needed set run level to ZOMBIE so that controller knows
             * that this channel has expired and does not wait.
             */
            setRunLevel(RunLevel.ZOMBIE);
        }
        watcher.cancel();

        // clear interrupted flag if it is set
        interrupted();

        // Make sure eos is written
        try
        {
            ByteBuffer otrava = MultiChannelMergeSort.eos(numericMBID);
            if (hitsConsumer != null) hitsConsumer.consume(otrava.asReadOnlyBuffer());
            if (moniConsumer != null) moniConsumer.consume(otrava.asReadOnlyBuffer());
            if (tcalConsumer != null) tcalConsumer.consume(otrava.asReadOnlyBuffer());
            if (supernovaConsumer != null) supernovaConsumer.consume(otrava.asReadOnlyBuffer());
            if (logger.isInfoEnabled()) {
                logger.info("Wrote EOS to streams.");
            }
        }
        catch (IOException iox)
        {
            logger.error(iox);
        }

        logger.info("End data collection thread.");

    } /* END OF run() METHOD */

    /**
     * Wrap up softboot -> domapp behavior 
     * */
    private IDOMApp softbootToDomapp() throws IOException, InterruptedException
    {
        /*
         * Based on discussion /w/ JEJ in Utrecht café, going for multiple retries here
         */
        for (int iBootTry = 0; iBootTry < 2; iBootTry++)
        {
            try
            {
                driver.commReset(card, pair, dom);
                driver.softboot (card, pair, dom);
                break;
            }
            catch (IOException iox)
            {
                logger.warn("Softboot attempt failed - retrying after 5 sec");
                Thread.sleep(5000L);
            }
        }

        FileNotFoundException savedEx = null;

        for (int i = 0; i < 2; i++) {
            driver.commReset(card, pair, dom);

            if (app == null)
            {
                // If app is null it implies the collector has deferred
                // opening of the DOR devfile to the thread.
                try
                {
                    app = new DOMApp(this.card, this.pair, this.dom);
                    break;
                }
                catch (FileNotFoundException ex)
                {
					logger.error("Trial "+i+": Open of "+card+""+pair+dom+" failed!");
					logger.error("Driver comstat for "+card+""+pair+dom+":\n"+driver.getComstat(card,pair,dom));
					logger.error("FPGA regs for card "+card+":\n"+driver.getFPGARegs(card));
                    app = null;
                    savedEx = ex;
                }
            }
        }

        if (app == null)
        {
            if (savedEx != null) throw savedEx;
            throw new FileNotFoundException("Couldn't open DOMApp");
        }
        else if (savedEx != null)
        {
            logger.error("Successful DOMApp retry after initial failure", savedEx);
        }

        app.transitionToDOMApp();
        return app;
    }

    /**
     * This is a deeper run - basically I want a nice way of efficiently getting
     * the stop signals written - a simple return to a wrapper which handles
     * this seems best. So the thread run method will handle that recovery
     * process
     */
    private void runcore(InterruptorTask intTask) throws Exception
    {

        driver.resetComstat(card, pair, dom);

        // Wrap up in retry loop - sometimes getMainboardID fails/times out
        // DOM is in a strange state here
        // this is a workaround for "Type 3" dropped DOMs
        numericMBID = 0;
        int NT      = 2;
        for(int i = 0; i < NT; i++)
        {
            intTask.ping();
            try
            {
                app = softbootToDomapp();
                try
                {
                    mbid = app.getMainboardID();
                }
                catch (MessageException ex)
                {
                    // if exception is wrapping a ClosedByInterruptException,
                    //   then throw the original exception
                    if (ex.getCause() != null &&
                        ex.getCause() instanceof ClosedByInterruptException)
                    {
                        throw (ClosedByInterruptException) ex.getCause();
                    }

                    // otherwise, throw the MessageException
                    throw ex;
                }
                numericMBID = Long.valueOf(mbid, 16).longValue();
                break;
            }
            catch (ClosedByInterruptException ex)
            {
                // clear the interrupt so it doesn't cause future problems
                Thread.interrupted();

                // log exception and continue
                logger.error("Timeout on trial "+i+" getting DOM ID", ex);
				logger.error("Driver comstat for "+card+""+pair+dom+":\n"+driver.getComstat(card,pair,dom));
				logger.error("FPGA regs for card "+card+":\n"+driver.getFPGARegs(card));
				app = null; /* We have to do this to guarantee that we reopen when we retry */
            }
        }

        setRunLevel(RunLevel.IDLE);
        
        if (numericMBID == 0)
            throw new Exception("Couldn't get DOM MB ID after "+NT+" trials.");

        intTask.ping();
        logger.info("Found DOM " + mbid + " running " + app.getRelease());

        // Grab 2 RAPCal data points to get started
        for (int nTry = 0; nTry < 10 && validRAPCalCount < 2; nTry++) execRapCal();
        lastTcalRead = System.currentTimeMillis();

        /*
         * Workhorse - the run loop
         */
        logger.info("Entering run loop");

        while (!stop_thread && !interrupted())
        {
            long t = System.currentTimeMillis();
            boolean tired = true;

            // Ping the watchdog task
            intTask.ping();

            loopCounter++;

            /* Do TCAL and GPS -- this always runs regardless of the run state */
            if (t - lastTcalRead >= tcalReadInterval)
            {
                if (logger.isDebugEnabled()) {
                    logger.debug("Doing TCAL - runLevel is " + getRunLevel());
                }
                lastTcalRead = t;
                execRapCal();
            }

            switch (getRunLevel())
            {
            case RUNNING:
                // Time to do a data collection?
                if (t - lastDataRead >= dataReadInterval)
                {
                    lastDataRead = t;
                    try
                    {
                        // Get debug information during Alpaca failures
                        ByteBuffer data = app.getData();
                        if (data.remaining() > 0) tired = false;
                        dataProcess(data);
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
                if (t - lastMoniRead >= moniReadInterval)
                {
                    lastMoniRead = t;
                    ByteBuffer moni = app.getMoni();
                    if (moni.remaining() > 0)
                    {
                        moniProcess(moni);
                        tired = false;
                    }
                }
                if (t - lastSupernovaRead > supernovaReadInterval)
                {
                    lastSupernovaRead = t;
                    while (true)
                    {
                        ByteBuffer sndata = app.getSupernova();
                        if (sndata.remaining() > 0)
                        {
                            supernovaProcess(sndata);
                            tired = false;
                            break;
                        }
                    }
                }
                break;

            case CONFIGURING:
                /* Need to handle a configure */
                logger.info("Got CONFIGURE signal.");
                configure(config);
                logger.info("DOM is configured.");
                setRunLevel(RunLevel.CONFIGURED);
                break;

            case STARTING:
                logger.info("Got START RUN signal " + canonicalName());
                app.beginRun();
                storeRunStartTime();
                logger.info("DOM is running.");
                setRunLevel(RunLevel.RUNNING);
                break;

            case STARTING_SUBRUN:
                setRunLevel(RunLevel.STOPPING_SUBRUN);
                app.endRun();
                setRunLevel(RunLevel.CONFIGURING);
                if (flasherConfig != null)
                {
                    if (logger.isInfoEnabled()) logger.info("Starting flasher subrun");
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
                    sleep(new Random().nextInt(250));
                    app.beginFlasherRun(
                            (short) flasherConfig.getBrightness(),
                            (short) flasherConfig.getWidth(),
                            (short) flasherConfig.getDelay(),
                            (short) flasherConfig.getMask(),
                            (short) flasherConfig.getRate()
                            );
                }
                else
                {
                    if (logger.isInfoEnabled()) {
                        logger.info("Returning to non-flashing state");
                    }
                    configure(config);
                    app.beginRun();
                }
                storeRunStartTime();
                setRunLevel(RunLevel.RUNNING);
                break;

            case PAUSING:
                if (logger.isInfoEnabled()) {
                    logger.info("Got PAUSE RUN signal " + canonicalName());
                }
                app.endRun();
                setRunLevel(RunLevel.CONFIGURED);
                break;

            case STOPPING:
                if (logger.isInfoEnabled()) {
                    logger.info("Got STOP RUN signal " + canonicalName());
                }
                app.endRun();
                ByteBuffer otrava = MultiChannelMergeSort.eos(numericMBID);
                if (hitsConsumer != null) hitsConsumer.consume(otrava.asReadOnlyBuffer());
                if (moniConsumer != null) moniConsumer.consume(otrava.asReadOnlyBuffer());
                if (tcalConsumer != null) tcalConsumer.consume(otrava.asReadOnlyBuffer());
                if (supernovaConsumer != null) supernovaConsumer.consume(otrava.asReadOnlyBuffer());
                if (logger.isInfoEnabled()) {
                    logger.info("Wrote EOS to streams.");
                }
                setRunLevel(RunLevel.CONFIGURED);
                break;
            }

            if (tired)
            {
                if (logger.isDebugEnabled()) {
                    logger.debug("Runcore loop is tired - sleeping " +
                                 threadSleepInterval + " ms.");
                }
                try
                {
                    Thread.sleep(threadSleepInterval);
                }
                catch (InterruptedException intx)
                {
                    logger.warn("Interrupted.");
                }
            }
        } /* END RUN LOOP */
    } /* END METHOD */

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
     */
    class InterruptorTask extends TimerTask
    {
        private AtomicBoolean pinged;

        InterruptorTask()
        {
            pinged = new AtomicBoolean(false);
        }

        public void run()
        {
            if (!pinged.get()) 
            {
                logger.error("data collection thread has become non-responsive - aborting.");
                app.close();
                interrupt();
            }
            pinged.set(false);
        }

        public void ping()
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("pinged at " + fmt.format(System.nanoTime() * 1.0E-09));
            }
            pinged.set(true);
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
        // TODO Auto-generated method stub
        return null;
    }

    public String getRunState()
    {
        // TODO Auto-generated method stub
        return getRunLevel().toString();
    }

}
