package icecube.daq.monitoring;

/**
 * Manage an active bin and a previous bin
 */
abstract class BinManager<C>
{
    /**
     * Bin of data
     */
    class Bin<C>
    {
        /** Start of this bin */
        private long binStart;
        /** End of this bin */
        private long binEnd;
        /** Current container for this bin */
        private C container;

        /**
         * Create a bin
         * @param binStart start of bin
         * @param binEnd end of bin
         * @param container container for the bin's value(s)
         */
        Bin(long binStart, long binEnd, C container)
        {
            this.binStart = binStart;
            this.binEnd = binEnd;
            this.container = container;
        }

        /**
         * Does this bin contain the specified index?
         * @return <tt>true</tt> if the index is inside this bin
         */
        boolean contains(long binIndex)
        {
            return binStart <= binIndex && binEnd > binIndex;
        }

        /**
         * Get the end of this bin
         * @return bin end
         */
        long getEnd()
        {
            return binEnd;
        }

        /**
         * Get the start of this bin
         * @return bin start
         */
        long getStart()
        {
            return binStart;
        }

        /**
         * Get the container for this bin's values
         * @return bin container
         */
        C getContainer()
        {
            return container;
        }

        boolean overlaps(long start, long end)
        {
            return start < binEnd && end >= binStart;
        }

        public String toString()
        {
            return String.format("Bin[%d-%d: %s]", binStart, binEnd,
                                 container.toString());
        }
    }

    /** Name of this set of bins */
    private String name;
    /** Width of each bin */
    private long binWidth;
    /** Active bin */
    private Bin<C> active;
    /** Previous bin (may be null) */
    private Bin<C> previous;
    /** Lock used to serialize access to the active and previous bins */
    private Object binLock = new Object();
    /** End of most recent active bin */
    private long lastBinEnd;

    /**
     * Create a bin manager
     * @param binStart starting index for the first bin
     * @param binWidth size of each bin
     */
    BinManager(String name, long binStart, long binWidth)
    {
        this.name = name;

        this.binWidth = binWidth;

        long partial = binStart % binWidth;

        long binEnd = binStart + binWidth;
        if (partial != 0) {
            // ensure initial bin ends on a boundary
            binEnd -= partial;
        }

        active = new Bin(binStart, binEnd, createBinContainer());
        lastBinEnd = active.getEnd();
    }

    public void clearBin(long binStart, long binEnd)
    {
        if (previous != null && previous.overlaps(binStart, binEnd)) {
            previous = null;
        } else if (active != null && active.overlaps(binStart, binEnd)) {
            if (previous != null) {
                throw new Error("Cannot clear active bin without clearing" +
                                " previous bin");
            }

            active = null;
        }
    }

    /**
     * Create a bin container
     * @return new bin container
     */
    abstract C createBinContainer();

    /**
     * Get the container for the specified index,
     * creating a container if necessary
     * @param binIndex index of container to return
     * @return bin container
     */
    C get(long binIndex)
    {
        synchronized (binLock) {
            // if the active bin doesn't contain this index...
            if (active == null || !active.contains(binIndex)) {
                long newStart;
                if (active != null) {
                    newStart = active.getEnd();
                } else {
                    newStart = lastBinEnd;
                }
                if (binIndex > newStart) {
                    while (binIndex > newStart) {
                        newStart += binWidth;
                    }
                    newStart -= binWidth;
                }

                if (binIndex < newStart) {
                    throw new Error("Bin index (" + binIndex +
                                    ") is less than active start index (" +
                                    newStart + ")");
                }

                // die if there's already a previous bin
                if (previous != null) {
                    throw new Error("Previous bin has not been cleared!" +
                                    "  Previous=" + previous + ", active=" +
                                    active + ", index=" + binIndex);
                }

                // demote the active bin
                previous = active;

                // create a new active bin
                active = new Bin(newStart, newStart + binWidth,
                                 createBinContainer());
                lastBinEnd = active.getEnd();
            }

            // return the container associated with this bin
            return active.getContainer();
        }
    }

    long getActiveEnd()
    {
        if (active == null) {
            throw new Error("BinManager has no active bin");
        }

        return active.getEnd();
    }

    long getActiveStart()
    {
        if (active == null) {
            throw new Error("BinManager has no active bin");
        }

        return active.getStart();
    }

    /**
     * Get the container for the specified index
     * @param binIndex index of container to return
     * @return bin container
     */
    C getExisting(long binStart, long binEnd)
    {
        synchronized (binLock) {
            if (previous != null && previous.overlaps(binStart, binEnd)) {
                return previous.getContainer();
            } else if (active != null && active.overlaps(binStart, binEnd)) {
                return active.getContainer();
            }
        }

        return null;
    }

    long getPreviousEnd()
    {
        if (previous == null) {
            throw new Error("BinManager has no previous bin");
        }

        return previous.getEnd();
    }

    long getPreviousStart()
    {
        if (previous == null) {
            throw new Error("BinManager has no previous bin");
        }

        return previous.getStart();
    }

    /**
     * Does this manager have an active bin?
     * @return <tt>true</tt> if there's an active bin
     */
    boolean hasActive()
    {
        return active != null;
    }

    /**
     * Does this manager have a previous bin?
     * @return <tt>true</tt> if there's a previous bin
     */
    boolean hasPrevious()
    {
        return previous != null;
    }

    /**
     * Is the bin index past the previous and/or active bin?
     * @param binIndex index to check
     * @return <tt>true</tt> if there is data to send
     */
    boolean pastLatestBin(long binIndex)
    {
        final long binEnd;
        if (active != null) {
            binEnd = active.getEnd();
        } else if (previous != null) {
            binEnd = previous.getEnd() + binWidth;
        } else {
            binEnd = lastBinEnd + binWidth;
        }

        return (binIndex >= binEnd);
    }

    public String toString()
    {
        StringBuilder buf = new StringBuilder("BinManager[");
        buf.append(name).append("::width=").append(binWidth);
        buf.append(",lastEnd=").append(lastBinEnd);

        if (previous == null) {
            if (active != null) {
                buf.append(",").append(active.toString());
            }
        } else if (active == null) {
            buf.append(",").append(previous.toString()).append("/<NoActive>");
        } else {
            buf.append(",").append(previous.toString()).append("/").
                append(active.toString());
        }

        buf.append(']');
        return buf.toString();
    }
}
