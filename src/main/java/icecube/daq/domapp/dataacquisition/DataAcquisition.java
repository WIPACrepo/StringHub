package icecube.daq.domapp.dataacquisition;

import icecube.daq.domapp.DOMApp;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.domapp.LocalCoincidenceConfiguration;
import icecube.daq.domapp.MessageException;
import icecube.daq.domapp.MessageType;
import icecube.daq.domapp.PulserMode;
import icecube.daq.domapp.TriggerMode;
import icecube.daq.domapp.dataprocessor.DataProcessor;
import icecube.daq.domapp.dataprocessor.DataProcessorError;
import icecube.daq.domapp.dataprocessor.DataStats;
import icecube.daq.dor.Driver;
import icecube.daq.dor.IDriver;
import icecube.daq.dor.TimeCalib;
import icecube.daq.time.monitoring.ClockMonitoringSubsystem;
import icecube.daq.time.monitoring.ClockProcessor;
import icecube.daq.util.FlasherboardConfiguration;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * A collection of methods that handle data acquisition
 * from the DOM device.
 *
 * Caller is assumed to be the DataCollector thread. This is a requirement
 * for correct behavior.
 *
 * This class was initially implemented with code migrated from
 * icecube.daq.domapp.DataCollector revision 15482.
 */
public class DataAcquisition
{
    private Logger logger = Logger.getLogger(DataAcquisition.class);

    private final int card;
    private final  int pair;
    private final  char dom;

    private final String id; //"<card>,<pair>,<dom>"

    private boolean supernova_disabled; //not known until configure time

    /** members supporting dom communication*/
    private final IDriver driver;
    private DOMApp app;
    private final File tcalFile;
    private final ByteBuffer intervalBuffer;


    // Note: read intervals are only applicable to polling mode.
    private long nextSupernovaRead = 0;
    private long nextMoniRead = 0;
    private long nextDataRead = 0;

    private long    dataReadInterval      = 10;
    private long    moniReadInterval      = 1000;
    private long    supernovaReadInterval = 1000;


    /** Wait time used when spinning on data reads. */
    private final static long SPIN_WAIT_SLEEP_MILLIS = 50;

    /** Performance tracker. */
    private final AcquisitionMonitor monitor;

    /** Target of acquired data. */
    private final DataProcessor dataProcessor;

    /** Counters maintained by processor, not threadsafe, use sparingly. */
    private final DataStats dataStats;

    /** Monitoring consumer of tcals. */
    private ClockProcessor tcalConsumer =
            ClockMonitoringSubsystem.Factory.processor();

    public DataAcquisition(final int card, final int pair, final char dom,
                           final DataProcessor dataProcessor)
    {
        this.card = card;
        this.pair = pair;
        this.dom = dom;
        this.dataProcessor = dataProcessor;
        this.dataStats = dataProcessor.getDataCounters();

        this.id = card + "" + pair + "" + dom;
        this.driver = Driver.getInstance();

        tcalFile = this.driver.getTCALFile(card, pair, dom);

        this.intervalBuffer = ByteBuffer.allocateDirect(4092);

        this.monitor = new AcquisitionMonitor(id);
    }

