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

public class PrioritySortTest implements BufferConsumer
{
    private double rate;
    private int    nch;

    private PrioritySort prio;
    private boolean timeOrdered;
    private int numBuffersSeen;
    private long lastUT;
    private final static Logger logger =
        Logger.getLogger(PrioritySortTest.class);

    public PrioritySortTest()
    {
        final String prop =
            "icecube.daq.bindery.PrioritySortTest.channels";
        nch = Integer.getInteger(prop, 16);
        rate = 500.0;
    }

    void setNumChannels(int val)
    {
        nch = val;
    }

    void setRate(double val)
    {
        rate = val;
    }

    @BeforeClass
    public static void loggingSetUp()
    {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.WARN);
    }

    @Before
    public void setUp() throws Exception
    {
        prio = new PrioritySort("Test", nch, this);
        for (int ch = 0; ch < nch; ch++) prio.register(ch);
        prio.start();
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
            genArr[ch] = new BufferGenerator(ch, rate, prio);
            genArr[ch].start();
        }

        for (int iMoni = 0; iMoni < 10; iMoni++)
        {
            Thread.sleep(1000L);
            if (logger.isInfoEnabled()) {
                logger.info(
                        "PRIO in: " + prio.getNumberOfInputs() +
                        " out: " + prio.getNumberOfOutputs() +
                        " queue size " + prio.getQueueSize()
                        );
            }
        }

        for (int ch = 0; ch < nch; ch++) genArr[ch].signalStop();
        prio.join();

        assertTrue(timeOrdered);
    }

    public void consume(ByteBuffer buf) throws IOException
    {
        long utc = buf.getLong(24);
        if (lastUT > utc) timeOrdered = false;
        numBuffersSeen++;
        if (numBuffersSeen % 100000 == 0 && logger.isInfoEnabled()) logger.info("# buffers: " + numBuffersSeen);
    }

    /**
     * There will be no more data.
     */
    public void endOfStream(long mbid)
        throws IOException
    {
        System.err.println("Saw end-of-stream!");
    }

    public static void main(String[] args) throws Exception
    {
        loggingSetUp();
        int nch = 16;
        double rate = 500.0;
        if (args.length > 0) nch = Integer.parseInt(args[0]);
        if (args.length > 1) rate = Double.parseDouble(args[1]);
        PrioritySortTest mcmt = new PrioritySortTest();
        mcmt.setNumChannels(nch);
        mcmt.setRate(rate);
        mcmt.setUp();
        mcmt.testTimeOrdering();
        System.gc();
        System.out.println("Number of buffers out: " + mcmt.numBuffersSeen);

    }
}
