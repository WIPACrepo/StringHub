package icecube.daq.bindery;

import icecube.daq.common.MockAppender;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static junit.framework.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests common to ChannelSorters.java implementations.
 *
 * Usage: Subclass and implement createSorter() method and policy methods.
 */
public abstract class AbstractChannelSorterTest
{
    private double rate;
    protected int    nch;

    protected ChannelSorter mms;
    private OutOfOrderHitPolicy outOfOrderHitPolicy;
    private UnknownMBIDPolicy unknownMBIDPolicy;
    private TimestampTrackingPolicy timestampTrackingPolicy;

    private MockConsumer mockConsumer;

    MockAppender appender = new MockAppender();

    private final static Logger logger = Logger.getLogger(MultiChannelMergeSortTest.class);


    public AbstractChannelSorterTest()
    {
        nch = 16;
        rate = 500.0;
    }

    public abstract ChannelSorter createTestSubject(BufferConsumer consumer);

    enum TimestampTrackingPolicy
    {
        TRACKED,
        NOT_TRACKED
    }
    public abstract TimestampTrackingPolicy getTimestampTrackingPolicy();


    enum OutOfOrderHitPolicy
    {
        DROP,
        DELIVER
    }
    public abstract OutOfOrderHitPolicy getOutOfOrderHitPolicy();

    enum UnknownMBIDPolicy
    {
        EXCEPTION,
        LOGGED
    }
    public abstract UnknownMBIDPolicy getUnknownMBIDPolicy();

    @Before
    public void setUp() throws Exception
    {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);

        outOfOrderHitPolicy = getOutOfOrderHitPolicy();
        unknownMBIDPolicy = getUnknownMBIDPolicy();
        timestampTrackingPolicy = getTimestampTrackingPolicy();

        mockConsumer = new MockConsumer();

        mms = createTestSubject(mockConsumer);

