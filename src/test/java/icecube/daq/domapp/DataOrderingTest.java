package icecube.daq.domapp;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.common.MockAppender;
import icecube.daq.domapp.dataprocessor.DataDispatcher;
import icecube.daq.domapp.dataprocessor.DataProcessor;
import icecube.daq.domapp.dataprocessor.DataProcessorError;
import icecube.daq.domapp.dataprocessor.DataStats;
import icecube.daq.domapp.dataprocessor.MockBufferConsumer;
import icecube.daq.domapp.dataprocessor.MockRapCal;
import icecube.daq.domapp.dataprocessor.UTCDispatcher;
import icecube.daq.rapcal.RAPCal;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;


/**
 *
 */
public class DataOrderingTest
{


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
    public void testNullInstantiation() throws DataProcessorError
    {
        // must support null consumer modes
        BufferConsumer nullConsumer = null;

        DataDispatcher dispatcher = constructDispatcher(null, nullConsumer,10000, 1000);

        assertFalse("Null consumer not supported", dispatcher.hasConsumer());

        try
        {
            dispatcher.eos(ByteBuffer.allocate(24));
        }
        catch (Exception e)
        {
            fail("Null consumer not supported");
        }
    }

    @Test
    public void testConsumerManagement()
            throws DataProcessorError
    {
        // must support ad hoc consumer methods
        MockBufferConsumer consumerMock = new MockBufferConsumer();

        DataDispatcher dispatcher = constructDispatcher(null, consumerMock, 10000, 1000);

        assertTrue("Consumer existence not supported", dispatcher.hasConsumer());

        ByteBuffer eos = ByteBuffer.allocate(32);
        eos.putLong(24, Long.MAX_VALUE);
        dispatcher.eos(eos);

        assertEquals("EOS not delivered", Long.MAX_VALUE,
                consumerMock.getReceivedTimes()[0]);

    }

    @Test
    public void testInOrderCondition()
    {

        try
        {
            internalTestInOrderCondition(10000);  // 10 microseconds
            internalTestInOrderCondition(0);      // zero epsilon
            internalTestInOrderCondition( -1);     // negative epsilon
        }
        catch (DataProcessorError e)
        {
            fail("IOEception " + e.getMessage());
        }
    }

    public void internalTestInOrderCondition(long epsilon) throws DataProcessorError
    {
        // generate data payloads in time order
        // this is the predominate condition
        MockRapCal rapcal = new MockRapCal(500);
      MockBufferConsumer sink = new MockBufferConsumer();

        DataDispatcher dispatcher = constructDispatcher(rapcal, sink, epsilon, 1000);

        long domclockSeed = 123556678;
        long[] domTimestamps = new long[]
                {
                        domclockSeed,
                        domclockSeed + 1,
                        domclockSeed + 2,
                        domclockSeed + 5000,
                        domclockSeed + 5001,
                        domclockSeed + 5003,
                        domclockSeed + 8888,
                        domclockSeed + 8888, //dups
                        domclockSeed + 9999
                };


        long[] utcTimestamps = new long[domTimestamps.length];
        for (int i = 0; i < utcTimestamps.length; i++)
        {
            utcTimestamps[i] = rapcal.domToUTC(domTimestamps[i]).in_0_1ns();
        }

        for (int i = 0; i < domTimestamps.length; i++)
        {
            dispatcher.dispatchBuffer(generateBuffer(domTimestamps[i]));
        }

        assertArrayEquals("in-order payloads not delivered",
                utcTimestamps,
                sink.getReceivedTimes());
    }

