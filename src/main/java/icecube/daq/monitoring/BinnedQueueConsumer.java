package icecube.daq.monitoring;

import java.util.HashMap;
import java.util.Map;

/**
 * Send periodic reports throughout a run
 */
abstract class BinnedQueueConsumer<T, K, C>
    extends QueueConsumer<T>
{
    class BinRange
    {
        long binStart;
        long binEnd;
        boolean isPrevious;

        BinRange(long binStart, long binEnd, boolean isPrevious)
        {
            this.binStart = binStart;
            this.binEnd = binEnd;
            this.isPrevious = isPrevious;
        }

        public String toString()
        {
            return String.format("BinRange[%d-%d]%s", binStart, binEnd,
                                 isPrevious ? "previous" : "");
        }
    }

    private long binWidth;

    private HashMap<K, BinManager<C>> map =
        new HashMap<K, BinManager<C>>();

    /**
     * The max value that has already been reported.
     * Incoming values (from delayed producers) less than or equal this
     * value will be rejected as they can no longer be reported and contravene
     * bin management.
     */
    private long lastReportedBinEnd = Long.MIN_VALUE;

    static class ExpiredRange extends Exception
    {
        public ExpiredRange(final String message)
        {
            super(message);
        }
    }

    BinnedQueueConsumer(IRunMonitor parent, long binWidth)
    {
        super(parent);

        this.binWidth = binWidth;
    }

    void clearBin(long binStart, long binEnd)
    {
        for (BinManager<C> mgr : map.values()) {
            mgr.clearBin(binStart, binEnd);
        }
        lastReportedBinEnd = binEnd;
    }

    boolean containsKey(K key)
    {
        return map.containsKey(key);
    }

    abstract BinManager<C> createBinManager(K key, long binStart,
                                            long binWidth);

    Iterable<Map.Entry<K, BinManager<C>>> entries()
    {
        return map.entrySet();
    }

    BinRange findRange()
    {
        long binStart = Long.MAX_VALUE;
        long binEnd = Long.MIN_VALUE;
        boolean isPrevious = false;
        for (BinManager<C> mgr : map.values()) {
            if (mgr.hasPrevious()) {
                isPrevious = true;
                break;
            }
        }

        for (BinManager<C> mgr : map.values()) {
            if (isPrevious) {
                if (mgr.hasPrevious()) {
                    if (mgr.getPreviousStart() < binStart) {
                        binStart = mgr.getPreviousStart();
                    }
                    if (mgr.getPreviousEnd() > binEnd) {
                        binEnd = mgr.getPreviousEnd() - 1;
                    }
                }
            } else if (mgr.hasActive()) {
                if (mgr.getActiveStart() < binStart) {
                    binStart = mgr.getActiveStart();
                }
                if (mgr.getActiveEnd() > binEnd) {
                    binEnd = mgr.getActiveEnd() - 1;
                }
            }
        }

        if (binStart == Long.MAX_VALUE) {
            return null;
        }

        return new BinRange(binStart, binEnd, isPrevious);
    }

    public synchronized C getContainer(long binIndex, K key)
            throws ExpiredRange
    {
        if(binIndex <= lastReportedBinEnd)
        {
            String msg = String.format("Index %d is earlier than the end" +
                    " of the last reported bin range %d",
                    binIndex, lastReportedBinEnd);
            throw new ExpiredRange(msg);
        }
        if (!map.containsKey(key)) {
            map.put(key, createBinManager(key, binIndex, binWidth));
        }

        BinManager<C> mgr = map.get(key);
        if (mgr.pastLatestBin(binIndex)) {
            boolean sendData = true;
            boolean hasPrevious = false;
            for (BinManager<C> chkMgr : map.values()) {
                // if the binIndex is in the latest bin...
                if (!chkMgr.pastLatestBin(binIndex)) {
                    // ...don't send a report yet
                    sendData = false;
                    break;
                } else if (chkMgr.hasPrevious()) {
                    hasPrevious = true;
                }
            }

            if (sendData && hasPrevious) {
                BinRange rng = findRange();
                if (rng == null) {
                    LOG.error("No data to report!");
                } else {
                    sendData(rng.binStart, rng.binEnd);
                    clearBin(rng.binStart, rng.binEnd);
                }
            }
        }

        return mgr.get(binIndex);
    }

    /**
     * Get the existing container
     * @param key key
     * @param binStart starting index
     * @param binEnd ending index
     * @return bin container
     */
    C getExisting(K key, long binStart, long binEnd)
    {
        if (map.containsKey(key)) {
            return map.get(key).getExisting(binStart, binEnd);
        }

        return null;
    }

    /**
     * Reset everything back to initial conditions for the next run
     */
    public void reset()
    {
        map.clear();
    }

    /**
     * Send the totals for the specified bin to Live
     */
    abstract void sendData(long binStart, long binEnd);

    /**
     * Send per-run quantities.
     */
    @Override
    public void sendRunData()
    {
        while (true) {
            BinRange rng = findRange();
            if (rng == null) {
                break;
            }

            sendData(rng.binStart, rng.binEnd);
            clearBin(rng.binStart, rng.binEnd);

            if (!rng.isPrevious) {
                break;
            }
        }

        reset();
    }

    public String toString()
    {
        StringBuilder buf = new StringBuilder("{");
        for (K key : map.keySet()) {
            if (buf.length() > 1) {
                buf.append(", ");
            }
            buf.append(key).append(": ").append(map.get(key));
        }
        buf.append("}");
        return buf.toString();
    }
}
