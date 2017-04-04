package icecube.daq.performance.binary.store.impl;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.binary.record.RecordReader;
import icecube.daq.performance.binary.buffer.WritableRecordBuffer;
import icecube.daq.performance.binary.store.RecordStore;
import icecube.daq.performance.common.PowersOfTwo;
import icecube.daq.performance.binary.buffer.IndexFactory;
import icecube.daq.performance.binary.buffer.RangeSearch;
import icecube.daq.performance.binary.buffer.RecordBufferIndex;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Implements a record store on top of a ring buffer.
 *
 * Actually implements a record store on top of any prunable record buffer,
 * the trick being that buffer indexes need to be updated when data is pruned.
 */
public class RingBufferRecordStore
     implements RecordStore.Ordered, RecordStore.Writable, RecordStore.Prunable
{
    private final RecordReader recordReader;
    private final RecordReader.LongField orderingField;
    private final RangeSearch search;
    private final RecordBufferIndex.UpdatableIndex index;

    private WritableRecordBuffer.Prunable ring;

    public RingBufferRecordStore(final RecordReader recordReader,
                                 final RecordReader.LongField orderingField,
                                 final PowersOfTwo size)
    {
        this(recordReader, orderingField, IndexFactory.NO_INDEX, size);
    }


    public RingBufferRecordStore(final RecordReader recordReader,
                                 final RecordReader.LongField orderingField,
                                 final int size)
    {
        this(recordReader, orderingField, IndexFactory.NO_INDEX, size);
    }

    public RingBufferRecordStore(final RecordReader recordReader,
                                 final RecordReader.LongField orderingField,
                                 final IndexFactory indexFactory,
                                 final PowersOfTwo size
                                 )
    {
        this(recordReader, orderingField,
                RecordBuffers.ring(size), indexFactory);
    }

    public RingBufferRecordStore(final RecordReader recordReader,
                                 final RecordReader.LongField orderingField,
                                 final IndexFactory indexFactory,
                                 final int size)
    {
        this(recordReader, orderingField, RecordBuffers.ring(size),
                indexFactory);
    }

    public RingBufferRecordStore(final RecordReader recordReader,
                                 final RecordReader.LongField orderingField,
                                 final WritableRecordBuffer.Prunable ring,
                                 final IndexFactory indexFactory
                                 )
    {
        this(recordReader, orderingField, ring, indexFactory,
                new RangeSearch.LinearSearch(recordReader, orderingField));
    }

    public RingBufferRecordStore(final RecordReader recordReader,
                                 final RecordReader.LongField orderingField,
                                 final WritableRecordBuffer.Prunable ring,
                                 final IndexFactory indexFactory,
                                 final RangeSearch search
                                 )
    {
        this.recordReader = recordReader;
        this.orderingField = orderingField;
        this.search = search;
        this.index = indexFactory.newIndex();
        this.ring = ring;
    }

    @Override
    public void store(final ByteBuffer buffer)
    {
        int position = this.ring.getLength();
        this.ring.put(buffer);
        index.addIndex(position, orderingField.value(buffer, 0));
    }

    @Override
    public int available()
    {
        return ring.available();
    }

    @Override
    public void closeWrite() throws IOException
    {
        //noop
    }

    @Override
    public void prune(final long boundaryValue)
    {
        int prunedIndex = 0;

        // jump past indexed values earlier than the boundary
        int skip = index.lessThan(boundaryValue);
        if(skip > 0)
        {
            prunedIndex += skip;
        }

        // walk the remaining records util the boundary is reached
        int length = ring.getLength();
        while(prunedIndex < length &&
                (orderingField.value(ring, prunedIndex) < boundaryValue) )
        {
            int recordLength = recordReader.getLength(ring, prunedIndex);
            prunedIndex += recordLength;
        }

        // prune the data
        ring.prune(prunedIndex);

        // update the index
        index.update(prunedIndex);
    }

    @Override
    public RecordBuffer extractRange(final long from, final long to)
    {
        return search.extractRange(ring, RecordBuffer.MemoryMode.COPY,
                index, from, to);
    }

    @Override
    public void forEach(final Consumer<RecordBuffer> action,
                        final long from, final long to) throws IOException
    {
        RecordBuffer shared = search.extractRange(ring,
                RecordBuffer.MemoryMode.SHARED_VIEW,
                index, from, to);

        shared.eachRecord(recordReader).forEach(action);
    }
}
