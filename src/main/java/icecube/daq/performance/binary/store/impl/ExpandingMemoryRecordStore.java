package icecube.daq.performance.binary.store.impl;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.binary.record.RecordReader;
import icecube.daq.performance.binary.buffer.WritableRecordBuffer;
import icecube.daq.performance.binary.buffer.IndexFactory;
import icecube.daq.performance.binary.store.RecordStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * A record store that utilizes an expanding list of ByteBuffers to
 * store an ordered stream of records. The store can be pruned based
 * on a given value.
 *
 * The store can optionally be adorned with an index on the ordering field
 * to improve the performance of record lookups.
 *
 * Note: Records may not span a segment, The segment size must be at least
 *       as large as the largest record expected.
 *
 * Note: The index should be sparse in the ordering domain.  This is a
 *       responsibility of the client to chose an index stride that is
 *       sparse given the production rate of the data.
 */
public class ExpandingMemoryRecordStore implements RecordStore.Prunable
{

    private final RecordReader recordReader;
    private final RecordReader.LongField orderingField;

    private final List<MemorySegment> segments;
    private MemorySegment currentSegment;

    private final int incrementSize;
    private final IndexFactory indexFactory;


    public ExpandingMemoryRecordStore(final RecordReader recordReader,
                               final RecordReader.LongField orderingField,
                               int incrementSize,
                               IndexFactory indexFactory)
    {
        this.recordReader = recordReader;
        this.orderingField = orderingField;
        this.incrementSize = incrementSize;
        this.indexFactory = indexFactory;

        segments = new ArrayList<MemorySegment>(2);
        addSegment();
    }

    @Override
    public void store(final ByteBuffer record) throws IOException
    {
        if(record.remaining() > currentSegment.available())
        {
            addSegment();
        }

        currentSegment.store(record);
    }

    @Override
    public int available()
    {
        return Integer.MAX_VALUE;
    }

    @Override
    public RecordBuffer extractRange(final long from, final long to)
            throws IOException
    {
        if(from > currentSegment.firstVal())
        {
            // The most frequent case
            return currentSegment.extractRange(from, to);
        }
        else
        {
            //todo narrow range queries to segments that matter
            //     this implementation examines all segments and
            //     adds a view to the chain even if it means adding
            //     an empty buffer.  The segment start/stop should be
            //     used to pare down the query.

            // generate a chained buffer spanning the look-back
            // segments
            RecordBuffer[] partials = new RecordBuffer[ segments.size()];
            for (int i = 0; i < segments.size(); i++)
            {
                MemorySegment segment = segments.get(i);
                if(segment.lastVal() >= from)
                {
                    partials[i] = segment.extractRange(from, to);
                }
                else
                {

                    partials[i] = RecordBuffers.EMPTY_BUFFER;
                }
            }

            return RecordBuffers.chain(partials);
        }
    }

    @Override
    public void forEach(final Consumer<RecordBuffer> action,
                        final long from, final long to) throws IOException
    {
        if(from > currentSegment.firstVal())
        {
            // The most frequent case
            currentSegment.forEach(action, from, to);
        }
        else
        {
            for (int i = 0; i < segments.size(); i++)
            {
                MemorySegment segment = segments.get(i);
                if(segment.lastVal() >= from)
                {
                    segment.forEach(action, from, to);
                }
            }
        }
    }

    @Override
    public void prune(long boundaryValue)
    {
        // prune to the boundary
        Iterator<MemorySegment> iterator = segments.iterator();
        while(iterator.hasNext())
        {
            MemorySegment segment = iterator.next();
            if(segment.lastVal() < boundaryValue)
            {
                iterator.remove();
            }
        }

        //Note: This maintains a currentSegment even if all segments have
        //      been pruned.
        if(segments.size() == 0)
        {
            addSegment();
        }
    }

    private void addSegment()
    {
        currentSegment = new MemorySegment(recordReader, orderingField,
                incrementSize, indexFactory);
        segments.add(currentSegment);
    }


    /**
     * An extension to a record buffer that maintains a reference
     * to the first and last stored values.
     */
    static class MemorySegment extends IndexingRecordStore
    {
        private long firstVal = Long.MAX_VALUE;
        private long lastVal = Long.MAX_VALUE;

        private final RecordReader.LongField orderingField;


        MemorySegment(final RecordReader recordReader,
                      final RecordReader.LongField orderingField,
                      final int size,
                      IndexFactory indexFactory)
        {
            super(recordReader, orderingField,
                    RecordBuffers.writable(size),
                    indexFactory.newIndex() );
            this.orderingField = orderingField;
        }

        public long firstVal()
        {
            return firstVal;
        }

        public long lastVal()
        {
            return lastVal;
        }

        @Override
        public void store(ByteBuffer record) throws IOException
        {
            long value = orderingField.value(record, 0);
            if(firstVal == Long.MAX_VALUE)
            {
                firstVal = value;
            }
            lastVal = value;

            super.store(record);
        }

    }

}
