package icecube.daq.monitoring;

/**
 * MBean interface for StringHub back-end data.
 */
public interface MonitoringDataMBean extends SenderMXBean
{
    /**
     * Get number of hits cached for readout being built
     *
     * @return num hits cached
     */
    int getNumHitsCached();
}
