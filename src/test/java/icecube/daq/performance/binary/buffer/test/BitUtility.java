package icecube.daq.performance.binary.buffer.test;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility methods for generating test data utilizing byte-wise operations
 * on short, int and long data types.
 */
public class BitUtility
{

    /**
     * Utility class for calculating the amount of whole/partial
     * primitives that can fit in a given storage space (and
     * accounting for an alignment offset)
     */
    public static class StorageInfo
    {
        public final int width;            // the number of bytes per value

        public final int capacity;         // the space available
        public final int startOrdinal;     // the ordinal of the byte to start
                                           // the fill with
        public final int numWhole;         // the number of whole primitives
                                           // that fit in capacity
        public final int partialWritten;   // the number of partial bytes
                                           // written at the end of the buffer,
                                           // high bytes post-pad, low bytes for
                                           // alignment padding
        public final int nextOrdinal;      // the ordinal of the next pattern
                                           // byte. Used to differentiate between
                                           // reaching end-of-buffer during
                                           // alignment padding (low bytes
                                           // first) and end-of-buffer (high
                                           // bytes first)

        public final int[] leadingPartial;
        public final int[] trailingPartial;


        public StorageInfo(final int capacity,
                           final int width,
                           final int startOrdinal)
        {
            this.width = width;

            this.capacity = capacity;
            this.startOrdinal = startOrdinal;
            numWhole = Math.max( (capacity-startOrdinal)/width, 0);
            partialWritten= capacity%width;
            nextOrdinal = (startOrdinal+capacity)%width;

            // the ordinals of the leading, partial value
            int numLeading = Math.min((width - startOrdinal)%width, capacity);
            leadingPartial = new int[numLeading];
            for (int i = 0; i < leadingPartial.length; i++)
            {
                leadingPartial[i] = startOrdinal+i;
            }

            // the ordinals of the trailing, partial value
            int numTrailing = (capacity-leadingPartial.length) % width;
            trailingPartial = new int[numTrailing];
            for (int i = 0; i < trailingPartial.length; i++)
            {
                trailingPartial[i] = i;
            }
        }

    }

    /**
     * Fill a buffer with a pattern value starting at the given byte alignment.
     * The buffer will be filled to capacity, utilizing byte-by-byte partial
     * writes of the pattern if required.
     *
     * The returned buffer will have a position after the padding and the limit
     * at capacity.
     */
    public static int fill(ByteBuffer buffer, long pattern)
    {
        return fill(buffer, pattern, 0);
    }

    /**
     * Fill a buffer with a pattern value starting at the given byte alignment.
     * The buffer will be filled to capacity, utilizing byte-by-byte partial
     * writes of the pattern if required.
     *
     * The returned buffer will have a position after the padding and the limit
     * at capacity.
     */
    public static int fill(ByteBuffer buffer, int pattern)
    {
        return fill(buffer, pattern, 0);
    }

    /**
     * Fill a buffer with a pattern value starting at the given byte alignment.
     * The buffer will be filled to capacity, utilizing byte-by-byte partial
     * writes of the pattern if required.
     *
     * The returned buffer will have a position after the padding and the limit
     * at capacity.
     */
    public static int fill(ByteBuffer buffer, short pattern)
    {
        return fill(buffer, pattern, 0);
    }


    /**
     * Fill a buffer with a pattern value starting at the given byte position
     * of the pattern. The buffer will be filled to capacity, utilizing
     * byte-by-byte partial writes of the pattern to fill the buffer
     * if required.
     * @return The ordinal of the next byte in the pattern, useful for chaining
     *         writes.
     */
    public static int fill(final ByteBuffer buffer,
                           final short pattern, final int startByte)
    {
        buffer.clear();

        // 1. leading partial value
        if(startByte > 0)
        {

            int numPartial = 2 - startByte;
            int writeBytes = Math.min(buffer.remaining(), numPartial);
            putPart(buffer, pattern, startByte, writeBytes, buffer.order());
            if(numPartial > writeBytes)
            {
                buffer.flip();
                return (startByte+writeBytes) % 2;
            }
        }

        //2. whole values
        while(buffer.remaining() >= 2)
        {
            buffer.putShort(pattern);
        }

        // 3. trailing partial value
        int partial = buffer.remaining();
        if(partial > 0)
        {
            putPart(buffer, pattern, partial, buffer.order());
        }

        buffer.flip();

        // return the ordinal of the next pattern byte.
        return partial;
    }




