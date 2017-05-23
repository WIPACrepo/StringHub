package icecube.daq.performance.binary.store.impl;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.binary.record.RecordReader;
import icecube.daq.performance.binary.store.RecordStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Writes records to both a primary and secondary cache. (Generally an
 * in-memory primary backed by an on-disk secondary)
 *
 * The primary cache will range in size from a user-defined
 * minimum and maximum span.
 *
 * Range Queries are satisfied by a read through of the primary cache, falling
 * back to the secondary for older data that has been evicted from the primary.
 *
 */
public class SplitStore implements RecordStore.OrderedWritable
{
    private final RecordReader recordReader;
    private final RecordReader.LongField orderingField;

    // Primary (Usually In-Memory)
    private final RecordStore.Prunable memory;
    private final long minSpan;
    private final long maxSpan;

       // Secondary (Usually File System)
    private final RecordStore.OrderedWritable spool;

    // the boundary value that divides a range query between
    // the primary and secondary
    private long queryBoundary = Long.MIN_VALUE;

    //todo support memory-size mode in addition to value-span mode
    /**
     * Defines the pruning basis
     */
//    public static enum Mode
//    {
//        VALUE,
//        MEMORY_SIZE
//    }

    /**
     *
     * @param recordReader
     * @param orderingField
     * @param memory
     * @param spool
     * @param minSpan The minimum value-span that will be maintained in memory.
     * @param maxSpan The maximum value-span that will be maintained in memory,
     *                defines the pruning frequency.
     * @throws IOException
     */
    public SplitStore(final RecordReader recordReader,
                      final RecordReader.LongField orderingField,
                      final RecordStore.Prunable memory,
                      final RecordStore.OrderedWritable spool,
                      final long minSpan,
                      final long maxSpan)
    {
        if(maxSpan < minSpan)
        {
            throw new IllegalArgumentException("maxSpan: " + maxSpan +
                    " > minSpan: " + minSpan);
        }

        this.recordReader = recordReader;
        this.orderingField = orderingField;
        this.spool = spool;
        this.memory = memory;
        this.minSpan = minSpan;
        this.maxSpan = maxSpan;
    }


    @Override
    public void store(final ByteBuffer buffer) throws IOException
    {
        long value = orderingField.value(buffer, 0);

        // to file
        spool.store(buffer);

        buffer.rewind();

        // to memory
        memory.store(buffer);

        // maintain the query boundary
        if(value > (queryBoundary+maxSpan) )
        {
            queryBoundary = (value-minSpan);
            memory.prune(queryBoundary);
        }
    }

    @Override
    public int available()
    {
        // memory is auto-pruned, so capacity is bound by spool
        return spool.available();
    }

    @Override
    public void closeWrite() throws IOException
    {
        memory.closeWrite();
        spool.closeWrite();
    }

    @Override
    /**
     * Execute a range query against the full cache.  Data may be read from
     * memory, disk or a combination as required.
     *
     * @param from  The start of the range.
     * @param to    The end of the range.
     * @return  A record buffer spanning the range.
     *
     * @throws java.io.IOException An error accessing the data.
     */
    public RecordBuffer extractRange(final long from, final long to)
            throws IOException
    {
        if(to < from)
        {
            throw new IllegalArgumentException("Illegal query range [" +
                    from + " - " + to + "]");
        }

        // segment the query
        if(from >= queryBoundary)
        {
            return recallPrimary(from, to);
        }
        else if (to < queryBoundary)
        {
            return recallSecondary(from, to);
        }
        else
        {
            // a mixed read
            RecordBuffer spool = recallSecondary(from, queryBoundary - 1);
            RecordBuffer memory = recallPrimary(queryBoundary, to);

            return RecordBuffers.chain(new RecordBuffer[] {spool, memory});
        }
    }

    @Override
    public void forEach(final Consumer<RecordBuffer> action,
                        final long from, final long to) throws IOException
    {
        if(to < from)
        {
            throw new IllegalArgumentException("Illegal query range [" + from +
                    " - " + to + "]");
        }

        // segment the query
        if(from >= queryBoundary)
        {
            memory.forEach(action, from, to);
        }
        else if (to < queryBoundary)
        {
            spool.forEach(action, from, to);
        }
        else
        {
            // a mixed read
            spool.forEach(action, from, queryBoundary - 1);
            memory.forEach(action, queryBoundary, to);
        }
    }

    /**
     * Execute a range query against the memory cache.
     *
     * @param from The start of the range.
     * @param to   The end of the range.
     * @return A record buffer spanning the range.
     *
     * @throws java.io.IOException An error accessing the data.
     */
    private RecordBuffer recallPrimary(final long from, final long to)
            throws IOException
    {
       return memory.extractRange(from, to);
    }

    /**
     * Execute a range query against the spool.
     *
     * @param from The start of the range.
     * @param to   The end of the range.
     * @return A record buffer spanning the range.
     *
     * @throws java.io.IOException An error accessing the data.
     */
    private RecordBuffer recallSecondary(final long from, final long to)
            throws IOException
    {
        return spool.extractRange(from, to);
    }

}
