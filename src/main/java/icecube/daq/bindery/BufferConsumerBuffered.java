package icecube.daq.bindery;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.io.BufferedOutputStream;

/**
 * Pass DOM messages from StreamBinder to a writable channel.
 */
public class BufferConsumerBuffered
    implements BufferConsumer
{
    // buffered output stream
    BufferedOutputStream bos;
   

    private byte[] buffer_data;
    // on scube the average scal message
    // is 681.25 bytes ( round up )
    private static final int BUFFER_INITIAL_SIZE = 682;
    
    /**
     * Create the consumer channel.
     *
     * @param out output channel
     */
    public BufferConsumerBuffered(BufferedOutputStream out)
    {
	this.bos = out;
	buffer_data = new byte[BUFFER_INITIAL_SIZE];
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

       while (buf.remaining() > buffer_data.length) {
	   buffer_data = new byte[buffer_data.length * 2 ];
       }

       // transfer the data
       buf.get(buffer_data);

       // write the data to disk
       this.bos.write(buffer_data, 0, buf.remaining());
   }
}