    @Test
    public void testRetrogradeWithinEpsilon() throws DataProcessorError
    {
        MockRapCal rapcal = new MockRapCal(500);
        MockBufferConsumer sink = new MockBufferConsumer();

        DataDispatcher dispatcher = constructDispatcher(rapcal, sink, 10000, 1000);

        // test retrograde within epsilon
        long[] domTimestamps = new long[]
                {
                        9995000,
                        9995001,
                        9995002,
                                   //<--- insert rapcal retrograde
                                   // 10000 utc (40 domticks)
                        9995002,   // should deliver
                        9995003,   // should deliver
                        9995015,   // should deliver
                        9995016,
                        9995017,
                                   // ...
                        9995038,
                        9995039,
                        9995040,
                        9995041,
                        9995042,  //retrograde end
                        9995053,
                        9995055,
                };

        rapcal.setOffset(500);
        long[] expectedUTCOutput = new long[domTimestamps.length];
        for (int i = 0; i < expectedUTCOutput.length; i++)
        {
            if(i==3)
            {
                rapcal.adjustOffset(-10000);
            }
            expectedUTCOutput[i] = rapcal.domToUTC(domTimestamps[i]).in_0_1ns();
        }

        rapcal.setOffset(500);
        for (int i = 0; i < domTimestamps.length; i++)
        {
            if(i==3)
            {
                rapcal.adjustOffset(-10000);
            }
            dispatcher.dispatchBuffer(generateBuffer(domTimestamps[i]));
        }

        assertArrayEquals("payloads within epsilon ordered not delivered",
                expectedUTCOutput,
                sink.getReceivedTimes());
    }


    @Test
    public void testRetrogradeBeyondEpsilon() throws DataProcessorError
    {
        MockRapCal rapcal = new MockRapCal(500);
        MockBufferConsumer sink = new MockBufferConsumer();

        DataDispatcher dispatcher = constructDispatcher(rapcal, sink, 10000, 1000);

        // test retrograde beyond epsilon
        long[] domTimestamps = new long[]
                {
                        9995000,
                        9995001,
                        9995002,
                        //<--- insert rapcal retrograde
                        // 25000 utc (100 domticks)
                        9995002,   // should skip
                        9995003,   // should skip
                        9995015,   // should skip
                        9995016,
                        9995017,
                        9995061,
                        9995062,   // should deliver, within epsilon
                        9995063,   // should deliver, within epsilon
                        9995064,   // should deliver, within epsilon
                                   // ...
                        9995101,
                        9995102, //retrograde end
                        9995103,
                        9999999
                };

        rapcal.setOffset(500);
        List<Long> expectedUTCOutput = new ArrayList<Long>();
        for (int i = 0; i < domTimestamps.length; i++)
        {
            if(i==3)
            {
                rapcal.adjustOffset(-25000);
            }
            if(i<3 || i>8)
            {
                //System.out.println("adding: " + domTimestamps[i] );
                expectedUTCOutput.add(rapcal.domToUTC(domTimestamps[i])
                        .in_0_1ns());
            }
            else
            {
             //skip
            }
        }

        rapcal.setOffset(500);
        for (int i = 0; i < domTimestamps.length; i++)
        {
            if(i==3)
            {
                rapcal.adjustOffset(-25000);
            }
            dispatcher.dispatchBuffer(generateBuffer(domTimestamps[i]));
        }

        assertArrayEquals("out-of-order payloads mis-handled ",
                unbox(expectedUTCOutput),
                sink.getReceivedTimes());
    }