    /**
     * Applies the configuration to the DOM.
     *
     * @param config The configuration to apply.
     * @throws AcquisitionError
     */
    public void doConfigure(final DOMConfiguration config)
            throws AcquisitionError
    {
        try
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

            // now step careful around this - some old MB versions don't support the message
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
                try {
                    dataProcessor.eos(DataProcessor.StreamType.SUPERNOVA);
                } catch (DataProcessorError dpe) {
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
        catch (MessageException e)
        {
            throw new AcquisitionError("Error configuring DOM on [" + id + "]", e);
        }
    }

    /**
     * Initiate the beginning of a run.
     *
     * @param watchdog
     * @return The run start time in utc.
     * @throws AcquisitionError
     */
    public long doBeginRun(final Watchdog watchdog) throws AcquisitionError
    {
        try
        {
            app.beginRun();
            monitor.reportRunStart();

            TimeCalib timeCalib = driver.readTCAL(tcalFile);
            long domclock = timeCalib.getDomTx().in_0_1ns() / 250L;
            return dataProcessor.resolveUTCTime(domclock).in_0_1ns();
        }
        catch (MessageException me)
        {
            throw new AcquisitionError("Error starting run on " +
                    canonicalName(), me);
        }
        catch (InterruptedException ie)
        {
            throw new AcquisitionError("Error starting run on " +
                    canonicalName(), ie);
        }
        catch (IOException ioe)
        {
            throw new AcquisitionError("Error starting run on " +
                    canonicalName(), ioe);
        }
        catch (DataProcessorError dpe)
        {
            // A failed RAPCal for the run start time is fatal to the run.
            throw new AcquisitionError("Error starting run on " +
                    canonicalName(), dpe);
        }
    }

    /**
     * Initiate a flasher run.
     *
     * @param flasherConfig the flasher config for the run.
     * @return The flasher run start time in utc.
     * @throws AcquisitionError
     */
    public long doBeginFlasherRun(FlasherboardConfiguration flasherConfig)
            throws AcquisitionError
    {
        try
        {
            app.beginFlasherRun(
                    (short) flasherConfig.getBrightness(),
                    (short) flasherConfig.getWidth(),
                    (short) flasherConfig.getDelay(),
                    (short) flasherConfig.getMask(),
                    (short) flasherConfig.getRate()
            );


            TimeCalib timeCalib = driver.readTCAL(tcalFile);

            long domclock = timeCalib.getDomTx().in_0_1ns() / 250L;
            return dataProcessor.resolveUTCTime(domclock).in_0_1ns();
        }
        catch (MessageException me)
        {
            throw new AcquisitionError("Error starting flasher run on " +
                    canonicalName(), me);
        }
        catch (InterruptedException ie)
        {
            throw new AcquisitionError("Error starting flasher run on " +
                    canonicalName(), ie);
        }
        catch (IOException ioe)
        {
            throw new AcquisitionError("Error starting flasher run on " +
                    canonicalName(), ioe);
        }
        catch (DataProcessorError dpe)
        {
            //a rapcal failure on flasher run start is fatal.
            throw new AcquisitionError("Error starting flasher run on " +
                    canonicalName(), dpe);
        }
    }

    /**
     * Change the flasher config.
     *
     * @param flasherConfig The flasher config to apply.
     * @return The point int time when the flash config is in effect, in utc.
     * @throws AcquisitionError
     */
    public long doChangeFlasherRun(FlasherboardConfiguration flasherConfig)
            throws AcquisitionError
    {
        try
        {
            app.changeFlasherSettings(
                    (short) flasherConfig.getBrightness(),
                    (short) flasherConfig.getWidth(),
                    (short) flasherConfig.getDelay(),
                    (short) flasherConfig.getMask(),
                    (short) flasherConfig.getRate()
            );

            TimeCalib timeCalib = driver.readTCAL(tcalFile);

            long domclock = timeCalib.getDomTx().in_0_1ns() / 250L;
            return dataProcessor.resolveUTCTime(domclock).in_0_1ns();
        }
        catch (MessageException me)
        {
            throw new AcquisitionError("Error starting flasher run on " +
                    canonicalName(), me);
        }
        catch (InterruptedException ie)
        {
            throw new AcquisitionError("Error starting flasher run on " +
                    canonicalName(), ie);
        }
        catch (IOException ioe)
        {
            throw new AcquisitionError("Error starting flasher run on " +
                    canonicalName(), ioe);
        }
        catch (DataProcessorError dpe)
        {
            throw new AcquisitionError("Error starting flasher run on " +
                    canonicalName(), dpe);
        }
    }

    /**
     * Pause the current run. Currently the same as stopping.
     * @throws AcquisitionError
     */
    public void doPauseRun() throws AcquisitionError
    {
        try
        {
            app.endRun();
        }
        catch (MessageException me)
        {
            throw new AcquisitionError("Error pausing run on " +
                    canonicalName(), me);
        }
    }

    /**
     * End the current run.
     * @throws AcquisitionError
     */
    public void doEndRun() throws AcquisitionError
    {
        try
        {
            app.endRun();
        }
        catch (MessageException me)
        {
            throw new AcquisitionError("Error ending run on " +
                    canonicalName(), me);
        }

        //this is a legacy development feature
        if(AcquisitionMonitor.ENABLE_STATS)
        {
            monitor.printWelfordStats(dataProcessor.getDataCounters());
        }
    }

    /**
     * Close the interface to the dom.
     */
    public void doClose()
    {
        if(app != null)
        {
            app.close();
        }
    }


    /**
     * Execute an interval acquisition.
     *
     * @param watchdog The watchdog.
     * @throws AcquisitionError
     */
    public boolean doInterval(final Watchdog watchdog) throws AcquisitionError
    {
        boolean tired = true;

        monitor.initiateCycle();
        try
        {
            boolean done = false;
            app.getInterval();

            while (!done) {
                monitor.initiateMessageRead();
                ByteBuffer msg = app.recvMessage(intervalBuffer);
                monitor.reportDataMessageRcv(msg);

                watchdog.ping();

                // consume message header
                byte msg_type = msg.get(0);
                byte msg_subtype = msg.get(1);
                msg.position(8);

                // pass message payload to processor
                if(MessageType.GET_DATA.equals(msg_type, msg_subtype)) {
                    if (msg.remaining()>0) {
                        tired = false;
                    }

                    dataProcessor.process(DataProcessor.StreamType.HIT, msg.slice());
                } else if(MessageType.GET_SN_DATA.equals(msg_type, msg_subtype)) {
                    if (msg.remaining()>0) {
                        dataProcessor.process(DataProcessor.StreamType.SUPERNOVA, msg.slice());
                        tired = false;
                    }
                    done = true;
                } else if(MessageType.GET_MONI.equals(msg_type, msg_subtype)) {
                    if (msg.remaining()>0) {
                        dataProcessor.process(DataProcessor.StreamType.MONI, msg.slice());
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
                    watchdog.sleep(SPIN_WAIT_SLEEP_MILLIS);
                }
            }

            return tired;
        }
        catch(Exception ex)
        {
            throw new AcquisitionError("Error acquiring data from [" +
                    id + "]", ex);
        }
        finally
        {
            monitor.completeCycle();
        }
    }

    /**
     * Execute an polling acquisition.
     *
    * @param watchdog The watchdog.
    * @throws AcquisitionError
    */
    public boolean doPolling(final Watchdog watchdog,
                             final long systemTimMillis)
            throws AcquisitionError
    {
        boolean tired = true;

        try
        {
            monitor.initiateCycle();

            // Time to do a data collection?
            if (systemTimMillis >= nextDataRead)
            {
                nextDataRead = systemTimMillis + dataReadInterval;

                try
                {
                    // Get debug information during Alpaca failures
                    monitor.initiateMessageRead();
                    ByteBuffer data = app.getData();
                    monitor.reportDataMessageRcv(data);

                    if (data.remaining() > 0) tired = false;

                    dataProcessor.process(DataProcessor.StreamType.HIT, data);
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

                monitor.initiateMessageRead();
                ByteBuffer moni = app.getMoni();
                monitor.reportDataMessageRcv(moni);
                if (moni.remaining() > 0)
                {
                    dataProcessor.process(DataProcessor.StreamType.MONI, moni);
                    tired = false;
                }
            }

            if (systemTimMillis > nextSupernovaRead)
            {
                nextSupernovaRead = systemTimMillis + supernovaReadInterval;
                while (!supernova_disabled)
                {
                    monitor.initiateMessageRead();
                    ByteBuffer sndata = app.getSupernova();
                    monitor.reportDataMessageRcv(sndata);

                    if (sndata.remaining() > 0)
                    {
                        dataProcessor.process(DataProcessor.StreamType.SUPERNOVA, sndata);
                        tired = false;
                        break;
                    }
                }
            }

            return tired;

        }
        catch(Exception ex)
        {
            throw new AcquisitionError("Error acquiring data from [" +
                    id + "]", ex);
        }
        finally
        {
            monitor.completeCycle();
        }
    }

    /**
     * Initialize the connection to the DOM, including
     * attempts at fault recovery.
     *
     * @param watchdog The watchdog.
     * @param alwaysSoftboot If true, dom will be soft booted.
     * @return The Mainboard ID reported by the device through the DOMApp
     *          message interface.
     *
     */
    public String doInitialization(final Watchdog watchdog,
                                   final boolean alwaysSoftboot)
            throws AcquisitionError
    {
        // Configure the watchdog to interrupt
        Watchdog.Mode mode =
                watchdog.setTimeoutAction(Watchdog.Mode.INTERRUPT_ONLY);

        try
        {
            String reportedMBID = null;

            driver.resetComstat(card, pair, dom);

            boolean needSoftboot = true;

            // Test the condition the DOM. if it is running DOMApp,
            // enforce a new run
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
                        reportedMBID = app.getMainboardID();
                    }
                    else
                    {
                        app.close();
                        app = null;
                    }
                }
                catch (Exception ex)
                {
                    // The watchdog may have fired, or domapp may have responded
                    // inconsistently such as when the previous run exited
                    // mid-interval
                    //
                    logger.warn("DOM is not responding to DOMApp query -" +
                            " will attempt to softboot", ex);

                    if(app != null)
                    {
                        app.close();
                        app = null;
                    }

                    // The watchdog may have fired, reset the interrupt.
                    Thread.currentThread().interrupted();
                    watchdog.ping();
                }

            }

            if (needSoftboot)
            {
                for (int iTry = 0; iTry < 2; iTry++)
                {
                    try
                    {
                        softbootToDomapp(watchdog);
                        reportedMBID = app.getMainboardID();
                        break;
                    }
                    catch (Exception ex2)
                    {
                        if (iTry == 1) throw ex2;
                        logger.error("Failure to softboot to DOMApp - will retry one time.", ex2);

                        // The watchdog may have fired, reset the interrupt.
                        Thread.currentThread().interrupted();

                        if(app != null)
                        {
                            app.close();
                        }
                    }
                }
            }

            return reportedMBID;
        }
        catch (Exception e)
        {
            throw new AcquisitionError("Error while initializing [" +
                    id + "]", e);
        }
        finally
        {
            //restore the watchdog mode
            watchdog.setTimeoutAction(mode);
        }

    }


