package icecube.daq.bindery;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Pass DOM messages from StreamBinder to a generic output stream.
 * This used to need an explicit BufferedOutputStream which is still possible,
 * however, any derivative of output stream may be passed.
 */
public class OutputStreamBufferConsumer
    implements BufferConsumer
{
    // buffered output stream
    OutputStream os;
    
    /**
     * Create the consumer channel.
     *
     * @param out output channel
     */
    public OutputStreamBufferConsumer(OutputStream out)
    {
        this.os = out;
    }

    /**
     * Write a single DOM message to the channel.
     *
     * @param buf DOM message
     *
     * @throws IOException if the write failed
     */
    public void consume(ByteBuffer buf)
        throws IOException
    {
        // Standard end-of-stream marker detection
        if (buf.getLong(24) == Long.MAX_VALUE) 
        {
            this.os.close();
            this.os = null;
        }
        if (this.os == null) return;
        byte[] buffer_data;
        if (buf.hasArray())
            buffer_data = buf.array();
        else
            buffer_data = new byte[buf.remaining()];
        // transfer the data
        buf.get(buffer_data);
        // write the data to disk
        this.os.write(buffer_data);
   }
}
