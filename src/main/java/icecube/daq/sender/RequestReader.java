package icecube.daq.sender;

import icecube.daq.io.PushPayloadReader;
import icecube.daq.payload.ILoadablePayload;
import icecube.daq.payload.MasterPayloadFactory;
import icecube.daq.payload.splicer.Payload;
import icecube.daq.trigger.IReadoutRequest;
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
    private Sender sender;

    private MasterPayloadFactory masterFactory;

    /**
     * Read requests from global trigger.
     *
     * @param server MBean server
     * @param sender readout request filler
     * @param factory payload factory
     */
    public RequestReader(String name, Sender sender,
                         MasterPayloadFactory factory)
        throws IOException
    {
        // parent constructor wants same args
        super(name);

        if (sender == null) {
            throw new IllegalArgumentException("Sender cannot be null");
        }
        this.sender = sender;

        masterFactory = factory;
    }

    public void pushBuffer(ByteBuffer buf)
        throws IOException
    {
        IReadoutRequest pay;

        try {
            pay = (IReadoutRequest) masterFactory.createPayload(0, buf);
        } catch (Exception ex) {
            LOG.error("Cannot create readout request", ex);
            throw new IOException("Cannot create readout request");
        }

        if (pay == null) {
            return;
        }

        try {
            ((ILoadablePayload) pay).loadPayload();
        } catch (Exception ex) {
            LOG.error("Cannot load readout request", ex);
            throw new IOException("Cannot load readout request");
        }

        // try putting the payload into the list.
        // TODO - really bad programming here - please clean up
        sender.addRequest((ILoadablePayload) pay);
    }

    public void sendStop()
    {
        sender.addRequestStop();
    }

    public void startProcessing()
    {
        super.startProcessing();
        sender.reset();
    }
}