    /**
     * Execute a TCAL operation.
     *
     * @param watchdog The watchdog.
     * @throws AcquisitionError Error occurred running TCAL.
     * @throws DataProcessorError Error occurred processing TCAL.
     */
    public void doTCAL(final Watchdog watchdog) throws AcquisitionError,
            DataProcessorError
    {
        try
        {
            long before = System.nanoTime();
            TimeCalib tcal = driver.readTCAL(tcalFile);
            long after = System.nanoTime();

            // re-synthesize a data buffer.
            ByteBuffer buf = ByteBuffer.allocate(314);
            tcal.writeUncompressedRecord(buf);
            buf.flip();

            dataProcessor.process(DataProcessor.StreamType.TCAL, buf);

            // Tee readings to clock monitoring for GPS clock triangulation
            //
            // Note: This is the point at which we associate a dor timestamp
            //       with a monotonic nano timestamp. We are choosing the dor
            //       tx time.
            final long dortxDor = tcal.getDorTxInDorUnits();
            final long dortxNano = tcal.getDorTXPointInTimeNano();
            tcalConsumer.process(new ClockProcessor.TCALMeasurement(dortxDor,
                    dortxNano, (after - before), card, id));

            // Note: nuisance hack, dom/system times must be tracked from
            //       acquisition thread because the nano point-in-time field
            //       is not propagated to the processor.
            dataStats.reportClockRelationship(tcal.getDomRxInDomUnits(),
                    dortxNano);
        }
        catch (InterruptedException e)
        {
            watchdog.handleInterrupted(e);
        }
        catch (IOException e)
        {
            throw new AcquisitionError("IO Error performing a tcal on ["
                    + id + "]", e);
        }
    }


