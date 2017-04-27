package icecube.daq.monitoring;

/**
 * The base monitoring interface for the sender subsystem.
 *
 */
public interface SenderMXBean
{
    /**
     * Get number of hits queued for processing.
     *
     * @return num hits queued
     */
    int getNumHitsQueued();

    /**
     * Get number of hits received.
     *
     * @return num hits received
     */
    long getNumHitsReceived();

    /**
     * Number of readout requests correctly queued for processing.
     *
     * @return num readout requests queued
     */
    long getNumReadoutRequestsQueued();

    /**
     * Number of readout requests received for this run.
     *
     * @return num readout requests received
     */
    long getNumReadoutRequestsReceived();

    /**
     * Get number of readouts sent.
     *
     * @return num readouts sent
     */
    long getNumReadoutsSent();
}
