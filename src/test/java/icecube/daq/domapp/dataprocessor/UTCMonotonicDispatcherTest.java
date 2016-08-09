package icecube.daq.domapp.dataprocessor;

import icecube.daq.util.TimeUnits;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;


import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Test UTCMonotonicDispatch
 */
public class UTCMonotonicDispatcherTest
{

    static final long MAX_DOM_CLOCK = TimeUnits.DOM.maxValue();

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
    public void testImmediateDispatch() throws DataProcessorError
    {
        //
        // Test immediate dispatch, where the data timestamps are already
        // bounded by rapcal
        //

        MockRapCal rapcal = new MockRapCal(99999);
        MockBufferConsumer consumer = new MockBufferConsumer();
        UTCMonotonicDispatcher subject = new UTCMonotonicDispatcher(consumer,
                DataProcessor.StreamType.HIT,
                rapcal);

        // set upper bound to cover all times
        rapcal.setUpperBound(Long.MAX_VALUE);

        subject.dispatchBuffer(generateBuffer(0));
        subject.dispatchBuffer(generateBuffer(1));
        subject.dispatchBuffer(generateBuffer(100000000));
        subject.dispatchBuffer(generateBuffer(444444444));
        subject.dispatchBuffer(generateBuffer(999999999));
        subject.dispatchBuffer(generateBuffer(777777777777L));
        subject.dispatchBuffer(generateBuffer(888888888888L));
        subject.dispatchBuffer(generateBuffer(MAX_DOM_CLOCK));

        assertEquals("dispatch expected",
                8, consumer.getReceivedTimes().length);

        assertEquals("no deferred records expected",
                0, subject.getDeferredRecordCount());
    }

    @Test
    public void testDeferredDispatch() throws DataProcessorError
    {

        //
        // Test deferred dispatch, where data must wait for an update
        // to the rapcal upper bound to be dispatched
        //

        long GPS_OFFSET = 12314;
        MockRapCal rapcal = new MockRapCal(GPS_OFFSET);
        MockBufferConsumer consumer = new MockBufferConsumer();
        UTCMonotonicDispatcher subject = new UTCMonotonicDispatcher(consumer,
                DataProcessor.StreamType.HIT,
                rapcal);


        rapcal.setUpperBound(Long.MIN_VALUE);

        long[] beforeMark_1 =
                {
                        0,
                        1

                };
        long MARK_1 = 1;
        long[] beforeMark_2 =
                {
                        2,
                        3,
                        33333,
                        44444,
                        55555,
                        100000

                };
        long MARK_2 = 100000;
        long[] beforeMark_3 =
                {
                        100001,
                        22222222,
                        55555555,
                        7777777777L,
                        8888888888L,
                        9999999999L
                };
        long MARK_3 = 9999999999L;
        long[] afterMark_3 =
                {
                        MARK_3 + 1,
                        MARK_3 + 1111111,
                        MARK_3 + 333333333,
                        MARK_3 + 999999999999L,
                };

        for (int i = 0; i < beforeMark_1.length; i++)
        {
            subject.dispatchBuffer(generateBuffer(beforeMark_1[i]));
        }
        assertEquals("no dispatch expected",
                0, consumer.getReceivedTimes().length);
        assertEquals("deferred records expected",
                2, subject.getDeferredRecordCount());
        consumer.clear();

        rapcal.setUpperBound(MARK_1);
        for (int i = 0; i < beforeMark_2.length; i++)
        {
            subject.dispatchBuffer(generateBuffer(beforeMark_2[i]));
        }
        assertArrayEquals("dispatch expected",
                toUTC(beforeMark_1, GPS_OFFSET),
                consumer.getReceivedTimes());
        assertEquals("deferred records expected",
                6, subject.getDeferredRecordCount());
        consumer.clear();

        rapcal.setUpperBound(MARK_2);
        for (int i = 0; i < beforeMark_3.length; i++)
        {
            subject.dispatchBuffer(generateBuffer(beforeMark_3[i]));
        }
        assertArrayEquals("dispatch expected",
                toUTC(beforeMark_2, GPS_OFFSET),
                consumer.getReceivedTimes());
        assertEquals("deferred records expected",
                6, subject.getDeferredRecordCount());
        consumer.clear();

        rapcal.setUpperBound(MARK_3);
        for (int i = 0; i < afterMark_3.length; i++)
        {
            subject.dispatchBuffer(generateBuffer(afterMark_3[i]));
        }
        assertArrayEquals("dispatch expected",
                toUTC(beforeMark_3, GPS_OFFSET),
                consumer.getReceivedTimes());
        assertEquals("deferred records expected",
                4, subject.getDeferredRecordCount());
        consumer.clear();

        // eos should dispatch remaining, plus EOS
        subject.eos(generateBuffer(Long.MAX_VALUE));
        assertArrayEquals("dispatch expected",
                append(toUTC(afterMark_3, GPS_OFFSET), Long.MAX_VALUE),
                consumer.getReceivedTimes());
        assertEquals("no deferred records expected",
                0, subject.getDeferredRecordCount());

    }

