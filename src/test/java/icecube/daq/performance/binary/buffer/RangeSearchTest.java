package icecube.daq.performance.binary.buffer;

import icecube.daq.performance.binary.record.RecordReader;
import icecube.daq.performance.binary.test.Assertions;
import icecube.daq.performance.binary.test.OrderedDataCase;
import icecube.daq.performance.binary.test.RecordGenerator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests RangeSearch.java
 */
@RunWith(Parameterized.class)
public class RangeSearchTest
{


    // GeneratedData is a generalization of test data.
    // It organizes test parameters:
    //   recordType
    //   test data population
    //   queries with expected results
    // and provides for record generation and query execution.
    static class GeneratedData
    {

        final String dataID;

        final RecordGenerator recordProvider;
        final RecordBufferIndex[] indexes;
        final OrderedDataCase.QueryCase[] queries;

        final RecordBuffer data;
        final RangeSearch search;

        GeneratedData(final String dataID,
                      final RecordGenerator recordProvider,
                      final OrderedDataCase dataCase)
        {
            this.recordProvider = recordProvider;
            this.dataID = dataID;
            this.search = new RangeSearch.LinearSearch(
                    recordProvider.recordReader(),
                    recordProvider.orderingField());
            this.queries = dataCase.queries();

            this.indexes = new RecordBufferIndex[3];
            this.indexes[0] = new RecordBufferIndex.NullIndex();            //empty
            this.indexes[1] = new RecordBufferIndex.ArrayListIndex();       //full
            this.indexes[2] = new RecordBufferIndex.SparseBufferIndex(1000);//sparse

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int pos = 0;
            long[] ordinals = dataCase.ordinals();
            for (int i = 0; i < ordinals.length; i++)
            {
                ByteBuffer record = recordProvider.generate(ordinals[i]);

                ((RecordBufferIndex.UpdatableIndex)indexes[0]).addIndex(pos, ordinals[i]);
                ((RecordBufferIndex.UpdatableIndex)indexes[1]).addIndex(pos, ordinals[i]);
                ((RecordBufferIndex.UpdatableIndex)indexes[2]).addIndex(pos, ordinals[i]);

                pos += record.remaining();
                try
                {
                    bos.write(record.array());
                }
                catch (IOException e)
                {
                    throw new Error("Test construction failure", e);
                }
            }
            data = RecordBuffers.wrap(bos.toByteArray());
        }

        /**
         * Helper method for execution of test data and queries across
         * all index modes and copy modes.
         * .
         */
        public void executeTestQueries()
        {
            RecordReader recordReader = recordProvider.recordReader();
            RecordReader.LongField orderingField = recordProvider.orderingField();

            for (int que = 0; que < queries.length; que++)
            {
                OrderedDataCase.QueryCase query = queries[que];


                for(RecordBuffer.MemoryMode mode : RecordBuffer.MemoryMode.values())
                {
                    //test w/out index
                    {
                        RecordBuffer answer = search.extractRange(data, mode, query.from, query.to);
                        String msg = dataID + ": Query " + que + ", Mode " + mode + " failed";
                        Assertions.assertContainsExactly(msg, recordReader, orderingField, answer, query.expected);
                    }

                    // test for all indexes
                    for(int ind = 0; ind < indexes.length; ind++)
                    {
                        RecordBufferIndex index = indexes[ind];
                        RecordBuffer answer = search.extractRange(data, mode,
                                index, query.from, query.to);

                        String msg =  dataID + ": Query " + que + ", Index " + ind + ", Mode " + mode + " failed";
                        Assertions.assertContainsExactly(msg, recordReader, orderingField, answer, query.expected);
                    }
                }
            }

        }

        RecordBuffer executeQuery(long from, long to)
        {
            return search.extractRange(data, RecordBuffer.MemoryMode.COPY,
                    from, to);
        }

    }


    // Tests are agnostic w/respect to actual record formats,
    // test parametrized for different types
    @Parameterized.Parameter(0)
    public RecordGenerator recordType;

    @Parameterized.Parameters(name = "RingBuffer[{0}]")
    public static List<Object[]> sizes()
    {
        List<Object[]> cases = new ArrayList<Object[]>(13);
        cases.add(new Object[]{new RecordGenerator.DAQRecordProvider(1024)});
        cases.add(new Object[]{new RecordGenerator.SmallRecordProvider()});
        return cases;
    }

    @Test
    public void testNormalData()
    {


        runQueryTests("normal", OrderedDataCase.STANDARD_CASE);

    }

