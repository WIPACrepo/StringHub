/* -*- mode: java; indent-tabs-mode:f; tab-width:4 -*- */

package icecube.daq.domapp;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.domapp.LocalCoincidenceConfiguration.RxMode;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.Driver;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.GPSService;
import icecube.daq.dor.IDriver;
import icecube.daq.dor.TimeCalib;
import icecube.daq.livemoni.LiveTCalMoni;
import icecube.daq.rapcal.RAPCal;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.rapcal.ZeroCrossingRAPCal;
import icecube.daq.util.RealTimeRateMeter;
import icecube.daq.util.StringHubAlert;
import icecube.daq.util.UTC;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private long                numericMBID;

    // the driver code used to rebuild a path on every call
    // get the File object ONCE
    private File tcalFile;

    private IDOMApp             app;
    private RAPCal              rapcal;
    private IDriver             driver;
    private boolean             stop_thread;
    private BufferConsumer      hitsConsumer;
    private BufferConsumer      moniConsumer;
    private BufferConsumer      tcalConsumer;
    private BufferConsumer      supernovaConsumer;

    private InterruptorTask intTask = new InterruptorTask();

    private static final Logger         logger  = Logger.getLogger(DataCollector.class);

    private static final DecimalFormat  fmt     = new DecimalFormat("#0.000000000");

    // TODO - replace these with properties-supplied constants
    // for now they are totally reasonable
    private long    threadSleepInterval   = 50;

    //private long    lastDataRead          = 0;
    //private long    lastMoniRead          = 0;
    //private long    lastSupernovaRead     = 0;
    //private long    lastTcalRead          = 0;

    // rewrite the processing loop so instead of
    // keeping trakc of the last read, figure out
    // when the next read will be, means fewer
    // subtractions
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

    private int     numHits               = 0;
    private int     numMoni               = 0;
    private int     numSupernova          = 0;
    private int     loopCounter           = 0;
    private long    lastTcalUT;
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
     * Simple hits-only constructor (other channels suppressed)
     * @param chInfo structure containing DOM channel information
     * @param hitsTo BufferConsumer target for hits
     * @throws IOException
     * @throws MessageException
     */
    public DataCollector(DOMChannelInfo chInfo, BufferConsumer hitsTo)
    throws IOException, MessageException
    {
        this(chInfo.card, chInfo.pair, chInfo.dom, null, hitsTo, null, null, null);
    }

    public DataCollector(
            int card, int pair, char dom,
            DOMConfiguration config,
            BufferConsumer hitsTo,
            BufferConsumer moniTo,
            BufferConsumer supernovaTo,
            BufferConsumer tcalTo) throws IOException, MessageException
	{
		// support class old signature
		// but default to disabling intervals
		this(card, pair, dom, config, hitsTo, moniTo, supernovaTo, tcalTo, false);
	}


    public DataCollector(
            int card, int pair, char dom,
            DOMConfiguration config,
            BufferConsumer hitsTo,
            BufferConsumer moniTo,
            BufferConsumer supernovaTo,
            BufferConsumer tcalTo,
			boolean enable_intervals) throws IOException, MessageException
    {
        super(card, pair, dom);
        this.card = card;
        this.pair = pair;
        this.dom = dom;

		//System.out.println("Enable stats: "+ENABLE_STATS+" intervals: "+ENABLE_INTERVAL);

        hitsConsumer = hitsTo;
        moniConsumer = moniTo;
        tcalConsumer = tcalTo;
        supernovaConsumer = supernovaTo;

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
				if (supernovaConsumer != null) supernovaConsumer.consume(eos.asReadOnlyBuffer());
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

    private long dispatchBuffer(ByteBuffer buf, BufferConsumer target) throws IOException
    {
        long domclk = buf.getLong(24);
        long utc    = rapcal.domToUTC(domclk).in_0_1ns();
        buf.putLong(24, utc);

        target.consume(buf);

        // Collect HLC / SLC hit statistics ...
        switch ( buf.getInt(4) )
        {
        case MAGIC_COMPRESSED_HIT_FMTID:
            int flagsLC = (buf.getInt(46) & 0x30000) >> 16;
            if (flagsLC != 0) rtLCRate.recordEvent(utc);
            // intentional fall-through
        case MAGIC_ENGINEERING_HIT_FMTID:
            rtHitRate.recordEvent(utc);
            lastHitTime = utc;
            if (firstHitTime < 0L) firstHitTime = utc;
        }
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
                if (moniMsg.contains("LBM OVERFLOW")) {
                    numLBMOverflows++;
                    logger.error("LBM Overflow");
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

    private GPSService execRapCal()
    {
        try
        {
            GPSService gps_serv = GPSService.getInstance();
            GPSInfo gps = gps_serv.getGps(card);
            UTC gpsOffset = new UTC(0L);
            if (gps != null) gpsOffset = gps.getOffset();

            TimeCalib tcal = driver.readTCAL(tcalFile);
            rapcal.update(tcal, gpsOffset);
			nextTcalRead = System.currentTimeMillis() + tcalReadInterval;

            validRAPCalCount++;

            if (getRunLevel().equals(RunLevel.RUNNING))
            {
                tcalProcess(tcal, gps);
            }
			return gps_serv;

        }
        catch (RAPCalException rcex)
        {
            logger.warn("Got RAPCal exception", rcex);
        }
        catch (IOException iox)
        {
            logger.warn("Got IO exception", iox);
        }
        catch (InterruptedException intx)
        {
            logger.warn("Got interrupted exception", intx);
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
	synchronized (this) {
	    lastTcalUT = 0L;
	}
        nextSupernovaDomClock = 0L;
        numSupernovaGaps = 0;

        logger.debug("Begin data collection thread");

        // Create a watcher timer
        Timer watcher = new Timer(getName() + "-timer");
        watcher.schedule(intTask, 20000L, 5000L);
        try
        {
			// core common to both intervals AND query
			// it will decide which method is appropriate and
			// call runcore_orig or runcore_interval
			runcore_universal();
        }
        catch (Exception x)
        {
            logger.error("Intercepted error in DataCollector runcore", x);
            /*
             * TODO cleanup needed set run level to ZOMBIE so that controller knows
             * that this channel has expired and does not wait.
             */
            setRunLevel(RunLevel.ZOMBIE);
            StringHubAlert.sendDOMAlert(alerter, "Zombie DOM", card, pair, dom,
                                        mbid, name, major, minor, runNumber,
                                        lastHitTime);
        }
        watcher.cancel();

        // clear interrupted flag if it is set
        interrupted();

        // Make sure EoS (end-of-stream symbol) is written
        try
        {
            ByteBuffer eos = MultiChannelMergeSort.eos(numericMBID);
            if (hitsConsumer != null) hitsConsumer.consume(eos.asReadOnlyBuffer());
            if (moniConsumer != null) moniConsumer.consume(eos.asReadOnlyBuffer());
            if (tcalConsumer != null) tcalConsumer.consume(eos.asReadOnlyBuffer());
            if (supernovaConsumer != null) supernovaConsumer.consume(eos.asReadOnlyBuffer());
            logger.info("Wrote EOS to streams.");
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
                intTask.ping();
                driver.softboot (card, pair, dom);
                break;
            }
            catch (IOException iox)
            {
                logger.warn("Softboot attempt failed - retrying after 5 sec");
                Thread.sleep(5000L);
            }
        }

        for (int i = 0; i < 2; i++)
        {
            Thread.sleep(20);
            driver.commReset(card, pair, dom);
            Thread.sleep(20);

            try
            {
                intTask.ping();
                app = new DOMApp(this.card, this.pair, this.dom);
                Thread.sleep(20);
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
	 * As there are two versions of runcore now ( both for the original query
	 * method and the get_interval method ), this is contains code common to both
	 * methods.  In addiiton it will decide which method to use.
	 *
	 * If the user explicitly disables intervals setting
	 * runConfig/Stringhub[id=X]/intervals/enable/false
	 * or the domapp version is not high enough to support
	 * intervals it will default to the query method.  Otherwise, intervals
	 * will be used.
	*/
	private void runcore_universal() throws Exception
	{

        driver.resetComstat(card, pair, dom);

        /*
         * I need the MBID right now just in case I have to shut this stream down.
         */
        mbid = driver.getProcfileID(card, pair, dom);
        numericMBID = Long.parseLong(mbid, 16);
        boolean needSoftboot = true;

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
                    mbid = app.getMainboardID();
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
                intTask.ping();
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
                    mbid = app.getMainboardID();
					break;
                }
                catch (Exception ex2)
                {
                    if (iTry == 1) throw ex2;
                    logger.error("Failure to softboot to DOMApp - will retry one time.");
                }
            }
        }
        numericMBID = Long.parseLong(mbid, 16);

        setRunLevel(RunLevel.IDLE);

        intTask.ping();
        if (logger.isDebugEnabled()) {
            logger.debug("Found DOM " + mbid + " running " + app.getRelease());
        }

        // Grab 2 RAPCal data points to get started
        for (int nTry = 0; nTry < 10 && validRAPCalCount < 2; nTry++)
        {
            Thread.sleep(100);
            execRapCal();
        }

		// determine if we should use get_intervals or
		// the original query algorithm
		String version = app.getRelease();
		if(!disable_intervals && version_supports_intervals(version)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Using intervals run loop!");
			}
			// configure is only called inside the following
			runcore_interval();
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug("Using original run loop!");
			}
			// explicitly turn off intervals if the dom-mb version
			// does not support intervals
			this.disable_intervals = true;
			// configure is only called inside the following
			runcore_orig();
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
	 * The guts of this method use the DOM query algorithm.
	 * A 'GET_DATA' message is sent down to the dom every 10ms followed by
	 * a get_moni and get_sn message wrapping up with a tcal every second.
	 *
     */
    private void runcore_orig() throws Exception
    {

        while (!stop_thread && !interrupted())
        {
            long t = System.currentTimeMillis();
            boolean tired = true;

            // Ping the watchdog task
            intTask.ping();

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
                // Time to do a data collection?
                if (t>=nextDataRead)
                {
		    nextDataRead = t + dataReadInterval;

                    try
                    {
                        // Get debug information during Alpaca failures
                        ByteBuffer data = app.getData();
                        if (data.remaining() > 0) tired = false;

			// generate some stats as to the average size
                        // of hit bytebuffers
			if(ENABLE_STATS) {
			    // Compute the mean and variance of data message sizes
			    // This is an implementation of welfords method
			    // http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance
			    int remaining = data.remaining();
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
                if (t >= nextMoniRead)
                {
		    nextMoniRead = t + moniReadInterval;

                    ByteBuffer moni = app.getMoni();
                    if (moni.remaining() > 0)
                    {
                        moniProcess(moni);
                        tired = false;
                    }
                }

                if (t > nextSupernovaRead)
                {
		    nextSupernovaRead = t + supernovaReadInterval;
                    while (!supernova_disabled)
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
                        sleep(new Random().nextInt(250));
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
                if (hitsConsumer != null) hitsConsumer.consume(eos.asReadOnlyBuffer());
                if (moniConsumer != null) moniConsumer.consume(eos.asReadOnlyBuffer());
                if (tcalConsumer != null) tcalConsumer.consume(eos.asReadOnlyBuffer());
                if (supernovaConsumer != null) supernovaConsumer.consume(eos.asReadOnlyBuffer());
                logger.debug("Wrote EOS to streams.");

				if(ENABLE_STATS) {
					System.out.println(card+"/"+pair+"/"+dom+": rate: "+config.getPulserRate()+" max: "+data_max+" min:"+data_min+" mean: "+data_fmt.format(data_m_n)+" var: "+data_fmt.format((data_s_n / ( data_count - 1.0 )))+" count: "+data_count+" zero count: "+data_zero_count+ " lbm overflows: "+numLBMOverflows+" hit rate: "+rtHitRate.getRate()+" hit rate LC: "+rtLCRate.getRate());
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


    /**
	 * This is an updated version of the original runcore method.
	 * It requires domapp version 4477 or above.
	 *
	 * Instead of sending query messages down to the dom every 10 ms one
	 * "GET_INTERVAL" message is sent.  The results from that message are that all
	 * data for the next second is returned followed by a moni and supernova message.
	 * If supernova messages are disabled get interval will not work.
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
     */
    private void runcore_interval() throws Exception
    {
        while (!stop_thread && !interrupted())
        {
            long t = System.currentTimeMillis();
            boolean tired = true;

            // Ping the watchdog task
            intTask.ping();

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
				app.getInterval();

				boolean done = false;
				while (!done) {
					tired = true;
					ByteBuffer msg = app.recvMessage(intervalBuffer);

					intTask.ping();

					byte msg_type = msg.get(0);
					byte msg_subtype = msg.get(1);

					// past the header
					msg.position(8);

					if(MessageType.GET_DATA.equals(msg_type, msg_subtype)) {
						if (msg.remaining()>0) {
							tired = false;
						}

						if(ENABLE_STATS) {
							// Compute the mean and variance of data message sizes
							// This is an implementation of welfords method
							// http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance
							int remaining = msg.remaining();
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

						dataProcess(msg.slice());
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
						try
							{
								Thread.sleep(threadSleepInterval);
							}
						catch (InterruptedException intx)
							{
								logger.warn("Interrupted.");
							}
					}
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
                        sleep(new Random().nextInt(250));
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
                if (hitsConsumer != null) hitsConsumer.consume(eos.asReadOnlyBuffer());
                if (moniConsumer != null) moniConsumer.consume(eos.asReadOnlyBuffer());
                if (tcalConsumer != null) tcalConsumer.consume(eos.asReadOnlyBuffer());
                if (supernovaConsumer != null) supernovaConsumer.consume(eos.asReadOnlyBuffer());
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
                if (app != null) app.close();
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
}
