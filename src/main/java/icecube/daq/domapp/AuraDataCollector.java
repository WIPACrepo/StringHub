package icecube.daq.domapp;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.dor.Driver;
import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.TimeCalib;
import icecube.daq.monitoring.TCalExceptionAlerter;
import icecube.daq.rapcal.RAPCal;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.rapcal.ZeroCrossingRAPCal;
import icecube.daq.util.UTC;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

public class AuraDataCollector extends AbstractDataCollector
{
    private AuraDRM             drm;
    private Driver              driver;
    private RAPCal              rapcal;
    private BufferConsumer      hits;
    private AtomicBoolean       running;
    private AtomicBoolean       forcedTrigger;
    private AtomicBoolean       radioTrigger;
    private AtomicBoolean       changeTriggerSetting;
    private int                 powerControlBits;
    private boolean             useDOMApp;
    private String              mbid;
    private long                mbid_numerique;
    private int                 evtCnt;
    private long                tracr_clock_offset  = 0L;
    private File                tcalFile;
    private File                gpsFile;

    private static final int    EVT_DOM_CLK         = 28;
    private static final int    EVT_TRACR_MATCH_CLK = 40;
    private static final int    EVT_TRACR_CLK       = 106;

    private short[][]           radioDACs           = {
            { 2000, 3000, 3200, 3250 },
            { 2000, 3000, 3200, 3250 },
            { 2000, 3000, 3200, 3250 },
            { 2000, 3000, 3200, 3250 } };

    // This is the default : 3/4 ch and 3/4 bands
    private int                 triggerSetting      = 68;
    private static final Logger logger              = Logger.getLogger(AuraDataCollector.class);

    public AuraDataCollector(int card, int pair, char dom, BufferConsumer hits)
    {
        this(card, pair, dom, hits, true);
    }

    public AuraDataCollector(int card, int pair, char dom, BufferConsumer hits,
            boolean useDOMApp)
    {
        super(card, pair, dom);
        driver = Driver.getInstance();
        rapcal = new ZeroCrossingRAPCal();
        this.hits = hits;
        this.evtCnt = 0;
        running = new AtomicBoolean(false);
        // this.powerControlBits = 0x3f;
        this.useDOMApp = useDOMApp;
        this.forcedTrigger = new AtomicBoolean(true);
        this.radioTrigger = new AtomicBoolean(false);
        changeTriggerSetting = new AtomicBoolean(false);

	tcalFile = driver.getTCALFile(card, pair, dom);
        gpsFile = driver.getGPSFile(card);
    }

