package icecube.daq.monitoring;

/**
 * MBean wrapper for monitored data objects.
 */
public class MonitoringMBean
{
    /** Monitoring data. */
    private MonitoringData data;

    /**
     * MBean wrapper for monitored data objects.
     *
     * @param data monitored data object
     */
    public MonitoringMBean(MonitoringData data)
    {
        this.data = data;
    }

    /**
     * Get average number of hits per readout.
     *
     * @return average hits per readout
     */
    public long getAverageHitsPerReadout()
    {
        if (data == null) {
            return 0;
        }

        return data.getAverageHitsPerReadout();
    }

    /**
     * Get back-end timing profile.
     *
     * @return back end timing
     */
    public String getBackEndTiming()
    {
        if (data == null) {
            return "<NO DATA>";
        }

        return data.getBackEndTiming();
    }

    /**
     * Get current rate of hits per second.
     *
     * @return hits per second
     */
    public double getHitsPerSecond()
    {
        if (data == null) {
            return 0.0;
        }

        return data.getHitsPerSecond();
    }

    /**
     * Get number of hits which could not be loaded.
     *
     * @return num bad hits
     */
    public long getNumBadHits()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumBadHits();
    }

    /**
     * Number of readout requests which could not be loaded.
     *
     * @return num bad readout requests
     */
    public long getNumBadReadoutRequests()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumBadReadoutRequests();
    }

    /**
     * Get number of passes through the main loop without a request.
     *
     * @return num empty loops
     */
    public long getNumEmptyLoops()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumEmptyLoops();
    }

    /**
     * Get number of hits cached for readout being built
     *
     * @return num hits cached
     */
    public int getNumHitsCached()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumHitsCached();
    }

    /**
     * Get number of hits thrown away.
     *
     * @return num hits discarded
     */
    public long getNumHitsDiscarded()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumHitsDiscarded();
    }

    /**
     * Get number of hits queued for processing.
     *
     * @return num hits queued
     */
    public int getNumHitsQueued()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumHitsQueued();
    }

    /**
     * Get number of hits received.
     *
     * @return num hits received
     */
    public long getNumHitsReceived()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumHitsReceived();
    }

    /**
     * Get number of null hits received.
     *
     * @return num null hits
     */
    public long getNumNullHits()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumNullHits();
    }

    /**
     * Get number of readouts which could not be created.
     *
     * @return num null readouts
     */
    public long getNumNullReadouts()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumNullReadouts();
    }

    /**
     * Number of readout requests dropped while stopping.
     *
     * @return num readout requests dropped
     */
    public long getNumReadoutRequestsDropped()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumReadoutRequestsDropped();
    }

    /**
     * Number of readout requests currectly queued for processing.
     *
     * @return num readout requests queued
     */
    public long getNumReadoutRequestsQueued()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumReadoutRequestsQueued();
    }

    /**
     * Number of readout requests received for this run.
     *
     * @return num readout requests received
     */
    public long getNumReadoutRequestsReceived()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumReadoutRequestsReceived();
    }

    /**
     * Get number of readouts which could not be sent.
     *
     * @return num readouts failed
     */
    public long getNumReadoutsFailed()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumReadoutsFailed();
    }

    /**
     * Get number of empty readouts which were ignored.
     *
     * @return num readouts ignored
     */
    public long getNumReadoutsIgnored()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumReadoutsIgnored();
    }

    /**
     * Get number of readouts sent.
     *
     * @return num readouts sent
     */
    public long getNumReadoutsSent()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumReadoutsSent();
    }

    /**
     * Get number of recycled payloads.
     *
     * @return num recycled
     */
    public long getNumRecycled()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumRecycled();
    }

    /**
     * Get number of hits not used for a readout.
     *
     * @return num unused hits
     */
    public long getNumUnusedHits()
    {
        if (data == null) {
            return 0;
        }

        return data.getNumUnusedHits();
    }

    /**
     * Get current rate of readout requests per second.
     *
     * @return readout requests per second
     */
    public double getReadoutRequestsPerSecond()
    {
        if (data == null) {
            return 0.0;
        }

        return data.getReadoutRequestsPerSecond();
    }

    /**
     * Get current rate of readouts per second.
     *
     * @return readouts per second
     */
    public double getReadoutsPerSecond()
    {
        if (data == null) {
            return 0.0;
        }

        return data.getReadoutsPerSecond();
    }

    /**
     * Get total number of hits which could not be loaded since last reset.
     *
     * @return total bad hits
     */
    public long getTotalBadHits()
    {
        if (data == null) {
            return 0;
        }

        return data.getTotalBadHits();
    }

    /**
     * Total number of stop messages received from the splicer.
     *
     * @return total data stops received
     */
    public long getTotalDataStopsReceived()
    {
        if (data == null) {
            return 0;
        }

        return data.getTotalDataStopsReceived();
    }

    /**
     * Total number of hits thrown away since last reset.
     *
     * @return total hits discarded
     */
    public long getTotalHitsDiscarded()
    {
        if (data == null) {
            return 0;
        }

        return data.getTotalHitsDiscarded();
    }

    /**
     * Total number of hits received since last reset.
     *
     * @return total hits received
     */
    public long getTotalHitsReceived()
    {
        if (data == null) {
            return 0;
        }

        return data.getTotalHitsReceived();
    }

    /**
     * Total number of readout requests received since the last reset.
     *
     * @return total readout requests received
     */
    public long getTotalReadoutRequestsReceived()
    {
        if (data == null) {
            return 0;
        }

        return data.getTotalReadoutRequestsReceived();
    }

    /**
     * Total number of readouts since last reset which could not be sent.
     *
     * @return total readouts failed
     */
    public long getTotalReadoutsFailed()
    {
        if (data == null) {
            return 0;
        }

        return data.getTotalReadoutsFailed();
    }

    /**
     * Total number of empty readouts which were ignored since the last reset.
     *
     * @return total readouts ignored
     */
    public long getTotalReadoutsIgnored()
    {
        if (data == null) {
            return 0;
        }

        return data.getTotalReadoutsIgnored();
    }

    /**
     * Total number of readouts sent since last reset.
     *
     * @return total readouts sent
     */
    public long getTotalReadoutsSent()
    {
        if (data == null) {
            return 0;
        }

        return data.getTotalReadoutsSent();
    }

    /**
     * Total number of stop messages received from the event builder.
     *
     * @return total request stops received
     */
    public long getTotalRequestStopsReceived()
    {
        if (data == null) {
            return 0;
        }

        return data.getTotalRequestStopsReceived();
    }

    /**
     * Total number of stop messages sent to the string processors
     *
     * @return total stops sent
     */
    public long getTotalStopsSent()
    {
        if (data == null) {
            return 0;
        }

        return data.getTotalStopsSent();
    }

    /**
     * Set Set monitoring data object. data object.
     *
     * @param mon monitoring data object
     */
    public void setData(MonitoringData mon)
    {
        data = mon;
    }
}