    /**
     * Fill a buffer with a pattern value starting at the given byte position
     * of the pattern. The buffer will be filled to capacity, utilizing
     * byte-by-byte partial writes of the pattern to fill the buffer
     * if required.
     * @return The ordinal of the next byte in the pattern, useful for chaining
     *         writes.
     */
    public static int fill(final ByteBuffer buffer,
                           final int pattern, final int startByte)
    {
        buffer.clear();

        // 1. leading partial value
        if(startByte > 0)
        {

            int numPartial = 4 - startByte;
            int writeBytes = Math.min(buffer.remaining(), numPartial);
            putPart(buffer, pattern, startByte, writeBytes, buffer.order());
            if(numPartial > writeBytes)
            {
                buffer.flip();
                return (startByte+writeBytes) % 4;
            }
        }

        //2. whole values
        while(buffer.remaining() >= 4)
        {
            buffer.putInt(pattern);
        }

        // 3. trailing partial value
        int partial = buffer.remaining();
        if(partial > 0)
        {
            putPart(buffer, pattern, partial, buffer.order());
        }

        buffer.flip();

        // return the ordinal of the next pattern byte.
        return partial;
    }

    /**
     * Fill a buffer with a pattern value starting at the given byte position
     * of the pattern. The buffer will be filled to capacity, utilizing
     * byte-by-byte partial writes of the pattern to fill the buffer
     * if required.
     * @return The ordinal of the next byte in the pattern, useful for chaining
     *         writes.
     */
    public static int fill(final ByteBuffer buffer,
                           final long pattern, final int startByte)
    {
        buffer.clear();

        // 1. leading partial value
        if(startByte > 0)
        {

            int numPartial = 8 - startByte;
            int writeBytes = Math.min(buffer.remaining(), numPartial);
            putPart(buffer, pattern, startByte, writeBytes, buffer.order());
            if(numPartial > writeBytes)
            {
                buffer.flip();
                return (startByte+writeBytes) % 8;
            }
        }

        //2. whole values
        while(buffer.remaining() >= 8)
        {
            buffer.putLong(pattern);
        }

        // 3. trailing partial value
        int partial = buffer.remaining();
        if(partial > 0)
        {
            putPart(buffer, pattern, partial, buffer.order());
        }

        buffer.flip();

        // return the ordinal of the next pattern byte.
        return partial;
    }

    public static void putPart(ByteBuffer buffer, short pattern, int numBytes,
                               ByteOrder endianess)
    {
        putPart(buffer, pattern, 0, numBytes, endianess);
    }

    public static void putPart(ByteBuffer buffer, int pattern, int numBytes,
                         ByteOrder endianess)
    {
        putPart(buffer, pattern, 0, numBytes, endianess);
    }

    public static void putPart(ByteBuffer buffer, long pattern, int numBytes,
                               ByteOrder endianess)
    {
        putPart(buffer, pattern, 0, numBytes, endianess);
    }

    /**
     * Write a portion of an short byte-wise into a buffer in the specified
     * byte order.
     */
    public static void putPart(ByteBuffer buffer, short pattern,
                               int skipBytes, int numBytes, ByteOrder endianess)
    {
        if(numBytes-skipBytes > 2)
        {
            throw new Error("Too many: " + numBytes);
        }
        if(ByteOrder.BIG_ENDIAN.equals(endianess))
        {
            for(int i=1; i>(1-numBytes); i--)
            {
                long shift = (pattern >> ((i - skipBytes)*8)) & 0xFF;
                byte b = (byte) shift;
                buffer.put(b);
            }
        }
        else if(ByteOrder.LITTLE_ENDIAN.equals(endianess))
        {
            for(int i=0; i<numBytes; i++)
            {
                byte b = (byte) ((pattern >> ((i + skipBytes)*8)) & 0xFF);
                buffer.put(b);
            }
        }
        else
        {
            throw new Error();
        }
    }


    /**
     * Write a portion of an int byte-wise into a buffer in the specified
     * byte order.
     */
    public static void putPart(ByteBuffer buffer, int pattern,
                               int startByte, int numBytes, ByteOrder endianess)
    {
        if(numBytes-startByte > 4)
        {
            throw new Error("Too many: start " + startByte
                    + " to " + (startByte+numBytes));
        }

        if(ByteOrder.BIG_ENDIAN.equals(endianess))
        {
            for(int i=3; i>(3-numBytes); i--)
            {
                int shift = (pattern >> ((i - startByte)*8)) & 0xFF;
                byte b = (byte) shift;
                buffer.put(b);
            }
        }
        else if(ByteOrder.LITTLE_ENDIAN.equals(endianess))
        {
            for(int i=0; i<numBytes; i++)
            {
                byte b = (byte) ((pattern >> ((i + startByte)*8)) & 0xFF);
                buffer.put(b);
            }
        }
        else
        {
            throw new Error();
        }
    }


