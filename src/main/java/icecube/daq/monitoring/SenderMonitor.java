package icecube.daq.monitoring;

public interface SenderMonitor
{
    /**
     * Get the time of the most recently queued hit.
     *
     * @return latest time
     */
    long getLatestHitTime();

    /**
     * Get the end time of the most recent readout data payload.
     *
     * @return latest readout data end time
     */
    long[] getLatestReadoutTimes();

    /**
     * Get number of hits cached for readout being built
     *
     * @return number of cached hits
     */
    int getNumHitsCached();

    /**
     * Get number of hits queued for processing.
     *
     * @return number of hits queued
     */
    int getNumHitsQueued();

    /**
     * Get number of hits received.
     *
     * @return number of hits received
     */
    long getNumHitsReceived();

    /**
     * Number of readout requests currectly queued for processing.
     *
     * @return number of readout requests queued
     */
    long getNumReadoutRequestsQueued();

    /**
     * Number of readout requests received for this run.
     *
     * @return number of readout requests received
     */
    long getNumReadoutRequestsReceived();

    /**
     * Get number of readouts sent.
     *
     * @return number of readouts sent
     */
    long getNumReadoutsSent();

    /**
     * Get number of recycled payloads.
     *
     * @return number of recycled payloads
     */
    long getNumRecycled();

    /**
     * Total number of hits received since last reset.
     *
     * @return total number of hits received since last reset
     */
    long getTotalHitsReceived();

    /**
     * Total number of stop messages sent to the string processors
     *
     * @return total number of sent stop messages
     */
    long getTotalStopsSent();
}
