/* -*- mode: java; indent-tabs-mode:t; tab-width:4 -*- */
package icecube.daq.bindery;

import icecube.daq.io.PayloadDestinationOutputEngine;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IPayload;
import icecube.daq.payload.MasterPayloadFactory;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.impl.MonitorPayloadFactory;
import icecube.daq.payload.impl.SourceID4B;
import icecube.daq.payload.impl.SuperNovaPayloadFactory;
import icecube.daq.payload.impl.TimeCalibrationPayloadFactory;
import icecube.daq.payload.impl.UTCTime8B;
import icecube.daq.payload.splicer.Payload;
import icecube.daq.trigger.impl.DOMID8B;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;

import org.apache.log4j.Logger;

/**
 * A sink for handling secondary streams like supernova / monitor / tcal
 * @author krokodil
 *
 */
public class SecondaryStreamConsumer implements BufferConsumer 
{

	private MasterPayloadFactory				payloadFactory;
	private IByteBufferCache					byteBufferCache;
	private PayloadDestinationOutputEngine		payloadOutput;
	private PayloadDestinationOutputEngine		tcalOutputEngine;
	private PayloadDestinationOutputEngine		supernovaOutputEngine;
	
	private static final Logger logger = Logger.getLogger(SecondaryStreamConsumer.class);
    
	public SecondaryStreamConsumer(MasterPayloadFactory payloadFactory, 
								   IByteBufferCache byteBufferCache,
								   PayloadDestinationOutputEngine output)
    {
		this.payloadFactory  = payloadFactory;
		this.byteBufferCache = byteBufferCache;
		this.payloadOutput   = output;
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

		logger.debug("Consuming record of length " + recl + " type = " + fmtid
				+ " - buf pos: " + buf.position());
		
		DOMID8B domId   = new DOMID8B(mbid);
		UTCTime8B utcTime = new UTCTime8B(utc);
		
		try 
		{
			Payload payload = null;
			ByteBuffer payload_buffer = null;
			switch (fmtid)
			{
			case 102: // Monitor record
				payload_buffer = MonitorPayloadFactory.createFormattedBufferFromDomHubRecord(
						byteBufferCache, domId, buf.position(), buf, utcTime
					);
				if (payload_buffer != null)
					payload = payloadFactory.createPayload(0, payload_buffer);
				if (payload != null)
					payloadOutput.getPayloadDestinationCollection().writePayload(payload);
				break;
			case 202: // TCAL record
				payload_buffer = TimeCalibrationPayloadFactory.createFormattedBufferFromDomHubRecord(
						byteBufferCache, domId, buf.position(), buf
					);
				if (payload_buffer != null)
					payload = payloadFactory.createPayload(0, payload_buffer);
				if (payload != null)
					payloadOutput.getPayloadDestinationCollection().writePayload(payload);
				break;
			case 302: // Supernova record
				payload_buffer = SuperNovaPayloadFactory.createFormattedBufferFromDomHubRecord(
						byteBufferCache, domId,	buf.position(), buf, utcTime
					);
				if (payload_buffer != null)
					payload = payloadFactory.createPayload(0, payload_buffer);
				if (payload != null)
					payloadOutput.getPayloadDestinationCollection().writePayload(payload);
				break;
			}
		}
		catch (DataFormatException dfx)
		{
			logger.warn(dfx.getMessage());
		}
	}

}
