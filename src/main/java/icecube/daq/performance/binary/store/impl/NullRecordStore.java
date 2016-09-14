package icecube.daq.performance.binary.store.impl;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.binary.store.RecordStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * A null implementation of an OrderedWritable useful in development.
 */
public class NullRecordStore implements RecordStore.OrderedWritable
{
    @Override
    public void store(final ByteBuffer buffer) throws IOException
    {
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
        return RecordBuffers.EMPTY_BUFFER;
    }

    @Override
    public void forEach(final Consumer<RecordBuffer> action,
                        final long from, final long to) throws IOException
    {
    }
}
