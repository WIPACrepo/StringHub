package icecube.daq.util;

/**
 * Describes meaning of long values used as clock values.
 *
 * The intent of this class is to allow long method parameters to be typed
 * without the additional overhead of object creation.
 */
public enum TimeUnits
{
    /** A 40MHz DOM clock value. */
    DOM
            {
                final long MAX_DOM_CLOCK = (1L<<48)-1;
                @Override
                final public long maxValue()
                {
                    return MAX_DOM_CLOCK;
                }

                @Override
                final public long asUTC(final long val)
                {
                    return 250 * val;
                }
            },

    /** A 20MHz DOR clock value. */
    DOR
            {
                final long MAX_DOR_CLOCK = (1L<<56)-1;

                @Override
                final public long maxValue()
                {
                    return MAX_DOR_CLOCK;
                }

                @Override
                final public long asUTC(final long val)
                {
                    return 500 * val;
                }
            },

    /** A 0.1 nanosecond time quantity. */
    UTC
            {
                @Override
                public long maxValue()
                {
                    return Long.MAX_VALUE;
                }

                @Override
                final public long asUTC(final long val)
                {
                    return val;
                }
            };


    /** Maximum value of the underlying clock. */
    abstract public long maxValue();

    /** Scale the time value to 0.1 nanosecond units. */
    abstract public long asUTC(final long val);

}
