package icecube.daq.stringhub;

import icecube.daq.domapp.AbstractDataCollector;

import java.util.Arrays;

/**
 * Calculate first/last good time, weeding out bogus times too far in the
 * past or future.
 */
public class GoodTimeCalculator
{
    /** One minute of DAQ ticks (10ths of nanoseconds) */
    private static final long ONE_MINUTE = 600000000000L;

    /**
     * <tt>true</tt> if looking for the first time,
     * <tt>false</tt> if looking for the last time
     */
    private boolean isFirstTime;

    /** List of times gathered from all DOMs */
    private long[] times;
    /** Number of DOMs which supplied valid(ish) times */
    private int numTimes;

    /** Cached earliest time */
    private long earliest;
    /** Cached latest time */
    private long latest;

    /** Set <tt>true</tt> if any DOMs were not ready to supply a time */
    private boolean notReady;

    /**
     * Gather DOM times to be used for computing first/last good time
     * for this hub
     *
     * @param conn DOMConnector
     * @param getFirstTime <tt>true</tt> if data collectors should
     *        return their first hit time; otherwise the last hit time
     *        will be gathered
     */
    GoodTimeCalculator(DOMConnector conn, boolean getFirstTime)
    {
        this.isFirstTime = getFirstTime;

        // initialize everything
        times = new long[conn.getNumberOfChannels()];
        numTimes = 0;
        earliest = Long.MAX_VALUE;
        latest = Long.MIN_VALUE;
        notReady = false;

        // gather times from all DOMs
        for (AbstractDataCollector adc : conn.getCollectors()) {
            long val = (getFirstTime ? adc.getFirstHitTime() :
                        adc.getLastHitTime());
            if (val < 0L) {
                // if a DOM's last time hasn't been set yet, give up
                notReady = true;
                break;
            }

            // add this time to the array
            times[numTimes++] = val;

            // cache earliest and latest times
            if (val < earliest) {
                earliest = val;
            }
            if (val > latest) {
                latest = val;
            }
        }
    }

    /**
     * Find the largest gap between values in the sorted list of DOM times
     *
     * @return index of end of largest gap
     */
    private int findLargestGap(long[] sorted)
    {
        long gap = Long.MIN_VALUE;
        int index = 0;
        for (int i = 1; i < sorted.length; i++) {
            long tmpGap = sorted[i] - sorted[i - 1];
            if (tmpGap > gap) {
                gap = tmpGap;
                index = i;
            }
        }
        return index;
    }

    /**
     * Get the earliest DOM time
     *
     * @return earliest time
     */
    private long getEarliestTime()
    {
        if (notReady) {
            // not all DOMs are ready to report a value!
            return 0;
        } else if (numTimes == 0) {
            // no DOMs reported a valid time
            return Long.MAX_VALUE;
        } else if (isSane()) {
            // the earliest & latest times were reasonably close to each other
            return earliest;
        }

        long[] sorted = truncateAndSort();
        int index = findLargestGap(sorted);

        // if the gap happens less than halfway through the list,
        //  return the time found at the end of the gap
        //  (this time will be the start of the largest hunk of valid times)
        if (sorted.length / 2 > index) {
            return sorted[index];
        }

        // return the earliest sane time
        return sorted[0];
    }

    /**
     * Get the latest DOM time
     *
     * @return latest time
     */
    private long getLatestTime()
    {
        if (notReady) {
            // not all DOMs are ready to report a value!
            return 0;
        } else if (numTimes == 0) {
            // no DOMs reported a valid time
            return 0L;
        } else if (isSane()) {
            // the earliest & latest times were reasonably close to each other
            return latest;
        }

        long[] sorted = truncateAndSort();
        int index = findLargestGap(sorted);

        // if the gap happens more than halfway through the list,
        //  return the time found at the start of the gap
        //  (this time will be the end of the largest hunk of valid times)
        if (sorted.length / 2 < index) {
            return sorted[index-1];
        }

        // return the latest sane time
        return sorted[sorted.length - 1];
    }

    /**
     * Get the good time
     *
     * @return good first/last time for this hub
     */
    public long getTime()
    {
        if (isFirstTime) {
            return getLatestTime();
        }

        return getEarliestTime();
    }

    /**
     * Is there a reasonable gap between the first and last DOM times?
     *
     * @return <tt>false</tt> if something's gone wrong
     */
    private boolean isSane()
    {
        return latest - earliest < ONE_MINUTE;
    }

    /**
     * Return a sorted array containing the sorted list of DOM times.
     *
     * @return sorted list of DOM times
     */
    private long[] truncateAndSort()
    {
        long[] workspace;
        if (numTimes == times.length) {
            // original array is full
            workspace = times;
        } else {
            // need a shorter array
            workspace = new long[numTimes];
            System.arraycopy(times, 0, workspace, 0, numTimes);
        }

        Arrays.sort(workspace);

        return workspace;
    }

    /**
     * Return debugging string
     *
     * @return string with all internal details useful for debugging
     */
    @Override
    public String toString()
    {
        StringBuilder buf = new StringBuilder("GoodTimeCalculator[");
        buf.append(isFirstTime ? "first" : "last");
        buf.append('#').append(numTimes);
        if (notReady) {
            buf.append(",!ready");
        }
        buf.append(",earliest=").append(earliest);
        buf.append(",latest=").append(latest);
        return buf.append(']').toString();
    }
}