    @Test
    public void testEmptyData()
    {
        runQueryTests("empty", OrderedDataCase.EMPTY_CASE);
    }

    @Test
    public void testOutOfOrderData()
    {
        ///
        /// Tests that range search detects out-of order data.
        ///
        /// This only occurs for cases where the index does
        /// not obscure the discontinuity.
        long[] ordinals = new long[]
                {
                        0, 1 ,2 ,3,
                        1, //oops!
                        4, 5, 6, 7
                };

        OrderedDataCase misordered = new OrderedDataCase(ordinals,
                new OrderedDataCase.QueryCase[0]);
        GeneratedData generated = new GeneratedData("out-of-order", recordType,
            misordered);

        try
        {
            generated.executeQuery(0, 7);
            fail("RangeSearch permitted out-of-order data");
        }
        catch (Error e)
        {
            String message = e.getMessage();
            assertTrue(message.matches("Out of order record at index: [0-9]+"));
        }

        try
        {
            generated.executeQuery(4, 7);
            fail("RangeSearch permitted out-of-order data");
        }
        catch (Error e)
        {
            String message = e.getMessage();
            assertTrue(message.matches("Out of order record at index: [0-9]+"));
        }
    }

    @Test
    public void testBadLengthData() throws Exception
    {
        ///
        /// Tests that range search detects certain malformed
        /// data. (particularly a zero-length record which
        /// would otherwise induce an infinite loop.
        ///

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        BadLengthRecordProvider factory =  new BadLengthRecordProvider();
        bos.write(factory.generateTestRecord(1).array());
        bos.write(factory.generateTestRecord(2).array());
        bos.write(factory.generateTestRecord(3).array());
        bos.write(factory.generateTestRecord(4).array());
        bos.write(factory.generateBadTestRecord(5, 0).array()); // bad length!
        bos.write(factory.generateTestRecord(6).array());
        bos.write(factory.generateTestRecord(7).array());
        bos.write(factory.generateTestRecord(8).array());
        bos.write(factory.generateTestRecord(9).array());

        RecordBuffer badRecords = RecordBuffers.wrap(bos.toByteArray());

        RangeSearch.LinearSearch subject =
                new RangeSearch.LinearSearch(factory.recordReader(),
                factory.orderingField());

        try
        {
            subject.extractRange(badRecords, RecordBuffer.MemoryMode.COPY, 6, 10);
            fail("search permitted zero-length record");
        }
        catch (Error e)
        {
            String message = e.getMessage();
            assertTrue(message.matches("Invalid record length" +
                    " at index: [0-9]+"));
        }

        try
        {
            subject.extractRange(badRecords, RecordBuffer.MemoryMode.COPY, 0, 10);
            fail("search permitted zero-length record");
        }
        catch (Error e)
        {
            String message = e.getMessage();
            assertTrue(message.matches("Invalid record length" +
                    " at index: [0-9]+"));
        }

    }

    private void runQueryTests(String name, OrderedDataCase orderedDataCase)
   {
           GeneratedData generated = new GeneratedData(name, recordType,
                   orderedDataCase);
           generated.executeTestQueries();
   }



    /**
     * A record provider that allows the creation of records
     * with a corrupted length field.
     */
    private static class BadLengthRecordProvider
    {
        public RecordReader recordReader()
        {
            return new RecordReader()
            {
                @Override
                public int getLength(final ByteBuffer buffer)
                {
                    return buffer.getInt(0);
                }

                @Override
                public int getLength(final ByteBuffer buffer, final int offset)
                {
                    return buffer.getInt(offset);
                }

                @Override
                public int getLength(final RecordBuffer buffer, final int offset)
                {
                    return buffer.getInt(offset);
                }
            };
        }

        public RecordReader.LongField orderingField()
        {
            return new RecordReader.LongField()
            {
                @Override
                public long value(final ByteBuffer buffer, final int offset)
                {
                    return buffer.getLong(offset + 4);
                }

                @Override
                public long value(final RecordBuffer buffer, final int offset)
                {
                    return buffer.getLong(offset + 4);

                }
            };
        }

        public ByteBuffer generateTestRecord(long utc)
        {
            ByteBuffer res = ByteBuffer.allocate(12);
            res.putInt(12);             // length
            res.putLong(utc);           // value
            res.flip();
            return res;
        }

        public ByteBuffer generateBadTestRecord(long utc, int badLength)
        {
            ByteBuffer res = ByteBuffer.allocate(12);
            res.putInt(badLength);      // length
            res.putLong(utc);           // value
            res.flip();
            return res;
        }

    }


}
