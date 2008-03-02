package icecube.daq.bindery;


import java.io.IOException;
import java.nio.ByteBuffer;
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
    private long[] channelIds;
    private long lastUT;
    private final int ngen;
    private final int nch;
    private final static Logger logger = Logger.getLogger(MultiChannelMergeSortTest.class);
    
    public MultiChannelMergeSortTest()
    {
        this(
                Integer.getInteger("icecube.daq.bindery.MultiChannelMergeSortTest.channels", 16),
                Integer.getInteger("icecube.daq.bindery.MultiChannelMergeSortTest.gen", 1000000)
            );
    }
    
    public MultiChannelMergeSortTest(int nch, int ngen)
    {
        this.nch = nch;
        this.ngen = ngen;
        channelIds = new long[nch];
        for (int ch = 0; ch < nch; ch++) channelIds[ch] = 1000 * ch;
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
        for (int ch = 0; ch < channelIds.length; ch++) mms.register(channelIds[ch]);
        mms.start();
        timeOrdered = true;
        numBuffersSeen = 0;
        lastUT = 0;
    }

    @Test
    public void testTimeOrdering() throws Exception
    {
        BufferGenerator[] genArr = new BufferGenerator[nch];
        Random die = new Random();
        int nch = channelIds.length;
        
        for (int ch = 0; ch < nch; ch++)
        {
            genArr[ch] = new BufferGenerator(channelIds[ch], die.nextInt(50), ngen, mms);
            genArr[ch].start();
        }

        mms.join();
        
        assertTrue(timeOrdered);
        assertEquals(nch * ngen + 1, numBuffersSeen);
        
    }
    
    public void consume(ByteBuffer buf) throws IOException
    {
        long utc = buf.getLong(24);
        if (lastUT > utc) timeOrdered = false;
        numBuffersSeen++;
        if (numBuffersSeen % 10000 == 0) logger.info("# buffers: " + numBuffersSeen);
    }
    
    public static void main(String[] args) throws Exception
    {
        loggingSetUp();
        int nch = 16;
        int ngen = 100000;
        if (args.length > 0) nch = Integer.parseInt(args[0]);
        if (args.length > 1) ngen = Integer.parseInt(args[1]);
        MultiChannelMergeSortTest mcmt = new MultiChannelMergeSortTest(nch, ngen);
        mcmt.setUp();
        mcmt.testTimeOrdering();
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
    private long t;
    private long mbid;
    private Random rand;
    private int ngen;
    private BufferConsumer consumer;
    private static final Logger logger = Logger.getLogger(BufferGenerator.class);
    
    BufferGenerator(long mbid, long t0, int n, BufferConsumer consumer)
    {
        t = t0;
        this.mbid = mbid;
        rand = new Random();
        this.consumer = consumer;
        ngen = n;
    }
    
    ByteBuffer next()
    {
        t += (rand.nextInt(20)+1);
        ByteBuffer buf = ByteBuffer.allocate(40);
        buf.putInt(40);
        buf.putInt(15071);
        buf.putLong(mbid);
        buf.putLong(0L);
        buf.putLong(t);
        buf.putLong(0x123456789aL);
        return (ByteBuffer) buf.flip();
    }
    
    public void run()
    {
        logger.debug(String.format("Starting run thread of buffer generator %012x", mbid));
        try
        {
            for (int loop = 0; loop < ngen; loop++)
            {
                consumer.consume(next());
                if (rand.nextDouble() < 0.0025) Thread.sleep(40);
            }
            consumer.consume(eos());
            logger.debug(String.format("Wrote eos for %012x", mbid));
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
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