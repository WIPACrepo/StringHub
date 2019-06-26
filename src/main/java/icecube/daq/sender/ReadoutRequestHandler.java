package icecube.daq.sender;

import icecube.daq.io.OutputChannel;
import icecube.daq.payload.IReadoutRequest;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.PayloadException;
import icecube.daq.performance.common.PowersOfTwo;
import icecube.daq.performance.queue.QueueStrategy;
import icecube.daq.sender.readout.ReadoutRequestFiller;
import org.apache.log4j.Logger;
import org.jctools.queues.SpscArrayQueue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Handles readout requests.
 *
 * Instances are started via the startup() method and stopped by
 * by issuing a stop message via addRequestStop().
 */
public class ReadoutRequestHandler implements RequestHandler
{
    private static Logger logger = Logger.getLogger(ReadoutRequestHandler.class);

    /** Maintains diagnostic counters for the sender subsystem. */
    private final SenderCounters counters;

    // todo consider blocking impl
    // todo should come from factory
    /** Queue holding incoming requests. */
    private final QueueStrategy<IReadoutRequest> inq =
            new QueueStrategy.NonBlockingPoll<IReadoutRequest>
    (new SpscArrayQueue<IReadoutRequest>(PowersOfTwo._131072.value()), 100);

    /** Destination for fulfilled requests*/
    private final OutputChannel out;

    /** Handles fulfilling requests. */
    private final ReadoutRequestFiller filler;

    /** Processing thread. */
    private final Thread thread;

    /** Flag for running status. */
    private volatile boolean running;

    /** Sentinel value for signalling end of requests. */
    private final IReadoutRequest STOP_MARKER = new StopMarker();

    public ReadoutRequestHandler(final SenderCounters counters,
                                 final ReadoutRequestFiller filler,
                                 final OutputChannel out)
    {
        this.counters = counters;
        this.filler = filler;
        this.out = out;

        thread = new Thread("ReadoutRequestHandler")
        {
            @Override
            public void run()
            {
                processingLoop();
            }
        };

    }

    void startup()
    {
        if(running)
        {
            throw new Error("Already started");
        }

        running = true;
        thread.start();
    }

    @Override
    public void addRequest(final IReadoutRequest request) throws IOException
    {
        try
        {
            counters.numReadoutRequestsReceived++;
            inq.enqueue(request);
        }
        catch (InterruptedException e)
        {
            throw new IOException("Error queing request", e);
        }
    }

    @Override
    public void addRequestStop() throws IOException
    {
        try
        {
            inq.enqueue(STOP_MARKER);
        }
        catch (InterruptedException e)
        {
            throw new IOException("Error stopping request handler",e);
        }
    }

    private void processingLoop()
    {
        try
        {
            while(running)
            {
                IReadoutRequest req = inq.dequeue();

                if(req == STOP_MARKER)
                {
                    out.sendLastAndStop();
                    logger.info("Stopping ReadoutRequest handler");
                    running = false;
                    break;
                }


                try
                {
                    ByteBuffer readout = filler.fillRequest(req);
                    if(readout != ReadoutRequestFiller.EMPTY_READOUT_DATA)
                    {
                        out.receiveByteBuffer(readout);
                        counters.numReadoutsSent++;
                    }
                    else
                    {
                        counters.numOutputsIgnored++;
                    }
                }
                catch (IOException | PayloadException ex)
                {
                    counters.numReadoutErrors++;
                    logger.error("Error filling readout request, ignoring", ex);
                }
                catch (Throwable th)
                {
                    counters.numReadoutErrors++;
                    logger.error("Error filling readout request, aborting", th);
                    running = false;
                }
                finally
                {
                    req.recycle();
                }
            }
        }
        catch (Throwable th)
        {
            //total breakdown
            logger.error("Error filling readout request, aborting", th);
        }
        finally
        {
            logger.info("ReadoutRequest handler stopped");
            running = false;
        }
    }


    /**
     * A sentinel value to signal that requests have stopped.
     */
    private static class StopMarker implements IReadoutRequest
    {

        @Override
        public void addElement(final int type, final int srcId,
                               final long firstTime, final long lastTime,
                               final long domId)
        {
        }

        @Override
        public int getEmbeddedLength()
        {
            return 0;
        }

        @Override
        public List getReadoutRequestElements()
        {
            return null;
        }

        @Override
        public long getUTCTime()
        {
            return 0;
        }

        @Override
        public int getUID()
        {
            return 0;
        }

        @Override
        public ISourceID getSourceID()
        {
            return null;
        }

        @Override
        public int length()
        {
            return 0;
        }

        @Override
        public int putBody(final ByteBuffer buf, final int offset)
                throws PayloadException
        {
            return 0;
        }

        @Override
        public void recycle()
        {
        }

        @Override
        public void setSourceID(final ISourceID srcId)
        {
        }

        @Override
        public void setUID(final int uid)
        {
        }
    }


}
