package icecube.daq.rapcal;


import icecube.daq.util.TimeUnits;

/**
 * An Isochron embodies the relationship between the DOM, DOR and GPS
 * clocks for an interval of time defined by two consecutive tcal
 * measurements.
 *
 * The interval that an isochron spans is defined by the DORTX time of the
 * contributing tcals (DORTX_0, DORTX_1]. For DOM times within this interval,
 * the isochron is considered the most accurate source of UTC time
 * reconstruction.  UTC reconstruction of DOM values outside of this interval
 * are performed by extrapolation.
 *
 * Usage Notes:
 *   Isochron is implemented internally in UTC units of 0.1 nanoseconds,
 *   clients must specify a unit to trigger scaling non-utc arguments, e.g.
 *   Units.DOM causes scaling of raw dom clock values by 250.
 *
 */
public class Isochron
{


    /**
     * Timestamp values from two successive tcal measurements, in 0.1
     * nanosecond units.
     */
    private final long[] t0;
    private final long[] t1;

    /**
     * The DOR to UTC offset, in 0.1 nanosecond units.
     */
    private final long gpsOffset;

    /**
     * Calculated isomarks.
     */
    private final long dorMid;
    private final long domMid;
    private final long dordt;
    private final long domdt;

    /**
     * Calculated cable length.
     */
    private final double clen;

    /**
     * Calculated DOM frequency variation.
     */
    private final double epsilon;


    /** Named indexes into the tcal arrays. */
    private final static int DOR_TX = 0;
    private final static int DOM_RX = 1;
    private final static int DOM_TX = 2;
    private final static int DOR_RX = 3;


    /**
     * Construct an Isochron for an interval starting from the previous
     * Isochron to the next time calibration.
     *
     * @param prev The previous Isochron.
     * @param next The next time calibration.
     * @param gpsOffset The offset between the DOR clock and UTC time.
     * @throws RAPCalException A degenerate condition.
     */
    public Isochron(final Isochron prev, final long[] next, final long gpsOffset)
            throws RAPCalException
    {
        this(prev.t1, next, gpsOffset);
    }

    /**
     * Construct an Isochron for an interval defined by sequential time
     * calibrations.
     *
     * @param t0 The first time calibration.
     * @param t1 The second time calibration.
     * @param gpsOffset  The offset between the DOR clock and UTC time.
     * @throws RAPCalException A degenerate condition was detected in the data.
     */
    public Isochron(final long[] t0, final long[] t1, final long gpsOffset)
            throws RAPCalException
    {
        // enforce monotonic time progression invariant
        ensureMonotonic(t0, t1);

        this.t0 = t0;
        this.t1 = t1;

        this.gpsOffset = gpsOffset;

        dordt = ((t1[DOR_TX] + t1[DOR_RX]) - (t0[DOR_TX] + t0[DOR_RX])) / 2;
        domdt = ((t1[DOM_RX] + t1[DOM_TX]) - (t0[DOM_RX] + t0[DOM_TX])) / 2;

        dorMid = (t1[DOR_TX] + t1[DOR_RX]) / 2;
        domMid = (t1[DOM_RX] + t1[DOM_TX]) / 2;

        epsilon = (double)(dordt - domdt)/domdt;

        // Note that using double precision here but DOM internal
        // delay is small number so OK
        clen = 0.5 *
                ( asSeconds(t1[DOR_RX] - t1[DOR_TX]) -
                ((1.0 + epsilon) * asSeconds(t1[DOM_TX] - t1[DOM_RX])) );
    }


    /**
     * Cable Length
     * @return The cable length, in 0.1 nanosecond units
     */
    public double getCableLength()
    {
        return clen;
    }

    /**
     * Epsilon
     * @return Epsilon
     */
    double getEpsilon()
    {
        return epsilon;
    }

    /**
     * The time the second tcal was received by the DOR card.
     * @return The DOR RX time of the second tcal that defines this
     *         isochron, in 0.1 nanosecond units.
     */
    public long getDORReceiveTime()
    {
        return t1[DOR_RX];
    }

    /**
     * The (inclusive) upper-bound of the dom clock interval.
     * @return The DOM TX time of the second tcal that defines this
     *         isochron, in 0.1 nanosecond units.
     */
    long getUpperBound()
    {
        return t1[DOM_TX];
    }

    /**
     * The (exclusive) lower-bound of the dom clock interval.
     * @return The DOM TX time of the first tcal that defines this
     *         isochron, in 0.1 nanosecond units.
     */
    long getLowerBound()
    {
        return t0[DOM_TX];
    }


