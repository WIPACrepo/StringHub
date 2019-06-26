package icecube.daq.sender;


import icecube.daq.common.MockAppender;
import icecube.daq.io.DAQOutputChannelManager;
import icecube.daq.io.OutputChannel;
import icecube.daq.sender.test.MockOutputChannel;
import icecube.daq.sender.test.MockOutputChannelManager;
import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.*;

/**
 * Tests OutputChannelFuture.java
 */
public class OutputChannelFutureTest
{

    private final MockAppender appender = new MockAppender();
    OutputChannelFuture subject;


    @Before
    public void setUp() throws IOException
    {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);

        subject = new OutputChannelFuture();

    }

    @After
    public void tearDown()
    {
        appender.assertNoLogMessages();
    }

    @Test
    public void testUnplumbed()
    {
        try
        {
            subject.receiveByteBuffer(ByteBuffer.allocate(111));
            fail("Should have thrown error");
        }
        catch (Error e)
        {
            //desired
            String expected = "Channel not available";
            assertEquals(expected, e.getMessage());
        }

        try
        {
            subject.sendLastAndStop();
            fail("Should have thrown error");
        }
        catch (Error e)
        {
            //desired
            String expected = "Channel not available";
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void testFullyPlumbed()
    {
        MockOutputChannel mockChannel = new MockOutputChannel();
        MockOutputChannelManager mockManager = new MockOutputChannelManager(mockChannel);

        subject.setChannelManager(mockManager);

        subject.receiveByteBuffer(ByteBuffer.allocate(123));
        assertEquals(1, mockChannel.received.size());

        subject.receiveByteBuffer(ByteBuffer.allocate(123));
        assertEquals(2, mockChannel.received.size());

        subject.receiveByteBuffer(ByteBuffer.allocate(123));
        assertEquals(3, mockChannel.received.size());

        subject.sendLastAndStop();
        assertTrue(mockChannel.stopCalled);
    }

    @Test
    public void testPartialPlumbed()
    {
        MockOutputChannelManager mockManager =
                new MockOutputChannelManager(null);

        subject.setChannelManager(mockManager);

        try
        {
            subject.receiveByteBuffer(ByteBuffer.allocate(111));
            fail("Should have thrown error");
        }
        catch (Error e)
        {
            //desired
            String expected = "Channel not available";
            assertEquals(expected, e.getMessage());
        }

        try
        {
            subject.sendLastAndStop();
            fail("Should have thrown error");
        }
        catch (Error e)
        {
            //desired
            String expected = "Channel not available";
            assertEquals(expected, e.getMessage());
        }
    }


}
