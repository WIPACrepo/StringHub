/* -*- mode: java; indent-tabs-mode:t; tab-width:4 -*- */
package icecube.daq.bindery;

import icecube.daq.io.PayloadDestinationOutputEngine;
import icecube.daq.io.PayloadTransmitChannel;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.MasterPayloadFactory;
import icecube.daq.payload.PayloadDestination;
import icecube.daq.payload.impl.SourceID4B;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;

/**
 * A sink for handling secondary streams like supernova / monitor / tcal
 * @author krokodil
 *
 */
public class SecondaryStreamConsumer implements BufferConsumer 
{
    private ByteBuffer stopPayload              = null;
    private HashMap<Integer, Integer> idMap     = new HashMap<Integer, Integer>();
    private PayloadTransmitChannel outputChannel= null;
    private PayloadDestinationOutputEngine outputEngine = null;
    private static final Logger logger          = Logger.getLogger(SecondaryStreamConsumer.class);
    private WritableByteChannel dbgChan = null;
    
	public SecondaryStreamConsumer(int hubId, PayloadDestinationOutputEngine outputEngine)
    {
        this.outputEngine = outputEngine;
        this.outputChannel = outputEngine.lookUpEngineBySourceID(new SourceID4B(hubId)); 
        stopPayload   = ByteBuffer.allocate(4);
        stopPayload.putInt(4);
        stopPayload.flip();
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
		int recl  = buf.getInt();
		int	fmtid = buf.getInt();
		long mbid = buf.getLong(); 
		buf.position(buf.position() + 8);
		long utc  = buf.getLong();

        if (recl == 32 && mbid == 0L) 
        {
            logger.info("Stopping payload destinations");
            outputChannel.receiveByteBuffer(stopPayload);
            outputEngine.getPayloadDestinationCollection().stopAllPayloadDestinations();
            return;
        }

		ByteBuffer payloadBuffer = ByteBuffer.allocate(recl-8);
        payloadBuffer.putInt(recl-8);
        payloadBuffer.putInt(idMap.get(fmtid));
        payloadBuffer.putLong(utc);
        payloadBuffer.putLong(mbid);
        payloadBuffer.put(buf);
        payloadBuffer.flip();
        logger.info("Created secondary stream buffer: RECL=" + recl
                + "/REM=" + payloadBuffer.remaining()
                + " - TYPE=" + fmtid);
        if (dbgChan != null) 
        {
            dbgChan.write(payloadBuffer);
            payloadBuffer.rewind();
        }
        outputChannel.receiveByteBuffer(payloadBuffer);
	}

}

