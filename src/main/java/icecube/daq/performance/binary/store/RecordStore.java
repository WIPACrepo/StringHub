package icecube.daq.performance.binary.store;

import icecube.daq.performance.binary.buffer.RecordBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Record Store is a facade into the record store package.
 *
 * Interfaces are provided that model a store of binary records. Stores
 * may be queried (@see Ordered), writable (@see Writable), prunable
 * (@see Prunable) or a combination (@see OrderedWritable).
 *
 * Store implementations are typically un-synchronized and not thread safe.
 * Synchronized wrappers are provided via the synchronizedXXX factory methods.
 *
 */
public interface RecordStore
{

    /**
     * Wraps a store with synchronization.
     * @param delegate The store to wrap.
     * @return A synchronized store backed by the delegate implementation.
     */
    public static OrderedWritable synchronizedStore(final OrderedWritable delegate)
    {
        return new SynchronizedStore(delegate);
    }

    /**
     * Wraps a store with synchronization.
     * @param delegate The store to wrap.
     * @return A synchronized store backed by the delegate implementation.
     */
    public static Prunable synchronizedStore(final Prunable delegate)
    {
        return new SynchronizedPrunable(delegate);
    }


    /**
     * Defines a range searching method for stores whose content is fully
     * ordered by a long field.
     *
     * Usually this will apply to time sorted records ordered by the
     * UTC timestamp field.
     */
    public static interface Ordered
    {

        /**
         * Extract a range of records bounded by an interval.
         * @param from The start of the interval.
         * @param to The end of the interval.
         * @return A buffer containing a copy the data range.
         * @throws IOException
         */
        public RecordBuffer extractRange(long from, long to)
                throws IOException;

        /**
         * Extract a range of records bounded by an interval.
         *
         * This form of extraction permits an implementer to satisfy the
         * request using a shared view of the backing store.
         *
         * Note: The buffer provided in the callback is a shared,
         *       volatile view into the data. Clients must not access
         *       the buffer outside of the scope of callback.
         *
         * @param target The recipient of the extracted data. The recipient
         *               must not access the buffer outside the scope of
         *               the callback.
         * @param from The start of the interval.
         * @param to The end of the interval.
         * @throws IOException
         */
        default public void extractRange(Consumer<RecordBuffer> target,
                                         long from, long to)
                throws IOException
        {
            target.accept(extractRange(from, to));
        }

        /**
         * Perform an operation on each record within interval.
         *
         * Note: The buffer provided in the callback is a shared,
         *       volatile view into the data. Clients must not access
         *       the buffer outside of the scope of callback.
         *
         * @param action The operation to be performed for each record.
         * @param from The start of the interval.
         * @param to The end of the interval.
         * @throws IOException
         */
        public void forEach(Consumer<RecordBuffer> action,
                            long from, long to)
                throws IOException;

    }


    /**
     * Defines operations for a record store that supports the addition
     * of new records.
     */
    public interface Writable
    {
        /**
         * Add a record to the store.
         *
         * @param buffer A buffer containing a single record.
         */
        public void store(ByteBuffer buffer) throws IOException;

        /**
         * How much storage is available in the store;
         *
         * @return The number of bytes available.
         */
        public int available();

        /**
         * Close the store for writing. If this is a bi-directional
         * store, it may remain open for reading.
         */
        public void closeWrite() throws IOException;

    }

    /**
     * Composite for stores that are ordered and writable.
     */
    public interface OrderedWritable extends Ordered, Writable{}


    /**
     * Defines operations for stores that can be pruned in terms
     * of the ordering field.
     */
    public interface Prunable extends OrderedWritable
    {

        /**
         * Prune the store by evicting records with a value less than
         * the boundary value;
         *
         * @param boundaryValue Records with a value less than the boundary
         *                      value will be evicted;
         */
        public void prune(long boundaryValue);

    }

    /**
     * Synchronized wrapper.
     */
    class SynchronizedStore implements OrderedWritable
    {
        protected final OrderedWritable delegate;


        private SynchronizedStore(final OrderedWritable delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public RecordBuffer extractRange(final long from, final long to)
                throws IOException
        {
            synchronized(this)
            {
                return delegate.extractRange(from, to);
            }
        }

        @Override
        public void forEach(final Consumer<RecordBuffer> action,
                            final long from, final long to)
                throws IOException
        {
            synchronized (this)
            {
                delegate.forEach(action, from, to);
            }
        }

        @Override
        public void store(final ByteBuffer buffer) throws IOException
        {
            synchronized(this)
            {
                delegate.store(buffer);
            }
        }

        @Override
        public int available()
        {
            synchronized(this)
            {
                return delegate.available();
            }
        }

        @Override
        public void closeWrite() throws IOException
        {
            synchronized (this)
            {
                delegate.closeWrite();
            }
        }
    }

    /**
     * Synchronized wrapper.
     */
    class SynchronizedPrunable implements Prunable
    {
        private final Prunable delegate;

        private SynchronizedPrunable(final Prunable delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public RecordBuffer extractRange(final long from, final long to)
                throws IOException
        {
            synchronized(this)
            {
                return delegate.extractRange(from, to);
            }
        }

        @Override
        public void forEach(final Consumer<RecordBuffer> action,
                            final long from, final long to)
                throws IOException
        {
            synchronized (this)
            {
                delegate.forEach(action, from, to);
            }
        }

        @Override
        public void store(final ByteBuffer buffer) throws IOException
        {
            synchronized(this)
            {
                delegate.store(buffer);
            }
        }

        @Override
        public int available()
        {
            synchronized(this)
            {
                return delegate.available();
            }
        }

        @Override
        public void closeWrite() throws IOException
        {
            synchronized (this)
            {
                delegate.closeWrite();
            }
        }

        @Override
        public void prune(final long boundaryValue)
        {
            synchronized(this)
            {
                delegate.prune(boundaryValue);
            }
        }

    }


}
