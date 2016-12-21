package icecube.daq.domapp;

/**
 * Monitor MBean for collection of data collectors
 */

public interface DataCollectorMBean
{
    /**
     * Get the mainboard ID for this DataCollector
     * @return 12-char hex string
     */
    String getMainboardId();
    
    /**
     * Get the SLC hit rate
     * @return hit rate in Hz
     */
    double getHitRate();
    
    /**
     * Get the HLC hitrate 
     * @return hit rate in Hz
     */
    double getHitRateLC();
    
    /**
     * Get the number of acquired hits.
     */
    long getNumHits();
    
    /**
     * Get the number of monitor records
     */
    long getNumMoni();

    /**
     * Get the number of successful tcals
     */
    long getNumTcal();

    /**
     * Get the number of tcals that where rejected by RAPCal.
     */
    long getNumBadTcals();


    /**
     * Get the average cable length estimate from RAPCal.
     */
    double getCableLength();

    /**
     * Get the latest frequency skew estimate from RAPCal.
     */
    double getDOMFrequencySkew();

    /**
     * Get the first DOR clock reading.
     */
    long getFirstDORTime();

    /**
     * Get the last DOR clock reading.
     */
    long getLastDORTime();

    /**
     * Get the first DOM clock reading.
     */
    long getFirstDOMTime();

    /**
     * Get the last DOM clock reading.
     */
    long getLastDOMTime();

    /**
     * Get the number of supernova packets
     */
    long getNumSupernova();

    /**
     * Get the run state
     */
    String getRunState();

    /**
     * Get the current acquisition loop count
     */
    long getAcquisitionLoopCount();

    /**
     * Get the number of DOM buffer overflows.
     */
    long getLBMOverflowCount();

    /**
     * Get the average latency of hit data acquisition in milliseconds.
     */
    long getAverageHitAcquisitionLatencyMillis();

    /**
     * Get the timestamp at which data acquisition began.
     *
     * NOTE: This is based on the local system clock.
     */
    String getAcquisitionStartTime();

    /**
     * Get the number of hit messages queued for processing, returned
     * array contains [current, max].
     */
    int[] getHitProcessorQueueDepth();

    /**
     * Get the number of hits queued for dispatching, returned
     * array contains [current, max].
     */
    int[] getHitDispatcherQueueDepth();

    /**
     * Get the acquisition pause time, returned
     * array contains [current average, max] in milliseconds.
     */
    long[] getAcquisitionPauseTimeMillis();
}