    /**
     * Write a portion of a long byte-wise into a buffer in the specified
     * byte order.
     */
    public static void putPart(ByteBuffer buffer, long pattern,
                         int skipBytes, int numBytes, ByteOrder endianess)
    {
        if(numBytes-skipBytes > 8)
        {
            throw new Error("Too many: " + numBytes);
        }
        if(ByteOrder.BIG_ENDIAN.equals(endianess))
        {
            for(int i=7; i>(7-numBytes); i--)
            {
                long shift = (pattern >> ((i - skipBytes)*8)) & 0xFF;
                byte b = (byte) shift;
                buffer.put(b);
            }
        }
        else if(ByteOrder.LITTLE_ENDIAN.equals(endianess))
        {
            for(int i=0; i<numBytes; i++)
            {
                byte b = (byte) ((pattern >> ((i + skipBytes)*8)) & 0xFF);
                buffer.put(b);
            }
        }
        else
        {
            throw new Error();
        }
    }


    public static byte getByteAtPosition(long pattern, int idx, ByteOrder endianess)
    {
        if(ByteOrder.BIG_ENDIAN.equals(endianess))
        {
            return (byte)((pattern >> ((7 - idx)*8)) & 0xFF);

        }
        else if(ByteOrder.LITTLE_ENDIAN.equals(endianess))
        {
            return (byte) ((pattern >> (idx*8)) & 0xFF);
        }
        else
        {
            throw new Error();
        }
    }

    public static byte getByteAtPosition(int pattern, int idx, ByteOrder endianess)
    {
        if(ByteOrder.BIG_ENDIAN.equals(endianess))
        {
            return (byte)((pattern >> ((3 - idx)*8)) & 0xFF);

        }
        else if(ByteOrder.LITTLE_ENDIAN.equals(endianess))
        {
            return (byte) ((pattern >> (idx*8)) & 0xFF);
        }
        else
        {
            throw new Error();
        }
    }
    public static byte getByteAtPosition(short pattern, int idx, ByteOrder endianess)
    {
        if(ByteOrder.BIG_ENDIAN.equals(endianess))
        {
            return (byte)((pattern >> ((1 - idx)*8)) & 0xFF);

        }
        else if(ByteOrder.LITTLE_ENDIAN.equals(endianess))
        {
            return (byte) ((pattern >> (idx*8)) & 0xFF);
        }
        else
        {
            throw new Error();
        }
    }

    public static short readShort(byte[] source, int position, ByteOrder order)
    {
        if (order.equals(ByteOrder.BIG_ENDIAN))
        {
            return (short)((source[position] << 8) | (source[position+1] & 0xff));

        }
        else if(order.equals(ByteOrder.LITTLE_ENDIAN))
        {
            return (short)((source[position+1] << 8) | (source[position] & 0xff));

        }
        else
        {
            throw new Error();
        }
    }

    public static int readInt(byte[] source, int position, ByteOrder order)
    {
        if (order.equals(ByteOrder.BIG_ENDIAN))
        {
            return (((source[position]) << 24) |
                    ((source[position+1] & 0xff) << 16) |
                    ((source[position+2] & 0xff) <<  8) |
                    ((source[position+3] & 0xff)      ));

        }
        else if(order.equals(ByteOrder.LITTLE_ENDIAN))
        {
            return (((source[position+3]) << 24) |
                    ((source[position+2] & 0xff) << 16) |
                    ((source[position+1] & 0xff) <<  8) |
                    ((source[position] & 0xff)      ));
        }
        else
        {
            throw new Error();
        }

    }

    public static long readLong(byte[] source, int position, ByteOrder order)
    {
        if (order.equals(ByteOrder.BIG_ENDIAN))
        {
            return ((((long)source[position]       ) << 56) |
                    (((long)source[position+1] & 0xff) << 48) |
                    (((long)source[position+2] & 0xff) << 40) |
                    (((long)source[position+3] & 0xff) << 32) |
                    (((long)source[position+4] & 0xff) << 24) |
                    (((long)source[position+5] & 0xff) << 16) |
                    (((long)source[position+6] & 0xff) <<  8) |
                    (((long)source[position+7] & 0xff)      ));

        }
        else if(order.equals(ByteOrder.LITTLE_ENDIAN))
        {
            return ((((long)source[position+7]       ) << 56) |
                    (((long)source[position+6] & 0xff) << 48) |
                    (((long)source[position+5] & 0xff) << 40) |
                    (((long)source[position+4] & 0xff) << 32) |
                    (((long)source[position+3] & 0xff) << 24) |
                    (((long)source[position+2] & 0xff) << 16) |
                    (((long)source[position+1] & 0xff) <<  8) |
                    (((long)source[position] & 0xff)      ));
        }
        else
        {
            throw new Error();
        }

    }


}
