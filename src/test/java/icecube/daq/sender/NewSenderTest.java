package icecube.daq.sender;


import icecube.daq.bindery.BufferConsumer;
import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.common.MockAppender;
import icecube.daq.io.DAQOutputChannelManager;
import icecube.daq.io.OutputChannel;
import icecube.daq.monitoring.SenderMXBean;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IReadoutRequest;
import icecube.daq.payload.IReadoutRequestElement;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.impl.ReadoutRequest;
import icecube.daq.payload.impl.ReadoutRequestElement;
import icecube.daq.payload.impl.SourceID;
import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.binary.record.RecordReader;
import icecube.daq.performance.binary.store.RecordStore;
import icecube.daq.performance.binary.store.impl.NullRecordStore;
import icecube.daq.performance.common.BufferContent;
import icecube.daq.sender.test.HitGenerator;
import icecube.daq.sender.test.MockOutputChannel;
import icecube.daq.sender.test.MockOutputChannelManager;
import icecube.daq.sender.test.MockRegistry;
import icecube.daq.stringhub.test.MockBufferCache;
import icecube.daq.util.DOMInfo;
import icecube.daq.util.IDOMRegistry;
import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import static org.junit.Assert.*;

/**
 *
 */
public class NewSenderTest
{
    private MockAppender appender;

    ISourceID srcID = new SourceID(1003);
    MockSpool mockSpool;
    MockOutputChannel mockHitOut;
    IByteBufferCache mockHitCache;
    IByteBufferCache mockReadoutCache;
    MockRegistry mockRegistry;

    HitGenerator generator = new HitGenerator();

    SenderSubsystem subject;

    @Before
    public void setUp() throws IOException
    {
        appender = new MockAppender();
        mockSpool = new MockSpool();
        mockHitOut = new MockOutputChannel();
        mockHitCache = new MockBufferCache("testHit");
        mockReadoutCache = new MockBufferCache("testReadout");
        mockRegistry = new MockRegistry();

        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);

