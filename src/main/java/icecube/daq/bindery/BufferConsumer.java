package icecube.daq.bindery;

import icecube.daq.priority.DataConsumer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Consume DOM messages from StreamBinder.
 */
public interface BufferConsumer
    extends DataConsumer<ByteBuffer>
{
    /**
     * Consume a single DOM message.
     *
     * @param buf DOM message
     */
    void consume(ByteBuffer buf)
        throws IOException;
}
