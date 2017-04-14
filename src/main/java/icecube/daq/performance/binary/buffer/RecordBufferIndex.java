package icecube.daq.performance.binary.buffer;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

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
     * Note: Does no scale well against the number of indexed
     * points.
     * O(n) for search in lessThan()
     * O(n) for offset update and pruning in update()
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
        ArrayList<Point> index = new ArrayList<>(1024);


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
            ArrayList<Point> updated = new ArrayList<>(index.size());

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
     * An index of values in a record buffer implemented
     * as an ordered list of positions and values.  Useful for
     * sparsely populated indexes where the list iteration is
     * short.
     *
     * Note: Scales better that ArrayListIndex.
     * O(log n) for search in lessThan()
     * O(1) for offset update in update()
     * O(log n) for pruning update()
     *
     * Note: Implementation is un-synchronized.
     */
    public static class BinarySearchArrayListIndex
            implements RecordBufferIndex.UpdatableIndex
    {
        /**
         * Holds an indexed value.
         */
        private static class Point
        {
            final int location;
            final long value;
            final SegmentOffset offset;

            Point(final int location, final long value,
                  final SegmentOffset offset)
            {
                this.location = location;
                this.value = value;
                this.offset = offset;
            }

            /**
             * @return The location of the value in the buffer,
             * adjusted for the latest offset.
             */
            int resolveLocation()
            {
                return location - offset.offset;
            }

            @Override
            public String toString()
            {
                return String.format("[(%d - %d)=%d, %d]", location,
                        offset.offset, resolveLocation(), value);
            }

        }

        /**
         * Holds the latest offset adjustments for a segment of
         * index points.
         *
         * This decreases the index update time from O(N) to O(1).
         *
         * The offset adjustment will be calculated on the fly during
         * binary search if and when the point is utilized.
         */
        private static class SegmentOffset
        {
            /**
             * The current location offset adjustment to apply to the point.
             */
            private int offset;          //

            /**
             * The maximum value of the offset, after which the point has
             * shifted begon the begining of the buffer being indexed.
             */
            private long expiryLocation;
        }

        /**
         * List of offsets associated with index points created before
         * the last offset update.
         *
         * Note: The index must be array-based to realize the benefit
         * of the binary search
         */
        private final List<SegmentOffset> previousOffsets = new ArrayList<>();

        /** The offset to associate with new index points. */
        private SegmentOffset currentOffset = new SegmentOffset();

        /** Flyweight zero-offset for use in searches. */
        private final SegmentOffset NO_OFFSET = new SegmentOffset();

        /**
         * List of index points.
         *
         * Note: The index must be array-based to realize the benefit
         * of the binary search.
         */
        private List<Point> index = new ArrayList<>(1024);

        /**
         * Compares Points by value field. Used to search index
         * for a value.
         */
        private final Comparator<Point> valueComparator =
                new Comparator<Point>()
                {
                    @Override
                    public int compare(final Point o1, final Point o2)
                    {
                        if (o1.value < o2.value)
                        {
                            return -1;
                        }
                        else if (o1.value > o2.value)
                        {
                            return 1;
                        }

                        return 0;
                    }
                };

        /**
         * Compares Points by location field. Used to search index
         * for a location.
         */
        private final Comparator<Point> locationComparator =
                new Comparator<Point>()
                {
                    @Override
                    public int compare(final Point o1, final Point o2)
                    {
                        if (o1.resolveLocation() < o2.resolveLocation())
                        {
                            return -1;
                        }
                        else if (o1.resolveLocation() > o2.resolveLocation())
                        {
                            return 1;
                        }

                        return 0;
                    }
                };

        @Override
        public int lessThan(final long value)
        {

            // Use binary search to find where the value lies in the index.
            // Note that both the sign and value of the return value of
            // binarySearch() encodes the search result.

            int point = Collections.binarySearch(index,
                    new Point(-1, value, null), valueComparator);

            if(point >= 0)
            {

                // positive return values indicate an exact match.
                // In order to meet the contract of lessThan(), walk backward
                // until a lower value or of index is reached.
                while( point > 0 && index.get(point-1).value >= value)
                {
                    point--;
                }
                if(point==0)
                {
                    return -1;
                }
                else
                {
                    return index.get(point-1).resolveLocation();
                }
            }
            else
            {
                // negative values encode the insertion point of the value
                if(point == -1 )
                {
                    return -1;
                }

                return index.get((-point)-2).resolveLocation();
            }
        }

        @Override
        public void addIndex(final int position, final long value)
        {
            index.add(new Point(position, value, currentOffset));
        }

        @Override
        public void update(final int offset)
        {
            if(index.size() < 1)
            {
                return;
            }

            // start a new offset group
            currentOffset.expiryLocation =
                    index.get(index.size() - 1).resolveLocation();
            previousOffsets.add(currentOffset);
            currentOffset = new SegmentOffset();

            // update prior offset groups, pruning those that have shifted
            // beyond the start of the buffer.
            Iterator<SegmentOffset> iterator = previousOffsets.iterator();
            while(iterator.hasNext())
            {
                SegmentOffset next = iterator.next();
                next.offset = next.offset + offset;

                if (next.offset > next.expiryLocation)
                {
                    iterator.remove();
                }
            }


            // prune points that have shifted past zero
            int cutoff = Collections.binarySearch(index,
                    new Point(0, -1, NO_OFFSET), locationComparator);

            final int startPoint;
            if(cutoff >= 0)
            {
                // positive values encodes exact match, start at
                // this point and prune previous points
                startPoint = cutoff;
            }
            else
            {
                // negative values encode insertion point as
                // (-(insertionpoint) - 1)
                startPoint = -(cutoff + 1);
            }


            if(startPoint > 0)
            {
                // copy survivors is faster than deleting others individually
                ArrayList<Point> updated = new ArrayList<>(index.size());
                updated.addAll(index.subList(startPoint, index.size()));
                index = updated;
            }

        }

        @Override
        public void clear()
        {
            index.clear();
            previousOffsets.clear();
            currentOffset = new SegmentOffset();
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder(index.size() * 32);
            sb.append("BinarySearchArrayListIndex:[");
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
        /** The backing index. */
        private final RecordBufferIndex.UpdatableIndex delegate;

        /** The value interval for indexing. */
        final long stride;

        /** Tracks the last indexed value. */
        private long lastIndexValue = Long.MIN_VALUE;

        /**
         * Constructor.
         * @param stride Defines the granularity of the index.
         */
        public SparseBufferIndex(final long stride)
        {
            this.delegate = new BinarySearchArrayListIndex();
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
            if(value - lastIndexValue >= stride ||
                    lastIndexValue == Long.MIN_VALUE)
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
            lastIndexValue = Long.MIN_VALUE;
        }

    }


}
