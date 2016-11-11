package icecube.daq.domapp.dataprocessor;

import icecube.daq.dor.TimeCalib;
import icecube.daq.monitoring.IRunMonitor;
import icecube.daq.util.RealTimeRateMeter;
import icecube.daq.util.SimpleMovingAverage;

import java.nio.ByteBuffer;

/**
 * Accumulates data counters from the processor for use by the data collector.
 *
 * This class was implemented to be modified by the processor thread
 * and read by the acquisition thread. It is not explicitly synchronized.
 * An exception is the reportClockRelationship() method which is called from
 * acquisition because it is the only object with knowledge of the
 * point-in-time of dom clock acquisition.
 *
 * Note that the values are derived from processed data. Acquired data
 * that is queued in the processor is not represented.
 */
public class DataStats
{

    //NOTE: Instances of this class are shared between threads,
    //      all members must be thread safe.

    private final long mbid;

    private volatile int     numHits               = 0;
    private volatile int     numMoni               = 0;
    private volatile int     numSupernova          = 0;

    private volatile int     numLBMOverflows       = 0;

    private volatile int     validRAPCalCount      = 0;
    private volatile int     errorRAPCalCount      = 0;
    private volatile double  domFrequencySkew      = 0;
    private volatile double  cableLength           = 0;

    private volatile long    firstHitTime          = -1;
    private volatile long    lastHitTime           = -1;

    private volatile long    firstDORTime          = -1;
    private volatile long    firstDOMTime          = -1;
    private volatile long    lastDORTime           = -1;
    private volatile long    lastDOMTime           = -1;

    private volatile int     processorQueueDepth   = 0;
    private volatile int     maxProcessorQueueDepth   = 0;
    private volatile int     dispatcherQueueDepth  = 0;
    private volatile int     maxDispatcherQueueDepth  = 0;

    // average latency measurement samples such that the
    // reported average has meaning within the 90 second
    // moni polling period.
    private SimpleMovingAverage avgHitAcquisitionLatencyMillis =
            new SimpleMovingAverage(9);
    private long windowNanos = 10000000000L;
    private long lastHitLatencySampleNanos;


    // Calculate 10-sec averages of the hit rate
    private RealTimeRateMeter rtHitRate = new RealTimeRateMeter(100000000000L);
    private RealTimeRateMeter rtLCRate  = new RealTimeRateMeter(100000000000L);

    //consider eliminating not used
    private long lastTcalUT;


    /**
     * Provides a loosely calibrated mapping from the DOM clock
     * to the system monotonic clock.
     *
     * This mapping is for diagnostic use only as the quality of the
     * calibration is not guaranteed.
     *
     */
    private class DOMToSystemTimer
    {
        private volatile long offsetNanos;

        void update(long domclk, long systemNanos)
        {
            offsetNanos = systemNanos - (domclk * 25);
        }

        long translate(long domclk)
        {
            return (domclk*25) + offsetNanos;
        }
    }
    private final DOMToSystemTimer domToSystemTimer = new
            DOMToSystemTimer();

    /**
     * Accumulates HLC hit information for batched submission
     * to a RunMonitor.
     *
     * Note: Not thread safe, for processor thread access only.
     */
    private static class BatchReporter
    {
        private final long mbid;
        private final int batchSize;
        private long[] batch;
        private int idx;

        // run monitor is set post-construction
        private IRunMonitor runMonitor;


        private BatchReporter(final long mbid,
                              final int batchSize)
        {
            this.batchSize = batchSize;
            this.mbid = mbid;
            this.batch = new long[batchSize];
        }

        void reportHLCHit(final long utc)
        {
            batch[idx++] = utc;
            if(idx == batchSize)
            {
                flush();
            }
        }

        void flush()
        {
            if(runMonitor != null)
            {
                if(idx < batchSize)
                {
                    // a runt batch
                    long[] runt = new long[idx];
                    System.arraycopy(batch, 0, runt, 0, runt.length);
                    runMonitor.countHLCHit(mbid, runt);
                }
                else
                {
                    // a full batch
                    runMonitor.countHLCHit(mbid, batch);
                }
            }
            batch = new long[batchSize];
            idx = 0;
        }

        void setRunMonitor(final IRunMonitor runMonitor)
        {
            this.runMonitor = runMonitor;
        }
    }

    // HLS hit reporting is propagated to an independent monitor.
    // The reporting is batched to minimize inter-thread transfer overhead.
    private final int HIT_MONITOR_BATCH_SIZE = 100;
    private final BatchReporter hlcReporter;


    public DataStats(long mbid)
    {
        this.mbid = mbid;
        this.hlcReporter = new BatchReporter(mbid, HIT_MONITOR_BATCH_SIZE);
    }

    protected  void reportProcessingStart(DataProcessor.StreamType streamType,
                                          ByteBuffer data)
    {
        //supports performance stats extensions
    }

