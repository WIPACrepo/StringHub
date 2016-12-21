package icecube.daq.domapp.dataprocessor;

import icecube.daq.domapp.RunLevel;
import icecube.daq.domapp.dataprocessor.test.MockDataProcessor;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;

/**
 * Tests AsynchronousDataProcessor.java
 *
 * NOTE: Test designed for threaded processor.
 */
public class AsynchronousDataProcessorTest
{

    private ByteBuffer DUMMY = ByteBuffer.allocate(0);


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

    @Test
    public void testRunLevel() throws DataProcessorError
    {
        final MockDataProcessor mock = new MockDataProcessor();
        final AsynchronousDataProcessor subject =
                AsynchronousDataProcessor.singleThreadedExecutor("test",
                        mock);

        assertNull("", mock.runLevel);

        subject.runLevel(RunLevel.RUNNING);

        subject.sync();

        assertEquals("", RunLevel.RUNNING, mock.runLevel);

        subject.shutdown();

    }

    @Test
    public void testSync() throws DataProcessorError, InterruptedException
    {
        //
        // test syncing of data stream
        //
        final MockDataProcessor mock = new MockDataProcessor();
        final AsynchronousDataProcessor subject =
                AsynchronousDataProcessor.singleThreadedExecutor("test",
                        mock);

        mock.lock();


        subject.process(DataProcessor.StreamType.HIT, DUMMY);
        subject.process(DataProcessor.StreamType.HIT, DUMMY);
        subject.process(DataProcessor.StreamType.HIT, DUMMY);
        subject.process(DataProcessor.StreamType.HIT, DUMMY);
        subject.process(DataProcessor.StreamType.HIT, DUMMY);


        Thread delayed = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    assertEquals("", 0, mock.processCount);
                    subject.sync();
                    assertEquals("", 5, mock.processCount);
                }
                catch (DataProcessorError dataProcessorError)
                {
                    fail(dataProcessorError.getMessage());
                }

            }
        };
        delayed.start();
        try{ Thread.sleep(200);} catch (InterruptedException e){}

        mock.unlock();
        delayed.join();

        subject.shutdown();

    }

    @Test
    public void testFullRejectPolicy() throws DataProcessorError, InterruptedException
    {
        //
        // tests limit on processing queue when in "Reject" policy
        //
        final MockDataProcessor mock = new MockDataProcessor();
        final AsynchronousDataProcessor subject =
                AsynchronousDataProcessor.singleThreadedExecutor("test",
                        mock, AsynchronousDataProcessor.QueueFullPolicy.Reject);

        mock.lock();


        // this one will be out for processing
        subject.process(DataProcessor.StreamType.HIT, DUMMY);

        // these will be QUEUED
        for(int i=0; i<AsynchronousDataProcessor.PROCESSING_QUEUE_DEPTH; i++)
        {
            subject.process(DataProcessor.StreamType.HIT, DUMMY);
        }

        try
        {
            // this one will be rejected
            subject.process(DataProcessor.StreamType.HIT, DUMMY);
            fail("Queue limit violated");
        }
        catch (DataProcessorError dataProcessorError)
        {
            //desired
            assertEquals("","Error submitting work to the processor",
                    dataProcessorError.getMessage());
        }

        mock.unlock();
        try{ Thread.sleep(100);} catch (InterruptedException e){}

        subject.sync();
        assertEquals("All should be processed",
                AsynchronousDataProcessor.PROCESSING_QUEUE_DEPTH+1,
                mock.processCount);

        subject.shutdown();

    }

    @Test
    public void testFullBlockingPolicy() throws DataProcessorError, InterruptedException
    {
        //
        // tests limit on processing queue when in "Block" policy
        //
        final MockDataProcessor mock = new MockDataProcessor();
        final AsynchronousDataProcessor subject =
                AsynchronousDataProcessor.singleThreadedExecutor("test",
                        mock, AsynchronousDataProcessor.QueueFullPolicy.Block);

        mock.lock();


        // this one will be out for processing
        subject.process(DataProcessor.StreamType.HIT, DUMMY);

        // these will be QUEUED
        for(int i=0; i<AsynchronousDataProcessor.PROCESSING_QUEUE_DEPTH; i++)
        {
            subject.process(DataProcessor.StreamType.HIT, DUMMY);
        }

        // this one will be block until the timer unlocks
        // the mock target
        new Thread(){
            @Override
            public void run()
            {
                try
                {
                    subject.process(DataProcessor.StreamType.HIT, DUMMY);
                    subject.process(DataProcessor.StreamType.HIT, DUMMY);
                    subject.process(DataProcessor.StreamType.HIT, DUMMY);
                    subject.process(DataProcessor.StreamType.HIT, DUMMY);
                    subject.process(DataProcessor.StreamType.HIT, DUMMY);
                    subject.process(DataProcessor.StreamType.HIT, DUMMY);
                    subject.process(DataProcessor.StreamType.HIT, DUMMY);
                    subject.process(DataProcessor.StreamType.HIT, DUMMY);
                    subject.process(DataProcessor.StreamType.HIT, DUMMY);
                    subject.process(DataProcessor.StreamType.HIT, DUMMY);
                }
                catch (DataProcessorError dataProcessorError)
                {
                    // this should fail the test but we can't fail from
                    // a worker thread. test relies on the count of delivered
                    // buffers at the end
                    dataProcessorError.printStackTrace(System.out);
                }
            }
        }.start();

        // ugly timing dependence  ... should be OK
        try{ Thread.sleep(1000);} catch (InterruptedException e){}

        mock.unlock();
        try{ Thread.sleep(1000);} catch (InterruptedException e){}

        subject.sync();
        assertEquals("All should be processed",
                AsynchronousDataProcessor.PROCESSING_QUEUE_DEPTH + 11,
                mock.processCount);

        subject.shutdown();
    }

    @Test
    public void testEOS() throws DataProcessorError, InterruptedException
    {
        //
        // tests eos()
        //
        final MockDataProcessor mock = new MockDataProcessor();
        final AsynchronousDataProcessor subject =
                AsynchronousDataProcessor.singleThreadedExecutor("test",
                        mock);

        mock.lock();



        subject.process(DataProcessor.StreamType.HIT, DUMMY);
        subject.process(DataProcessor.StreamType.HIT, DUMMY);
        subject.process(DataProcessor.StreamType.HIT, DUMMY);
        subject.eos();

        mock.unlock();

        subject.sync();
        assertEquals("Work not processed", 3, mock.processCount);
        assertTrue("EOS not senrt",mock.sawEOS);

        subject.shutdown();

    }

    @Test
    public void testShutdown() throws DataProcessorError
    {
        MockDataProcessor mock = new MockDataProcessor();
        AsynchronousDataProcessor subject =
                AsynchronousDataProcessor.singleThreadedExecutor("test",
                        mock);

        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        try{ Thread.sleep(200);} catch (InterruptedException e){}

        mock.lock();
        assertEquals("", 3, mock.processCount);

        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);

        mock.unlock();
        subject.shutdown();

        assertEquals("All data not processed", 10, mock.processCount);
        assertTrue("shutdown not propagated", mock.sawShutdown);


        try
        {
            subject.eos(DataProcessor.StreamType.HIT);
            fail("Accepted work after shutdown");
        }
        catch (DataProcessorError dataProcessorError)
        {
            //desired
            assertEquals("","Error submitting work to the processor",
                    dataProcessorError.getMessage());
        }

        try
        {
            subject.resolveUTCTime(123);
            fail("Accepted work after shutdown");
        }
        catch (DataProcessorError dataProcessorError)
        {
            //desired
            assertEquals("","Error submitting work to the processor",
                    dataProcessorError.getMessage());
        }


        try
        {
            subject.process(DataProcessor.StreamType.HIT,
                    ByteBuffer.allocate(0));
            fail("Accepted work after shutdown");
        }
        catch (DataProcessorError dataProcessorError)
        {
            //desired
            assertEquals("","Error submitting work to the processor",
                    dataProcessorError.getMessage());
        }

        try
        {
            subject.sync();
            fail("Accepted work after shutdown");
        }
        catch (DataProcessorError dataProcessorError)
        {
            //desired
            assertEquals("","Error submitting work to the processor",
                    dataProcessorError.getMessage());
        }

        // allow duplicate shutdown
        subject.shutdown();
        subject.shutdown();
    }

    @Test
    public void testForcedShutdownTimeout() throws DataProcessorError
    {
        //
        // Test a shutdown with interruptibly blocked jobs
        //
        MockDataProcessor mock = new MockDataProcessor();
        AsynchronousDataProcessor subject =
                AsynchronousDataProcessor.singleThreadedExecutor("test",
                        mock);

        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        try{ Thread.sleep(200);} catch (InterruptedException e){}

        mock.setMode(MockDataProcessor.Mode.BLOCK_ONCE_INTERRUPTABLY);
        assertEquals("", 3, mock.processCount);

        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);

        //NOTE: mock is locked, seven jobs will be discarded
        System.out.println("Testing forcedShutdown, may take 5 seconds...");
        subject.shutdown(5 * 1000);

        assertEquals("expected process count", 3, mock.processCount);
        assertTrue("shutdown not propagated", mock.sawShutdown);

    }

    @Test
    public void testForcedShutdownBlockedTimeout() throws DataProcessorError
    {
        //
        // Test a shutdown with an un-interruptible blocked job and a
        // time limit on the forced shutdown.
        //
        MockDataProcessor mock = new MockDataProcessor();
        AsynchronousDataProcessor subject =
                AsynchronousDataProcessor.singleThreadedExecutor("test",
                        mock);

        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        try{ Thread.sleep(200);} catch (InterruptedException e){}

        mock.lock();
        assertEquals("", 3, mock.processCount);

        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);

        //NOTE: mock is locked uninterruptibly, shutdown will
        // block, then fail
        System.out.println("Testing forcedShutdown, may take 6 seconds... ");
        try
        {
            subject.FORCED_SHUTDOWN_TIMEOUT_SECONDS = 1;
            subject.shutdown(5 * 1000);
            fail("Shutdown should not return without processor thread" +
                    " exiting normally");
        }
        catch (DataProcessorError dataProcessorError)
        {
            //desired
            assertEquals("", "Timed out waiting for shutdown",
                    dataProcessorError.getMessage());
        }

        assertEquals("expected process count", 3, mock.processCount);
        assertFalse("shutdown propagated", mock.sawShutdown);

        // Processor is in a pathological state, the following is
        // not an advertised contract of the interface, but it is
        // the behavior at the time of writing.
        mock.unlock();

        try{ Thread.sleep(200);} catch (InterruptedException e){}

        assertEquals("expected process count", 4, mock.processCount);
        assertFalse("shutdown not propagated", mock.sawShutdown);
    }


    @Test
    public void testForcedShutdownBlockedInterrupted() throws DataProcessorError
    {
        //
        // Test a shutdown with an un-interruptible blocked job and
        // an external watchdog.
        //
        MockDataProcessor mock = new MockDataProcessor();
        AsynchronousDataProcessor subject =
                AsynchronousDataProcessor.singleThreadedExecutor("test",
                        mock);

        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        try{ Thread.sleep(200);} catch (InterruptedException e){}

        mock.lock();
        assertEquals("", 3, mock.processCount);

        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);

        //NOTE: mock is locked uninterruptibly, shutdown will
        // block, then fail
        System.out.println("Testing forcedShutdown, may take 6 seconds... ");

        //
        // shutdown is doomed. it will never, ever, ever complete on its
        // own. So we will set up a watchdog to interrupt and ensure it
        // excepts the way we expect it to
        //
        final Thread main = Thread.currentThread();
        new Thread(){
            @Override
            public void run()
            {
                try{ Thread.sleep(6000);} catch (InterruptedException e){}
                main.interrupt();
            }
        }.start();

        try
        {
            subject.shutdown(5 * 1000);
            fail("Shutdown should not return without processor thread" +
                    " exiting normally");
        }
        catch (DataProcessorError dataProcessorError)
        {
            //desired
            assertEquals("", "Interrupted waiting for shutdown",
                    dataProcessorError.getMessage());
        }

        assertEquals("expected process count", 3, mock.processCount);
        assertFalse("shutdown propagated", mock.sawShutdown);

        // Processor is in a pathological state, the following is
        // not an advertised contract of the interface, but it is
        // the behavior at the time of writing.
        mock.unlock();

        try{ Thread.sleep(200);} catch (InterruptedException e){}

        assertEquals("expected process count", 4, mock.processCount);
        assertFalse("shutdown not propagated", mock.sawShutdown);
    }


    @Test
    public void testProcessingErrors() throws DataProcessorError
    {
        //
        // Errors thrown from downstream processing should
        // shut down the processor
        //

        //CASE I: give processor time to shut down by itself
        {
        MockDataProcessor mock = new MockDataProcessor();
        AsynchronousDataProcessor subject =
                AsynchronousDataProcessor.singleThreadedExecutor("test",
                        mock);

        mock.lock();

        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);

        mock.setMode(MockDataProcessor.Mode.ONE_ERROR);

        mock.unlock();

        try{ Thread.sleep(200);} catch (InterruptedException e){}

        subject.shutdown();

        assertEquals("", 0, mock.processCount);
        }

        //CASE II: possibly induce a simultaneous shutdown by
        //         invoking a shutdown from the client as well
        //         as within the processor error handler
        {
            MockDataProcessor mock = new MockDataProcessor();
            AsynchronousDataProcessor subject =
                    AsynchronousDataProcessor.singleThreadedExecutor("test",
                            mock);

            mock.lock();
            mock.delayMillis = 200;

            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);

            mock.setMode(MockDataProcessor.Mode.ONE_ERROR);

            mock.unlock();
            subject.shutdown();
            assertEquals("", 0, mock.processCount);
        }


    }

    @Test
    public void testProcessingErrorsDurningSynchronousCalls() throws Exception
    {
        /// Test that a forced shutdown while a pending
        /// synchronous call does not deadlock the caller

        //CASE I: forced shutdown with pending graceful shutdown
        {
            MockDataProcessor mock = new MockDataProcessor();
            AsynchronousDataProcessor subject =
                    AsynchronousDataProcessor.singleThreadedExecutor("test",
                            mock);

            mock.lock();
            mock.delayMillis = 200;

            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);

            mock.setMode(MockDataProcessor.Mode.ONE_ERROR);

            mock.unlock();
            subject.shutdown();
            assertEquals("", 0, mock.processCount);
        }

        //CASE II: forced shutdown with pending sync() call
        {
            MockDataProcessor mock = new MockDataProcessor();
            AsynchronousDataProcessor subject =
                    AsynchronousDataProcessor.singleThreadedExecutor("test",
                            mock);

            mock.lock();
            mock.delayMillis = 200;

            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);

            mock.setMode(MockDataProcessor.Mode.ONE_ERROR);

            mock.unlock();
            try
            {
                subject.sync();
                fail("Sync suceeded despite shutdown");
            }
            catch (DataProcessorError dataProcessorError)
            {
               //desired
                assertEquals("","Cancelled while syncing", dataProcessorError.getMessage());
            }
            assertEquals("", 0, mock.processCount);
        }

        //CASE III: forced shutdown with pending resolveUTCTime() call
        {
            MockDataProcessor mock = new MockDataProcessor();
            AsynchronousDataProcessor subject =
                    AsynchronousDataProcessor.singleThreadedExecutor("test",
                            mock);

            mock.lock();
            mock.delayMillis = 200;

            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);

            mock.setMode(MockDataProcessor.Mode.ONE_ERROR);

            mock.unlock();
            try
            {
                subject.resolveUTCTime(1234);
                fail("resolveUTCTime suceeded despite shutdown");
            }
            catch (DataProcessorError dataProcessorError)
            {
                //desired
                assertEquals("","Cancelled while resolving UTC time", dataProcessorError.getMessage());
            }
            assertEquals("", 0, mock.processCount);
        }

    }




    @Test
    public void testOtherErrors() throws DataProcessorError
    {
        //
        // Errors thrown from downstream processing should
        // shut down the processor
        //

        //CASE I: eos
        {
            MockDataProcessor mock = new MockDataProcessor();
            AsynchronousDataProcessor subject =
                    AsynchronousDataProcessor.singleThreadedExecutor("test",
                            mock);

            mock.lock();

            subject.eos();
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);

            mock.setMode(MockDataProcessor.Mode.ONE_ERROR);

            mock.unlock();
            try{ Thread.sleep(200);} catch (InterruptedException e){}

            subject.shutdown();

            assertEquals("", 0, mock.processCount);
        }

        //CASE II: sync
        {
            MockDataProcessor mock = new MockDataProcessor();
            AsynchronousDataProcessor subject =
                    AsynchronousDataProcessor.singleThreadedExecutor("test",
                            mock);


            mock.setMode(MockDataProcessor.Mode.ONE_ERROR);

            try
            {
                subject.sync();
                fail("Expected Error");
            }
            catch (DataProcessorError dataProcessorError)
            {
               // desired
                assertEquals("","Cancelled while syncing",
                        dataProcessorError.getMessage());
            }

            try
            {
                subject.process(DataProcessor.StreamType.MONI, DUMMY);
                fail("Expected Error");
            }
            catch (DataProcessorError dataProcessorError)
            {
                // desired
                assertEquals("","Error submitting work to the processor",
                        dataProcessorError.getMessage());
            }

            try{ Thread.sleep(200);} catch (InterruptedException e){}

            subject.shutdown();

            assertEquals("", 0, mock.processCount);
        }

        //CASE III: runLevel
        {
            MockDataProcessor mock = new MockDataProcessor();
            AsynchronousDataProcessor subject =
                    AsynchronousDataProcessor.singleThreadedExecutor("test",
                            mock);

            mock.lock();

            subject.runLevel(RunLevel.RUNNING);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);

            mock.setMode(MockDataProcessor.Mode.ONE_ERROR);

            mock.unlock();
            try{ Thread.sleep(200);} catch (InterruptedException e){}

            subject.shutdown();

            assertEquals("", 0, mock.processCount);
        }

        //CASE IV: resolveUTCTime
        {
            MockDataProcessor mock = new MockDataProcessor();
            AsynchronousDataProcessor subject =
                    AsynchronousDataProcessor.singleThreadedExecutor("test",
                            mock);


            mock.setMode(MockDataProcessor.Mode.ONE_ERROR);
            try
            {
                subject.resolveUTCTime(1212);
            }
            catch (DataProcessorError dataProcessorError)
            {
                //expected
                assertEquals("","Error while resolving UTC time",
                        dataProcessorError.getMessage());
            }
        }

    }

    @Test
    public void testMultipleErrors() throws DataProcessorError
    {
        //
        // Permanently break downstream processor, processor
        // will have to work around this and force shutdown
        //

            MockDataProcessor mock = new MockDataProcessor();
            AsynchronousDataProcessor subject =
                    AsynchronousDataProcessor.singleThreadedExecutor("test",
                            mock);

            mock.lock();

            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);
            subject.process(DataProcessor.StreamType.MONI, DUMMY);

            mock.setMode(MockDataProcessor.Mode.ERROR);

            mock.unlock();

            try{ Thread.sleep(200);} catch (InterruptedException e){}

            subject.shutdown();

            assertEquals("", 0, mock.processCount);
    }

    @Test
    public void testErrorDuringShutdown() throws DataProcessorError
    {
        //
        // Set up an error during shutdown
        //

        MockDataProcessor mock = new MockDataProcessor();
        AsynchronousDataProcessor subject =
                AsynchronousDataProcessor.singleThreadedExecutor("test",
                        mock);


        mock.setMode(MockDataProcessor.Mode.ONE_ERROR);


        try{ Thread.sleep(200);} catch (InterruptedException e){}

        subject.shutdown();

    }

        @Test
    public void testBlockedProcessor() throws DataProcessorError
    {
        //
        // Test that processor will interrupt itself on shutdown if
        // blocked
        //

        MockDataProcessor mock = new MockDataProcessor();
        AsynchronousDataProcessor subject =
                AsynchronousDataProcessor.singleThreadedExecutor("test",
                        mock);

        mock.setMode(MockDataProcessor.Mode.BLOCK_ONCE_INTERRUPTABLY);

        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);
        subject.process(DataProcessor.StreamType.MONI, DUMMY);

        System.out.println("Blocking for 5 seconds");
        subject.shutdown(5 * 1000);

        assertEquals("", 0, mock.processCount);

        try{ Thread.sleep(100);} catch (InterruptedException e){}
        assertEquals("", 0, mock.processCount);

    }

}
