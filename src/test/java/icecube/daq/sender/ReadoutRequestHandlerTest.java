package icecube.daq.sender;

import icecube.daq.common.MockAppender;
import icecube.daq.payload.IReadoutRequest;
import icecube.daq.payload.PayloadException;
import icecube.daq.payload.impl.ReadoutRequest;
import icecube.daq.sender.readout.ReadoutRequestFiller;
import icecube.daq.sender.test.MockOutputChannel;
import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests ReadoutRequestHandler.java
 */
public class ReadoutRequestHandlerTest
{
    private MockAppender appender;

    private MockRequestFiller mockFiller;
    private SenderCounters counters;
    private MockOutputChannel mockOutputChannel;
    private ReadoutRequestHandler subject;

    @Before
    public void setUp() throws IOException
    {
        appender = new MockAppender();
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);

        counters = new SenderCounters();
        mockFiller = new MockRequestFiller();
        mockOutputChannel = new MockOutputChannel();
        subject = new ReadoutRequestHandler(counters, mockFiller,
                mockOutputChannel);
        subject.startup();

    }

    @After
    public void tearDown()
    {
        try
        {
            appender.assertNoLogMessages();
        }
        finally
        {
            try
            {
                subject.addRequestStop();
            }
            catch (Throwable th)
            {
                throw new Error("Bad cleanup", th);
            }
        }
    }

    @Test
    public void testProcessingLoop() throws IOException
    {
        IReadoutRequest req = new ReadoutRequest(123,123,123);
        ByteBuffer mockResponse = ByteBuffer.allocate(123);
        mockFiller.setResponse(mockResponse);
        subject.addRequest(req);

        try{ Thread.sleep(200);} catch (InterruptedException e){}

        assertEquals(1, mockOutputChannel.received.size());
        assertSame(mockResponse, mockOutputChannel.received.get(0));
        assertEquals(1, counters.numReadoutRequestsReceived);
        assertEquals(1, counters.numReadoutsSent);
        assertEquals(0, counters.numOutputsIgnored);
        assertEquals(0, counters.numReadoutErrors);


        // empty data response
        mockResponse = ReadoutRequestFiller.EMPTY_READOUT_DATA;
        mockFiller.setResponse(mockResponse);
        mockOutputChannel.reset();
        subject.addRequest(req);

        try{ Thread.sleep(200);} catch (InterruptedException e){}

        assertEquals(0, mockOutputChannel.received.size());
        assertEquals(2, counters.numReadoutRequestsReceived);
        assertEquals(1, counters.numReadoutsSent);
        assertEquals(1, counters.numOutputsIgnored);
        assertEquals(0, counters.numReadoutErrors);


        subject.addRequestStop();
        try{ Thread.sleep(200);} catch (InterruptedException e){}

        assertTrue(mockOutputChannel.stopCalled);
    }

    @Test
    public void testProcessingLoopError() throws IOException
    {
        /// Tests IOException handling

        IReadoutRequest req = new ReadoutRequest(123,123,123);
        ByteBuffer mockResponse = ByteBuffer.allocate(123);
        mockFiller.setResponse(mockResponse);
        mockFiller.exceptionMode =
                MockRequestFiller.ExceptionType.IO_EXCEPTION;
        subject.addRequest(req);

        try{ Thread.sleep(200);} catch (InterruptedException e){}

        assertEquals(0, mockOutputChannel.received.size());
        assertEquals(1, counters.numReadoutRequestsReceived);
        assertEquals(0, counters.numReadoutsSent);
        assertEquals(0, counters.numOutputsIgnored);
        assertEquals(1, counters.numReadoutErrors);

        assertEquals(1, appender.getNumberOfMessages());
        String expected = "Error filling readout request, ignoring";
        assertEquals(expected, appender.getMessage(0));
        appender.clear();
    }

    @Test
    public void testProcessingLoopError2() throws IOException
    {
        /// Tests Payload handling

        IReadoutRequest req = new ReadoutRequest(123,123,123);
        ByteBuffer mockResponse = ByteBuffer.allocate(123);
        mockFiller.setResponse(mockResponse);
        mockFiller.exceptionMode =
                MockRequestFiller.ExceptionType.PAYLOAD_EXCEPTION;
        subject.addRequest(req);

        try{ Thread.sleep(200);} catch (InterruptedException e){}

        assertEquals(0, mockOutputChannel.received.size());
        assertEquals(1, counters.numReadoutRequestsReceived);
        assertEquals(0, counters.numReadoutsSent);
        assertEquals(0, counters.numOutputsIgnored);
        assertEquals(1, counters.numReadoutErrors);

        assertEquals(1, appender.getNumberOfMessages());
        String expected = "Error filling readout request, ignoring";
        assertEquals(expected, appender.getMessage(0));
        appender.clear();
    }

    @Test
    public void testProcessingLoopError3() throws IOException
    {
        /// Tests unchecked exception handling

        IReadoutRequest req = new ReadoutRequest(123,123,123);
        ByteBuffer mockResponse = ByteBuffer.allocate(123);
        mockFiller.setResponse(mockResponse);
        mockFiller.exceptionMode =
                MockRequestFiller.ExceptionType.UNCHECKED;
        subject.addRequest(req);

        try{ Thread.sleep(200);} catch (InterruptedException e){}

        assertEquals(0, mockOutputChannel.received.size());
        assertEquals(1, counters.numReadoutRequestsReceived);
        assertEquals(0, counters.numReadoutsSent);
        assertEquals(0, counters.numOutputsIgnored);
        assertEquals(1, counters.numReadoutErrors);

        assertEquals(1, appender.getNumberOfMessages());
        String expected = "Error filling readout request, aborting";
        assertEquals(expected, appender.getMessage(0));
        appender.clear();
    }


    static class MockRequestFiller implements ReadoutRequestFiller
    {

        ByteBuffer response;

        ExceptionType exceptionMode = ExceptionType.None;
        enum ExceptionType
        {
            None
                    {
                        @Override
                        void throwException()
                                throws IOException, PayloadException
                        {
                        }
                    },
            IO_EXCEPTION
                    {
                        @Override
                        void throwException()
                                throws IOException, PayloadException
                        {
                            throw new IOException("IO_EXCEPTION");
                        }
                    },
            PAYLOAD_EXCEPTION
                    {
                        @Override
                        void throwException()
                                throws IOException, PayloadException
                        {
                            throw new PayloadException("PAYLOAD_EXCEPTION");
                        }
                    },
            UNCHECKED
                    {
                        @Override
                        void throwException()
                                throws IOException, PayloadException
                        {
                            throw new Error("UNCHECKED");
                        }
                    };


            abstract void throwException()
                    throws IOException, PayloadException;
        }

        @Override
        public ByteBuffer fillRequest(final IReadoutRequest request)
                throws IOException, PayloadException
        {
            exceptionMode.throwException();
            return response;
        }

        public void setResponse(ByteBuffer response)
        {
            this.response = response;
        }

    }


}
