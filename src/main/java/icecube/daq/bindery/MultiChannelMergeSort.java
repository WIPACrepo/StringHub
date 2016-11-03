package icecube.daq.bindery;

import icecube.daq.hkn1.Node;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

import icecube.daq.performance.common.PowersOfTwo;
import icecube.daq.performance.diagnostic.Metered;
import icecube.daq.performance.queue.QueueProvider;
import icecube.daq.performance.queue.QueueStrategy;
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
    private QueueStrategy<ByteBuffer> q;
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

    /** Default bound of input queue. */
    public static final PowersOfTwo DEFAULT_INPUT_MAX = PowersOfTwo._131072;


    public MultiChannelMergeSort(int nch, BufferConsumer out)
    {
        this(nch, out, "g");
    }

    public MultiChannelMergeSort(int nch, BufferConsumer out,
                                 String channelType, PowersOfTwo maxQueue)
    {
        this(nch, out, channelType, maxQueue,
                new Metered.DisabledMeter(), new Metered.DisabledMeter());
    }
    public MultiChannelMergeSort(int nch, BufferConsumer out,
                                 String channelType, PowersOfTwo maxQueue,
                                 Metered.Buffered queueMeter,
                                 Metered.UTCBuffered sortMeter)
    {
        this.out = out;
        terminalNode = null;
        running = false;
        lastUT = 0L;
        inputMap = new HashMap<Long, Node<DAQBuffer>>();
        final QueueStrategy<ByteBuffer> queue =
          QueueProvider.Subsystem.SORTER_INPUT.createQueue(maxQueue);
        q = new MeteredQueue(queue, queueMeter);
        inputCounter = 0;
        outputCounter = 0;

        this.sortMeter = sortMeter;

        this.thread = new Thread(this, "MultiChannelMergeSort-" + channelType);
    }

    public MultiChannelMergeSort(int nch, BufferConsumer out,
                                 String channelType)
    {
        this(nch, out, channelType, DEFAULT_INPUT_MAX);
    }

    public MultiChannelMergeSort(int nch, BufferConsumer out,
                                 String channelType,
                                 Metered.Buffered queueMeter,
                                 Metered.UTCBuffered sortMeter)
    {
        this(nch, out, channelType, DEFAULT_INPUT_MAX, queueMeter, sortMeter);
    }

    /**
     * Are any sorter threads still active?
     * @return <tt>true</tt> if one or more sorter threads are active
     */
    @Override
    public boolean isRunning()
    {
        return thread.isAlive();
    }

    @Override
    public void join(long millis) throws InterruptedException
    {
        thread.join(millis);
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
            q.enqueue(buf);
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
                ByteBuffer buf = q.dequeue();
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
     * Decorate a QueueStrategy&lt;ByteBuffer&gt; with metering
     * on enqueue() and dequeue().
     */
    private class MeteredQueue implements QueueStrategy<ByteBuffer>
    {
        private final QueueStrategy<ByteBuffer> queue;
        private final Metered.Buffered meter;

        private MeteredQueue(final QueueStrategy<ByteBuffer> queue,
                             final Metered.Buffered meter)
        {
            this.queue = queue;
            this.meter = meter;
        }

        @Override
        public void enqueue(final ByteBuffer buffer) throws InterruptedException
        {
            meter.reportIn(buffer.remaining());
            queue.enqueue(buffer);
        }

        @Override
        public ByteBuffer dequeue() throws InterruptedException
        {
            ByteBuffer buffer = queue.dequeue();
            meter.reportOut(buffer.remaining());
            return buffer;
        }

        @Override
        public int size()
        {
            return queue.size();
        }

    }


}
