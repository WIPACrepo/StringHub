package icecube.daq.domapp;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.dor.TimeCalib;
import icecube.daq.livemoni.LiveTCalMoni;
import icecube.daq.rapcal.RAPCal;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.stringhub.test.MockAppender;
import icecube.daq.util.UTC;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Before;
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
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
    }

    @Before
    public void setUp() throws Exception
    {
    }

    @Test
    public void testNullInstantiation() throws IOException
    {
        // must support null consumer modes
        BufferConsumer nullConsumer = null;

        DataCollector.UTCMessageStream stream = new DataCollector
                .UTCMessageStream(nullConsumer, "test", 10000);

        assertFalse("Null consumer not supported", stream.hasConsumer());

        try
        {
            stream.eos(ByteBuffer.allocate(24));
        }
        catch (Exception e)
        {
            fail("Null consumer not supported");
        }
    }

    @Test
    public void testConsumerManagement() throws IOException
    {
        // must support ad hoc consumer methods
        BufferConsumerMock consumerMock = new BufferConsumerMock();

        DataCollector.UTCMessageStream stream = new DataCollector
                .UTCMessageStream(consumerMock, "test", 10000);

        assertTrue("Consumer existence not supported", stream.hasConsumer());


        ByteBuffer eos = ByteBuffer.allocate(32);
        eos.putLong(24, Long.MAX_VALUE);
        stream.eos(eos);

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
            internalTestInOrderCondition(-1);     // negative epsilon
        }
        catch (IOException e)
        {
            fail("IOEception " + e.getMessage());
        }
    }

    public void internalTestInOrderCondition(long epsilon) throws IOException
    {
        // generate data payloads in time order
        // this is the predominate condition
        RapCalMock rapcal = new RapCalMock(500);
      BufferConsumerMock sink = new BufferConsumerMock();

        DataCollector.UTCMessageStream stream = new DataCollector
                .UTCMessageStream(sink, "test", epsilon);

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
            stream.dispatchBuffer(rapcal, generateBuffer(domTimestamps[i]));
        }

        assertArrayEquals("in-order payloads not delivered",
                utcTimestamps,
                sink.getReceivedTimes());
    }

    @Test
    public void testRetrogradeWithinEpsilon() throws IOException
    {
        RapCalMock rapcal = new RapCalMock(500);
        BufferConsumerMock sink = new BufferConsumerMock();

        DataCollector.UTCMessageStream stream = new DataCollector
                .UTCMessageStream(sink, "test", 10000);


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
            stream.dispatchBuffer(rapcal, generateBuffer(domTimestamps[i]));
        }

        assertArrayEquals("payloads within epsilon ordered not delivered",
                expectedUTCOutput,
                sink.getReceivedTimes());
    }


    @Test
    public void testRetrogradeBeyondEpsilon() throws IOException
    {
        RapCalMock rapcal = new RapCalMock(500);
        BufferConsumerMock sink = new BufferConsumerMock();

        DataCollector.UTCMessageStream stream = new DataCollector
                .UTCMessageStream(sink, "test", 10000);


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
            stream.dispatchBuffer(rapcal, generateBuffer(domTimestamps[i]));
        }

        assertArrayEquals("out-of-order payloads mis-handled ",
                unbox(expectedUTCOutput),
                sink.getReceivedTimes());
    }


    @Test
    public void testRetrogradeZeroEpsilon() throws IOException
    {
        RapCalMock rapcal = new RapCalMock(500);
        BufferConsumerMock sink = new BufferConsumerMock();

        DataCollector.UTCMessageStream stream = new DataCollector
                .UTCMessageStream(sink, "test", 0);


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
            stream.dispatchBuffer(rapcal, generateBuffer(domTimestamps[i]));
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

    //simulates a time conversion, exposing
    // the offset for manipulation
    public class RapCalMock implements RAPCal
    {

        public long offset;

        public RapCalMock(final long offset)
        {
            this.offset = offset;
        }

        public UTC domToUTC(final long domclk)
        {
            return new UTC(250L * domclk + offset);
        }

        void setOffset(long offset)
        {
            this.offset = offset;
        }
        void adjustOffset(long adjustment)
        {
            offset+= adjustment;
        }

        public double clockRatio()
        {
            return 0;
        }

        public double cableLength()
        {
            return 0;
        }

        public boolean laterThan(final long domclk)
        {
            return false;
        }

        public void setMoni(final LiveTCalMoni moni)
        {
        }

        public void update(final TimeCalib tcal, final UTC gpsOffset)
                throws RAPCalException
        {
        }
    }


    @Test
    public void testLoggingThrottling() throws IOException
    {
        RapCalMock rapcal = new RapCalMock(500);
        BufferConsumerMock sink = new BufferConsumerMock();

        DataCollector.UTCMessageStream stream = new DataCollector
                .UTCMessageStream(sink, "test", 0);

        long CURRENT_TIME=100000;
        long BACKWARDS_TIME=1234;
        stream.dispatchBuffer(rapcal, generateBuffer(CURRENT_TIME));

        //install a countable logger
        MockAppender mockLogger = new MockAppender(Level.ERROR);
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(mockLogger);

        //should only log MAX_LOGGING+1 in a row
        mockLogger.setVerbose(true);
        for (int i=1; i<10000; i++)
        {
            stream.dispatchBuffer(rapcal, generateBuffer(BACKWARDS_TIME));
            int numSeen = mockLogger.getNumberOfMessages();
            if(i<stream.MAX_LOGGING)
            {
                assertEquals("Expect one log per occurrence", i, numSeen);
            }
            else if( i == stream.MAX_LOGGING)
            {
                assertEquals("Expect one log per occurrence," +
                        " plus dampening message", i+1, numSeen);
            }
            else
            {
                assertEquals("Expect max logging plus dampen message",
                        stream.MAX_LOGGING+1, numSeen);
            }
        }


        //should log LOGGED_OCCURRENCES_PERIOD, followed by
        //MAX_LOGGING+1 every LOGGED_OCCURRENCES_PERIOD
        mockLogger.clear();
        mockLogger.setVerbose(false);
        stream = new DataCollector.UTCMessageStream(sink, "test", 0);
        for (int i=1; i<100000; i++)
        {
            stream.dispatchBuffer(rapcal, generateBuffer(CURRENT_TIME));
            stream.dispatchBuffer(rapcal, generateBuffer(BACKWARDS_TIME));

            int numSeen = mockLogger.getNumberOfMessages();

            if(i< stream.LOGGED_OCCURRENCES_PERIOD + stream.MAX_LOGGING)
            {
                assertEquals("Expect one log per occurrence", i, numSeen);
            }
            else if( i == stream.LOGGED_OCCURRENCES_PERIOD + stream.MAX_LOGGING)
            {
                assertEquals("Expect one log per occurrence," +
                        " plus dampening message", i+1, numSeen);
            }
            else
            {
                //now we expect a run of MAX_LOGGING+1 once every
                //LOGGED_OCCURRENCE_PERIOD

                int periodNumber = i/stream.LOGGED_OCCURRENCES_PERIOD;
                int occurrenceCount = i % stream.LOGGED_OCCURRENCES_PERIOD;


                if(occurrenceCount < stream.MAX_LOGGING)
                {
                    int expected = stream.LOGGED_OCCURRENCES_PERIOD +
                            (stream.MAX_LOGGING +1) *  (periodNumber-1) +
                            occurrenceCount;
                    assertEquals("Expect one log per occurrence",
                            expected, numSeen);

                }
                else if(occurrenceCount == stream.MAX_LOGGING)
                {
                    int expected = stream.LOGGED_OCCURRENCES_PERIOD +
                            (stream.MAX_LOGGING +1) *  (periodNumber-1) +
                            occurrenceCount + 1;
                    assertEquals("Expect one log per occurrence," +
                            " plus dampening message", expected, numSeen);
                }
                else
                {
                    int expected = stream.LOGGED_OCCURRENCES_PERIOD +
                            (stream.MAX_LOGGING +1) *  periodNumber;
                assertEquals("Expect max logging during period",
                        expected, numSeen);
                }
            }

        }

    }

    private static class BufferConsumerMock implements BufferConsumer
    {
        List<Long> receivedTimes = new ArrayList<Long>(10);

        public void consume(final ByteBuffer buf) throws IOException
        {
            receivedTimes.add(buf.getLong(24));
        }

        public long[] getReceivedTimes()
        {
            return unbox(receivedTimes);
        }

        public void clear()
        {
            receivedTimes.clear();
        }


    }

}


