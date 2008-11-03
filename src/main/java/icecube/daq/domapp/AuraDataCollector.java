package icecube.daq.domapp;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.dor.Driver;
import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.TimeCalib;
import icecube.daq.rapcal.RAPCal;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.rapcal.ZeroCrossingRAPCal;
import icecube.daq.util.UTC;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

public class AuraDataCollector extends AbstractDataCollector
{
    private AuraDRM drm;
    private Driver driver;
    private RAPCal rapcal;
    private BufferConsumer hits;
    private AtomicBoolean running;
    private int powerControlBits;
    private static final Logger logger = Logger.getLogger(AuraDataCollector.class);
    
    public AuraDataCollector(int card, int pair, char dom, BufferConsumer hits)
    {
        super(card, pair, dom);
        driver = Driver.getInstance();
        rapcal = new ZeroCrossingRAPCal();
        this.hits = hits;
        running = new AtomicBoolean(false);
        powerControlBits = 0x3f;
    }
    
    public void run()
    {
        Timer t3 = new Timer();

        try
        {
            driver.softboot(card, pair, dom);
            drm = new AuraDRM(card, pair, dom);
            String mbid = drm.getMainboardId();
            long mbid_n = Long.parseLong(mbid, 16);
            
            // Load the DOMAPP FPGA image
            drm.sendCommand("s\" domapp.sbi.gz\" find if gunzip fpga endif");
            setRunLevel(RunLevel.IDLE);

            t3.schedule(new TCALTask(), 1000L, 1000L);

            running.set(true);
            while (running.get() && !interrupted())
            {
                switch (getRunLevel())
                {
                case CONFIGURING:
                    drm.powerOnFlasherboard();
                    Thread.sleep(5000);
                    drm.writeVirtualAddress(4, powerControlBits);
                    while (drm.readVirtualAddress(4) != powerControlBits) Thread.sleep(100);
                    setRunLevel(RunLevel.CONFIGURED);
                    break;
                case STARTING:
                    setRunLevel(RunLevel.RUNNING);
                    break;
                case RUNNING:
                    ByteBuffer buf = drm.forcedTrig(1);
                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    long domclk = buf.getLong(28);
                    long utc = rapcal.domToUTC(domclk).in_0_1ns();
                    ByteBuffer xtb = ByteBuffer.allocate(4886);
                    xtb.putInt(4886);
                    xtb.putInt(602);
                    xtb.putLong(mbid_n);
                    xtb.putLong(domclk);
                    xtb.putLong(utc);
                    xtb.put(buf);
                    xtb.rewind();
                    hits.consume(xtb);
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
        
        t3.cancel();
    }
    
    @Override
    public void close()
    {
        // TODO Auto-generated method stub

    }

    @Override
    public long getAcquisitionLoopCount()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long getNumHits()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long getNumMoni()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long getNumSupernova()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long getNumTcal()
    {
        // TODO Auto-generated method stub
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
            // TODO Auto-generated method stub
            try
            {
                try
                {
                    GPSInfo gps = driver.readGPS(card);
                    gpsOffset = gps.getOffset();
                }
                catch (GPSException gpsx)
                {
                    logger.warn("GPS exception");
                }
                TimeCalib tcal = driver.readTCAL(card, pair, dom);
                synchronized (rapcal)
                {
                    rapcal.update(tcal, gpsOffset);
                }
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            catch (InterruptedException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            catch (RAPCalException rcx)
            {
                rcx.printStackTrace();
            }
            
        }
        
    }

}
