package icecube.daq.performance.binary.store.impl;

import icecube.daq.common.MockAppender;
import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.WritableRecordBuffer;
import icecube.daq.performance.binary.store.RecordStore;
import icecube.daq.performance.binary.store.impl.test.Mock;
import icecube.daq.performance.binary.test.RecordGenerator;
import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests SplitStore.java
 */
public class SplitStoreTest
{


    public static final int MIN_SPAN = 1024;
    public static final int MAX_SPAN = 2048;
    RecordGenerator generator =
            new RecordGenerator.RandomLengthRecordProvider(333);

    Mock.MockBuffer mockBuffer = new Mock.MockBuffer();
    Mock.MockIndex mockIndex = new Mock.MockIndex();
    Mock.MockSearch mockSearch = new Mock.MockSearch();

    Mock.MockStore primary = new Mock.MockStore(true);
    Mock.MockStore secondary = new Mock.MockStore(true);
    RecordStore.OrderedWritable subject =
           new SplitStore(generator.recordReader(), generator.orderingField(),
                   primary, secondary, MIN_SPAN, MAX_SPAN);

    Consumer<RecordBuffer> sink = new Consumer<RecordBuffer>()
    {
        @Override
        public void accept(final RecordBuffer buffer)
        {
        }

        @Override
        public String toString()
        {
            return "text-xyzzy";
        }
    };

    MockAppender appender;