        subject = new NewSender(srcID.getSourceID(), mockHitCache,
                mockReadoutCache, mockRegistry, mockSpool);

    }

    @After
    public void tearDown()
    {
        appender.assertNoLogMessages();

        try
        {
            subject.getReadoutRequestHandler().addRequestStop();
            subject.getHitInput().consume(MultiChannelMergeSort.eos(111));
        }
        catch (Throwable e)
        {
           //
        }

        // yuk, tear down can create spurious log messages
        try{ Thread.sleep(1000);} catch (InterruptedException e){}
        appender.clear();

    }

    @Test
    public void testConsumeBeforeStart() throws IOException
    {
        BufferConsumer input = subject.getHitInput();

        try
        {
            input.consume(generator.generateHit(true, 0));
            fail("Expected error");
        }
        catch (Error e)
        {
            //desired
            assertEquals("Sender was not started", e.getMessage());
        }
    }

    @Test
    public void testConsumeWithoutChannel() throws IOException
    {
        BufferConsumer input = subject.getHitInput();

        subject.startup();
        try
        {
            input.consume(generator.generateHit(true, 0));
            fail("Expected error");
        }
        catch (Error e)
        {
            //desired
            assertEquals("Channel not available", e.getMessage());
        }
    }

    @Test
    public void testConsumeNoSLC() throws IOException
    {
        BufferConsumer input = subject.getHitInput();
        subject.setHitOutput(new MockOutputChannelManager(mockHitOut));

        subject.startup();

        input.consume(generator.generateHit(true, 0));
        assertEquals(1, mockHitOut.received.size());

        input.consume(generator.generateHit(true, 0));
        assertEquals(2, mockHitOut.received.size());

        input.consume(generator.generateHit(true, 0));
        assertEquals(3, mockHitOut.received.size());

        input.consume(generator.generateHit(false, 0));
        assertEquals(3, mockHitOut.received.size());


        input.consume(MultiChannelMergeSort.eos(111));
        assertEquals(3, mockHitOut.received.size());
        assertTrue(mockHitOut.stopCalled);

        assertEquals(4, mockSpool.storeCount);

    }

    @Test
    public void testConsumeSLC() throws IOException
    {
        BufferConsumer input = subject.getHitInput();
        subject.setHitOutput(new MockOutputChannelManager(mockHitOut));

        subject.forwardIsolatedHitsToTrigger();
        subject.startup();

        input.consume(generator.generateHit(false, 0));
        assertEquals(1, mockHitOut.received.size());

        input.consume(generator.generateHit(false, 0));
        assertEquals(2, mockHitOut.received.size());

        input.consume(generator.generateHit(false, 0));
        assertEquals(3, mockHitOut.received.size());

        input.consume(generator.generateHit(true, 0));
        assertEquals(4, mockHitOut.received.size());


        input.consume(MultiChannelMergeSort.eos(111));
        assertEquals(4, mockHitOut.received.size());
        assertTrue(mockHitOut.stopCalled);

        SenderMXBean monitor = subject.getMonitor();
        assertEquals(4, monitor.getNumHitsReceived());

        assertEquals(4, mockSpool.storeCount);
    }

    @Test
    public void testReadout() throws IOException
    {
        final RequestHandler readout = subject.getReadoutRequestHandler();
        final SenderMXBean monitor = subject.getMonitor();
        final MockOutputChannel readoutChannel = new MockOutputChannel();
        subject.setDataOutput(new MockOutputChannelManager(readoutChannel));

        int SRC = srcID.getSourceID();
        int DOMID = 777;
        mockRegistry.setDomInfo(new DOMInfo(DOMID, SRC, 1, 3));
        mockSpool.setQueryResponse(generator.getType(),
                generator.generateHit(100, DOMID),
                generator.generateHit(200, DOMID),
                generator.generateHit(300, DOMID),
                generator.generateHit(400, DOMID),
                generator.generateHit(500, DOMID)
        );


        IReadoutRequest request  = createRequest(SRC, 98, 501);
        readout.addRequest(request);

        assertEquals(1, monitor.getNumReadoutRequestsQueued());

        subject.startup();
        try{ Thread.sleep(500);} catch (InterruptedException e){}

        assertEquals(1, monitor.getNumReadoutRequestsReceived());
        assertEquals(1, monitor.getNumReadoutsSent());
        assertEquals(1, readoutChannel.received.size());

    }

    private static IReadoutRequest createRequest(int srcID, long from, long to)
    {
        int TYPE = IReadoutRequestElement.READOUT_TYPE_GLOBAL;
        long DOM = IReadoutRequestElement.NO_DOM;
        return  createRequest(111, 222, srcID,
                new ReadoutRequestElement( TYPE, srcID, from, to, DOM));
    }

    private static IReadoutRequest createRequest(long time, int uid, int srcID,
                                          IReadoutRequestElement... elements)
    {
        ReadoutRequest rr = new ReadoutRequest(time, uid, srcID);
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

    class MockSpool implements RecordStore.OrderedWritable
    {
        private RecordReader type;
        private RecordBuffer queryResponse;

        int storeCount;

        void setQueryResponse(final RecordReader type,
                              final ByteBuffer... queryResponse)
        {
            RecordBuffer[] parts = new RecordBuffer[queryResponse.length];
            for (int i = 0; i < queryResponse.length; i++)
            {
                parts[i] = RecordBuffers.wrap(queryResponse[i],
                        BufferContent.ZERO_TO_CAPACITY);

            }
            this.queryResponse = RecordBuffers.chain(parts);
        }

        @Override
        public RecordBuffer extractRange(final long from, final long to)
                throws IOException
        {
            return queryResponse;
        }

        @Override
        public void forEach(final Consumer<RecordBuffer> action,
                            final long from, final long to) throws IOException
        {
            queryResponse.eachRecord(type);
        }

        @Override
        public void store(final ByteBuffer buffer) throws IOException
        {
            storeCount++;
        }

        @Override
        public int available()
        {
            return 0;
        }

        @Override
        public void closeWrite() throws IOException
        {
        }
    }

}