    protected  void reportProcessingEnd(DataProcessor.StreamType streamType)
    {
        //supports performance stats extensions
    }

    protected void reportLBMOverflow()
    {
        numLBMOverflows++;
    }

    /**
     * Report the relationship between the DOM clock and the system monotonic
     * clock.
     *
     * Note: This is called from the acquisition thread.
     */
    public void reportClockRelationship(final long domClock, final long systemNanos)
    {
        domToSystemTimer.update(domClock, systemNanos);
    }

    protected void reportTCAL(final TimeCalib tcal,
                              final long utc,
                              final double cableLength,
                              final double domFrequencySkew)
    {
        validRAPCalCount++;
        lastTcalUT = utc;

        this.cableLength = cableLength;
        this.domFrequencySkew = domFrequencySkew;

        if(firstDORTime <0) {firstDORTime = tcal.getDorTxInDorUnits();}
        if(firstDOMTime <0) {firstDOMTime = tcal.getDomTxInDomUnits();}

        lastDORTime = tcal.getDorTxInDorUnits();
        lastDOMTime = tcal.getDomTxInDomUnits();
    }

    protected void reportTCALError()
    {
        errorRAPCalCount++;
    }

    protected void reportMoni()
    {
        numMoni++;
    }

    protected void reportSupernova()
    {
        numSupernova++;
    }

    protected void reportHit(final boolean isLC,
                             final long domclk,
                             final long utc)
    {
        numHits++;

        //record rates
        if(isLC)
        {
            rtLCRate.recordEvent(utc);
            hlcReporter.reportHLCHit(utc);
        }
        rtHitRate.recordEvent(utc);

        //track time span, prohibiting backward steps
        lastHitTime = Math.max(lastHitTime, utc);
        if (firstHitTime < 0L) {firstHitTime = utc;}

        // track the latency of the hit from DOM to here, sampling
        // once per 10 seconds
        long now = now();
        if( (numHits==1) || (now - lastHitLatencySampleNanos > windowNanos) )
        {
            long latency = now - domToSystemTimer.translate(domclk);
            avgHitAcquisitionLatencyMillis.add(latency/1000000);
            lastHitLatencySampleNanos = now;
        }
    }

    protected void reportHitStreamEOS()
    {
        // ensure accumulated hlc hits are reported
        hlcReporter.flush();
    }

    protected void reportProcessorQueueDepth(final int depth)
    {
        this.processorQueueDepth = depth;
        maxProcessorQueueDepth = Math.max(maxProcessorQueueDepth, depth);
    }

    protected void reportDispatcherQueueDepth(final int depth)
    {
        this.dispatcherQueueDepth = depth;
        maxDispatcherQueueDepth = Math.max(maxDispatcherQueueDepth, depth);
    }

    private long now()
    {
        return System.nanoTime();
    }

    public long getMainboardID()
    {
        return mbid;
    }

    public int getNumHits()
    {
        return numHits;
    }

    public long getFirstHitTime()
    {
        return firstHitTime;
    }

    public long getLastHitTime()
    {
        return lastHitTime;
    }

    public double getLCHitRate()
    {
        return rtLCRate.getRate();
    }

    public double getHitRate()
    {
        return rtHitRate.getRate();
    }

    public long getNumLBMOverflows()
    {
        return numLBMOverflows;
    }

    public int getNumMoni()
    {
        return  numMoni;
    }

    public int getNumSupernova()
    {
        return numSupernova;
    }

    public int getValidRAPCalCount()
    {
        return validRAPCalCount;
    }

    public int getErrorRAPCalCount()
    {
        return errorRAPCalCount;
    }

    public double getCableLength()
    {
        return cableLength;
    }

    public double getDomFrequencySkew()
    {
        return domFrequencySkew;
    }

    public long getFirstDORTime()
    {
        return firstDORTime;
    }

    public long getFirstDOMTime()
    {
        return firstDOMTime;
    }

    public long getLastDORTime()
    {
        return lastDORTime;
    }

    public long getLastDOMTime()
    {
        return lastDOMTime;
    }

    public long getLastTcalUT()
    {
        return lastTcalUT;
    }

    public int getProcessorQueueDepth()
    {
        return processorQueueDepth;
    }

    public int getMaxProcessorQueueDepth()
    {
        return maxProcessorQueueDepth;
    }

    public int getDispatcherQueueDepth()
    {
        return dispatcherQueueDepth;
    }

    public int getMaxDispatcherQueueDepth()
    {
        return maxDispatcherQueueDepth;
    }

    public double getAvgHitAcquisitionLatencyMillis()
    {
        return avgHitAcquisitionLatencyMillis.getAverage();
    }

    public void setRunMonitor(final IRunMonitor runMonitor)
    {
        this.hlcReporter.setRunMonitor(runMonitor);
    }

}