    @Test
    public void testRetrogradeZeroEpsilon() throws DataProcessorError
    {
        MockRapCal rapcal = new MockRapCal(500);
        MockBufferConsumer sink = new MockBufferConsumer();

        DataDispatcher dispatcher = constructDispatcher(rapcal, sink, 0, 1000);

        // test retrograde wit epsilon at zero
        long[] domTimestamps = new long[]
                {
                        9995000,
                        9995001,
                        9995002,
                        //<--- insert retrograde
                        // 25000 utc (100 domticks)
                        9995002,   // should skip
                        9995003,   // should skip
                        9995015,   // should skip
                        9995016,
                        9995017,
                        9995061,
                        9995062,   // should skip
                        9995063,   // should skip
                        9995064,   // should skip
                                   // ...
                        9995101,   // should skip
                        9995102,   //retrograde end, should deliver
                        9995103,
                        9999999
                };

        rapcal.setOffset(500);
        List<Long> expectedUTCOutput = new ArrayList<Long>();
        for (int i = 0; i < domTimestamps.length; i++)
        {
            if(i==3)
            {
                rapcal.adjustOffset(-25000);
            }
            if(i<3 || i>12)
            {
                //System.out.println("adding: " + domTimestamps[i] );
                expectedUTCOutput.add(rapcal.domToUTC(domTimestamps[i])
                        .in_0_1ns());
            }
            else
            {
                //skip
            }
        }

        rapcal.setOffset(500);
        for (int i = 0; i < domTimestamps.length; i++)
        {
            if(i==3)
            {
                rapcal.adjustOffset(-25000);
            }
            dispatcher.dispatchBuffer(generateBuffer(domTimestamps[i]));
        }

        assertArrayEquals("out-of-order payloads mis-handled ",
                unbox(expectedUTCOutput),
                sink.getReceivedTimes());
    }


    private static ByteBuffer generateBuffer(long domclock)
        {
            ByteBuffer buf = ByteBuffer.allocate(32);
            buf.putLong(24, domclock);
            return buf;
        }

    private static long[] unbox(List<Long> boxed)
    {
        long[] unboxed = new long[boxed.size()];
        for (int i = 0; i < boxed.size(); i++)
        {
            unboxed[i] = boxed.get(i);
        }
        return unboxed;
    }

    @Test
    public void testMaxDroppedMessages() throws DataProcessorError
    {

        int MAX_DROPS = 1000;

        MockRapCal rapcal = new MockRapCal(500);
        MockBufferConsumer sink = new MockBufferConsumer();

        DataDispatcher dispatcher = constructDispatcher(rapcal, sink,
                10000, MAX_DROPS);

        long CURRENT_TIME=100000;
        long BACKWARDS_TIME=1234;

        /// SCENARIO I : a constant stream of out of order
        dispatcher.dispatchBuffer(generateBuffer(CURRENT_TIME));

        for (int i=0; i<MAX_DROPS; i++)
        {
            dispatcher.dispatchBuffer(generateBuffer(BACKWARDS_TIME));
        }

        //next one should generate an error
        try
        {
            dispatcher.dispatchBuffer(generateBuffer(BACKWARDS_TIME));
            fail("Dispatcher allowed more that " +
                    MAX_DROPS + " drops");
        }
        catch (DataProcessorError dataProcessorError)
        {
            //desired in this test.
        }

        /// SCENARIO II : Out of orders mixed with in-order and
        //  sub-epsilon out-of-order
        dispatcher = constructDispatcher(rapcal, sink,
                10000, MAX_DROPS);

        CURRENT_TIME=100000;
        BACKWARDS_TIME=1234;
        dispatcher.dispatchBuffer(generateBuffer(CURRENT_TIME));

        for (int i=0; i<MAX_DROPS; i++)
        {
            // out-of order
            dispatcher.dispatchBuffer(generateBuffer(BACKWARDS_TIME));

            // some in-order
            for(int y=0; y<100; y++)
            {
                CURRENT_TIME+=123;
                dispatcher.dispatchBuffer(generateBuffer(CURRENT_TIME));
            }

            //a sub-epsilon out-of-order
            dispatcher.dispatchBuffer(generateBuffer(CURRENT_TIME-10));

        }

        //next one should generate an error
        try
        {
            dispatcher.dispatchBuffer(generateBuffer(BACKWARDS_TIME));
            fail("Dispatcher allowed more that " +
                    MAX_DROPS + " drops");
        }
        catch (DataProcessorError dataProcessorError)
        {
            //desired in this test.
        }
    }


