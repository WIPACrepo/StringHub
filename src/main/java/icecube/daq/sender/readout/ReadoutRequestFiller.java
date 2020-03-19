package icecube.daq.sender.readout;

import icecube.daq.payload.IDOMID;
import icecube.daq.payload.IReadoutRequestElement;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.PayloadException;
import icecube.daq.payload.IReadoutRequest;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;

/**
 * Fulfills readout requests against a binary store of hit records.
 *
 * Adapted from code sources:
 *    icecube.daq.sender.Sender
 *    icecube.daq.reqFiller.RequestFiller
 *
 * Adapted to work from a binary data store and simplified to the data
 * types required by StringHub.
 *
 */
public interface ReadoutRequestFiller
{
    /** Sentinel value for empty requests. */
    public static final ByteBuffer EMPTY_READOUT_DATA = ByteBuffer.allocate(0);


    /**
     * Execute a readout request. Readouts that do not result in hits will
     * result in the sentinel EMPTY_READOUT_DATA instance being returned.
     *
     * @param request A readout request.
     * @return The readout data payload or EMPTY_READOUT_DATA.
     * @throws IOException Error accessing the data.
     * @throws PayloadException Format error within the data.
     */
    public ByteBuffer fillRequest(final IReadoutRequest request)
            throws IOException, PayloadException;


    /**
     * A debugging decorator that logs the details of all requests.
     */
    public class LoggingDecorator implements ReadoutRequestFiller
    {
        private final ReadoutRequestFiller delegate;

        private static Logger logger = Logger.getLogger(LoggingDecorator.class);

        public LoggingDecorator(final ReadoutRequestFiller delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public ByteBuffer fillRequest(final IReadoutRequest request)
                throws IOException, PayloadException
        {
            ISourceID from = request.getSourceID();
            int uid = request.getUID();

            List elements = request.getReadoutRequestElements();
            int numElements = elements.size();

            StringBuilder sb = new StringBuilder();
            sb.append("DEBUG\n");
            sb.append("ReadoutRequest {").append("\n");
            sb.append("   UID: ").append(uid).append("\n");
            sb.append("   From: ").append(from.getSourceID())
                    .append("(").append(from.toString()).append(")")
                    .append("\n");
            sb.append("   Elements[").append(numElements).append("]")
                    .append("\n");
            Iterator iter =  elements.iterator();
            int count=0;
            while (iter.hasNext())
            {
                IReadoutRequestElement elem =
                        (IReadoutRequestElement) iter.next();
                int type = elem.getReadoutType();
                long start = elem.getFirstTime();
                long end = elem.getLastTime();
                ISourceID src = elem.getSourceID();
                IDOMID dom = elem.getDOMID();


                sb.append("      [").append(count).append("]")
                        .append(", type:").append(type)
                        .append(", range[").append(start).append(" - ")
                        .append(end).append("]")
                        .append(", src: ").append(src.getSourceID())
                        .append(" (").append(src.toString()).append(")")
                        .append(", dom: ").append(dom.longValue()).append("\n");

            }
            sb.append("}");

            logger.warn(sb.toString());

            return delegate.fillRequest(request);
        }
    }

}
