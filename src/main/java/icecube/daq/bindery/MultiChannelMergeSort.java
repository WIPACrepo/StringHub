package icecube.daq.bindery;

import icecube.daq.hkn1.Node;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

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
 * @see DataCollector
 *   
 * 
 * @author kael
 *
 */
public class MultiChannelMergeSort extends Thread implements BufferConsumer
{
    private LinkedBlockingQueue<ByteBuffer> q;
    private BufferConsumer out;
    private HashMap<Long, Node<DAQBuffer>> inputMap;
    private Node<DAQBuffer> terminalNode;
    private final DAQBufferComparator bufferCmp = new DAQBufferComparator();
    private boolean running;
    private static final Logger logger = Logger.getLogger(MultiChannelMergeSort.class);
    private long lastUT;
    private static final ByteBuffer eos = ByteBuffer.allocate(32);
    
    static
    {
        eos.putInt(0, 32).putInt(4, 0).putLong(24, Long.MAX_VALUE);
    }
    
    public MultiChannelMergeSort(int nch, BufferConsumer out)
    {
        this(nch, out, "g");
    }
    
    public MultiChannelMergeSort(int nch, BufferConsumer out, String channelType)
    {
        super("MultiChannelMergeSort-" + channelType);
        this.out = out;
        terminalNode = null;
        running = false;
        lastUT = 0L;
        inputMap = new HashMap<Long, Node<DAQBuffer>>();
        q = new LinkedBlockingQueue<ByteBuffer>();
    }

    /**
     * This method will take the ByteBuffer supplied as argument
     * and insert into the queue of buffers to process.
     * 
     * @throws InterruptedException 
     * 
     */
    public void consume(ByteBuffer buf) throws IOException
    {
        try
        {
            q.put(buf);
        }
        catch (Exception ex)
        {
            logger.error("Skipped buffer", ex);
        }
    }

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
        terminalNode = Node.makeTree(inputMap.values(), bufferCmp);
        running = true;
        int inputCounter  = 0;
        int outputCounter = 0;
        
        while (running)
        {
            try
            {
                ByteBuffer buf = q.take();
                DAQBuffer daqBuffer = new DAQBuffer(buf);
                if (logger.isDebugEnabled())
                {
                    logger.debug(
                            String.format("took buffer from MBID %012x at UT %d", 
                            daqBuffer.mbid, daqBuffer.timestamp
                            )
                        );
                }
                if (inputMap.containsKey(daqBuffer.mbid))
                {
                    inputCounter++;
                    if (logger.isDebugEnabled() && inputCounter % 1000 == 0)
                    {
                        logger.debug("Inputs: " + inputCounter + " Outputs: " + outputCounter);
                    }
                    inputMap.get(daqBuffer.mbid).push(daqBuffer);
                    while (!terminalNode.isEmpty())
                    {
                        outputCounter++;
                        DAQBuffer sorted = terminalNode.pop();
                        if (lastUT > sorted.timestamp) 
                            logger.warn(
                                "Out-of-order sorted value: " + lastUT + 
                                ", " + sorted.timestamp);
                        lastUT = sorted.timestamp;
                        if (sorted.timestamp == Long.MAX_VALUE)
                        {
                            running = false;
                            logger.info("Found STOP symbol in stream - shutting down.");
                        }
                        out.consume(sorted.buf);
                    }
                }
            }
            catch (Exception ex)
            {
                logger.error("Aborting sort thread", ex);
                running = false;
            }
        }
    }
    
    public static ByteBuffer eos(long mbid)
    {
        eos.putLong(8, mbid);
        eos.clear();
        return eos.asReadOnlyBuffer();
    }
    
}

class DAQBuffer
{
    ByteBuffer buf;
    long mbid;
    long timestamp;
    
    DAQBuffer(ByteBuffer buf)
    {
        this.buf = buf;
        mbid = buf.getLong(8);
        timestamp = buf.getLong(24);
    }
}

class DAQBufferComparator implements Comparator<DAQBuffer>
{

    public int compare(DAQBuffer left, DAQBuffer right)
    {
        if (left.timestamp < right.timestamp) 
            return -1;
        else if (left.timestamp > right.timestamp)
            return 1;
        else
            return 0;
    }
    
}