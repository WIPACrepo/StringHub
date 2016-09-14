package icecube.daq.performance.common;

/**
 * Enumerates the powers of two of the int type.
 *
 * An enumerated type is useful for certain performance-minded code that
 * requires powers of 2.
 */
public enum PowersOfTwo
{
    _1(1),
    _2(2),
    _4(4),
    _8(8),
    _16(16),
    _32(32),
    _64(64),
    _128(128),
    _256(256),
    _512(512),
    _1024(1024),
    _2048(2048),
    _4096(4096),
    _8192(8192),
    _16384(16384),
    _32768(32768),
    _65536(65536),
    _131072(131072),
    _262144(262144),
    _524288(524288),
    _1048576(1048576),
    _2097152(2097152),
    _4194304(4194304),
    _8388608(8388608),
    _16777216(16777216),
    _33554432(33554432),
    _67108864(67108864),
    _134217728(134217728),
    _268435456(268435456),
    _536870912(536870912),
    _1073741824(1073741824);

    private final int value;
    private final int mask;


    private PowersOfTwo(int value)
    {
        this.value = value;
        this.mask = value-1;
    }

    /**
     * @return The integer value.
     */
    public int value()
    {
        return value;
    }

    /**
     * @return The bitmask that isolates the lower bits.  Useful for
     *         optimizing the modulus operator.
     *
     *         Example:  n % 1024 can be replaced by n & (1024-1) for
     *                   positive values of n.
     *
     *         The mask can also be used to implement an efficient
     *         wrapping counter.
     *
     *         Example: To implement a counter that wraps to zero at 1024:
     *
     *                   while(n++)
     *                   {
     *                       // val [0-1023]
     *                       val = n & (1024-1);
     *                   }
     *
     */
    public int mask()
    {
        return mask;
    }

    /**
     * Convenience method to test if a value is a power of two;
     *
     * @param value the values to test.
     * @return True if the value is a pwer of two;
     */
    public static boolean isPowerOfTwo(final int value)
    {
        return (value != 0) && ((value & (value - 1)) == 0);
    }

    /**
     * Convenience method to navigate from a value to an enum instance.
     * @param value A power of two.
     * @return The enum corresponding to this value, or null if value
     *         is not a power of 2;
     */
    public static PowersOfTwo lookup(int value)
    {
        for(PowersOfTwo p : PowersOfTwo.values())
        {
            if(p.value() == value)
            {
                return p;
            }
        }

        return null;
    }

}