    public void run()
    {
        Timer t3 = new Timer();

        try
        {
            driver.softboot(card, pair, dom);
            drm = new AuraDRM(card, pair, dom);
            mbid = drm.getMainboardId();
            mbid_numerique = Long.parseLong(mbid, 16);

            if (useDOMApp) drm.loadDOMAppSBI();

            // Now flag this process as IDLE
            setRunLevel(RunLevel.IDLE);

            t3.schedule(new TCALTask(), 1000L, 1000L);

            running.set(true);
            while (running.get() && !interrupted())
            {
                switch (getRunLevel())
                {
                case CONFIGURING:
                    // Turn on TRACR
                    Thread.sleep(1000);
                    drm.powerOnFlasherboard();
                    Thread.sleep(5500);
                    /*
                     * Get tracr-mb time offset. Make sure the cluster does not
                     * trigger by setting all DACs to 0
                     */

                    for (int ant = 0; ant < 4; ant++)
                        for (int band = 0; band < 4; band++)
                            drm.setRadioDAC(ant, band, 0);
                    drm.writeRadioDACs();
                    drm.resetTRACRFifo();
                    tracr_clock_offset = drm.getTRACRClockOffset(10000);
                    logger.info(" Got TRACR offset: " + tracr_clock_offset);

                    /*
                     * Turn on power There is a little black-magic here --
                     * amplifier bits and then SHORTS are turned on one by one.
                     */
                    if (!(drm.writePowerBits(powerControlBits)))
                    {
                        setRunLevel(RunLevel.STOPPING);
                    }
                    else
                    {
                        if (radioTrigger.get()) // Write dacs only if not forced
                                                // trigger
                        {
                            for (int ant = 0; ant < 4; ant++)
                                for (int band = 0; band < 4; band++)
                                    drm.setRadioDAC(ant, band, radioDACs[ant][band]);
                            drm.writeRadioDACs();
                        }
                        if (changeTriggerSetting.get())
                        {
                            drm.setTriggerLogic(triggerSetting);
                            logger.info(mbid + " Trigger Setting changed to"
                                    + drm.getTriggerLogic());
                        }
                        else
                        {
                            logger.info(mbid + " Default trigger Setting used"
                                    + drm.getTriggerLogic());
                        }

                        setRunLevel(RunLevel.CONFIGURED);
                    }

                    break;
                case STARTING:
                    drm.resetTRACRFifo();
                    logger.info("STARTING RUN on DOM " + mbid);
                    setRunLevel(RunLevel.RUNNING);
                    break;
                case RUNNING:
                    if (forcedTrigger.get()) sendRadioBuffer(drm.forcedTrig(1));
                    if (radioTrigger.get()) sendRadioBuffer(drm.radioTrig(1));
                    break;
                case STOPPING:
                    drm.powerOffFlasherboard();
                    setRunLevel(RunLevel.CONFIGURED);
                    break;
                }
            }

        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        try
        {
            hits.consume(MultiChannelMergeSort.eos(mbid_numerique));
        }
        catch (IOException iox)
        {
            iox.printStackTrace();
        }
        t3.cancel();
    }

    private void sendRadioBuffer(ByteBuffer buf)
    {
        int xtbSize = buf.limit() + 32;

        // Decode clocks and translate to UT via rapcal
        long utc;
        buf.order(ByteOrder.LITTLE_ENDIAN);
        long domclk = buf.getLong(EVT_DOM_CLK);
        if (buf.limit() > EVT_TRACR_CLK + 6)
        {
            long tracr_match_clk = buf.getLong(EVT_TRACR_MATCH_CLK) & 0xffffffffffL;
            long tracr_clk = 0;
            buf.order(ByteOrder.BIG_ENDIAN);
            for (int i = 0; i < 6; i++)
                tracr_clk = (tracr_clk << 8) | ((int) buf.get(EVT_TRACR_CLK + i) & 0xff);
            tracr_clk = tracr_clk & 0xffffffffffL;
            buf.order(ByteOrder.LITTLE_ENDIAN);
            utc = rapcal.domToUTC(2 * tracr_clk + tracr_clock_offset).in_0_1ns();
            if (logger.isDebugEnabled())
            {
                logger.debug("DOMClk: " + domclk + " TracrMatchClk: " + tracr_match_clk
                        + " TracrClk: " + tracr_clk + " - UTC: " + utc);
                logger.debug("Offset (.1ns): " + (rapcal.domToUTC(domclk).in_0_1ns() - utc));
            }
        }
        else
        {
            utc = rapcal.domToUTC(domclk).in_0_1ns();
            if (logger.isDebugEnabled())
                logger.debug("No tracr clock (beacon?): DOMClk: " + domclk + " - UTC: " + utc);
        }
        ByteBuffer xtb = ByteBuffer.allocate(xtbSize);
        xtb.putInt(xtbSize);
        xtb.putInt(evtCnt++);
        xtb.putLong(mbid_numerique);
        xtb.putLong(domclk);
        xtb.putLong(utc);
        xtb.put(buf);
        xtb.rewind();
        try
        {
            if (logger.isDebugEnabled())
                logger.debug("Sending buffer of size " + xtb.remaining());
            hits.consume(xtb);
        }
        catch (IOException iox)
        {
            logger.error("Caught IOException: " + iox.getLocalizedMessage());
        }
    }

    public String getMainboardId()
    {
        return this.mbid;
    }

    public void setPowerLevel(int value)
    {
        this.powerControlBits = value;
    }

    public void setTriggerLogic(int value)
    {
        this.triggerSetting = value;
        changeTriggerSetting.set(true);
    }

    public void setForcedTriggers(boolean enabled)
    {
        forcedTrigger.set(enabled);
    }

    public void setRadioTriggers(boolean enabled)
    {
        radioTrigger.set(enabled);
    }

    public void setRadioDACs(short[][] dacs)
    {
        this.radioDACs = dacs;
    }

    public void setTCalExceptionAlerter(TCalExceptionAlerter alerter)
    {
        rapcal.setMoni(alerter);
    }

    @Override
    public void close()
    {
        drm.close();
    }

    @Override
    public long getAcquisitionLoopCount()
    {
        return 0;
    }

    @Override
    public long getNumHits()
    {
        return 0;
    }

    @Override
    public long getNumMoni()
    {
        return 0;
    }

    @Override
    public long getNumSupernova()
    {
        return 0;
    }

    @Override
    public long getNumTcal()
    {
        return 0;
    }

    @Override
    public void signalShutdown()
    {
        running.set(false);

    }

    private class TCALTask extends TimerTask
    {
        private UTC gpsOffset;

        TCALTask()
        {
            gpsOffset = new UTC();
        }

        @Override
        public void run()
        {
            try
            {
                try
                {
                    GPSInfo gps = driver.readGPS(gpsFile);
                    gpsOffset = gps.getOffset();
                }
                catch (GPSException gpsx)
                {
                    logger.warn("GPS exception");
                }
                TimeCalib tcal = driver.readTCAL(tcalFile);
                synchronized (rapcal)
                {
                    rapcal.update(tcal, gpsOffset);
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            catch (RAPCalException rcx)
            {
                rcx.printStackTrace();
            }

        }

    }

}
