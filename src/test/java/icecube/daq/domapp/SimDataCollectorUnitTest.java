package icecube.daq.domapp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import icecube.daq.dor.IDriver;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.bindery.BufferConsumer;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;



public class SimDataCollectorUnitTest implements BufferConsumer
{
    private IDriver driver;
    AbstractDataCollector dc;
    LinkedBlockingQueue<ByteBuffer> q;

    public SimDataCollectorUnitTest()
    {
        q = new LinkedBlockingQueue<ByteBuffer>(1000);
    }

    @BeforeClass
    public static void setupLogging()
    {
        // exercise logging calls, but output to nowhere
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(new NullAppender());
        Logger.getRootLogger().setLevel(Level.ALL);
    }

    @AfterClass
    public static void tearDownLogging()
    {
        BasicConfigurator.resetConfiguration();
    }

    @Before
    public void setUp() throws InterruptedException
    {
        DOMChannelInfo chan = new DOMChannelInfo("056a7bb14cde", 0, 1, 'B');
        DOMConfiguration config = new DOMConfiguration();
        config.setSimNoiseRate(1000.0);
        config.setSimHLCFrac(0.25);
        q.clear();
        dc = new SimDataCollector(chan, config, this, null, null, null, false);
        dc.signalConfigure();
        while (!dc.isConfigured()) Thread.sleep(50);
        dc.signalStartRun();
        while (!dc.isRunning()) Thread.sleep(50);
        Thread.sleep(500);
        dc.signalStopRun();
        while (!dc.isConfigured()) Thread.sleep(50);
        dc.signalShutdown();
    }

    @Override
    public void consume(ByteBuffer buf) throws IOException
    {
        try
        {
            q.put(buf);
        }
        catch (InterruptedException intx)
        {
            // ignore interrupts
        }
    }

    @Override
    public void endOfStream(long mbid)
    {
        throw new Error("Only used by PrioritySort");
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
