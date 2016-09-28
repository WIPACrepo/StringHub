package icecube.daq.bindery;

import icecube.daq.hkn1.Node;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

import icecube.daq.performance.diagnostic.Metered;
import org.apache.log4j.Logger;

/**
 * A processor which merges DOM buffer inputs from multiple sources and
 * outputs a chronologically ordered sequence of these buffers.  The buffers
 * may be hits, time calibration records, monitor records, or supernova records.
 * The contract on the input is that the ByteBuffer contains a 32-byte header
 * with a long integer channel identifier (e.g., mainboard ID) beginnig at
 * the 8th byte position and a long integer timestamp beginning at the 24th
 * byte.
 * <p>
 * The class exposes a {@link BufferConsumer} interface to the producers of
 * the input data.  The interface is thread safe so that multiple threads may
 * concurrently request consumption of data buffers.  The output is also
 * via a BufferConsumer interface supplied at construction time.
 * <p>
 * Callers must know <i>a priori</i> the number of input channels and, prior
 * to startup of the HNK1 sorting thread, the channel IDs must have been
 * registered by calling the <code>register</code> method before
 * <code>Thread.start()</code>.
 * <p>
 * The sorted buffers on output are passed to the caller-supplied output
 * BufferConsumer.  The typical use pattern for this class is
 * <pre>
 * Sender sender = new Sender(...);
 * MultiChannelMergeSort hitsSorter = MultiChannelMergeSort(NDOM, sender, "hitsSort");
 * MultiChannelMergeSort moniSorter = ...
 * collectors.add(new DataCollector(0, 0, 'A', hitsSorter, moniSorter, ...))
 * collectors.add(new DataCollector(0, 0, 'B', hitsSorter, moniSorter, ...))
 * ...
 * collectors.add(new DataCollector(7, 3, 'B', hitsSorter, moniSorter, ...))
 * </pre>
 *
 * @see #register
 * @see BufferConsumer
 * @see Thread
 *
 *
 * @author kael
 *
 */
