package icecube.daq.domapp.dataprocessor;

import icecube.daq.bindery.BufferConsumer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements a buffer consumer for testing.
 */
public class MockBufferConsumer implements BufferConsumer
{
    List<Long> receivedTimes = new ArrayList<Long>(10);

    public void consume(final ByteBuffer buf) throws IOException
    {
        receivedTimes.add(buf.getLong(24));
    }

    public void endOfStream(long mbid)
    {
        throw new Error("Only used by PrioritySort");
    }

    public long[] getReceivedTimes()
    {
        return unbox(receivedTimes);
    }

    public void clear()
    {
        receivedTimes.clear();
    }


    private static long[] unbox(List<Long> boxed)
    {
        long[] unboxed = new long[boxed.size()];
        for (int i = 0; i < boxed.size(); i++)
        {
            unboxed[i] = boxed.get(i);
        }
        return unboxed;
    }
}