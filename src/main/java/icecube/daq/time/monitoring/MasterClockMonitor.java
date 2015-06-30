package icecube.daq.time.monitoring;

import icecube.daq.dor.GPSInfo;
import org.apache.log4j.Logger;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Estimates the Master Clock offset on a specific DOR card with
 * respect to an NTP source.
 *
 * Master Clock readings are provided on a per-card basis
 * and the offset for a card is calculated using tcals which
 * may be provided on a per-dom basis.
 *
 * Clock readings may arrive in any order and at arbitrary
 * frequency.  The Master Clock offset is calculated on receipt
 * of a GPS snapshot.
 *
 * The master clock offset for a card is maintained as an average
 * of offsets calculated by the most recent GPSSnap and the most recent
 * tcals from each active DOMS on the card.
 *
 *
 * NOTE: Dropped DOMS. I don't anticipate a problem if a stale tcal
 *       from a long-dropped DOM is resident here, although it is
 *       un-tidy. The estimate only uses DOR clock, so even a poor
 *       tcal can produce a reasonable offset.
 */
class MasterClockMonitor
{

    private final Logger logger = Logger.getLogger(MasterClockMonitor.class);

    /** The DOR card that is being monitored. */
    private final int card;

    /**
     * A reference to the most recent TCALs, per DOM.
     */
    private final Map<String, ClockProcessor.TCALMeasurement> currentTCALs =
            new HashMap<String, ClockProcessor.TCALMeasurement>(64);

    /**
     * The offset between the master clock and the NTP clock derived
     * from a single NTP query.
     */
    private double masterClockOffsetMillis = 0;

    /** The current year. Note that this breaks at year-end. */
    private final int currentYear;

    /** Number of nanoseconds in a dor tick. */
    private static final int NANOS_PER_DOR_TICK = 50;

    /** Number of nanos in a millisecond, as a double. */
    private static final double NANOS_PER_MILLI_DOUBLE = 1000000D;

    /** Debugging aid. */
    private boolean VERBOSE_LOGGING =
            Boolean.getBoolean("icecube.daq.time.monitoring.verbose-logging");


    MasterClockMonitor(final int card)
    {
        this.card = card;

        //NOTE: This bounds the monitor to the current
        //      year. Acceptable at the inception.
        TimeZone utc_zone = TimeZone.getTimeZone("GMT");
        Calendar now = Calendar.getInstance(utc_zone);
        this.currentYear = now.get(Calendar.YEAR);
    }

    double getMasterClockOffsetMillis()
    {
        return masterClockOffsetMillis;
    }

    double process(final ClockProcessor.NTPMeasurement ntp,
                   final ClockProcessor.GPSSnapshot gpssnap)
    {

        // factor out the GPS string-to-millis calculation
        // from the loop.
        final long gpsMillis = GPSToMillis(gpssnap.gps);
        final long gpsDor = gpssnap.gps.getDorclk();

        // calculate an offset for each tcal source.
        int numSamples = 0;
        double sum = 0;
        for(ClockProcessor.TCALMeasurement tcal : currentTCALs.values())
        {
            double offset = estimateOffset(ntp, gpsMillis, gpsDor, tcal);

            if(VERBOSE_LOGGING)
            {
                logVerbose(ntp, gpssnap.gps, tcal, offset);
            }

            sum += offset;
            numSamples++;
        }

        if(numSamples > 0)
        {
            // Average the readings to form a single offset
            // for the card.
            masterClockOffsetMillis = sum/numSamples;
        }
        else
        {
            // No offset calculation without a tcal reading.
            // This condition exists at startup until a tcal
            // is established.
        }

        return masterClockOffsetMillis;
    }

    void process(final ClockProcessor.TCALMeasurement tcal)
    {

        // dynamically populate the map of tcals
        // indexed by cwd. A TCAL reading does
        // not trigger an offset calculation. The
        // tcal reading will be used at the next
        // GPS snapshot.
        currentTCALs.put(tcal.cwd, tcal);
    }

    /**
     * Estimate the difference between the NTP source and the Master Clock.
     *
     * @param ntp An NTP reading.
     * @param gps_point_millis The GPS PPS timestamp, converted to the system
     *                         timescale of milliseconds since the unix epoch.
     * @param gps_point_dor The DOR clock timestamp from the GPS snapshot.
     *
     * @return The number of milliseconds that the GPS clock is offset from the
     *         NTP source.
     */
    private double estimateOffset(final ClockProcessor.NTPMeasurement ntp,
                                  final long gps_point_millis,
                                  final long gps_point_dor,
                                  final ClockProcessor.TCALMeasurement tcal)
    {
        // NOTE: Mildly convoluted.
        //
        //       It is expensive to synchronize a GPS reading against
        //       the system clock.  The GPS snaps are buffered and you
        //       have to spin read to get a reading at a known point-
        //       in-time on the local clock.  It is easier to make this
        //       association with a TCAL reading and then use the DOR
        //       clock timestamps to reckon from the TCAL point-in-time
        //       to the GPS snap point-in-time.
        //
        //       So...
        //
        // Calculate the gps snap monotonic point-in-time by way of the tcal
        // monotonic point-in-time adjusted by the dor clock difference.
        long gps_point_nano =
        tcal.tcal_point_nano +
                ((gps_point_dor - tcal.tcal_point_dor) * NANOS_PER_DOR_TICK);

        // calculate where the GPS snap occurred with respect to the NTP
        // reading by adding the elapsed system time to the ntp time.
        double estimatedMasterClockMillisAtSnap = (ntp.ntp_system_time) +
                (gps_point_nano - ntp.ntp_point_nano)/ NANOS_PER_MILLI_DOUBLE;

        // calculate the offset from the Master Clock to the NTP clock
        return estimatedMasterClockMillisAtSnap - gps_point_millis;
    }

    private long GPSToMillis(GPSInfo gps)
    {
        GregorianCalendar calendar =
                new GregorianCalendar(currentYear,0,1,0,0,0);
        calendar.setTimeZone(TimeZone.getTimeZone("GMT"));
        calendar.add(Calendar.DAY_OF_MONTH, gps.getDay()-1);
        calendar.add(Calendar.HOUR_OF_DAY, gps.getHour());
        calendar.add(Calendar.MINUTE, gps.getMin());
        calendar.add(Calendar.SECOND, gps.getSecond());

        return calendar.getTime().getTime();
    }

    // support verbose data logging for debugging
    private static final String verboseFormat = "card [%d] mc-offset [%f ms]" +
            " gps-timestring [%s] gps-millis [%d] gps-dor [%d]" +
            " tcal-dom [%s] tcal-pit-nano [%d]" +
            " tcal-dor[%d] tcal-duration [%d ms]" +
            " ntp-millis [%d] ntp-pit-nano [%d] ntp-duration [%d ms] ";
    private void logVerbose(ClockProcessor.NTPMeasurement ntp,
                            GPSInfo gps,
                            ClockProcessor.TCALMeasurement tcal,
                            double offset)
    {
        String msg = String.format(verboseFormat, card, offset,
                gps.getTimestring(), GPSToMillis(gps), gps.getDorclk(),
                tcal.cwd, tcal.tcal_point_nano,
                tcal.tcal_point_dor, tcal.tcal_exec_nano/1000000,
                ntp.ntp_system_time, ntp.ntp_point_nano,
                ntp.ntp_exec_nano/1000000);
        logger.info(msg);
    }


}