    @Test
    public void testLoggingThrottling() throws DataProcessorError
    {

        int MAX_DROPS=Integer.MAX_VALUE;

        MockRapCal rapcal = new MockRapCal(500);
        MockBufferConsumer sink = new MockBufferConsumer();

        UTCDispatcher dispatcher = constructDispatcher(rapcal, sink, 0, MAX_DROPS);

        int MAX_LOGGING = dispatcher.MAX_LOGGING;
        int LOGGED_OCCURRENCES_PERIOD = dispatcher.LOGGED_OCCURRENCES_PERIOD;


        long CURRENT_TIME=100000;
        long BACKWARDS_TIME=1234;
        dispatcher.dispatchBuffer(generateBuffer(CURRENT_TIME));

        //install a countable logger
        MockAppender mockLogger = new MockAppender(Level.ERROR);
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(mockLogger);

        //should only log MAX_LOGGING+1 in a row
        mockLogger.setVerbose(false);
        for (int i=1; i<10000; i++)
        {
            dispatcher.dispatchBuffer(generateBuffer(BACKWARDS_TIME));
            int numSeen = mockLogger.getNumberOfMessages();
            if(i<MAX_LOGGING)
            {
                assertEquals("Expect one log per occurrence", i, numSeen);
            }
            else if( i == MAX_LOGGING)
            {
                assertEquals("Expect one log per occurrence," +
                        " plus dampening message", i+1, numSeen);
            }
            else
            {
                assertEquals("Expect max logging plus dampen message",
                        MAX_LOGGING+1, numSeen);
            }
        }


        //should log LOGGED_OCCURRENCES_PERIOD, followed by
        //MAX_LOGGING+1 every LOGGED_OCCURRENCES_PERIOD
        mockLogger.clear();
        mockLogger.setVerbose(false);
        dispatcher = constructDispatcher(rapcal, sink, 0,  MAX_DROPS);
       // stream = new DataCollector.UTCMessageStream(sink, "test", 0);
        for (int i=1; i<100000; i++)
        {
            dispatcher.dispatchBuffer(generateBuffer(CURRENT_TIME));
            dispatcher.dispatchBuffer(generateBuffer(BACKWARDS_TIME));

            int numSeen = mockLogger.getNumberOfMessages();

            if(i< LOGGED_OCCURRENCES_PERIOD + MAX_LOGGING)
            {
                assertEquals("Expect one log per occurrence", i, numSeen);
            }
            else if( i == LOGGED_OCCURRENCES_PERIOD + MAX_LOGGING)
            {
                assertEquals("Expect one log per occurrence," +
                        " plus dampening message", i+1, numSeen);
            }
            else
            {
                //now we expect a run of MAX_LOGGING+1 once every
                //LOGGED_OCCURRENCE_PERIOD

                int periodNumber = i/LOGGED_OCCURRENCES_PERIOD;
                int occurrenceCount = i % LOGGED_OCCURRENCES_PERIOD;


                if(occurrenceCount < MAX_LOGGING)
                {
                    int expected = LOGGED_OCCURRENCES_PERIOD +
                            (MAX_LOGGING +1) *  (periodNumber-1) +
                            occurrenceCount;
                    assertEquals("Expect one log per occurrence",
                            expected, numSeen);

                }
                else if(occurrenceCount == MAX_LOGGING)
                {
                    int expected = LOGGED_OCCURRENCES_PERIOD +
                            (MAX_LOGGING +1) *  (periodNumber-1) +
                            occurrenceCount + 1;
                    assertEquals("Expect one log per occurrence," +
                            " plus dampening message", expected, numSeen);
                }
                else
                {
                    int expected = LOGGED_OCCURRENCES_PERIOD +
                            (MAX_LOGGING +1) *  periodNumber;
                assertEquals("Expect max logging during period",
                        expected, numSeen);
                }
            }

        }

    }

    /**
     * factory for creating test instances
     */
    private static UTCDispatcher constructDispatcher(final RAPCal rapcal,
                                                      final BufferConsumer target,
                                                      long orderingEpsilon,
                                                      long maxDrops)
    {

                return new UTCDispatcher(target,
                        DataProcessor.StreamType.MONI, rapcal,orderingEpsilon,
                        maxDrops);

    }

}
