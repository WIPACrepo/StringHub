/* -*- mode: java; indent-tabs-mode:f; tab-width:4 -*- */
package icecube.daq.bindery;

import icecube.daq.io.OutputChannel;
import icecube.daq.payload.IByteBufferCache;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;

import org.apache.log4j.Logger;

/**
 * A sink for handling secondary streams like supernova / monitor / tcal
 * @author krokodil
 *
 */
public class SecondaryStreamConsumer implements BufferConsumer
{
    private HashMap<Integer, Integer> idMap     = new HashMap<Integer, Integer>();
    private OutputChannel outputChannel= null;
    private IByteBufferCache cacheMgr           = null;
    private static final Logger logger          = Logger.getLogger(SecondaryStreamConsumer.class);
    private WritableByteChannel dbgChan = null;
    /**
     * Set a prescale of N on the output
     */
    private int prescale;
    private int prescaleCounter = 0;

    public SecondaryStreamConsumer(int hubId, IByteBufferCache cacheMgr, OutputChannel outputChannel)
    {
        this(hubId, cacheMgr, outputChannel, 1);
    }

	public SecondaryStreamConsumer(int hubId, IByteBufferCache cacheMgr, OutputChannel outputChannel, int prescale)
    {
        this.outputChannel = outputChannel;
        this.cacheMgr = cacheMgr;
        this.prescale = prescale;
        idMap.put(102, 5);
        idMap.put(202, 4);
        idMap.put(302, 16);
	}

	public void setDebugChannel(WritableByteChannel ch) { dbgChan = ch; }

	/**
	 * We are assuming that this consumes buffers which adhaere to
	 * the TestDAQ standard 32-byte 'iiq8xq' header.
	 */
	public void consume(ByteBuffer buf) throws IOException
	{
        buf.order(ByteOrder.BIG_ENDIAN);
        int recl  = buf.getInt();
        int fmtid = buf.getInt();
        long mbid = buf.getLong();
        buf.position(buf.position() + 8);
        long utc  = buf.getLong();

        if (recl == 32 && utc == Long.MAX_VALUE)
        {
            logger.info("Stopping payload destinations");
            outputChannel.sendLastAndStop();
        }
        else
        {
            int id;
            if (idMap.containsKey(fmtid)) {
                id = idMap.get(fmtid);
            } else {
                logger.error("Unknown format ID " + id);
                id = -1;
            }

            ByteBuffer payloadBuffer = cacheMgr.acquireBuffer(recl-8);
            payloadBuffer.putInt(recl-8);
            payloadBuffer.putInt(id);
            payloadBuffer.putLong(utc);
            payloadBuffer.putLong(mbid);
            payloadBuffer.put(buf);
            payloadBuffer.flip();
            if (dbgChan != null)
            {
                dbgChan.write(payloadBuffer);
                payloadBuffer.rewind();
            }
            if (prescale <=0 || ++prescaleCounter == prescale)
            {
                outputChannel.receiveByteBuffer(payloadBuffer);
                prescaleCounter = 0;
            }
        }
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
