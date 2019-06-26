package icecube.daq.bindery;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * Pass DOM messages from StreamBinder to a writable channel.
 */
public class BufferConsumerChannel
    implements BufferConsumer
{
    /** output channel. */
    private WritableByteChannel out;

    /**
     * Create the consumer channel.
     *
     * @param out output channel
     */
    public BufferConsumerChannel(WritableByteChannel out)
    {
        this.out = out;
    }

    /**
     * Write a single DOM message to the channel.
     *
     * @param buf DOM message
     *
     * @throws IOException if the write failed
     */
    @Override
    public void consume(ByteBuffer buf)
        throws IOException
    {
        out.write(buf);
    }

    /**
     * There will be no more data.
     */
    @Override
    public void endOfStream(long mbid)
        throws IOException
    {
        consume(MultiChannelMergeSort.eos(mbid));
    }
}
