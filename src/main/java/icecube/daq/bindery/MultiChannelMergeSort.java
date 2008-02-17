package icecube.daq.bindery;

import icecube.daq.hkn1.Node;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

/**
 * This class is a thread safe object which will merge-n-sort buffers passed to its
 * BufferConsumer interface.  The class does the sorting in its own thread.
 * The sorted buffers are passed to the caller-supplied output BufferConsumer.
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
    
    public MultiChannelMergeSort(int nch, BufferConsumer out, String channelType)
    {
        super("MultiChannelMergeSort-" + channelType);
        this.out = out;
        terminalNode = null;
        running = false;
        lastUT = 0L;
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
        while (running)
        {
            try
            {
                ByteBuffer buf = q.take();
                DAQBuffer daqBuffer = new DAQBuffer(buf);
                inputMap.get(daqBuffer.timestamp).push(daqBuffer);
                while (!terminalNode.isEmpty())
                {
                    DAQBuffer sorted = terminalNode.pop();
                    if (lastUT > sorted.timestamp) 
                        logger.warn(
                            "Out-of-order sorted value: " + lastUT + 
                            ", " + sorted.timestamp);
                    if (sorted.mbid == 0L && sorted.timestamp == Long.MAX_VALUE) running = false;
                    out.consume(sorted.buf);
                }
            }
            catch (Exception ex)
            {
                logger.error("Aborting sort thread", ex);
                running = false;
            }
        }
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