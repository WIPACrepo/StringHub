package icecube.daq.domapp.dataprocessor;

import icecube.daq.util.RealTimeRateMeter;

import java.nio.ByteBuffer;

/**
 * Accumulates data counters from the processor for use by the data collector.
 *
 * This class was implemented to be modified by the processor thread
 * and read by the acquisition thread. It is not explicitly synchronized.
 *
 * Note that the values derived from processed data. Acquired data
 * that is queued in the processor is not represented.
 */
public class DataStats
{

    //NOTE: Instances of this class are shared between threads,
    //      all members must be thread safe.

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


    // Calculate 10-sec averages of the hit rate
    private RealTimeRateMeter rtHitRate = new RealTimeRateMeter(100000000000L);
    private RealTimeRateMeter rtLCRate  = new RealTimeRateMeter(100000000000L);

    //consider eliminating not used
    private long lastTcalUT;

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

    protected void reportTCAL(long utc, double cableLength,
                              double domFrequencySkew)
    {
        validRAPCalCount++;
        lastTcalUT = utc;

        this.cableLength = cableLength;
        this.domFrequencySkew = domFrequencySkew;
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
        }
        rtHitRate.recordEvent(utc);

        //track time span, prohibiting backward steps
        lastHitTime = Math.max(lastHitTime, utc);
        if (firstHitTime < 0L) {firstHitTime = utc;}

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

    public long getLastTcalUT()
    {
        return lastTcalUT;
    }


}
