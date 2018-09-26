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

    private ErrorMode errorMode = new NoError();

    public interface ErrorMode
    {
        public void invoke(String msg) throws IOException;
    }

    public static class NoError implements ErrorMode
    {
        @Override
        public void invoke(String msg) throws IOException {}
    }

    public static class CallNumberErrorMode implements ErrorMode
    {
        final int onCallNumber;
        final int toCallNumber;
        final boolean unchecked;

        private int callCount;

        public CallNumberErrorMode(final int onCallNumber,
                         final int toCallNumber,
                         final boolean unchecked)
        {
            this.onCallNumber = onCallNumber;
            this.toCallNumber = toCallNumber;
            this.unchecked = unchecked;
        }

        @Override
        public void invoke(String msg) throws IOException
        {
            callCount++;
            if(callCount >= onCallNumber && callCount <= toCallNumber)
            {
                if(unchecked)
                {
                    throw new Error(msg);
                }
                else
                {
                    throw new IOException(msg);
                }
            }
        }
    }


    @Override
    public void consume(final ByteBuffer buf) throws IOException
    {
        errorMode.invoke("generated Error on consume()");
        receivedTimes.add(buf.getLong(24));
    }

    @Override
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

    public void setErrorMode(ErrorMode errorMode)
    {
        this.errorMode = errorMode;
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
