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
     */
    String getAcquisitionStartTime();
}
