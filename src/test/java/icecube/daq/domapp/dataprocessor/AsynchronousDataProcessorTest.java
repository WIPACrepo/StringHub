package icecube.daq.domapp.dataprocessor;

import icecube.daq.domapp.RunLevel;
import icecube.daq.domapp.dataprocessor.test.MockDataProcessor;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
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
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);

        Logger.getRootLogger().warn("XXXX Following test cases will log" +
                " ERRORS and stack traces.");
    }

    @Before
    public void setUp() throws Exception
    {
    }

    @AfterClass
    public static void tearDown()
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
    public void testFull() throws DataProcessorError, InterruptedException
    {
        //
        // tests limit on processing queue
        //
        final MockDataProcessor mock = new MockDataProcessor();
        final AsynchronousDataProcessor subject =
                AsynchronousDataProcessor.singleThreadedExecutor("test",
                        mock);

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
        }

        try
        {
            subject.resolveUTCTime(123);
            fail("Accepted work after shutdown");
        }
        catch (DataProcessorError dataProcessorError)
        {
            //desired
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
        }

        try
        {
            subject.sync();
            fail("Accepted work after shutdown");
        }
        catch (DataProcessorError dataProcessorError)
        {
            //desired
        }

        // allow duplicate shutdown
        subject.shutdown();
        subject.shutdown();
    }

    @Test
    public void testForcedShutdown() throws DataProcessorError
    {
        //
        // Test a shutdown with blocked jobs
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

        //NOTE: mock is locked, seven jobs will be discarded
        System.out.println("Testing forcedShutdown, may take " +
                AsynchronousDataProcessor.GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS +
                " seconds... ");
        subject.shutdown();

        assertEquals("expected process count", 3, mock.processCount);
        assertTrue("shutdown not propagated", mock.sawShutdown);

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

            mock.lock();

            subject.sync();
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
            }
        }

    }

    @Test
    public void testMultipleErrors() throws DataProcessorError
    {
        //
        // Permenently break downstream processor, processor
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

        subject.shutdown();

        assertEquals("", 0, mock.processCount);

        try{ Thread.sleep(100);} catch (InterruptedException e){}
        assertEquals("", 0, mock.processCount);

    }

}
