package icecube.daq.sender;

import icecube.daq.common.MockAppender;
import icecube.daq.io.OutputChannel;
import icecube.daq.monitoring.BatchHLCReporter;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.impl.SourceID;
import icecube.daq.sender.test.HitGenerator;
import icecube.daq.sender.test.MockOutputChannel;
import icecube.daq.sender.test.MockRegistry;
import icecube.daq.stringhub.test.MockBufferCache;
import icecube.daq.util.DOMInfo;
import icecube.daq.util.DOMRegistryException;
import icecube.daq.util.IDOMRegistry;
import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;


/**
 * Tests TriggerChannel.java
 */
public abstract class TriggerChannelTest
{
    private final MockAppender appender = new MockAppender();

    ISourceID srcID = new SourceID(99);
    MockOutputChannel mock = new MockOutputChannel();
    IByteBufferCache cache = new MockBufferCache("test");
    IDOMRegistry registry = new MockRegistry();
    boolean forwardSLCHits = false;

    OutputChannel subjectHLC;
    OutputChannel subjectCLC;

    HitGenerator generator = new HitGenerator();

    abstract OutputChannel createSubject(OutputChannel target, ISourceID srcID, IByteBufferCache cache,
                                         IDOMRegistry registry, boolean forwardIsolatedHits);

    @Before
    public void setUp() throws IOException
    {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);

        subjectHLC =
                new TriggerChannel.FilteredOutputTransitional(
                        mock, srcID, cache, registry, forwardSLCHits,
                        new BatchHLCReporter(111) );

        subjectCLC =
                new TriggerChannel.FilteredOutputTransitional(
                        mock, srcID, cache, registry, forwardSLCHits,
                        new BatchHLCReporter(111) );
    }

    @After
    public void tearDown()
    {
        appender.assertNoLogMessages();
    }

    @Test
    public void testHitFiltering()
    {

        // HLC/Non HLC filtering
        subjectHLC.receiveByteBuffer(generator.generateHit(false, (short) 0));
        assertEquals(0, mock.received.size());

        subjectHLC.receiveByteBuffer(generator.generateHit(true, (short) 0));
        assertEquals(1, mock.received.size());

        subjectHLC.receiveByteBuffer(generator.generateHit(false, (short) 0));
        assertEquals(1, mock.received.size());

        // trigger mode 4 pass
        subjectHLC.receiveByteBuffer(generator.generateHit(false, (short) 4));
        assertEquals(2, mock.received.size());

        // bad payload
        subjectHLC.receiveByteBuffer(ByteBuffer.allocate(100));
        assertEquals(2, mock.received.size());

        assertEquals(1, appender.getNumberOfMessages());
        String expected = "Ignoring PayloadException:";
        assertEquals(expected, appender.getMessage(0));
        appender.clear();

    }

    public static class TestLegacy extends TriggerChannelTest
    {

        @Override
        OutputChannel createSubject(final OutputChannel target,
                                    final ISourceID srcID,
                                    final IByteBufferCache cache,
                                    final IDOMRegistry registry,
                                    final boolean forwardIsolatedHits)
        {
            return  new TriggerChannel.FilteredOutputTransitional(
                    mock, srcID, cache, registry, forwardSLCHits,
                    new BatchHLCReporter(111) );
        }
    }

    public static class TestNew extends TriggerChannelTest
    {

        @Override
        OutputChannel createSubject(final OutputChannel target,
                                    final ISourceID srcID,
                                    final IByteBufferCache cache,
                                    final IDOMRegistry registry,
                                    final boolean forwardIsolatedHits)
        {
            return  new TriggerChannel.FilteredOutput(
                    mock, srcID, cache, registry, forwardSLCHits,
                    new BatchHLCReporter(111) );
        }
    }

    public static class TestCompare
    {

        private final MockAppender appender = new MockAppender();

        ISourceID srcID = new SourceID(99);
        IByteBufferCache cache = new MockBufferCache("test");
        IDOMRegistry registry = new MockRegistry();
        boolean forwardSLCHits = false;

        OutputChannel subjectA;
        MockOutputChannel mockA = new MockOutputChannel();
        OutputChannel subjectB;
        MockOutputChannel mockB = new MockOutputChannel();

        HitGenerator generator = new HitGenerator();

        @Before
        public void setUp() throws IOException
        {
            BasicConfigurator.resetConfiguration();
            BasicConfigurator.configure(appender);

            subjectA =
                    new TriggerChannel.FilteredOutputTransitional(
                            mockA, srcID, cache, registry, forwardSLCHits,
                            new BatchHLCReporter(111) );

            subjectB =
                    new TriggerChannel.FilteredOutput(
                            mockB, srcID, cache, registry, forwardSLCHits,
                            new BatchHLCReporter(111) );
        }

        @After
        public void tearDown()
        {
            appender.assertNoLogMessages();
        }

        @Test
       public void compareOutput()
       {
           ///
           /// Compare the output of both filter implementations
           ///
           for (int i = 0; i<1000; i++)
           {
               boolean isLC = Math.random() > 0.5;
               ByteBuffer hit = generator.generateHit(isLC, (short) 1);
               subjectA.receiveByteBuffer(hit);
               hit.rewind();
               subjectB.receiveByteBuffer(hit);
           }

           List<ByteBuffer> listA = mockA.received;
           List<ByteBuffer> listB = mockB.received;

           assertEquals(listA.size(), listB.size());

           for (int i = 0; i < listA.size(); i++)
           {
               ByteBuffer bbA = listA.get(i);
               ByteBuffer bbB = listB.get(i);

               assertArrayEquals(bbA.array(), bbB.array());
           }
       }
    }




}
