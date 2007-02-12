package icecube.daq.sender;

import icecube.daq.io.PushPayloadInputEngine;
import icecube.daq.payload.IByteBufferCache;
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
public class RequestInputEngine
    extends PushPayloadInputEngine
{
    private static final Log LOG =
        LogFactory.getLog(RequestInputEngine.class);

    /** back-end processor which digests readout requests. */
    private Sender sender;

    private MasterPayloadFactory masterFactory;

    /**
     * Read requests from global trigger.
     *
     * @param server MBean server
     * @param name DAQ component name
     * @param id DAQ component ID
     * @param fcn engine function
     * @param sender readout request filler
     * @param bufMgr byte buffer cache manager
     * @param factory payload factory
     */
    public RequestInputEngine(String name, int id,
                              String fcn, Sender sender,
                              IByteBufferCache bufMgr,
                              MasterPayloadFactory factory)
    {
        // parent constructor wants same args
        super(name, id, fcn, "Sender", bufMgr);

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

        //try putting the payload into the list.
        sender.addRequest((Payload) pay);
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