    @Test
    public void testEOS() throws DataProcessorError
    {
        //
        // Test that EOS drains the deferred data and propagates EOS.
        //
        MockRapCal rapcal = new MockRapCal(99999);
        MockBufferConsumer consumer = new MockBufferConsumer();
        UTCMonotonicDispatcher subject = new UTCMonotonicDispatcher(consumer,
                DataProcessor.StreamType.HIT,
                rapcal);

        // set upper bound to cover no times
        rapcal.setUpperBound(Long.MIN_VALUE);

        subject.dispatchBuffer(generateBuffer(0));
        subject.dispatchBuffer(generateBuffer(1));
        subject.dispatchBuffer(generateBuffer(100000000));
        subject.dispatchBuffer(generateBuffer(444444444));
        subject.dispatchBuffer(generateBuffer(999999999));
        subject.dispatchBuffer(generateBuffer(777777777777L));
        subject.dispatchBuffer(generateBuffer(888888888888L));
        subject.dispatchBuffer(generateBuffer(MAX_DOM_CLOCK));

        assertEquals("no dispatch expected",
                0, consumer.getReceivedTimes().length);

        assertEquals("deferred records expected",
                8, subject.getDeferredRecordCount());

        subject.eos(generateBuffer(Long.MAX_VALUE));

        assertEquals("full dispatch expected",
                9, consumer.getReceivedTimes().length);

        assertEquals("no deferred records expected",
                0, subject.getDeferredRecordCount());

        assertEquals("eos marker expected",
                Long.MAX_VALUE, consumer.getReceivedTimes()[8]);
    }

    @Test
    public void testExceptionOnEOS() throws DataProcessorError
    {

        //
        // Test that EOS is sent even if deferred data generates an error
        //

        MockRapCal rapcal = new MockRapCal(99999);
        MockBufferConsumer consumer = new MockBufferConsumer();
        UTCMonotonicDispatcher subject = new UTCMonotonicDispatcher(consumer,
                DataProcessor.StreamType.HIT,
                rapcal);

        // set upper bound to cover no times
        rapcal.setUpperBound(Long.MIN_VALUE);

        subject.dispatchBuffer(generateBuffer(0));
        subject.dispatchBuffer(generateBuffer(1));
        subject.dispatchBuffer(generateBuffer(100000000));
        subject.dispatchBuffer(generateBuffer(444444444));
        subject.dispatchBuffer(generateBuffer(999999999));
        subject.dispatchBuffer(generateBuffer(777777777777L));
        subject.dispatchBuffer(generateBuffer(888888888888L));
        subject.dispatchBuffer(generateBuffer(MAX_DOM_CLOCK));

        assertEquals("no dispatch expected",
                0, consumer.getReceivedTimes().length);

        assertEquals("deferred records expected",
                8, subject.getDeferredRecordCount());

        consumer.setErrorMode(new MockBufferConsumer.CallNumberErrorMode(1,1, false));

        try
        {
            subject.eos(generateBuffer(Long.MAX_VALUE));
            fail("Exception expected");
        }
        catch (DataProcessorError dataProcessorError)
        {
            //expected, but EOS should still be generated

            assertEquals("only EOS dispatch expected",
                    1, consumer.getReceivedTimes().length);

            assertEquals("remaining deferred records expected",
                    7, subject.getDeferredRecordCount());

            assertEquals("eos marker expected",
                    Long.MAX_VALUE, consumer.getReceivedTimes()[0]);

        }

    }

    @Test
    public void testErrorOnEOS() throws DataProcessorError
    {

        //
        // Test that EOS is sent even if deferred data generates an error
        //

        MockRapCal rapcal = new MockRapCal(99999);
        MockBufferConsumer consumer = new MockBufferConsumer();
        UTCMonotonicDispatcher subject = new UTCMonotonicDispatcher(consumer,
                DataProcessor.StreamType.HIT,
                rapcal);

        // set upper bound to cover no times
        rapcal.setUpperBound(Long.MIN_VALUE);

        subject.dispatchBuffer(generateBuffer(0));
        subject.dispatchBuffer(generateBuffer(1));
        subject.dispatchBuffer(generateBuffer(100000000));
        subject.dispatchBuffer(generateBuffer(444444444));
        subject.dispatchBuffer(generateBuffer(999999999));
        subject.dispatchBuffer(generateBuffer(777777777777L));
        subject.dispatchBuffer(generateBuffer(888888888888L));
        subject.dispatchBuffer(generateBuffer(MAX_DOM_CLOCK));

        assertEquals("no dispatch expected",
                0, consumer.getReceivedTimes().length);

        assertEquals("deferred records expected",
                8, subject.getDeferredRecordCount());

        consumer.setErrorMode(new MockBufferConsumer.CallNumberErrorMode(1,1, true));

        try
        {
            subject.eos(generateBuffer(Long.MAX_VALUE));
            fail("Exception expected");
        }
        catch (Throwable th)
        {
            //expected, but EOS should still be generated

            assertEquals("only EOS dispatch expected",
                    1, consumer.getReceivedTimes().length);

            assertEquals("remaining deferred records expected",
                    7, subject.getDeferredRecordCount());

            assertEquals("eos marker expected",
                    Long.MAX_VALUE, consumer.getReceivedTimes()[0]);

        }

    }

