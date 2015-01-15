package icecube.daq.stringhub;

/**
 * MBeans for ReplayHubComponent
 */
public interface ReplayHubComponentMBean
    extends StringHubComponentMBean
{
    /**
     * Return the number of requests queued for writing.
     *
     * @return output queue size
     */
    long getNumQueued();

    /**
     * Get the current gap between DAQ time and system time
     *
     * @return gap between DAQ and system time
     */
    double getTimeGap();

    /**
     * Get the total number of payloads read.
     *
     * @return total payloads
     */
    long getTotalPayloads();

    /**
     * Get the total time (in nanoseconds) spent sleeping in order to
     * match DAQ time to system time
     *
     * @return total nanoseconds spent sleeping
     */
    long getTotalSleep();
}