    @Before
    public void setUp()
    {
        mockBuffer.reset();
        mockIndex.reset();
        mockSearch.reset();
        primary.reset();
        secondary.reset();

        appender = new MockAppender();
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);
    }

    @After
    public void tearDown()
    {
        int numberOfMessages = appender.getNumberOfMessages();
        for(int msg = 0; msg<numberOfMessages; msg++)
        {
            System.out.println(appender.getMessage(msg));
        }
        appender.assertNoLogMessages();
    }


    @Test
    public void testConstruction()
    {
        ///
        /// Tests method delegation
        ///
        try
        {
            new SplitStore(generator.recordReader(), generator.orderingField(),
                    primary, secondary, 100, 99);
            fail("Illegal ranges accepted");
        }
        catch (IllegalArgumentException iae)
        {
            assertEquals("maxSpan: 99 > minSpan: 100", iae.getMessage());
        }
    }

    @Test
    public void testAvailable()
    {
        ///
        /// Tests method delegation
        ///
        subject.available();
        secondary.assertCalls("available()");
    }

    @Test
    public void testCloseWrite() throws IOException
    {
        ///
        /// Tests method delegation
        ///
        subject.closeWrite();

        primary.assertCalls("closeWrite()");
        secondary.assertCalls("closeWrite()");
    }


    @Test
    public void testStore() throws IOException
    {
        ///
        /// Tests store() method
        ///

        // within the max span no pruning should occur
        long[] values = new long[]
                {
                        Long.MIN_VALUE, (Long.MIN_VALUE + 1),
                        (Long.MIN_VALUE + (MAX_SPAN - 100)),
                        (Long.MIN_VALUE + (MAX_SPAN - 1)),
                        (Long.MIN_VALUE + MAX_SPAN)
                };
        for (int i = 0; i < values.length; i++)
        {
            ByteBuffer record = generator.generate(values[i]);
            String recordStr = record.toString();
            subject.store(record);
            primary.assertCalls("store(" + recordStr + ")");
            secondary.assertCalls("store(" + recordStr + ")");

            primary.reset();
            secondary.reset();
        }


        // once the max span is exceeded, the primary should be pruned
        // to the min span
        long value = Long.MIN_VALUE + MAX_SPAN + 1;
        long expectedPruneValue = value-MIN_SPAN;
        ByteBuffer record = generator.generate(value);
        String recordStr = record.toString();
        subject.store(record);
        primary.assertCalls("store(" + recordStr + ")",
                            "prune(" + expectedPruneValue + ")");
        secondary.assertCalls("store(" + recordStr + ")");

    }

    @Test
    public void testQuery() throws IOException
    {
        ///
        /// Tests extractRange() and forEach() methods
        ///

        // within the max span no pruning should occur
        // and queries should be serviced from the primary.
        long[] values = new long[]
                {
                        Long.MIN_VALUE, (Long.MIN_VALUE + 1),
                        (Long.MIN_VALUE + (MAX_SPAN - 100)),
                        (Long.MIN_VALUE + (MAX_SPAN - 1)),
                        (Long.MIN_VALUE + MAX_SPAN)
                };
        for (int i = 0; i < values.length; i++)
        {
            ByteBuffer record = generator.generate(values[i]);
            String recordStr = record.toString();
            subject.store(record);
            primary.assertCalls("store(" + recordStr + ")");
            secondary.assertCalls("store(" + recordStr + ")");

            primary.reset();
            secondary.reset();
        }

        subject.extractRange(Long.MIN_VALUE, Long.MAX_VALUE);
        primary.assertCalls("extractRange(" + Long.MIN_VALUE +
                ", " + Long.MAX_VALUE + ")");
        secondary.assertCalls();
        primary.reset();
        secondary.reset();


        // once the max span is exceeded, the primary should be pruned
        // to the min span, queries should split at the boundary of the
        // split
        long value = Long.MIN_VALUE + MAX_SPAN + 1;
        long expectedPruneValue = value-MIN_SPAN;
        ByteBuffer record = generator.generate(value);
        String recordStr = record.toString();
        subject.store(record);
        primary.assertCalls("store(" + recordStr + ")",
                "prune(" + expectedPruneValue + ")");
        secondary.assertCalls("store(" + recordStr + ")");

        primary.reset();
        secondary.reset();

        //extractRange(): all primary
        subject.extractRange(expectedPruneValue, Long.MAX_VALUE);
        primary.assertCalls(formatExtractCall(expectedPruneValue, Long.MAX_VALUE) );
        secondary.assertCalls();
        primary.reset();
        secondary.reset();

        //extractRange(): all secondary
        subject.extractRange(Long.MIN_VALUE, expectedPruneValue-1);
        primary.assertCalls();
        secondary.assertCalls(formatExtractCall(Long.MIN_VALUE, expectedPruneValue-1));
        primary.reset();
        secondary.reset();

        //extractRange(): split
        subject.extractRange(Long.MIN_VALUE, Long.MAX_VALUE);
        primary.assertCalls(formatExtractCall(expectedPruneValue, Long.MAX_VALUE) );
        secondary.assertCalls(formatExtractCall(Long.MIN_VALUE, expectedPruneValue-1));
        primary.reset();
        secondary.reset();




        //forEach(): all primary
        subject.forEach(sink, expectedPruneValue, Long.MAX_VALUE);
        primary.assertCalls(formatForEachCall(sink, expectedPruneValue, Long.MAX_VALUE) );
        secondary.assertCalls();
        primary.reset();
        secondary.reset();

        //forEach(): all secondary
        subject.forEach(sink, Long.MIN_VALUE, expectedPruneValue - 1);
        primary.assertCalls();
        secondary.assertCalls(formatForEachCall(sink, Long.MIN_VALUE, expectedPruneValue - 1));
        primary.reset();
        secondary.reset();

        //forEach(): split
        subject.forEach(sink, Long.MIN_VALUE, Long.MAX_VALUE);
        primary.assertCalls(formatForEachCall(sink, expectedPruneValue, Long.MAX_VALUE) );
        secondary.assertCalls(formatForEachCall(sink, Long.MIN_VALUE, expectedPruneValue - 1));
        primary.reset();
        secondary.reset();


        // illegal query
        try
        {
            subject.extractRange(10, 0);
            fail("illegal query");
        }
        catch (IllegalArgumentException iae)
        {
            assertEquals("Illegal query range [10 - 0]", iae.getMessage());
        }
        try
        {
            subject.forEach(null, 10, 0);
            fail("illegal query");
        }
        catch (IllegalArgumentException iae)
        {
            assertEquals("Illegal query range [10 - 0]", iae.getMessage());
        }

    }

    @Test
    public void testPrimaryOverflow() throws IOException
    {
        ///
        /// Tests behavior when the size of data in the pruning span
        /// exceeds the size of the primary buffer
        ///
        int SIZE = 1024 * 1024 * 1;
        RecordStore.Prunable primary =
                new RingBufferRecordStore(generator.recordReader(), generator.orderingField(), SIZE);
        Mock.MockStore secondary = new Mock.MockStore(true);
        RecordStore.OrderedWritable subject =
                new SplitStore(generator.recordReader(), generator.orderingField(),
                        primary, secondary, MIN_SPAN, Long.MAX_VALUE/2);

        int overflowsObserved = 0;
        long primarySize = 0;
        long value = 123;
        long boundary = value - MIN_SPAN;
        while(overflowsObserved < 10)
        {
            ByteBuffer item = generator.generate(value);
            int itemSize = item.remaining();
            subject.store(item);
            if( (primarySize + itemSize) > SIZE)
            {
                // primary should have overflowed here causing split store
                // to prune all records and log the condition
                primarySize = itemSize;
                overflowsObserved++;
                String expectedLogMsg =
                        String.format("In-memory hit cache too small for" +
                                " span [%d - %d], pruning to value: %d",
                                boundary, value, value);
                boundary = value;

                assertEquals(1, appender.getNumberOfMessages());
                assertEquals(expectedLogMsg, appender.getMessage(0));
                appender.clear();

                // check that the query boundary is managed correctly
                secondary.reset();
                subject.forEach(sink, Long.MIN_VALUE, value);
                secondary.assertCalls(formatForEachCall(sink, Long.MIN_VALUE, boundary-1));
            }
            else
            {
                primarySize += itemSize;
            }
            assertEquals((SIZE - primarySize), primary.available());

            value++;
        }
    }

    static String formatExtractCall(long from, long to)
    {
        return "extractRange(" + from + ", " + to + ")";
    }

    static String formatForEachCall(Consumer<RecordBuffer> consumer, long from, long to)
    {
        return "forEach(" +consumer + ", " + from + ", " + to + ")";
    }

}
