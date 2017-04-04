package icecube.daq.performance.binary.store.impl;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.store.RecordStore;
import icecube.daq.performance.binary.store.impl.test.Mock;
import icecube.daq.performance.binary.test.RandomOrderedValueSequence;
import icecube.daq.performance.binary.test.RecordGenerator;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;

/**
 * Tests AutoPruningRecordStore.java
 */
public class IndexingRecordStoreTest
{


    RecordGenerator generator =
            new RecordGenerator.RandomLengthRecordProvider(333);

    Mock.MockBuffer mockBuffer = new Mock.MockBuffer();
    Mock.MockIndex mockIndex = new Mock.MockIndex();
    Mock.MockSearch mockSearch = new Mock.MockSearch();

    RecordStore.OrderedWritable subject =
            new IndexingRecordStore(generator.recordReader(),
                    generator.orderingField(),
                    mockBuffer, mockIndex, mockSearch);

    @Before
    public void setUp()
    {
        mockBuffer.reset();
        mockIndex.reset();
    }

    @Test
    public void testConstructors()
    {

        ///
        /// Tests alternate constructors
        ///
        RecordStore.OrderedWritable product  =
                new IndexingRecordStore(generator.recordReader(),
                generator.orderingField(), mockIndex, 1024);

        assertEquals(1024, product.available());


    }
    @Test
    public void voidTestDelegation() throws IOException
    {
        ///
        /// Tests that calls are delegated through to buffer
        ///
        subject.available();
        subject.closeWrite();
        mockBuffer.assertCalls("available()", "closeWrite()");
    }

    @Test
    public void testIndexing() throws IOException
    {
        ///
        /// Tests that store calls are intercepted and the value added
        /// to the index
        ///
        RandomOrderedValueSequence seq = new RandomOrderedValueSequence();
        for(int i = 0; i < 100; i++)
        {
            long val = seq.next();
            ByteBuffer record = generator.generate(val);
            String recordStr = record.toString();
            int pos = mockBuffer.size;
            subject.store(record);

            mockBuffer.assertCalls("getLength()", "put(" + recordStr + ")");
            mockIndex.assertCalls("addIndex(" +pos +", " + val + ")");
            mockBuffer.reset();
            mockIndex.reset();
        }

    }

    @Test
    public void testSearch() throws IOException
    {
        ///
        /// Tests that searches are implemented using the index
        ///
        subject.extractRange(111,222);
        String expected = "extractRange("+mockBuffer.toString()+", "
                + RecordBuffer.MemoryMode.COPY.toString()+", " +
                mockIndex.toString() + ", 111, 222)";
        mockSearch.assertCalls(expected);

        mockSearch.reset();
        Consumer<RecordBuffer> consumer = new Consumer<RecordBuffer>()
        {
            @Override
            public void accept(final RecordBuffer buffer)
            {
            }
        };
        subject.forEach(consumer, 555, 666);
        expected = "extractRange("+mockBuffer.toString()+", "
                + RecordBuffer.MemoryMode.SHARED_VIEW.toString()+", " +
                mockIndex.toString() + ", 555, 666)";
        mockSearch.assertCalls(expected);

        mockSearch.lastIteratorMock.assertConsumer(consumer);
    }









}
