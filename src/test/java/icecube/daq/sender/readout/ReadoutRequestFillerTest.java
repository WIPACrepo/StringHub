package icecube.daq.sender.readout;

import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IReadoutRequest;
import icecube.daq.payload.IReadoutRequestElement;
import icecube.daq.payload.PayloadException;
import icecube.daq.payload.impl.ReadoutRequest;
import icecube.daq.payload.impl.ReadoutRequestElement;
import icecube.daq.payload.impl.SourceID;
import icecube.daq.performance.binary.buffer.RangeSearch;
import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.binary.record.RecordReader;
import icecube.daq.performance.binary.record.UTCRecordReader;
import icecube.daq.performance.binary.record.pdaq.DaqBufferRecordReader;
import icecube.daq.performance.binary.store.RecordStore;
import icecube.daq.performance.binary.test.TestData;
import icecube.daq.performance.common.BufferContent;
import icecube.daq.stringhub.test.MockBufferCache;
import icecube.daq.util.DOMRegistryException;
import icecube.daq.util.DOMRegistryFactory;
import icecube.daq.util.IDOMRegistry;
import icecube.daq.util.LocatePDAQ;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import static org.junit.Assert.*;


/**
 *Tests ReadoutRequestFiller implementations
 */
public class ReadoutRequestFillerTest
{
    public static final DaqBufferRecordReader DATA_TYPE =
            DaqBufferRecordReader.instance;

    ReadoutRequestFiller subject;
    TestStore store;
    SourceID testSource;
    long testStartUTC = Long.MIN_VALUE;
    long testEndUTC = Long.MAX_VALUE;

    IByteBufferCache mockCache = new MockBufferCache("test");


    @Before
    public void setUp() throws IOException, DOMRegistryException, PayloadException
    {
        // ensure LocatePDAQ uses the test version of the config directory
        File configDir =
            new File(getClass().getResource("/config").getPath());
        if (!configDir.exists()) {
            throw new IllegalArgumentException("Cannot find config" +
                                               " directory under " +
                                               getClass().getResource("/"));
        }
        System.setProperty(LocatePDAQ.CONFIG_DIR_PROPERTY,
                           configDir.getAbsolutePath());

        testSource = new SourceID(27);

        ByteBuffer data = TestData.DELTA_COMPRESSED.toByteBuffer();
        store = new TestStore(DATA_TYPE, RecordBuffers.wrap(data,
                BufferContent.ZERO_TO_CAPACITY));
        IDOMRegistry doRegistry = DOMRegistryFactory.load();
        subject = new ReadoutRequestFillerImpl(testSource, doRegistry, mockCache, store);

        // survey the data for time bounds
        RecordBuffer raw = store.extractRange(Long.MIN_VALUE, Long.MAX_VALUE);
        for(Integer idx : raw.eachIndex(DATA_TYPE))
        {
            long utc = DATA_TYPE.getUTC(raw, idx);
            if(testStartUTC == Long.MIN_VALUE)
            {
                testStartUTC = utc;
            }
            testEndUTC = utc;
        }
    }

    @After
    public void tearDown()
    {
        System.clearProperty(LocatePDAQ.CONFIG_DIR_PROPERTY);
    }

    @Test
    public void testEmptyRequests() throws IOException, PayloadException
    {
        ///
        /// Test request that are out of range by time, source
        //
        {
            // empty request
            IReadoutRequest rr = createRequest(1, 99);
            ByteBuffer byteBuffer = subject.fillRequest(rr);
            assertTrue(ReadoutRequestFiller.EMPTY_READOUT_DATA == byteBuffer);
        }

        {
            // early request
            ReadoutRequestElement rre = new ReadoutRequestElement(IReadoutRequestElement.READOUT_TYPE_GLOBAL,
                    testSource.getSourceID(), testStartUTC-999999, testStartUTC-1, -1 );
            IReadoutRequest rr = createRequest(1, 99, rre);
            ByteBuffer byteBuffer = subject.fillRequest(rr);
            assertTrue(ReadoutRequestFiller.EMPTY_READOUT_DATA == byteBuffer);
        }

        {
            // late request
            ReadoutRequestElement rre = new ReadoutRequestElement(IReadoutRequestElement.READOUT_TYPE_GLOBAL,
                    testSource.getSourceID(), testEndUTC+1, testEndUTC+999999, -1 );
            IReadoutRequest rr = createRequest(1, 99, rre);
            ByteBuffer byteBuffer = subject.fillRequest(rr);
            assertTrue(ReadoutRequestFiller.EMPTY_READOUT_DATA == byteBuffer);
        }

    }

    private IReadoutRequest createRequest(long time,
                                         int uid, IReadoutRequestElement... elements)
   {
       ReadoutRequest rr = new ReadoutRequest(time, uid, testSource.getSourceID());
       for (int i = 0; i < elements.length; i++)
       {
           IReadoutRequestElement element = elements[i];
           rr.addElement(element.getReadoutType(),
                   element.getSourceID().getSourceID(),
                   element.getFirstTime(), element.getLastTime(),
                   element.getDomID().longValue());
       }
       return rr;
   }

    //private IReadoutRequest createRequest

    /**
     * Wraps a buffer as a store.
     * @param <T>
     */
    class TestStore<T extends UTCRecordReader & RecordReader> implements RecordStore.Ordered
    {
        final T type;
        final RecordBuffer buffer;
        final RangeSearch search;


        TestStore(final T type, final RecordBuffer buffer)
        {
            this.type = type;
            this.buffer = buffer;
            search = new RangeSearch.LinearSearch(type, new UTCRecordReader.UTCField(type));
        }

        @Override
        public RecordBuffer extractRange(final long from, final long to) throws IOException
        {
            return search.extractRange(buffer, RecordBuffer.MemoryMode.COPY, from, to);
        }

        @Override
        public void forEach(final Consumer<RecordBuffer> action, final long from, final long to) throws IOException
        {
            RecordBuffer view = search.extractRange(buffer, RecordBuffer.MemoryMode.COPY, from, to);
            for (RecordBuffer rb : view.eachRecord(DATA_TYPE))
            {
                action.accept(rb);
            }
        }
    }

}
