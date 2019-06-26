package icecube.daq.performance.binary.store.impl;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.store.RecordStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Wraps a prunable record store with an automatic pruning policy
 * where records will be pruned up to the point of the latest request.
 *
 * Note: Multi-threaded use of this store requires external synchronization.
 */
public class AutoPruningRecordStore implements RecordStore.OrderedWritable
{

    private final RecordStore.Prunable delegate;

    private long latestRequestValue = Long.MIN_VALUE;


    public AutoPruningRecordStore(final RecordStore.Prunable delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public void store(final ByteBuffer buffer) throws IOException
    {
        delegate.store(buffer);
    }

    @Override
    public int available()
    {
        return delegate.available();
    }

    @Override
    public void closeWrite() throws IOException
    {
        delegate.closeWrite();
    }

    @Override
    public RecordBuffer extractRange(final long from, final long to)
            throws IOException
    {
        RecordBuffer answer = delegate.extractRange(from, to);
        latestRequestValue = to;
        delegate.prune(latestRequestValue);
        return answer;
    }


    @Override
    public void forEach(final Consumer<RecordBuffer> action,
                        final long from, final long to) throws IOException
    {
        delegate.forEach(action, from, to);
        latestRequestValue = to;
        delegate.prune(latestRequestValue);
    }
}