    /**
     * Check whether the given DOM time is bounded by this TCAL.
     *
     * @param domclk A time value from the DOM clock.
     * @param units Indicates the unit of the time value.
     * @return true if this Isochron bounds the time.
     */
    boolean containsDomClock(final long domclk, final TimeUnits units)
    {
        return containsDomClock(units.asUTC(domclk));
    }

    /**
     * Check whether the isochron upper bound is later than or equal to
     * a particular DOM time.
     *
     * @param domclk A time value from the DOM clock.
     * @param units Indicates the unit of the time value.
     * @return true if the upper bound is later or equal to the time.
     */
    boolean laterThan(final long domclk, final TimeUnits units)
    {
        return laterThan(units.asUTC(domclk));
    }

    /**
     * Reconstruct the UTC time corresponding to the DOM clock time.
     *
     * @param domclk A time value from the DOM clock.
     * @param units Indicates the unit of the time value.
     * @return The UTC time corresponding to the DOM time.
     */
    long reconstructUTC(final long domclk, final TimeUnits units)
    {
        return reconstructUTC(units.asUTC(domclk));
    }

    /**
     * toString
     * @return A human readable representation.
     */
    @Override
    public String toString()
    {
        return String.format("Isochron[[%d, %d, %d, %d], [%d, %d, %d, %d]," +
                "epsilon=%f,domMid=%d,dorMid=%d,clen=%f]",
                t0[0],t0[1],t0[2],t0[3],
                t1[0],t1[1],t1[2],t1[3],
                epsilon, domMid, dorMid, clen);
    }

    /**
     * Check whether the given DOM time is bounded by this TCAL.
     *
     * @param domclk DOM timestamp, scaled to 0.1 nanosecond ticks
     * @return true if this Isochron bounds the time.
     */
    private boolean containsDomClock(final long domclk)
    {
        return ( domclk > getLowerBound() ) &&
                ( domclk <= getUpperBound() );
    }

    /**
     * Check whether the isochron upper bound is later than or equal to
     * a particular DOM time.
     *
     * @param domclk DOM timestamp, scaled to 0.1 nanosecond ticks
     * @return true if the upper bound is later or equal to the time.
     */
    private boolean laterThan(final long domclk)
    {
        return getUpperBound()  >= domclk;
    }

    /**
     * Reconstruct the UTC time corresponding to the DOM clock time.
     *
     * @param domclk DOM timestamp, scaled to 0.1 nanosecond ticks
     * @return The UTC time corresponding to the DOM time.
     */
    private long reconstructUTC(final long domclk)
    {
        long dt = domclk - domMid;

        // Note: The following is not the same as dt += (epsilon * dt) which
        //       silently promotes dt to a double and then casts resulting sum
        //       as a long. This is a non-intuitive aspect of the java
        //       language specification.
        //
        // Example:
        //
        // dt = 10000000001
        // epsilon = -0.875
        // dt += (long) (epsilon * dt) = 1250000001
        // dt += (epsilon * dt)        = 1250000000
        //
        dt += (long) (epsilon * dt);
        return dorMid + dt + gpsOffset;
    }

    /**
     * Ensure that timestamps are increasing monotonically.
     *
     * @throws RAPCalException when monotonicity is violated;
     */
    private static void ensureMonotonic(final long[] early, final long[] later)
            throws RAPCalException
    {
        //defend against DOM clock rollover
        if(!isMonotonic(early, later))
        {
            String msg =
                    String.format("Non-monotonic timestamps:" +
                            " t0[%d, %d, %d, %d], t1[%d, %d, %d, %d]" ,
                            early[0], early[1], early[2], early[3],
                            later[0], later[1], later[2], later[3]);
            throw new RAPCalException(msg);
        }
    }

    /**
     * Check that timestamps are increasing monotonically through the
     * interval.
     */
    private static boolean isMonotonic(final long[] a, final long[] b)
    {
        return isMonotonic(a) && isMonotonic(b) &&
                (b[DOR_TX] >= a[DOR_RX]) &&
                (b[DOM_RX] >= a[DOM_TX]);
    }
    /**
     * Check that timestamps are increasing monotonically within the
     * time calibration.
     */
    private static boolean isMonotonic(final long[] a)
    {
        return (a[DOR_RX] >= a[DOR_TX]) && (a[DOM_TX] >= a[DOM_RX]);
    }

    /**
     * Convert a UTC time from 0.1 nanosecond units to seconds.
     * @param utc The value in 0.1 nanosecond units.
     * @return The value in seconds
     */
    private static double asSeconds(final double utc)
    {
        return 1.0E-10 * utc;
    }


}
