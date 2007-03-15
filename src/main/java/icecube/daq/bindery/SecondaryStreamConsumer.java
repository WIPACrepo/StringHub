/* -*- mode: java; indent-tabs-mode:t; tab-width:4 -*- */
package icecube.daq.bindery;

import icecube.daq.io.PayloadDestinationOutputEngine;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.MasterPayloadFactory;
import icecube.daq.payload.PayloadDestination;

import java.io.IOException;
import java.nio.ByteBuffer;
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
    private ByteBuffer payloadBuffer   = null;
    private ByteBuffer stopPayload     = null;
    private HashMap<Integer, Integer> idMap  = new HashMap<Integer, Integer>();
    private PayloadDestinationOutputEngine output;
    
    private static final Logger logger = Logger.getLogger(SecondaryStreamConsumer.class);

	public SecondaryStreamConsumer(MasterPayloadFactory payloadFactory, 
								   IByteBufferCache byteBufferCache,
								   PayloadDestinationOutputEngine output)
    {
        this.output = output; 
	    payloadBuffer = ByteBuffer.allocate(5000);
        stopPayload   = ByteBuffer.allocate(4);
        stopPayload.putInt(4);
        stopPayload.flip();
        idMap.put(102, 5);
        idMap.put(202, 4);
        idMap.put(302, 16);
	}

	
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
            Iterator it = output.getPayloadDestinationCollection().getAllPayloadDestinations().iterator();
            while (it.hasNext())
            {
                PayloadDestination dest = (PayloadDestination) it.next();
                dest.write(0, stopPayload, 4);
                stopPayload.flip();
            }
            output.getPayloadDestinationCollection().stopAllPayloadDestinations();
            return;
        }

		logger.debug("Consuming rec length = " + recl + " type = " + fmtid +
					" mbid = " + String.format("%012x", mbid) + 
					" utc = " +  utc);
		payloadBuffer.clear();
        payloadBuffer.putInt(recl);
        payloadBuffer.putInt(idMap.get(fmtid));
        payloadBuffer.putLong(utc);
        payloadBuffer.putLong(mbid);
        payloadBuffer.put(buf);
        payloadBuffer.flip();
        Iterator it = output.getPayloadDestinationCollection().getAllPayloadDestinations().iterator();
        while (it.hasNext())
        {
            PayloadDestination dest = (PayloadDestination) it.next();
            dest.write(0, payloadBuffer, payloadBuffer.remaining());
            payloadBuffer.flip();
        }
	}

}

