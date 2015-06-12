package icecube.daq.bindery;

import icecube.daq.priority.AdjustmentTask;
import icecube.daq.priority.DataConsumer;
import icecube.daq.priority.SortInput;
import icecube.daq.priority.Sorter;
import icecube.daq.priority.SorterException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

import org.apache.log4j.Logger;

class BufferForwarder
    implements DataConsumer<DAQBuffer>
{
    private DataConsumer<ByteBuffer> consumer;
    private long numInputs;

    BufferForwarder(DataConsumer<ByteBuffer> consumer)
    {
        this.consumer = consumer;
    }

    public void consume(DAQBuffer db)
        throws IOException
    {
        consumer.consume(db.buf);
    }

    public void endOfStream(long ignored)
        throws IOException
    {
        consumer.endOfStream(ignored);
    }
}

public class PrioritySort
    implements ChannelSorter, PrioritySortMBean
{
    private static final Logger LOG = Logger.getLogger(PrioritySort.class);

    private static final DAQBuffer EOS = createEndOfStreamMarker();

    private DataConsumer<ByteBuffer> consumer;
    private Sorter<DAQBuffer> sorter;
    private HashMap<Long, SortInput<DAQBuffer>> inputMap =
        new HashMap<Long, SortInput<DAQBuffer>>();

    public PrioritySort(String name, int nch,
                        DataConsumer<ByteBuffer> consumer)
        throws SorterException
    {
        this.consumer = consumer;

        sorter =
            new Sorter<DAQBuffer>(name, nch, new DAQBufferComparator(),
                                  new BufferForwarder(consumer), EOS);
    }

    /**
     * Add a DOM hit to the queue.
     *
     * @param buf buffer containing DOM hit
     */
    public void consume(ByteBuffer buf)
        throws IOException
    {
        DAQBuffer db = new DAQBuffer(buf);
        push(db.mbid, db);
    }

    private static final DAQBuffer createEndOfStreamMarker()
    {
        ByteBuffer eos = ByteBuffer.allocate(32);
        eos.putInt(0, 32).putInt(4, 0).putLong(8, Long.MAX_VALUE).
            putLong(24, Long.MAX_VALUE);
        return new DAQBuffer(eos.asReadOnlyBuffer());
    }

    /**
     * There will be no more data.
     */
    public void endOfStream(long mbid)
        throws IOException
    {
        push(mbid, EOS);
    }

    /**
     * Get the number of objects required for a sort to be initiated
     *
     * @return number of objects required for a sort to be initiated
     */
    public int getChunkSize()
    {
        return sorter.getChunkSize();
    }

    /**
     * Get the time of the last object added to the sorter
     *
     * @return last input time
     */
    public long getLastInputTime()
    {
        return 0L;
    }

    /**
     * Get the time of the last object to leave the sorter
     *
     * @return last output time
     */
    public long getLastOutputTime()
    {
        return 0L;
    }

    /**
     * Get sorter name
     *
     * @return name
     */
    public String getName()
    {
        return sorter.getName();
    }

    /**
     * Get number of sorter checks
     *
     * @return number of checks
     */
    public long getNumberOfChecks()
    {
        return sorter.getNumChecked();
    }

    /**
     * Get number of objects submitted to the sorter
     *
     * @return number of inputs
     */
    public long getNumberOfInputs()
    {
        long total = 0;
        for (SortInput<DAQBuffer> in : inputMap.values()) {
            total += in.getInputCount();
        }
        return total;
    }

    /**
     * Get number of objects sorted by the sorter
     *
     * @return number of outputs
     */
    public long getNumberOfOutputs()
    {
        return sorter.getNumOutput();
    }

    /**
     * Get number of calls to sorter.process()
     *
     * @return number of calls to process()
     */
    public long getNumberOfProcessCalls()
    {
        return sorter.getNumProcessCalls();
    }

    /**
     * Get number of objects waiting to be sorted
     *
     * @return number of queued objects
     */
    public int getQueueSize()
    {
        return sorter.getNumQueued();
    }

    /**
     * Wait for sorter thread to finish
     */
    public void join()
    {
        sorter.waitForStop();
    }

    /**
     * Push a buffer into the sorter
     *
     * @param mbid mainboard ID
     * @param buf data
     *
     * @throws IOException if the mainboard ID or data is not valid
     */
    private void push(long mbid, DAQBuffer buf)
        throws IOException
    {
        if (!inputMap.containsKey(mbid)) {
            final String errMsg =
                String.format("Found hit for unknown %s %012x",
                              sorter.getName(), mbid);
            throw new IOException(errMsg);
        }

        SortInput<DAQBuffer> in = inputMap.get(mbid);
        if (buf.isEOS()) {
            try {
                in.put(EOS);
            } catch (SorterException se) {
                throw new IOException("Cannot put " + sorter.getName() +
                                      " end-of-stream", se);
            }
        } else {
            try {
                in.put(buf);
            } catch (SorterException se) {
                throw new IOException("Cannot put " + sorter.getName() +
                                      " data", se);
            }
        }
    }

    /**
     * Register a channel with the sort.
     * @param mbid
     */
    public void register(long mbid)
    {
        if (inputMap.containsKey(mbid)) {
            LOG.error(String.format("Overwriting sort input for %s %012x",
                                    sorter.getName(), mbid));
        }

        try {
            inputMap.put(mbid,
                         sorter.register(String.format("%012x", mbid)));
        } catch (SorterException sex) {
            LOG.error("Ignoring unexpected " + sorter.getName() +
                      " sorter exception", sex);
        }
    }

    /**
     * Register the encapsulated sorter with the adjustment thread
     *
     * @param task adjustment task
     */
    public void registerSorter(AdjustmentTask task)
    {
        task.register(sorter);
    }

    /**
     * Start sorter.
     */
    public void start()
    {
        sorter.start();
    }
}