public class MultiChannelMergeSort
    implements BufferConsumer, ChannelSorter, Runnable
{
    private LinkedBlockingQueue<ByteBuffer> q;
    private BufferConsumer out;
    private HashMap<Long, Node<DAQBuffer>> inputMap;
    private Node<DAQBuffer> terminalNode;
    private final DAQBufferComparator bufferCmp = new DAQBufferComparator();
    private boolean running;
    private static final Logger logger =
        Logger.getLogger(MultiChannelMergeSort.class);
    private volatile long lastInputUT;
    private volatile long lastUT;
    private int inputCounter;
    private int outputCounter;

    private Thread thread;


    /** Meters for tracing throughput. */
    private final Metered.UTCBuffered sortMeter;


    public MultiChannelMergeSort(int nch, BufferConsumer out)
    {
        this(nch, out, "g");
    }

    public MultiChannelMergeSort(int nch, BufferConsumer out,
                                 String channelType, int maxQueue)
    {
        this(nch, out, channelType, maxQueue,
                new Metered.DisabledMeter(), new Metered.DisabledMeter());
    }
    public MultiChannelMergeSort(int nch, BufferConsumer out,
                                 String channelType, int maxQueue,
                                 Metered.Buffered queueMeter,
                                 Metered.UTCBuffered sortMeter)
    {
        this.out = out;
        terminalNode = null;
        running = false;
        lastUT = 0L;
        inputMap = new HashMap<Long, Node<DAQBuffer>>();
        q = new MeteredQueue(queueMeter, maxQueue);
        inputCounter = 0;
        outputCounter = 0;

        this.sortMeter = sortMeter;

        this.thread = new Thread(this, "MultiChannelMergeSort-" + channelType);
    }

    public MultiChannelMergeSort(int nch, BufferConsumer out,
                                 String channelType)
    {
        this(nch, out, channelType, 100000);
    }

    public MultiChannelMergeSort(int nch, BufferConsumer out,
                                 String channelType,
                                 Metered.Buffered queueMeter,
                                 Metered.UTCBuffered sortMeter)
    {
        this(nch, out, channelType, 100000, queueMeter, sortMeter);
    }

    @Override
    public void join() throws InterruptedException
    {
        thread.join();
    }

    @Override
    public void start()
    {
        thread.start();
    }

    /**
     * This method will take the ByteBuffer supplied as argument
     * and insert into the queue of buffers to process.
     *
     * @throws IOException
     *
     */
    public void consume(ByteBuffer buf) throws IOException
    {
        try
        {
            q.put(buf);
        }
        catch (Throwable th)
        {
           throw new IOException("Error queueing buffer", th);
        }
    }

    public void endOfStream(long mbid)
        throws IOException
    {
        consume(eos(mbid));
    }

    public synchronized long getNumberOfInputs() { return inputCounter; }
    public synchronized long getNumberOfOutputs() { return outputCounter; }
    public synchronized int getQueueSize() { return q.size(); }

    /**
     * Register a channel with the sort.
     * @param mbid
     */
    public synchronized void register(long mbid)
    {
        inputMap.put(mbid, new Node<DAQBuffer>(bufferCmp));
    }

    public void run()
    {
        terminalNode = Node.makeTree(inputMap.values());
        running = true;

        while (running)
        {
            try
            {
                ByteBuffer buf = q.take();
                int inSize = buf.remaining();

                DAQBuffer daqBuffer = new DAQBuffer(buf);
                lastInputUT = daqBuffer.timestamp;


                if (logger.isDebugEnabled())
                {
                    logger.debug(
                            String.format("took buffer from MBID %012x at UT %d",
                            daqBuffer.mbid, daqBuffer.timestamp
                            )
                        );
                }
                if (!inputMap.containsKey(daqBuffer.mbid))
                {
                    final String errmsg =
                        String.format("Dropping hit from unknown MBID %012x",
                                      daqBuffer.mbid);
                    logger.error(errmsg);
                }
                else
                {
                    inputCounter++;
                    sortMeter.reportIn(inSize, daqBuffer.timestamp);

                    if (logger.isDebugEnabled() && inputCounter % 1000 == 0)
                    {
                        logger.debug("Inputs: " + inputCounter + " Outputs: " + outputCounter);
                    }
                    inputMap.get(daqBuffer.mbid).push(daqBuffer);
                    while (!terminalNode.isEmpty())
                    {
                        DAQBuffer sorted = terminalNode.pop();
                        if (lastUT > sorted.timestamp) {
                            final String errmsg =
                                String.format("Out-of-order %012x sorted value:" +
                                              " %d, %d (diff %d)", sorted.mbid,
                                              lastUT, sorted.timestamp,
                                              lastUT - sorted.timestamp);
                            logger.warn(errmsg);
                        }
                        lastUT = sorted.timestamp;
                        if (sorted.timestamp == Long.MAX_VALUE)
                        {
                            running = false;
                            logger.info("Found STOP symbol in stream - shutting down.");
                            out.endOfStream(sorted.mbid);
                        }
                        else
                        {
                            int outSize = sorted.buf.remaining();
                            out.consume(sorted.buf);
                            outputCounter++;
                            sortMeter.reportOut(outSize, sorted.timestamp);
                        }
                    }
                }
            }
            catch (Throwable th)
            {
                logger.error("Aborting sort thread", th);
                running = false;
            }
        }
    }

    public static ByteBuffer eos(long mbid)
    {
        ByteBuffer eos = ByteBuffer.allocate(32);
        eos.putInt(0, 32).putInt(4, 0).putLong(8, mbid).putLong(24, Long.MAX_VALUE);
        eos.clear();
        return eos.asReadOnlyBuffer();
    }

    public long getLastInputTime() { return lastInputUT; }
    public long getLastOutputTime() { return lastUT; }


    /**
     * Decorate a LinkedBlockingQueue with metering on put()
     * and take().
     */
    private class MeteredQueue extends LinkedBlockingQueue<ByteBuffer>
    {
        private final Metered.Buffered meter;

        private MeteredQueue(final Metered.Buffered meter, int bound)
        {
            super(bound);
            this.meter = meter;
        }

        @Override
        public void put(final ByteBuffer buffer) throws InterruptedException
        {
            meter.reportIn(buffer.remaining());
            super.put(buffer);
        }

        @Override
        public ByteBuffer take() throws InterruptedException
        {
            ByteBuffer buffer = super.take();
            meter.reportOut(buffer.remaining());
            return buffer;
        }

    }


}
