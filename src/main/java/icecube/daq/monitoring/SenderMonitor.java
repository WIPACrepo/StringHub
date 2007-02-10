package icecube.daq.monitoring;

public interface SenderMonitor
{
    /**
     * Get average number of hits per readout.
     *
     * @return hits/readout
     */
    long getAverageHitsPerReadout();

    /**
     * Get back-end timing profile.
     *
     * @return back-end timing profile
     */
    String getBackEndTiming();

    /**
     * Get current rate of hits per second.
     *
     * @return hits/second
     */
    double getHitsPerSecond();
 
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
     * Get number of hits which could not be loaded.
     *
     * @return number of bad hits received
     */
    long getNumBadHits();

    /**
     * Number of readout requests which could not be loaded.
     *
     * @return number of bad readout requests
     */
    long getNumBadReadoutRequests();

    /**
     * Get number of passes through the main loop without a request.
     *
     * @return number of empty loops
     */
    long getNumEmptyLoops();

    /**
     * Get number of hits cached for readout being built
     *
     * @return number of cached hits
     */
    int getNumHitsCached();

    /**
     * Get number of hits thrown away.
     *
     * @return number of hits thrown away
     */
    long getNumHitsDiscarded();

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
     * Get number of null hits received.
     *
     * @return number of null hits received
     */
    long getNumNullHits();

    /**
     * Get number of readouts which could not be created.
     *
     * @return number of null readouts
     */
    long getNumNullReadouts();

    /**
     * Number of readout requests dropped while stopping.
     *
     * @return number of readout requests dropped
     */
    long getNumReadoutRequestsDropped();

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
     * Get number of readouts which could not be sent.
     *
     * @return number of failed readouts
     */
    long getNumReadoutsFailed();

    /**
     * Get number of empty readouts which were ignored.
     *
     * @return number of ignored readouts
     */
    long getNumReadoutsIgnored();

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
     * Get number of hits not used for a readout.
     *
     * @return number of unused hits
     */
    long getNumUnusedHits();

    /**
     * Get current rate of readouts per second.
     *
     * @return readouts/second
     */
    double getReadoutsPerSecond();

    /**
     * Get current rate of readout requests per second.
     *
     * @return readout requests/second
     */
    double getReadoutRequestsPerSecond();

    /**
     * Get total number of hits which could not be loaded since last reset.
     *
     * @return total number of bad hits since last reset
     */
    long getTotalBadHits();

    /**
     * Total number of stop messages received from the splicer.
     *
     * @return total number of received stop messages
     */
    long getTotalDataStopsReceived();

    /**
     * Total number of hits thrown away since last reset.
     *
     * @return total number of hits thrown away since last reset
     */
    long getTotalHitsDiscarded();

    /**
     * Total number of hits received since last reset.
     *
     * @return total number of hits received since last reset
     */
    long getTotalHitsReceived();

    /**
     * Total number of readout requests received since the last reset.
     *
     * @return total number of readout requests received since the last reset
     */
    long getTotalReadoutRequestsReceived();

    /**
     * Total number of readouts since last reset which could not be sent.
     *
     * @return total number of failed readouts
     */
    long getTotalReadoutsFailed();

    /**
     * Total number of empty readouts which were ignored since the last reset.
     *
     * @return total number of ignored readouts since last reset
     */
    long getTotalReadoutsIgnored();

    /**
     * Total number of readouts sent since last reset.
     *
     * @return total number of readouts sent since last reset.
     */
    long getTotalReadoutsSent();

    /**
     * Total number of stop messages received from the event builder.
     *
     * @return total number of received stop messages
     */
    long getTotalRequestStopsReceived();

    /**
     * Total number of stop messages sent to the string processors
     *
     * @return total number of sent stop messages
     */
    long getTotalStopsSent();
}
