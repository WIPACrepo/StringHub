package icecube.daq.performance.binary.store.impl;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.binary.record.RecordReader;
import icecube.daq.performance.binary.buffer.WritableRecordBuffer;
import icecube.daq.performance.binary.buffer.RangeSearch;
import icecube.daq.performance.binary.buffer.RecordBufferIndex;
import icecube.daq.performance.binary.store.RecordStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;


/**
 * Composes a record type, store and index.
 */
public class IndexingRecordStore implements RecordStore.OrderedWritable
{

    private final RecordReader recordReader;
    private final RecordReader.LongField orderingField;
    private final WritableRecordBuffer buffer;
    private final RangeSearch search;
    private final RecordBufferIndex.UpdatableIndex index;



    public IndexingRecordStore(final RecordReader recordReader,
                               final RecordReader.LongField orderingField,
                               RecordBufferIndex.UpdatableIndex index,
                               final int size)
    {
        this(recordReader, orderingField,
                RecordBuffers.writable(size), index);
    }
    public IndexingRecordStore(final RecordReader recordReader,
                               final RecordReader.LongField orderingField,
                               final WritableRecordBuffer buffer,
                               RecordBufferIndex.UpdatableIndex index)
    {
        this(recordReader, orderingField, buffer, index,
                new RangeSearch.LinearSearch(recordReader, orderingField) );

    }

    public IndexingRecordStore(final RecordReader recordReader,
                               final RecordReader.LongField orderingField,
                               final WritableRecordBuffer buffer,
                               final RecordBufferIndex.UpdatableIndex index,
                               final RangeSearch search)
    {
        this.recordReader = recordReader;
        this.orderingField = orderingField;
        this.buffer = buffer;
        this.index = index;
        this.search = search;
    }

    @Override
    public void store(final ByteBuffer buffer) throws IOException
    {
        index.addIndex(this.buffer.getLength(), orderingField.value(buffer, 0));
        this.buffer.put(buffer);
    }

    @Override
    public int available()
    {
        return buffer.available();
    }

    @Override
    public void closeWrite() throws IOException
    {
        //noop
    }

    @Override
    public RecordBuffer extractRange(final long from, final long to)
    {
        return search.extractRange(this.buffer, RecordBuffer.MemoryMode.COPY,
                this.index, from, to);
    }

    @Override
    public void forEach(final Consumer<RecordBuffer> action,
                        final long from, final long to) throws IOException
    {
        RecordBuffer shared = search.extractRange(this.buffer, RecordBuffer.MemoryMode.SHARED_VIEW,
                this.index, from, to);

        shared.eachRecord(recordReader).forEach(action);
    }
}