    /**
     * Access the Mainboard ID via the proc file.
     * @return The mainboard id as read from the proc file.
     * @throws AcquisitionError
     */
    public String getMBID() throws AcquisitionError
    {
        try
        {
            return driver.getProcfileID(card, pair, dom);
        }
        catch (IOException ioe)
        {
            throw new AcquisitionError("Error getting mbid from " + canonicalName(), ioe);
        }
    }

    /**
     * Gather verbose logging for recent acquisition cycles.
     *
     * This may be useful for diagnosing watchdog-initiated shutdowns.
     * @return A list of lings that log recent acquisition history.
     */
    public List<StringBuilder> logHistory()
    {
        return monitor.logHistory();
    }


    /**
     * Provides access to the system time that run start occurred.
     *
     * @return The system time when the run was started.
     */
    public long getRunStartSystemTime()
    {
        return monitor.runStartTime;
    }


    /**
     * Wrap up softboot -> domapp behavior
     */
    private void softbootToDomapp(final Watchdog watchdog) throws IOException,
            InterruptedException
    {
        // Based on discussion /w/ JEJ in Utrecht café, going for multiple
        // retries here
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
     * It is polite to call instances by name like [00A]
     * @return canonical name string
     */
    private String canonicalName()
    {
        return "[" + id + "]";
    }

}
