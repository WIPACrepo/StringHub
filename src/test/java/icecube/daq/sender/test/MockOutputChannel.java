package icecube.daq.sender.test;

import icecube.daq.io.OutputChannel;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Mock OutputChannel for test use.
 */
public class MockOutputChannel implements OutputChannel
{
    public List<ByteBuffer> received = new ArrayList();
    public boolean stopCalled = false;

    @Override
    public void receiveByteBuffer(final ByteBuffer buf)
    {
        received.add(buf);
    }

    @Override
    public void sendLastAndStop()
    {
        stopCalled = true;
    }

    public void reset()
    {
        received.clear();
    }
}
