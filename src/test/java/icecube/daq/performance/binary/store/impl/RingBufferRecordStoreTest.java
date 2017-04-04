package icecube.daq.performance.binary.store.impl;

import icecube.daq.performance.binary.buffer.IndexFactory;
import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBufferIndex;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.binary.store.RecordStore;
import icecube.daq.performance.binary.store.impl.test.Mock;
import icecube.daq.performance.binary.test.RecordGenerator;
import icecube.daq.performance.common.PowersOfTwo;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

/**
 * Tests RingBufferRecordStore.java
 */
public class RingBufferRecordStoreTest
{

    // prune needs a real buffer
    Mock.DelegatingBuffer mockBuffer =
            new Mock.DelegatingBuffer(RecordBuffers.ring(PowersOfTwo._131072));
    Mock.DelegatingIndex mockIndex = new Mock.DelegatingIndex(new RecordBufferIndex.ArrayListIndex());
    Mock.MockSearch mockSearch = new Mock.MockSearch();

    IndexFactory indexFactory = new IndexFactory()
    {
        @Override
        public RecordBufferIndex.UpdatableIndex newIndex()
        {
            return mockIndex;
        }
    };

    RecordGenerator generator =
            new RecordGenerator.FixedLengthRecordProvider(128);

    RecordStore.Prunable subject = new RingBufferRecordStore(generator.recordReader(),
            generator.orderingField(), mockBuffer,  indexFactory, mockSearch);

    @Test
    public void testConstructors() throws IOException
    {
        ///
        /// Tests alternate constructors
        ///

        {
            RingBufferRecordStore product =
                    new RingBufferRecordStore(generator.recordReader(),
                            generator.orderingField(), PowersOfTwo._1024);

            assertEquals(1024, product.available());
        }

        {
            RingBufferRecordStore product =
                    new RingBufferRecordStore(generator.recordReader(),
                            generator.orderingField(), 1111);

            assertEquals(1111, product.available());
        }

    }

    @Test
    public void testDelegation() throws IOException
    {
        ///
        /// Tests that calls are delegated through to base store
        ///

        // test delegation
        ByteBuffer record = generator.generate(19445);
        String recordStr = record.toString();

        subject.available();
        mockBuffer.assertCalls("available()");
        mockBuffer.reset();

        subject.store(record);
        mockBuffer.assertCalls("getLength()", "put(" + recordStr + ")");
        mockIndex.assertCalls("addIndex("+0 + ", " + 19445 + ")");
        mockBuffer.reset();
        mockIndex.reset();

        subject.extractRange(88, -72);
        mockSearch.assertCalls("extractRange(" + mockBuffer + ", " +
                RecordBuffer.MemoryMode.COPY + ", " + mockIndex + ", 88, -72)");
        mockSearch.reset();

        subject.forEach(null, 123, 999);
        mockSearch.assertCalls("extractRange(" + mockBuffer + ", " +
                RecordBuffer.MemoryMode.SHARED_VIEW + ", " + mockIndex + ", 123, 999)");
        mockSearch.reset();

    }


    @Test
    public void testPrune() throws IOException
    {
        ///
        /// Tests prune()
        ///

        subject.prune(998);
        mockBuffer.assertCalls("getLength()", "prune(0)");
        mockIndex.assertCalls("lessThan(998)", "update(0)");
        mockBuffer.reset();
        mockIndex.reset();

        // fill some data
        int COUNT=1000;
        int[] positions = new int[COUNT];
        long[] values = new long[COUNT];
        int pos=0;
        long value=1888;
        for(int i=0; i<COUNT; i++)
        {
            ByteBuffer record = generator.generate(value);
            String recordStr = record.toString();
            int size = record.remaining();
            subject.store(record);
            mockBuffer.assertCalls("getLength()", "put(" + recordStr + ")");
            mockIndex.assertCalls("addIndex(" + pos + ", " + value + ")");

            mockBuffer.reset();
            mockIndex.reset();

            positions[i] = pos;
            values[i] = value;

            value += 1987;
            pos += size;
        }

        // prune to a specific value
        int translate = 0;
        for (int i = 5; i < values.length-1; i++)
        {
            value = values[i];

            subject.prune(value+1);

            int nextPos = positions[i+1] - translate;
            mockBuffer.assertCertainCalls("prune", "prune(" + nextPos + ")");
            mockIndex.assertCertainCalls("update", "update(" + nextPos + ")");
            translate += nextPos;

            mockBuffer.reset();
            mockIndex.reset();
        }

    }

    public void testCloseWrite() throws IOException
    {
        subject.closeWrite();
    }



}
