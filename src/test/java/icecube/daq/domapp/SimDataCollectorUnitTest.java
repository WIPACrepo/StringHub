package icecube.daq.domapp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.dor.DOMChannelInfo;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class SimDataCollectorUnitTest implements BufferConsumer
{
    AbstractDataCollector dc;
    LinkedBlockingQueue<ByteBuffer> q;
    
    public SimDataCollectorUnitTest()
    {
        q = new LinkedBlockingQueue<ByteBuffer>(1000);
    }
    
    @BeforeClass
    public static void setupLogging()
    {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
    }
    
    @Before
    public void setUp() throws InterruptedException
    {
        DOMChannelInfo chan = new DOMChannelInfo("056a7bb14cde", 0, 1, 'B');
        DOMConfiguration config = new DOMConfiguration();
        config.setSimNoiseRate(1000.0);
        config.setSimHLCFrac(0.25);
        q.clear();
        dc = new SimDataCollector(chan, config, this, null, null, null);
        dc.signalConfigure();
        while (!dc.isConfigured()) Thread.sleep(50);
        dc.signalStartRun();
        while (!dc.isRunning()) Thread.sleep(50);
        Thread.sleep(500);
        dc.signalStopRun();
        while (!dc.isConfigured()) Thread.sleep(50);
        dc.signalShutdown();
    }

    public void consume(ByteBuffer buf) throws IOException
    {
        try
        {
            q.put(buf);
        }
        catch (InterruptedException intx)
        {
        
        }
    }
    
    @Test
    public void testLCSim() throws Exception
    {
        boolean sawHLC = false, sawSLC = false;
        while (true)
        {
            ByteBuffer buf = q.take();
            if (buf.getInt(0) == 32 && buf.getLong(24) == Long.MAX_VALUE) break;
            boolean isHLC = (buf.getInt(46) & 0x30000) != 0;
            if (isHLC) sawHLC = true;
            if (!isHLC) sawSLC = true;
        }
        assertTrue(sawHLC);
        assertTrue(sawSLC);
    }
}
