package icecube.daq.performance.binary.buffer;


import java.util.ArrayList;

/**
 * Defines an indexed lookup service for record stores.
 */
public interface RecordBufferIndex
{
    /**
     * Returns the index of a record with a value less than
     * the given value.
     *
     * Note: We prohibit indexing to an exact value to avoid requiring
     *       indexes to manage repeating values, i.e. forcing implementations
     *       to guarantee that the first record in a repeating value sequence
     *       is indexed.
     *
     * @param value A value in the ordering scheme of the buffer.
     * @return The index of some record with a value less than the given
     *         value or -1 if all values in the buffer are after the given
     *         value.
     */
    public int lessThan(final long value);


    /**
     * Adds mutating methods for building and managing an index.
     */
    interface UpdatableIndex extends RecordBufferIndex
    {
        /**
         * Add an indexed value. Both position and value must be added
         * sequentially in increasing magnitude.
         *
         * @param position
         * @param value
         */
        public void addIndex(int position, long value);

        /**
         * Update the index by translating each position backward by
         * an offset value. Positions that translate to a negative location
         * will be evicted from the index. This function is utilized by
         * circular buffers to maintain an index on a sliding window of values.
         *
         * @param offset The offset to apply to the indexes, must be positive.
         */
        public void update(int offset);

        /**
         * Evict all values in the index.
         */
        public void clear();
    }

    /**
     * A suitable default for un-indexed cases.
     */
    public static final class NullIndex implements UpdatableIndex
    {
        @Override
        public final int lessThan(final long value)
        {
            return -1;
        }

        @Override
        public final void addIndex(final int position, final long value)
        {
        }

        @Override
        public final void update(final int offset)
        {
        }

        @Override
        public void clear()
        {
        }
    }

    /**
     * An index of values in a record buffer implemented
     * as an ordered list of positions and values.  Useful for
     * sparsely populated indexes where the list iteration is
     * short.
     *
     * Note: Implementation is un-synchronized.
     */
    public static class ArrayListIndex
            implements RecordBufferIndex.UpdatableIndex
    {

        static class Point
        {
            final int location;
            final long value;

            Point(final int location, final long value)
            {
                this.location = location;
                this.value = value;
            }

            @Override
            public String toString()
            {
                return "["+location +", " + value +"]";
            }

        }
        ArrayList<Point> index = new ArrayList<Point>(1024);


        @Override
        public int lessThan(final long value)
        {
            int startIdx = -1;
            for(Point point: index)
            {
                if(point.value >= value)
                {
                    break;
                }
                else
                {
                    startIdx = point.location;
                }
            }
            return startIdx;
        }

        @Override
        public void addIndex(final int position, final long value)
        {
            index.add(new Point( position, value));
        }

        @Override
        public void update(final int offset)
        {
            ArrayList<Point> updated = new ArrayList<Point>(index.size());

            for(Point originalPoint: index)
            {
                int updatedLocation = originalPoint.location - offset;
                if(updatedLocation >= 0)
                {
                    updated.add(new Point(updatedLocation, originalPoint.value));
                }
            }
            index = updated;
        }

        @Override
        public void clear()
        {
            index.clear();
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder(index.size() * 32);
            sb.append("ArrayListIndex:[");
            for (Point p : index)
            {
                 sb.append(p);
            }
            sb.append("]");
            return sb.toString();
        }
    }


    /**
     * Maintains a sparse index of values.
     *
     * Note: Implementation is un-synchronized.
     */
    public class SparseBufferIndex implements RecordBufferIndex.UpdatableIndex
    {
        private final ArrayListIndex delegate;
        final long stride;

        private long lastIndexValue = Long.MIN_VALUE;

        /**
         * Constructor.
         * @param stride Defines the granularity of the index.
         */
        public SparseBufferIndex(final long stride)
        {
            this.delegate = new ArrayListIndex();
            this.stride = stride;
        }

        @Override
        public int lessThan(final long value)
        {
            return delegate.lessThan(value);
        }

        @Override
        public void addIndex(final int position, final long value)
        {
            if(lastIndexValue == Long.MIN_VALUE || value - lastIndexValue >= stride)
            {
                delegate.addIndex(position, value);
                lastIndexValue = value;
            }
        }

        @Override
        public void update(final int offset)
        {
            delegate.update(offset);
        }

        @Override
        public void clear()
        {
            delegate.clear();
        }
    }


}
