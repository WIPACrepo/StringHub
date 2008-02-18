package icecube.daq.bindery;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MultiChannelMergeSortTest implements BufferConsumer
{

    private MultiChannelMergeSort mms; 
    private boolean timeOrdered;
    private int numBuffersSeen;
    private static long[] channelIds = new long[] { 0x1234, 0x4321, 0x3412, 0x2143 };
    private long lastUT;
    private final int NGEN = 10000;
    
    @BeforeClass
    public static void loggingSetUp()
    {
        Logger.getRootLogger().setLevel(Level.INFO);
    }

    @Before
    public void setUp() throws Exception
    {
        mms = new MultiChannelMergeSort(4, this);
        for (int ch = 0; ch < channelIds.length; ch++) mms.register(channelIds[ch]);
        mms.start();
        timeOrdered = true;
        numBuffersSeen = 0;
        lastUT = 0;
    }

    @Test
    public void testTimeOrdering() throws Exception
    {
        BufferGenerator[] genArr = new BufferGenerator[4];
        Random die = new Random();
        int nch = channelIds.length;
        
        for (int ch = 0; ch < nch; ch++)
        {
            genArr[ch] = new BufferGenerator(channelIds[ch], die.nextInt(50), NGEN, mms);
            genArr[ch].start();
        }

        // Wait on spawned threads
        // for (int ch = 0; ch < nch; ch++) genArr[ch].join();
        mms.join();
        
        assertTrue(timeOrdered);
        assertEquals(nch * NGEN + 1, numBuffersSeen);
        
    }
    
    public void consume(ByteBuffer buf) throws IOException
    {
        long utc = buf.getLong(24);
        if (lastUT > utc) timeOrdered = false;
        numBuffersSeen++;
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
        t += rand.nextInt(10);
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