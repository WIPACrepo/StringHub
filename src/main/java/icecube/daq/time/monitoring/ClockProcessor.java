package icecube.daq.time.monitoring;

import icecube.daq.dor.GPSInfo;

/**
 * Defines the public interface and data objects for the subsystem.
 */
public interface ClockProcessor
{

    /**
     * A GPS snapshot reading from the gpssync file.
     */
    public static class GPSSnapshot
    {
        final GPSInfo gps;
        final int card;
        public GPSSnapshot(final GPSInfo gps, final int card)
        {
            this.gps = gps;
            this.card = card;
        }
    }

    /**
     * A TCAL reading
     */
    public static class TCALMeasurement
    {
        final long tcal_point_dor;
        final long tcal_point_nano;
        final long tcal_exec_nano;
        final int card;
        final String cwd;

        public TCALMeasurement(final long tcal_point_dor,
                               final long tcal_point_nano,
                               final long tcal_exec_nano,
                               final int card,
                               final String cwd)
        {
            this.tcal_point_dor = tcal_point_dor;
            this.tcal_point_nano = tcal_point_nano;
            this.tcal_exec_nano = tcal_exec_nano;
            this.card = card;
            this.cwd = cwd;
        }
    }

    /**
     * An NTP reading.
     */
    public static class  NTPMeasurement
    {
        final String serverID;
        final long ntp_system_time;
        final double local_clock_offset;
        final long ntp_point_nano;
        final long ntp_exec_nano;

        public NTPMeasurement(final String serverID,
                              final long ntp_system_time,
                              final double local_clock_offset,
                              final long ntp_point_nano,
                              final long ntp_exec_nano)
        {
            this.serverID = serverID;
            this.ntp_system_time = ntp_system_time;
            this.local_clock_offset = local_clock_offset;
            this.ntp_point_nano = ntp_point_nano;
            this.ntp_exec_nano = ntp_exec_nano;
        }
    }

    /**
     * Accepts a GPS snapshot reading.
     * @param gpssnap
     */
    public void process(GPSSnapshot gpssnap);

    /**
     * Accepts a TCAL reading.
     * @param tcal
     */
    public void process(TCALMeasurement tcal);

    /**
     * Accepts an NTP reading.
     * @param ntp
     */
    public void process(NTPMeasurement ntp);

}
