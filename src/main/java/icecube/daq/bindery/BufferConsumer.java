package icecube.daq.bindery;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Consume DOM messages from StreamBinder.
 */
public interface BufferConsumer
{
    /**
     * Consume a single DOM message.
     *
     * @param buf DOM message
     * @throws IOException if anything goes wrong
     */
    void consume(ByteBuffer buf)
        throws IOException;
}
