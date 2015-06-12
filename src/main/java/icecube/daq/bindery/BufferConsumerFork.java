package icecube.daq.bindery;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This is a real simple adapter class which will simply copy ByteBuffers
 * passed into it into two output BufferConsumers.
 *
 * @author kael
 *
 */
public class BufferConsumerFork implements BufferConsumer
{
    private BufferConsumer t1;
    private BufferConsumer t2;

    public BufferConsumerFork(BufferConsumer t1, BufferConsumer t2)
    {
        this.t1 = t1;
        this.t2 = t2;
    }

    public void consume(ByteBuffer buf) throws IOException
    {
        t1.consume(buf);
        t2.consume(buf);
    }

    /**
     * There will be no more data.
     */
    public void endOfStream(long mbid)
        throws IOException
    {
        consume(MultiChannelMergeSort.eos(mbid));
    }
}
