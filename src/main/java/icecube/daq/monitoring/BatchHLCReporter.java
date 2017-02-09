package icecube.daq.monitoring;

/**
 * Accumulates HLC hit information for batched submission
 * to a RunMonitor.
 *
 * Note: Not thread safe, for single thread access only.
 */
public class BatchHLCReporter
{
    private final int batchSize;
    private long[] mbidBatch;
    protected long[] utcBatch;
    protected int idx;

    // run monitor is set post-construction
    private IRunMonitor runMonitor;


    public BatchHLCReporter(final int batchSize)
    {
        this.batchSize = batchSize;
        this.mbidBatch = new long[batchSize];
        this.utcBatch = new long[batchSize];
    }

    public void reportHLCHit(final long mbid, final long utc)
    {
        mbidBatch[idx] = mbid;
        utcBatch[idx] = utc;

        idx++;
        if(idx == batchSize)
        {
            flush();
        }
    }

    public void flush()
    {
        if(runMonitor != null)
        {
            if(idx < batchSize)
            {
                // a runt batch
                long[] runtMbid = new long[idx];
                long[] runtUTC = new long[idx];
                System.arraycopy(mbidBatch, 0, runtMbid, 0, runtMbid.length);
                System.arraycopy(utcBatch, 0, runtUTC, 0, runtUTC.length);
                runMonitor.countHLCHit(runtMbid, runtUTC);
            }
            else
            {
                // a full batch
                runMonitor.countHLCHit(mbidBatch, utcBatch);
            }
        }
        this.mbidBatch = new long[batchSize];
        this.utcBatch = new long[batchSize];
        idx = 0;
    }

    public void setRunMonitor(final IRunMonitor runMonitor)
    {
        this.runMonitor = runMonitor;
    }

    /**
     * Decorates BatchHLCReporter with a time-based automatic flush.
     *
     * This behavior is desired to control the accuracy of the last
     * hlc count bin of a switch run. (Because hubs vary in the rate of hlc
     * hits)
     */
    public static class TimedAutoFlush extends BatchHLCReporter
    {
        private final long autoFlushInterval;

        public TimedAutoFlush(final int batchSize, final long autoFlushInterval)
        {
            super(batchSize);
            this.autoFlushInterval = autoFlushInterval;
        }

        @Override
        public void reportHLCHit(final long mbid, final long utc)
        {
            super.reportHLCHit(mbid, utc);
            if(idx > 1)
            {
                long interval = utcBatch[idx-1] - utcBatch[0];
                if(interval >= autoFlushInterval)
                {
                    flush();
                }
            }
        }
    }
}
