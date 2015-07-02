package icecube.daq.time.monitoring;

import java.util.HashMap;
import java.util.Map;

/**
 * Management bean for clock monitor
 */
public interface ClockMonitorMBean
{

    /**
     * Get the minimum, maximum and current offset between the System Clock
     * and the NTP clock.
     *
     * @return A Double[] containing the minimum, maximum and current
     *         System Clock offset in milliseconds.
     */
    public Double[] getSystemClockOffsets();

    /**
     * Get the minimum, maximum and current offset between the
     * Master Clock and the NTP clock.
     *
     * @return A Double[] containing the minimum, maximum and current
     *         Master Clock offset in milliseconds.
     */
    public Double[] getMasterClockOffsets();

    /**
     * Get the minimum, maximum and current offset between the
     * Master Clock and the NTP clock indexed by DOR card.
     *
     * @return A map of Double[] indexed by DOR card
     *         number with entries containing the min, max and current
     *         Master Clock offset for the card.
     */
    public Map<String, Double[]> getMasterClockCardOffsets();

    /**
     * Get the number of NTP readings rejected due to filtering.

     * @return The number of rejected readings.
     */
    public long getRejectedNTPCount();

    /**
     * Get the number of TCAL readings rejected due to filtering.
     *
     * @return The number of rejected readings.
     */
    public long getRejectedTCalCount();


}
