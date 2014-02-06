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
}
