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
     * Get number of hits cached for readout being built
     *
     * @return num hits cached
     */
    @Override
    public int getNumHitsCached()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumHitsCached();
    }

    /**
     * Get number of hits queued for processing.
     *
     * @return num hits queued
     */
    @Override
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
    @Override
    public long getNumHitsReceived()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumHitsReceived();
    }

    /**
     * Number of readout requests currectly queued for processing.
     *
     * @return num readout requests queued
     */
    @Override
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
    @Override
    public long getNumReadoutRequestsReceived()
    {
        if (sender == null) {
            return 0;
        }

        return sender.getNumReadoutRequestsReceived();
    }

    /**
     * Get number of readouts sent.
     *
     * @return num readouts sent
     */
    @Override
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
        StringBuilder buf = new StringBuilder("MonitoringData:");

        if (sender == null) {
            buf.append("\n  No sender monitoring data available");
        } else {
            buf.append("\n  numHitsCached ").append(getNumHitsCached());
            buf.append("\n  numHitsQueued ").append(getNumHitsQueued());
            buf.append("\n  numHitsRcvd ").append(getNumHitsReceived());
            buf.append("\n  numReadoutRequestsQueued ").
                append(getNumReadoutRequestsQueued());
            buf.append("\n  numReadoutRequestsRcvd ").
                append(getNumReadoutRequestsReceived());
            buf.append("\n  numReadoutsSent ").append(getNumReadoutsSent());
            buf.append("\n  numRecycled ").append(getNumRecycled());
            buf.append("\n  totHitsRcvd ").append(getTotalHitsReceived());
            buf.append("\n  totStopsSent ").append(getTotalStopsSent());
        }

        return buf.toString();
    }
}
