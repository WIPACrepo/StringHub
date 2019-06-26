package icecube.daq.sender;

import icecube.daq.payload.IPayload;
import icecube.daq.payload.IReadoutRequest;

import java.io.IOException;

/**
 * Extracted from icecube.daq.sender, defines the interface between a
 * RequestReader and a RequestHandler.
 */
public interface RequestHandler
{
    /**
     * Add a request to be satisfied by the sender.
     * @param request The readout request.
     * @throws IOException
     */
    public void addRequest(IReadoutRequest request) throws IOException;

    /**
     * Stop handling requests and issue a stop message on the data
     * readout channel.
     * .
     * @throws IOException
     */
    public void addRequestStop() throws IOException;

    /**
     * A trivial adapter for request input.
     * @param sender The lgacy sender implementation.
     * @return An adapter that makes the sender conform the
     *         RequestHandler interface.
     */
    public static RequestHandler adapter(final Sender sender)
    {
        return new RequestHandler()
        {
            @Override
            public void addRequest(final IReadoutRequest request)
                    throws IOException
            {
                sender.addRequest((IPayload) request);
            }

            @Override
            public void addRequestStop() throws IOException
            {
                sender.addRequestStop();
            }
        };
    }

}
