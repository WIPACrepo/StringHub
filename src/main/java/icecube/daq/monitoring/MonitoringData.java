package icecube.daq.monitoring;

/**
 * Wrapper for all monitored data objects.
 */
public class MonitoringData
    implements MonitoringDataMBean
{
    /** Sender monitoring. */
    private SenderMonitor sender;

    /**
     * Wrapper for all monitored data objects.
     */
    public MonitoringData()
    {
    }

    /**
     * Get internal timing profile.
     *
     * @return internal timing
     */
    public String getInternalTiming()
    {
        if (sender == null) {
            return "<NO SENDER>";
        }

        return sender.getInternalTiming();
    }

    /**
     * Get the time of the most recently queued hit.
     *
     * @return latest time
     */
    public long getLatestHitTime()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getLatestHitTime();
    }

    /**
     * Get the end time of the most recent readout data payload.
     *
     * @return latest readout data end time
     */
    public long[] getLatestReadoutTimes()
    {
        if (sender == null) {
            return new long[0];
        }

        return sender.getLatestReadoutTimes();
    }

    /**
     * Get number of hits which could not be loaded.
     *
     * @return num bad hits
     */
    public long getNumBadHits()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumBadHits();
    }

    /**
     * Number of readout requests which could not be loaded.
     *
     * @return num bad readout requests
     */
    public long getNumBadReadoutRequests()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumBadReadoutRequests();
    }

    /**
     * Get number of passes through the main loop without a request.
     *
     * @return num empty loops
     */
    public long getNumEmptyLoops()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumEmptyLoops();
    }

    /**
     * Get number of hits cached for readout being built
     *
     * @return num hits cached
     */
    public int getNumHitsCached()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumHitsCached();
    }

    /**
     * Get number of hits thrown away.
     *
     * @return num hits discarded
     */
    public long getNumHitsDiscarded()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumHitsDiscarded();
    }

    /**
     * Get number of hits dropped while stopping.
     *
     * @return number of hits dropped
     */
    public long getNumHitsDropped()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumHitsDropped();
    }

    /**
     * Get number of hits queued for processing.
     *
     * @return num hits queued
     */
    public int getNumHitsQueued()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumHitsQueued();
    }

    /**
     * Get number of hits received.
     *
     * @return num hits received
     */
    public long getNumHitsReceived()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumHitsReceived();
    }

    /**
     * Get number of null hits received.
     *
     * @return num null hits
     */
    public long getNumNullHits()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumNullHits();
    }

    /**
     * Get number of readouts which could not be created.
     *
     * @return num null readouts
     */
    public long getNumNullReadouts()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumNullReadouts();
    }

    /**
     * Number of readout requests dropped while stopping.
     *
     * @return num readout requests dropped
     */
    public long getNumReadoutRequestsDropped()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumReadoutRequestsDropped();
    }

    /**
     * Number of readout requests currectly queued for processing.
     *
     * @return num readout requests queued
     */
    public long getNumReadoutRequestsQueued()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumReadoutRequestsQueued();
    }

    /**
     * Number of readout requests received for this run.
     *
     * @return num readout requests received
     */
    public long getNumReadoutRequestsReceived()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumReadoutRequestsReceived();
    }

    /**
     * Get number of readouts which could not be sent.
     *
     * @return num readouts failed
     */
    public long getNumReadoutsFailed()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumReadoutsFailed();
    }

    /**
     * Get number of empty readouts which were ignored.
     *
     * @return num readouts ignored
     */
    public long getNumReadoutsIgnored()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumReadoutsIgnored();
    }

    /**
     * Get number of readouts sent.
     *
     * @return num readouts sent
     */
    public long getNumReadoutsSent()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumReadoutsSent();
    }

    /**
     * Get number of recycled payloads.
     *
     * @return num recycled
     */
    public long getNumRecycled()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumRecycled();
    }

    /**
     * Get total number of hits which could not be loaded since last reset.
     *
     * @return total bad hits
     */
    public long getTotalBadHits()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getTotalBadHits();
    }

    /**
     * Total number of stop messages received from the splicer.
     *
     * @return total data stops received
     */
    public long getTotalDataStopsReceived()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getTotalDataStopsReceived();
    }

    /**
     * Total number of hits thrown away since last reset.
     *
     * @return total hits discarded
     */
    public long getTotalHitsDiscarded()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getTotalHitsDiscarded();
    }

    /**
     * Total number of hits received since last reset.
     *
     * @return total hits received
     */
    public long getTotalHitsReceived()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getTotalHitsReceived();
    }

    /**
     * Total number of readout requests received since the last reset.
     *
     * @return total readout requests received
     */
    public long getTotalReadoutRequestsReceived()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getTotalReadoutRequestsReceived();
    }

    /**
     * Total number of readouts since last reset which could not be sent.
     *
     * @return total readouts failed
     */
    public long getTotalReadoutsFailed()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getTotalReadoutsFailed();
    }

    /**
     * Total number of empty readouts which were ignored since the last reset.
     *
     * @return total readouts ignored
     */
    public long getTotalReadoutsIgnored()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getTotalReadoutsIgnored();
    }

    /**
     * Total number of readouts sent since last reset.
     *
     * @return total readouts sent
     */
    public long getTotalReadoutsSent()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getTotalReadoutsSent();
    }

    /**
     * Total number of stop messages received from the event builder.
     *
     * @return total request stops received
     */
    public long getTotalRequestStopsReceived()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getTotalRequestStopsReceived();
    }

    /**
     * Total number of stop messages sent to the string processors
     *
     * @return total stops sent
     */
    public long getTotalStopsSent()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getTotalStopsSent();
    }

    /**
     * Set sender monitoring data object.
     *
     * @param mon monitoring data object
     */
    public void setSenderMonitor(SenderMonitor mon)
    {
        sender = mon;
    }

    /**
     * String representation of monitoring data.
     *
     * @return monitored data
     */
    public String toString()
    {
        StringBuffer buf = new StringBuffer("MonitoringData:");

        if (sender == null) {
            buf.append("\n  No sender monitoring data available");
        } else {
            buf.append("\n  backEndTiming ").append(getInternalTiming());
            buf.append("\n  numBadHits ").append(getNumBadHits());
            buf.append("\n  numBadReadoutRequests ").
                append(getNumBadReadoutRequests());
            buf.append("\n  numEmptyLoops ").append(getNumEmptyLoops());
            buf.append("\n  numHitsCached ").append(getNumHitsCached());
            buf.append("\n  numHitsDiscarded ").append(getNumHitsDiscarded());
            buf.append("\n  numHitsDropped ").append(getNumHitsDropped());
            buf.append("\n  numHitsQueued ").append(getNumHitsQueued());
            buf.append("\n  numHitsRcvd ").append(getNumHitsReceived());
            buf.append("\n  numNullHits ").append(getNumNullHits());
            buf.append("\n  numNullReadouts ").append(getNumNullReadouts());
            buf.append("\n  numReadoutRequestsDropped ").
                append(getNumReadoutRequestsDropped());
            buf.append("\n  numReadoutRequestsQueued ").
                append(getNumReadoutRequestsQueued());
            buf.append("\n  numReadoutRequestsRcvd ").
                append(getNumReadoutRequestsReceived());
            buf.append("\n  numReadoutsFailed ").
                append(getNumReadoutsFailed());
            buf.append("\n  numReadoutsIgnored ").
                append(getNumReadoutsIgnored());
            buf.append("\n  numReadoutsSent ").append(getNumReadoutsSent());
            buf.append("\n  numRecycled ").append(getNumRecycled());
            buf.append("\n  totBadHits ").append(getTotalBadHits());
            buf.append("\n  totDataStopsRcvd ").
                append(getTotalDataStopsReceived());
            buf.append("\n  totHitsDiscarded ").
                append(getTotalHitsDiscarded());
            buf.append("\n  totHitsRcvd ").append(getTotalHitsReceived());
            buf.append("\n  totReadoutRequestsRcvd ").
                append(getTotalReadoutRequestsReceived());
            buf.append("\n  totReadoutsFailed ").
                append(getTotalReadoutsFailed());
            buf.append("\n  totReadoutsIgnored ").
                append(getTotalReadoutsIgnored());
            buf.append("\n  totReadoutsSent ").append(getTotalReadoutsSent());
            buf.append("\n  totRequestStopsRcvd ").
                append(getTotalRequestStopsReceived());
            buf.append("\n  totStopsSent ").append(getTotalStopsSent());
        }

        return buf.toString();
    }
}