        for (int ch = 0; ch < nch; ch++) mms.register(ch);
        mms.start();
    }

    @After
    public void tearDown() throws Exception
    {
        // ensure end of stream regardless of test exit point
        for (int ch = 0; ch < nch; ch++)
        {
            try
            {
                mms.endOfStream(ch);
            }
            catch (IOException e)
            {
                // PrioritySort does not allow duplicate end-of-stream
                // MultiChannelMergeSorter allows it.
            }
        }

        try {
            mms.join(10000);
        } catch (InterruptedException ie) {
            // ignore interrupts
        }
        assertFalse(mms.toString() + " thread never finished", mms.isRunning());

        // XXX throw away all log messages
        appender.clear();

        BasicConfigurator.resetConfiguration();
    }

    @Test
    public void testSetupAndTearDown()
    {
        // tests the setUp()/tearDown() methods in isolation
    }

    @Test
    public void testTimeOrdering() throws Exception
    {
        ///  Tests sorting

        BufferGenerator[] genArr = new BufferGenerator[nch];
        CountingConsumer countDecorator = new CountingConsumer(mms);

        for (int ch = 0; ch < nch; ch++)
        {
            genArr[ch] = new BufferGenerator(ch, rate, countDecorator);
            genArr[ch].start();
        }

        for (int iMoni = 0; iMoni < 10; iMoni++)
        {
            Thread.sleep(1000L);
            if (logger.isInfoEnabled()) {
                logger.info(
                        "MMS in: " + mms.getNumberOfInputs() +
                                " out: " + mms.getNumberOfOutputs() +
                                " queue size " + mms.getQueueSize()
                );
            }
        }

        for (int ch = 0; ch < nch; ch++) genArr[ch].signalStop();
        for (int ch = 0; ch < nch; ch++) genArr[ch].join();
        mms.join(1000);
        assertFalse(mms.toString() + " thread never finished", mms.isRunning());

        logger.info(
                "MMS in: " + mms.getNumberOfInputs() +
                        " out: " + mms.getNumberOfOutputs() +
                        " queue size " + mms.getQueueSize()
        );
        assertTrue("Stream not time-ordered", mockConsumer.timeOrdered);
        assertTrue("endOfStream() not called", mockConsumer.EOSCalled);
        assertEquals("Inputs micounted", countDecorator.count, mms.getNumberOfInputs());
        assertEquals("Outputs miscounted", countDecorator.count-nch, mms.getNumberOfOutputs());
        assertEquals("Outputs not delivered", mockConsumer.numBuffersSeen, mms.getNumberOfOutputs());


        assertEquals("Queue not empty", 0, mms.getQueueSize());

        switch (timestampTrackingPolicy)
        {
            case TRACKED:
                assertEquals("Wrong last InputTime", Long.MAX_VALUE, mms.getLastInputTime());
                assertEquals("Wrong last OutputTime", Long.MAX_VALUE, mms.getLastOutputTime());
                break;
            case NOT_TRACKED:
                assertEquals("Wrong last InputTime", 0, mms.getLastInputTime());
                assertEquals("Wrong last OutputTime", 0, mms.getLastOutputTime());
                break;
            default:
                fail();
        }
    }

    @Test
    public void testDebug() throws Exception
    {
        /// Test running in debug logging mode - mainly for code coverage

        Logger.getRootLogger().setLevel(Level.DEBUG);

        BufferGenerator[] generators = new BufferGenerator[nch];
        for(int ch = 0; ch < nch; ch++)
        {
            generators[ch] = new BufferGenerator(ch,1000, mms);
            generators[ch].start();
        }

        try{ Thread.sleep(1000);} catch (InterruptedException e){}

        for(int ch = 0; ch < nch; ch++)
        {
            generators[ch].signalStop();
        }
        for(int ch = 0; ch < nch; ch++)
        {
            generators[ch].join();
        }

        mms.join(1000);
        assertFalse(mms.toString() + " thread never finished", mms.isRunning());

        assertTrue("Sorter Did not run in debug", mockConsumer.numBuffersSeen > 0);
    }

    @Test
    public void testOutOfOrder() throws Exception
    {
        /// Test out of order hits

        appender.setLevel(Level.WARN);


        for(int ch = 0; ch < nch; ch++)
        {
            mms.consume(BufferGenerator.generateBuffer(ch, 1000 + ch));
        }

        // these will be dropped and logged
        for(int ch = 0; ch < nch; ch++)
        {
            mms.consume(BufferGenerator.generateBuffer(ch, 500 + ch));
        }

        for(int ch = 0; ch < nch; ch++)
        {
            mms.consume(MultiChannelMergeSort.eos(ch));
        }

        mms.join(1000);
        assertFalse(mms.toString() + " thread never finished", mms.isRunning());

        assertEquals("Missing Inputs", 3 * nch, mms.getNumberOfInputs());
        assertEquals("Wrong Outputs", 2 * nch, mms.getNumberOfOutputs());

        switch (outOfOrderHitPolicy)
        {
            case DELIVER:
                assertEquals("Missing Hits", 2 * nch, mockConsumer.numBuffersSeen);
                assertFalse(mockConsumer.timeOrdered);
                break;
            case DROP:
                assertEquals("Missing Hits", nch, mockConsumer.numBuffersSeen);
                assertTrue(mockConsumer.timeOrdered);
                break;
            default:
                fail();

        }

        assertEquals("Out-of-Order not logged", nch, appender.getNumberOfMessages());

    }


    @Test
    public void testUnregisteredMbid() throws Exception
    {
        // Test hits from an unregistered mbid

        appender.setLevel(Level.ERROR);

        for(int ch = 0; ch < nch; ch++)
        {
            mms.consume(BufferGenerator.generateBuffer(ch, 1000 + ch));
        }


        boolean expectedException = false;
        int expectedLogCount = 1;

        switch (unknownMBIDPolicy)
        {
            case EXCEPTION:
                expectedLogCount = 0;
                expectedException = true;
                break;
            case LOGGED:
                expectedLogCount = 1;
                expectedException = false;
                break;
            default:
                fail();
        }

        try
        {
            mms.consume(BufferGenerator.generateBuffer(nch + 999, 1000 + nch));
        }
        catch (IOException e)
        {
          if(!expectedException){throw new Error(e);}
        }

        for(int ch = 0; ch < nch; ch++)
        {
            mms.consume(MultiChannelMergeSort.eos(ch));
        }

        mms.join(1000);
        assertFalse(mms.toString() + " thread never finished", mms.isRunning());

        assertEquals("Missing Inputs", 2 * nch, mms.getNumberOfInputs());
        assertEquals("Wrong Outputs", nch, mms.getNumberOfOutputs());

        assertEquals("bad mbid hit not logged", expectedLogCount, appender.getNumberOfMessages());

    }

    @Test
    public void testEndOfStream() throws Exception
    {
        // Test the end of stream method

        for(int ch = 0; ch < nch; ch++)
        {
            mms.consume(BufferGenerator.generateBuffer(ch, 1000 + ch));
        }

        for(int ch = 0; ch < nch; ch++)
        {
            mms.endOfStream(ch);
        }

        mms.join(1000);
        assertFalse(mms.toString() + " thread never finished", mms.isRunning());

        assertEquals("Missing Inputs", 2*nch, mms.getNumberOfInputs());
        assertEquals("Wrong Outputs", nch, mms.getNumberOfOutputs());
    }

    @Test
    public void testEndOfStreamMarkers() throws Exception
    {
        // Test eos markers delivered on all channels

        for(int ch = 0; ch < nch; ch++)
        {
            mms.consume(BufferGenerator.generateBuffer(ch, 1000 + ch));
        }

        for(int ch = 0; ch < nch; ch++)
        {
            mms.consume(BufferGenerator.generateBuffer(ch, Long.MAX_VALUE));
        }

        mms.join(1000);
        assertFalse(mms.toString() + " thread never finished", mms.isRunning());

        assertEquals("Missing Inputs", 2*nch, mms.getNumberOfInputs());
        assertEquals("Wrong Outputs", nch, mms.getNumberOfOutputs());
    }

    @Test
    public void testErrorInConsumer() throws Exception
    {
        /// Tests a consumer that generates an error
        appender.setLevel(Level.ERROR);


        for(int ch = 0; ch < nch; ch++)
        {
            mms.consume(BufferGenerator.generateBuffer(ch, 1000 + ch));
        }

        //catch up with sorter
        int spinCount=0;
        while(mockConsumer.numBuffersSeen<1)
        {
            if(spinCount++ > 100){fail("Stalled Sorter");}
            try{ Thread.sleep(20);} catch (InterruptedException e){}
        }

        // this should kill the sorter
        mockConsumer.throwErrorOnConsume = true;
        for(int ch = 0; ch < nch; ch++)
        {
            mms.consume(BufferGenerator.generateBuffer(ch, 1500 + ch));
        }

        mms.join(1000);
        assertFalse(mms.toString() + " thread never finished", mms.isRunning());

        assertEquals("Missing Inputs", nch+1, mms.getNumberOfInputs());
        assertEquals("Wrong Outputs", 1, mms.getNumberOfOutputs());

        assertEquals("Missing Hits", 1, mockConsumer.numBuffersSeen);

        assertEquals("Consumer not logged", 1, appender.getNumberOfMessages());

    }

    private class MockConsumer implements BufferConsumer
    {
        private boolean throwErrorOnConsume;
        private boolean timeOrdered;
        private int numBuffersSeen;
        private long lastUT;
        private boolean EOSCalled;

        public MockConsumer()
        {
            timeOrdered = true;
        }

        @Override
        public void consume(final ByteBuffer buf) throws IOException
        {
            if(throwErrorOnConsume)
            {
                throw new Error("Testing Error");
            }

            long utc = buf.getLong(24);
            if (lastUT > utc) timeOrdered = false;
            lastUT = utc;
            numBuffersSeen++;
            if (numBuffersSeen % 100000 == 0 && logger.isInfoEnabled()) logger.info("# buffers: " + numBuffersSeen);
        }

        @Override
        public void endOfStream(final long token) throws IOException
        {
            EOSCalled = true;
        }
    }
    private class CountingConsumer implements BufferConsumer
    {
        private final BufferConsumer delegate;

        volatile long count;

        private CountingConsumer(final BufferConsumer delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public void consume(final ByteBuffer buf) throws IOException
        {
            delegate.consume(buf);
            synchronized (this)
            {
                count++;
            }
        }

        @Override
        public void endOfStream(final long token) throws IOException
        {
            delegate.endOfStream(token);
        }
    }


}
