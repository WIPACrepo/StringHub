package icecube.daq.bindery;

import icecube.daq.io.PayloadDestinationOutputEngine;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IPayload;
import icecube.daq.payload.MasterPayloadFactory;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.impl.SourceID4B;
import icecube.daq.payload.impl.SuperNovaPayloadFactory;
import icecube.daq.payload.impl.UTCTime8B;
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

	private MasterPayloadFactory	payloadFactory;
	private IByteBufferCache		byteBufferCache;
	private SuperNovaPayloadFactory	supernovaPayloadFactory;
	private PayloadDestinationOutputEngine	payloadOutputEngine;
	
	private static final Logger logger = Logger.getLogger(SecondaryStreamConsumer.class);
	/**
	 * We are assuming that this consumes buffers which adhaere to
	 * the TestDAQ standard 32-byte 'iiq8xq' header.
	 */
	public void consume(ByteBuffer buf) throws IOException 
	{
		int 	recl	= buf.getInt();
		int		fmtid	= buf.getInt();
		long	mbid	= buf.getLong();
		buf.position(buf.position() + 8);
		long 	utc		= buf.getLong();
		
		DOMID8B domId   = new DOMID8B(mbid);
		UTCTime8B utcTime = new UTCTime8B(utc);
		
		IPayload payload;
		
		try 
		{
			switch (fmtid)
			{
			case 102: // Monitor record
				break;
			case 202: // TCAL record
				break;
			case 302: // Supernova record
				payload = payloadFactory.createPayload(
						0, SuperNovaPayloadFactory.createFormattedBufferFromDomHubRecord(
								byteBufferCache, domId, 
								buf.position(), buf, utcTime
						)
					);
				break;
			}
			// payloadOutputEngine.sendPayload(new SourceID4B(SourceIdRegistry.STRING_HUB_SOURCE_ID), payload);
		}
		catch (DataFormatException dfx)
		{
			logger.warn(dfx.getMessage());
		}
	}

}