    @Test
    public void testMaxDeferred() throws DataProcessorError
    {

        //
        // Test the bound on the maximum number of deferred records
        //

        long GPS_OFFSET = 12314;
        MockRapCal rapcal = new MockRapCal(GPS_OFFSET);
        MockBufferConsumer consumer = new MockBufferConsumer();
        UTCMonotonicDispatcher subject = new UTCMonotonicDispatcher(consumer,
                DataProcessor.StreamType.HIT,
                rapcal);

        int max = UTCMonotonicDispatcher.MAX_DEFFERED_RECORDS;
        for(int i=0; i<max-1; i++)
        {
            subject.dispatchBuffer(generateBuffer(10000 + i));
        }

        try
        {
            subject.dispatchBuffer(generateBuffer(9999999));
            fail("Expected limit error");
        }
        catch (DataProcessorError dpe)
        {
            String expected = "Over limit of "+ max +" records waiting for" +
                    " rapcal DOM clock range [" + 10000 + ", " + 9999999 +"]";
            assertEquals("", expected, dpe.getMessage());
        }

        assertEquals("no dispatch expected", 0, consumer.receivedTimes.size());

        //should be possible to dispatch remaining
        subject.eos(generateBuffer(Long.MAX_VALUE));

        // max + 1 becaus EOS marker is delivered also
        assertEquals("dispatch expected", max+1, consumer.receivedTimes.size());

    }

    @Test
    public void testCallback() throws DataProcessorError
    {
        //test the callback mechanism
        long GPS_OFFSET = -99999;
        MockRapCal rapcal = new MockRapCal(GPS_OFFSET);
        MockBufferConsumer consumer = new MockBufferConsumer();
        MockDispatchCallback callback = new MockDispatchCallback();
        UTCMonotonicDispatcher subject = new UTCMonotonicDispatcher(consumer,
                DataProcessor.StreamType.HIT,
                rapcal);


        long[] beforeMark =
                {
                        1111111, 2222222, 3333333, 4444444, 5555555
                };
        long MARK = 5555555;
        long[] afterMark =
                {
                        6666666, 7777777, 8888888
                };
        int lastElementIdx = afterMark.length-1;

        rapcal.setUpperBound(MARK);
        for (int i = 0; i < beforeMark.length; i++)
        {
            subject.dispatchBuffer(generateBuffer(beforeMark[i]), callback);
        }
        for (int i = 0; i < lastElementIdx; i++)
        {
            subject.dispatchBuffer(generateBuffer(afterMark[i]), callback);
        }
        assertArrayEquals("callback expected",
                toUTC(beforeMark, GPS_OFFSET),
                callback.getReceivedTimes());
        callback.clear();

        rapcal.setUpperBound(9999999);
        subject.dispatchBuffer(generateBuffer(afterMark[lastElementIdx]),
                callback);
        assertArrayEquals("callback expected",
                toUTC(afterMark, GPS_OFFSET),
                callback.getReceivedTimes());

    }

    /**
     * Append a value to the end of an array.
     */
    private static long[] append(long[] orig, long value)
    {
        long[] ret = new long[orig.length + 1];
        System.arraycopy(orig, 0, ret, 0, orig.length);
        ret[orig.length] = value;
        return ret;
    }

    private static long[] toUTC(long[] dom, long GPSOffset)
    {
        long[] utc = new long[dom.length];
        for (int i = 0; i < dom.length; i++)
        {
            utc[i] = dom[i] * 250 + GPSOffset;
        }
        return utc;
    }

    private static ByteBuffer generateBuffer(long domclock)
    {
        ByteBuffer buf = ByteBuffer.allocate(32);
        buf.putLong(24, domclock);
        return buf;
    }

    private static class MockDispatchCallback
            implements DataDispatcher.DispatchCallback
    {
        List<Long> receivedTimes = new ArrayList<Long>(10);

        @Override
        public void wasDispatched(final long utc)
        {
            receivedTimes.add(utc);
        }

        public long[] getReceivedTimes()
        {
            return unbox(receivedTimes);
        }

        public void clear()
        {
            receivedTimes.clear();
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
    }


}
