package icecube.daq.bindery;

import icecube.daq.common.MockAppender;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.*;

/**
 * Tests Buffer ConsumerAsync.java
 */
public class BufferConsumerAsyncTest
{
    private static MockAppender appender;
    MockConsumer mockConsumer;
    BufferConsumerAsync subject;
    private final int NUM_TEST_BUFFERS = 500000;
    private final int MAX_BUFFERS = NUM_TEST_BUFFERS+1;

    // Set huge to support automated testing on sluggish virtual machine.
    public static final int NOMINAL_SYNC_MILLIS = 5000;

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
    public void setUp()
    {
        BasicConfigurator.resetConfiguration();
        appender = new MockAppender(Level.INFO);
        BasicConfigurator.configure(appender);

       mockConsumer = new MockConsumer();
       subject = new BufferConsumerAsync(mockConsumer, MAX_BUFFERS,
               BufferConsumerAsync.QueueFullPolicy.Block, "test-channel");
    }

    @After
    public void tearDown() throws Exception
    {
        if(!subject.join(100))
        {
            subject.endOfStream(0L);
            subject.join(Long.MAX_VALUE);
        }

        BasicConfigurator.resetConfiguration();
    }

    @Test
    public void testStartStop()
    {
       /// Test setUp/tearDown in isolation
    }


    @Test
    public void testDelivery() throws Exception
    {
        // Test flow of buffers through the consumer

        for(int i=0; i< NUM_TEST_BUFFERS; i++)
        {
            subject.consume(ByteBuffer.allocate(128));
        }

        int spinCount = 0;
        while (mockConsumer.numBuffersDelivered < NUM_TEST_BUFFERS)
        {
            if(spinCount++ > 50)
            try{ Thread.sleep(200);} catch (InterruptedException e){}
        }

        assertEquals("Wrong number of buffers delivered", NUM_TEST_BUFFERS,
                mockConsumer.numBuffersDelivered);

        subject.endOfStream(0);
        subject.join();
    }

    @Test
    public void testSync() throws Exception
    {
        /// Test syncing the queue

        mockConsumer.consumptionLock.lock();

        ByteBuffer marker = ByteBuffer.allocateDirect(128);
        for(int i=0; i< 1000; i++)
        {
            subject.consume(ByteBuffer.allocate(128));
        }
        subject.consume(marker);

        assertTrue("Lock failed", mockConsumer.lastBuffer != marker);

        try
        {
            subject.sync(NOMINAL_SYNC_MILLIS);
            fail("Sync did not timeout");
        }
        catch (IOException ex)
        {
            // expected
        }

        mockConsumer.consumptionLock.unlock();

        subject.sync(NOMINAL_SYNC_MILLIS);

        assertTrue("Did not sync to marker", mockConsumer.lastBuffer == marker);


        subject.endOfStream(0);

        subject.join();
    }


    @Test
    public void testLimitRejectPolicy() throws Exception
    {
        /// Test the queue limits under the rejection policy.

        // do away with the preloaded subject
        subject.endOfStream(-1);
        subject.join();

        mockConsumer = new MockConsumer();
        subject = new BufferConsumerAsync(mockConsumer, MAX_BUFFERS,
                BufferConsumerAsync.QueueFullPolicy.Reject,
                "test-channel");
        //

        mockConsumer.consumptionLock.lock();


        // sorta racey ... assuming the first buffer
        // makes it to the consumption lock
        subject.consume(ByteBuffer.allocate(128));
        try{ Thread.sleep(10);} catch (InterruptedException e){}
        for(int i=0; i< MAX_BUFFERS; i++)
        {
            subject.consume(ByteBuffer.allocate(128));
        }

        ByteBuffer marker = ByteBuffer.allocateDirect(128);
        try
        {
            subject.consume(marker);
            fail("Accepted Buffers beyond capacity");
        }
        catch (IOException e)
        {
            //desired, the queue is full
        }

        mockConsumer.consumptionLock.unlock();

        subject.sync(NOMINAL_SYNC_MILLIS);

        assertTrue("Marker should have been discarded",
                mockConsumer.lastBuffer != marker);

        assertEquals("", MAX_BUFFERS+1, mockConsumer.numBuffersDelivered);

        subject.endOfStream(0);

        subject.join();

    }

