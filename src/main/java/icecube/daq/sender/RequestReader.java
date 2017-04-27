package icecube.daq.sender;

import icecube.daq.io.PushPayloadReader;
import icecube.daq.payload.ILoadablePayload;
import icecube.daq.payload.IReadoutRequest;
import icecube.daq.payload.impl.ReadoutRequestFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Read requests from global trigger.
 */
public class RequestReader
    extends PushPayloadReader
{
    private static final Log LOG =
        LogFactory.getLog(RequestReader.class);

    /** back-end processor which digests readout requests. */
    private RequestHandler sender;

    private ReadoutRequestFactory factory;

    /**
     * Read requests from global trigger.
     *
     * @param name stream name (for error messages)
     * @param sender readout request filler
     * @param factory payload factory
     */
    public RequestReader(String name, RequestHandler sender,
                         ReadoutRequestFactory factory)
        throws IOException
    {
        // parent constructor wants same args
        super(name);

        if (sender == null) {
            throw new IllegalArgumentException("Sender cannot be null");
        }
        this.sender = sender;

        this.factory = factory;
    }

    public void pushBuffer(ByteBuffer buf)
        throws IOException
    {
        IReadoutRequest pay;

        try {
            pay = factory.createPayload(buf, 0);
        } catch (Exception ex) {
            LOG.error("Cannot create readout request", ex);
            throw new IOException("Cannot create readout request");
        }

        try {
            ((ILoadablePayload) pay).loadPayload();
        } catch (Exception ex) {
            LOG.error("Cannot load readout request", ex);
            throw new IOException("Cannot load readout request");
        }

        //try putting the payload into the list.
        sender.addRequest(pay);
    }

    public void sendStop()
    {
        try {
            sender.addRequestStop();
        } catch (IOException ioe) {
            LOG.error("Cannot add stop to request queue", ioe);
        }
    }
}
