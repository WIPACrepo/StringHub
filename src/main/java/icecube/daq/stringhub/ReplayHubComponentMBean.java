package icecube.daq.stringhub;

/**
 * MBeans for ReplayHubComponent
 */
public interface ReplayHubComponentMBean
    extends StringHubComponentMBean
{
    /**
     * Return the number of hitspool files read so far.
     *
     * @return number of files
     */
    int getNumFiles();

    /**
     * Return the number of payloads queued for reading.
     *
     * @return input queue size
     */
    long getNumInputsQueued();

    /**
     * Return the number of payloads queued for writing.
     *
     * @return output queue size
     */
    long getNumOutputsQueued();

    /**
     * Get the total time (in nanoseconds) behind the DAQ time.
     *
     * @return total nanoseconds behind the current DAQ time
     */
    long getTotalBehind();

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