    @Test
    public void testLimitBlockingPolicy() throws Exception
    {
        /// Test the queue limits under the blcocking policy

        mockConsumer.consumptionLock.lock();


        // sorta racey ... assuming the first buffer
        // makes it to the consumption lock
        subject.consume(ByteBuffer.allocate(128));
        try{ Thread.sleep(10);} catch (InterruptedException e){}
        for(int i=0; i< MAX_BUFFERS; i++)
        {
            subject.consume(ByteBuffer.allocate(128));
        }

        final ByteBuffer marker = ByteBuffer.allocateDirect(128);

        // under the blocking policy, the next consume()
        // call will block until the mock is unlocked
        final CountDownLatch startLatch = new CountDownLatch(1);
        final AtomicLong blockDelay = new AtomicLong(0);
        Thread submitThread = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    long start = System.nanoTime();
                    startLatch.countDown();
                    subject.consume(marker);
                    long stop = System.nanoTime();
                    blockDelay.set(stop-start);
                }
                catch (IOException e)
                {
                    // should fail test, but can't fail off
                    // main thread, detect by detecting the
                    // marker later
                    e.printStackTrace(System.out);
                }
            }
        };
        submitThread.start();

        // ensure submitter is good and blocked
        startLatch.await();
        long before = System.nanoTime();
        try{ Thread.sleep(1000);} catch (InterruptedException e){}
        long after = System.nanoTime();

        mockConsumer.consumptionLock.unlock();

        long minDelay = after - before;
        submitThread.join();
        assertTrue("Should have blocked a " + minDelay + " instead of " +
                blockDelay.get(), blockDelay.get() > minDelay);

        subject.sync(NOMINAL_SYNC_MILLIS);

        assertTrue("Marker should have been delivered",
                mockConsumer.lastBuffer == marker);

        assertEquals("", MAX_BUFFERS+2, mockConsumer.numBuffersDelivered);

        subject.endOfStream(0);

        subject.join();

    }


    @Test
    public void testEndOfStream() throws Exception
    {
        /// Test end of stream

        ByteBuffer marker = ByteBuffer.allocate(128);
        subject.consume(marker);

        subject.endOfStream(0);
        subject.join();

        assertTrue("Did not call EOS", mockConsumer.endOfStreamCalled);
        assertTrue("Did not deliver marker",
                mockConsumer.lastBuffer == marker);
    }

    @Test
    public void testErrorOnConsumer() throws Exception
    {
        /// Test an error from consumer

        ByteBuffer marker = ByteBuffer.allocate(128);
        subject.consume(marker);
        subject.sync(NOMINAL_SYNC_MILLIS);

        mockConsumer.errorOnConsume = true;

        subject.consume(ByteBuffer.allocate(128));

        assertTrue("Error did not cause shutdown", subject.join(1000));
        assertTrue("Mock should have marker",
                mockConsumer.lastBuffer == marker);

        assertEquals("Did not Log error",
                "Async consumer encountered an error, abandoning 0 buffers",
                appender.getMessage(appender.getNumberOfMessages()-1));

        try
        {
            subject.consume(ByteBuffer.allocate(128));
            fail("Accepted buffers after error");
        }
        catch(IOException io)
        {
            // desired
        }
    }

    @Test
    public void testErrorOnEOS() throws Exception
    {
        /// Test an EOS error from consumer

        mockConsumer.errorOnEOS = true;

        subject.consume(ByteBuffer.allocate(128));

        ByteBuffer marker = ByteBuffer.allocate(128);
        subject.consume(marker);

        subject.endOfStream(0);


        assertTrue("Error did not cause shutdown", subject.join(1000));
        assertTrue("Mock should have marker",
                mockConsumer.lastBuffer == marker);

        assertFalse("Mock did not generate error",
                mockConsumer.endOfStreamCalled);

        assertEquals("Did not Log error",
                "Async consumer encountered an error, abandoning 0 buffers",
                appender.getMessage(appender.getNumberOfMessages()-1));

        try
        {
            subject.consume(ByteBuffer.allocate(128));
            fail("Accepted buffers after error");
        }
        catch(IOException io)
        {
            // desired
        }
    }

    @Test
    public void testJoin() throws Exception
    {

        /// Test join timeout

        ByteBuffer marker = ByteBuffer.allocate(128);
        subject.consume(marker);

        boolean finished = subject.join(200);
        assertFalse("Join failed", finished);

        subject.endOfStream(0);
        subject.join();
    }

    @Test
    public void testUseAfterEOS() throws Exception
    {
        /// Test usage after stopping

        mockConsumer.consumptionLock.lock();
        subject.consume(ByteBuffer.allocate(128));
        subject.consume(ByteBuffer.allocate(128));
        subject.consume(ByteBuffer.allocate(128));

        ByteBuffer marker = ByteBuffer.allocateDirect(128);
        subject.consume(marker);

        subject.endOfStream(0);

        try
        {
            subject.consume(ByteBuffer.allocate(128));
            fail("Accepted Buffers after EOS");
        }
        catch (IOException e)
        {
            //desired, subject is shutdown
        }

        try
        {
            subject.sync(100);
            fail("Accepted sync after EOS");
        }
        catch (IOException e)
        {
            //desired, subject is shutdown
        }

        try
        {
            subject.endOfStream(0);
            fail("Accepted EOS after EOS");
        }
        catch (IOException e)
        {
            //desired, subject is shutdown
        }

        mockConsumer.consumptionLock.unlock();

        subject.join();

        assertTrue("Marker not delivered",
                mockConsumer.lastBuffer == marker);
    }


    class MockConsumer implements BufferConsumer
    {
        long numBuffersDelivered;
        boolean endOfStreamCalled;

        boolean errorOnConsume;
        boolean errorOnEOS;

        Lock consumptionLock = new ReentrantLock();

        ByteBuffer lastBuffer;

        @Override
        public void consume(final ByteBuffer buf) throws IOException
        {
            try
            {
                consumptionLock.lock();

                if(errorOnConsume)
                {
                    throw new Error("Test Error");
                }
                numBuffersDelivered++;
                lastBuffer = buf;
            }
            finally
            {
                consumptionLock.unlock();
            }
        }

        @Override
        public void endOfStream(final long token) throws IOException
        {
            if(errorOnEOS)
            {
                throw new Error("Test Error");
            }
            endOfStreamCalled = true;
        }
    }

}
