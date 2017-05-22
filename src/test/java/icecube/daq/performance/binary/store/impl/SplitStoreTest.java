package icecube.daq.performance.binary.store.impl;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.store.RecordStore;
import icecube.daq.performance.binary.store.impl.test.Mock;
import icecube.daq.performance.binary.test.RecordGenerator;
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
    @Before
    public void setUp()
    {
        mockBuffer.reset();
        mockIndex.reset();
        mockSearch.reset();
        primary.reset();
        secondary.reset();
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

    static String formatExtractCall(long from, long to)
    {
        return "extractRange(" + from + ", " + to + ")";
    }

    static String formatForEachCall(Consumer<RecordBuffer> consumer, long from, long to)
    {
        return "forEach(" +consumer + ", " + from + ", " + to + ")";
    }

}
