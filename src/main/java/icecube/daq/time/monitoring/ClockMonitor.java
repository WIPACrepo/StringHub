package icecube.daq.time.monitoring;

import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Monitors the local and master clock readings with respect to
 * readings from an NTP source.
 *
 * Design Notes:
 *
 * Clock readings may arrive in any order and at arbitrary
 * frequency:
 *
 *    The Master Clock offset is calculated per-card on receipt
 *    of a GPS snapshot.
 *
 *    The local clock offset is calculated on
 *    receipt of a NTP measurement.
 *
 * A single threaded client is assumed. Not safe for multi-threaded use.
 */
class ClockMonitor implements ClockProcessor, ClockMonitorMBean
{

    /** Logger. */
    private final Logger logger = Logger.getLogger(ClockMonitor.class);

    /**
     * Configuration values, see package documentation.
     */
    private final long maxTCALSampleDurationNanos;
    private final long maxNTPSampleDurationNanos;

    private final long systemClockAlertThresholdMillis;
    private final long masterClockAlertThresholdMillis;

    private final long systemClockSampleWindow;
    private final long masterClockSampleWindow;

    private final long maxConsecutiveNTPRejects;


    /**
     * over-threshold sample counts
     */
    private int systemClockOverThresholdCount;
    private int masterClockOverThresholdCount;
    private int poorQualityCount;

    /**
     * Rejected sample counts
     */
    private long rejectedNTPCount;
    private long consecutiveRejectedNTPCount;
    private long rejectedTCalCount;

    /**
     * Object that handles alerts.
     */
    private final ClockAlerter alerter;

    /**
     * A reference to the most recent NTP measurement.
     */
    private NTPMeasurement currentNTP;

    /**
     * Holds min, max and current offset values for the system clock.
     */
    private final MinMaxCurrent systemClockOffset;

    /**
     * Holds min, max and current offset values for the master clock offset.
     */
    private final MinMaxCurrent masterClockOffset;

    /**
     * The min, max and current master clock offsets, per card. Synchronized
     * for shared use with the mbean server.
     */
    private final Map<Integer, MinMaxCurrent> perCardMasterClockOffsets =
            Collections.synchronizedMap(new HashMap<Integer, MinMaxCurrent>(8));
    /**
     * The Master Clock monitors, per card.
     */
    private final Map<Integer, MasterClockMonitor> masterClockMonitorSet =
            new HashMap<Integer, MasterClockMonitor>(8);



    /**
     * The highest GPS quality indicator, as coded by GPSInfo.
     */
    private final static int GPS_QUALITY_VERY_GOOD = 32;


    /**
     * Construct a clock monitor with the specified configuration.
     *
     * @param alerter Handles generating alert messages.
     * @param maxTCALSampleDurationMillis Tcals with an execution time greater
     *                                    than this value will be rejected.
     * @param maxNTPSampleDurationMillis NTP queries with an execution time
     *                                   greater than this value will be
     *                                   rejected.
     * @param systemClockAlertThresholdMillis The number of milliseconds that
     *                                        the system clock is permitted to
     *                                        vary offset from the NTP source.
     * @param masterClockAlertThresholdMillis The number of milliseconds that
     *                                        the master clock is permitted to
     *                                        vary offset from the NTP source.
     * @param systemClockSampleWindow The number of consecutive over-threshold
     *                                system clock readings to permit before
     *                                issuing an alert.
     * @param masterClockSampleWindow The number of consecutive over-threshold
     *                                master clock readings to permit before
     *                                issuing an alert.
     * @param maxConsecutiveNTPRejects The number of consecutive rejected
     *                                 NTP readings to permit before
     *                                issuing an alert.
     */
    ClockMonitor( final ClockAlerter alerter,
                  final long maxTCALSampleDurationMillis,
                  final long maxNTPSampleDurationMillis,
                  final long systemClockAlertThresholdMillis,
                  final long masterClockAlertThresholdMillis,
                  final long systemClockSampleWindow,
                  final long masterClockSampleWindow,
                  final long maxConsecutiveNTPRejects)
    {
        this.alerter = alerter;

        this.maxTCALSampleDurationNanos = maxTCALSampleDurationMillis * 1000000;
        this.maxNTPSampleDurationNanos = maxNTPSampleDurationMillis * 1000000;
        this.systemClockAlertThresholdMillis = systemClockAlertThresholdMillis;
        this.masterClockAlertThresholdMillis = masterClockAlertThresholdMillis;
        this.systemClockSampleWindow = systemClockSampleWindow;
        this.masterClockSampleWindow = masterClockSampleWindow;
        this.maxConsecutiveNTPRejects = maxConsecutiveNTPRejects;

        systemClockOffset = new MinMaxCurrent();
        masterClockOffset = new MinMaxCurrent();
    }

    @Override
    public Double[] getSystemClockOffsets()
    {
        return systemClockOffset.toArray();
    }

    @Override
    public Double[] getMasterClockOffsets()
    {
        return masterClockOffset.toArray();
    }

    @Override
    public Map<Integer, Double[]> getMasterClockCardOffsets()
    {
        HashMap<Integer, Double[]> map = new HashMap<Integer, Double[]>(8);
        for(Map.Entry<Integer,MinMaxCurrent> entry :
                perCardMasterClockOffsets.entrySet())
        {
            map.put(entry.getKey(), entry.getValue().toArray());
        }
        return map;
    }

    @Override
    public long getRejectedNTPCount()
    {
        return rejectedNTPCount;
    }

    @Override
    public long getRejectedTCalCount()
    {
        return rejectedTCalCount;
    }

