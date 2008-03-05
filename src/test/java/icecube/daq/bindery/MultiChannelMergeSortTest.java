package icecube.daq.bindery;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class MultiChannelMergeSortTest implements BufferConsumer
{

    private MultiChannelMergeSort mms; 
    private boolean timeOrdered;
    private int numBuffersSeen;
    private long lastUT;
    private final double rate;
    private final int    nch;
    private final static Logger logger = Logger.getLogger(MultiChannelMergeSortTest.class);
    
    public MultiChannelMergeSortTest()
    {
        this(
                Integer.getInteger("icecube.daq.bindery.MultiChannelMergeSortTest.channels", 16),
                500.0
            );
    }
    
    public MultiChannelMergeSortTest(int nch, double rate)
    {
        this.nch  = nch;
        this.rate = rate;
    }
    
    @BeforeClass
    public static void loggingSetUp()
    {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
    }

    @Before
    public void setUp() throws Exception
    {
        mms = new MultiChannelMergeSort(nch, this);
        for (int ch = 0; ch < nch; ch++) mms.register(ch);
        mms.start();
        numBuffersSeen = 0;
        lastUT = 0;
        timeOrdered = true;
    }

    @Test
    public void testTimeOrdering() throws Exception
    {
        BufferGenerator[] genArr = new BufferGenerator[nch];

        for (int ch = 0; ch < nch; ch++)
        {
            genArr[ch] = new BufferGenerator(ch, rate, mms);
            genArr[ch].start();
        }

        for (int iMoni = 0; iMoni < 100; iMoni++)
        {
            Thread.sleep(3000L);
            logger.info(
                    "MMS in: " + mms.getNumberOfInputs() + 
                    " out: " + mms.getNumberOfOutputs() +
                    " queue size " + mms.getQueueSize()
                    );
        }
        
        for (int ch = 0; ch < nch; ch++) genArr[ch].signalStop();
        mms.join();
        
        assertTrue(timeOrdered);
    }
    
    public void consume(ByteBuffer buf) throws IOException
    {
        long utc = buf.getLong(24);
        if (lastUT > utc) timeOrdered = false;
        numBuffersSeen++;
        if (numBuffersSeen % 100000 == 0) logger.info("# buffers: " + numBuffersSeen);
    }
    
    public static void main(String[] args) throws Exception
    {
        loggingSetUp();
        int nch = 16;
        double rate = 500.0;
        if (args.length > 0) nch = Integer.parseInt(args[0]);
        if (args.length > 1) rate = Double.parseDouble(args[1]);
        MultiChannelMergeSortTest mcmt = new MultiChannelMergeSortTest(nch, rate);
        mcmt.setUp();
        mcmt.testTimeOrdering();
        System.gc();
        System.out.println("Number of buffers out: " + mcmt.numBuffersSeen);
                
    }
    
}

/**
 * Generates a sequence of ordered byte buffers
 * @author kael
 *
 */
class BufferGenerator extends Thread
{
    private long mbid;
    private long lastTime;
    private double rate;
    private Random rand;
    private BufferConsumer consumer;
    private static final Logger logger = Logger.getLogger(BufferGenerator.class);
    private volatile boolean run;
    
    BufferGenerator(long mbid, double rate, BufferConsumer consumer)
    {
        this.mbid = mbid;
        this.rate = rate;
        rand = new Random();
        this.consumer = consumer;
        this.lastTime = System.nanoTime();
        run = false;
    }
    
    public synchronized void signalStop()
    {
        run = false;
    }
    
    public synchronized boolean isRunning()
    {
        return run;
    }
    
    public void run()
    {
        synchronized (this) { run = true; }
        logger.info("Starting run thread of buffer generator " + mbid);
        try
        {
            while (isRunning() && !interrupted())
            {
                long curTime  = System.nanoTime();
                double deltaT = 1.0E-09 * (curTime - lastTime);
                int ngen = (int) (rate * deltaT);
                ArrayList<Long> times = new ArrayList<Long>(ngen);
                for (int i = 0; i < ngen; i++)
                {
                    double t = rand.nextDouble() * deltaT;
                    long  it = lastTime + (long) (t * 1.0E+09);
                    times.add(it);
                }
                lastTime = curTime;
                Collections.sort(times);
                for (int loop = 0; loop < ngen; loop++)
                {
                    ByteBuffer buf = ByteBuffer.allocate(40);
                    buf.putInt(40);
                    buf.putInt(0x1734);
                    buf.putLong(mbid);
                    buf.putLong(0L);
                    buf.putLong(times.get(loop));
                    buf.putInt(1);
                    buf.putInt(2);
                    consumer.consume((ByteBuffer) buf.flip());
                }
                Thread.sleep(50);
            }
            consumer.consume(eos());
            logger.debug(String.format("Wrote eos for %012x", mbid));
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        logger.info("Buffer generator thread " + mbid + " exiting.");
    }
    
    ByteBuffer eos()
    {
        ByteBuffer buf = ByteBuffer.allocate(32);
        buf.putInt(32);
        buf.putInt(15071);
        buf.putLong(mbid);
        buf.putLong(0L);
        buf.putLong(Long.MAX_VALUE);
        return (ByteBuffer) buf.flip();
    }
}