    @Override
    public void process(final TCALMeasurement tcal)
    {
        if(tcal.tcal_exec_nano > maxTCALSampleDurationNanos)
        {
            rejectedTCalCount++;
            return;
        }

        //forward to card monitor
        MasterClockMonitor monitor = lookupCardMonitor(tcal.card);
        monitor.process(tcal);
    }

    /**
     * Process a GPS snapshot.
     *
     * 1. Detect and monitor quality
     * 2. Pair with most recent NTP reading and forward to a dor card-specific
     *    monitor.
     * 3. Track offset and issue alerts following configuration policy.
     */
    @Override
    public void process(final GPSSnapshot gpssnap)
    {
        // monitor GPS quality
        if (gpssnap.gps.getQuality() != GPS_QUALITY_VERY_GOOD)
        {
            poorQualityCount++;
        }
        else
        {
            poorQualityCount = 0;
        }

        // raise a quality alert
        if(poorQualityCount >= masterClockSampleWindow)
        {
            issueMasterClockQualityAlert(gpssnap.gps.getQuality());
        }


        if(currentNTP != null)
        {
            // forward to the per-card monitor
            final MasterClockMonitor monitor = lookupCardMonitor(gpssnap.card);
            double offset = monitor.process(currentNTP, gpssnap);

            // save min/max/current readings
            masterClockOffset.add(offset);
            final MinMaxCurrent minMaxCurrent = lookupCardMinMax(gpssnap.card);
            minMaxCurrent.add(offset);

            // track high/low samples in window
            if(Math.abs(offset) > masterClockAlertThresholdMillis)
            {
                masterClockOverThresholdCount++;
            }
            else
            {
                masterClockOverThresholdCount = 0;
            }

            // raise an offset alert
            if(masterClockOverThresholdCount >= masterClockSampleWindow)
            {
                issueMasterClockOffsetAlert(offset, currentNTP);
            }
        }
        else
        {
            //can't estimate offset without an NTP reading
        }
    }

    @Override
    /**
     * Process an NTP reading.
     *
     * 1. Reject long running samples.
     * 2. Track system clock offset and issue alerts following
     *    configuration policy.
     */
    public void process(final NTPMeasurement ntp)
    {
        // filter outliers
        if(ntp.ntp_exec_nano > maxNTPSampleDurationNanos)
        {
            rejectedNTPCount++;
            consecutiveRejectedNTPCount++;

            // Note: detect the condition where the ntp server query time
            //       is consistently above the cutoff.
            if(consecutiveRejectedNTPCount > maxConsecutiveNTPRejects)
            {
                final String reason = String.format("NTP server responses" +
                        " are too slow: [%d ms]", ntp.ntp_exec_nano);
                issueNTPFilterAlert(reason, ntp);
            }

            return;
        }
        else
        {
            consecutiveRejectedNTPCount = 0;
        }

        // save
        currentNTP = ntp;
        systemClockOffset.add(ntp.local_clock_offset);


        // track high samples in window
        if(Math.abs(ntp.local_clock_offset) > systemClockAlertThresholdMillis)
        {
            systemClockOverThresholdCount++;
        }
        else
        {
            systemClockOverThresholdCount = 0;
        }

        // raise an alert
        if(systemClockOverThresholdCount >= systemClockSampleWindow)
        {
            issueSystemClockOffsetAlert(ntp.local_clock_offset, ntp);
        }
    }

    /**
     * Access card-specific monitor, instantiating if necessary.
     */
    private MasterClockMonitor lookupCardMonitor(final int card)
    {
        if(!masterClockMonitorSet.containsKey(card))
        {
            masterClockMonitorSet.put(card,
                    new MasterClockMonitor(card));
        }

        return masterClockMonitorSet.get(card);
    }

    /**
     * Access card-specific readings, instantiating if necessary.
     */
    private MinMaxCurrent lookupCardMinMax(final int card)
    {
        if(!perCardMasterClockOffsets.containsKey(card))
        {
            perCardMasterClockOffsets.put(card,
                    new MinMaxCurrent());
        }

        return perCardMasterClockOffsets.get(card);
    }


    /**
     * Issue an alert request.
     *
     * @param offset The system clock offset.
     * @param ntp The reference NTP server.
     */
    private void issueSystemClockOffsetAlert(final double offset,
                                             final NTPMeasurement ntp)
    {
        alerter.alertSystemClockOffset(offset, ntp.serverID);
    }

    /**
     * Issue an alert request.
     *
     * @param offset The system clock offset.
     * @param ntp The reference NTP server.
     */
    private void issueMasterClockOffsetAlert(final double offset,
                                             final NTPMeasurement ntp)
    {
        alerter.alertMasterClockOffset(offset, ntp.serverID);
    }

    /**
     * Issue an alert request.
     *
     * @param quality The master clock quality.
     */
    private void issueMasterClockQualityAlert(int quality)
    {
        alerter.alertMasterClockQuality(quality);
    }

    /**
     * Issue an alert request.
     *
     * @param reason A description of the alert cause.
     * @param ntp The reference NTP server.
     */
    private void issueNTPFilterAlert(final String reason,
                                     final NTPMeasurement ntp)
    {
        alerter.alertNTPServer(reason, ntp.serverID);
    }


    /**
     * Holds a tuple of doubles representing a min, max
     * and current value.
     */
    private static class MinMaxCurrent
    {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double current;

        void add(final double current)
        {
            this.current = current;
            min = Math.min(current, min);
            max = Math.max(current, max);
        }

        Double[] toArray()
        {
            return new Double[]{min, max, current};
        }
    }


}
