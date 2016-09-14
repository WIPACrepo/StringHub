package icecube.daq.performance.binary.buffer.test;

import icecube.daq.performance.binary.buffer.WritableRecordBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 *
 */
public class RecordBufferUtility
{

    /**
     * Add a specific number of bytes to a record buffer
     * @param buffer
     * @param length
     */
    public static void fill(WritableRecordBuffer buffer, int length)
    {
        ByteBuffer bb = ByteBuffer.allocate(length);

        for (int i = 0; i < length; i++)
        {
            bb.put((byte) (Math.random() * 128));
        }
        bb.flip();

        buffer.put(bb);
    }

    /**
     * Fill a buffer with a pattern value starting at the given byte position
     * of the pattern. The buffer will be filled to capacity, utilizing
     * byte-by-byte partial writes of the pattern to fill the buffer
     * if required.
     * @return The ordinal of the next byte in the pattern, useful for chaining
     *         writes.
     */
    public static int fill(final WritableRecordBuffer buffer,
                           final short pattern, final int startByte)
    {

        // 1. leading partial value
        if(startByte > 0)
        {
            int numPartial = 2 - startByte;
            int writeBytes = Math.min(buffer.available(), numPartial);
            putPart(buffer, pattern, startByte, writeBytes);
            if(numPartial > writeBytes)
            {
                return (startByte+writeBytes) % 2;
            }
        }

        //2. whole values
        ByteBuffer xfer = ByteBuffer.allocate(2);
        xfer.putShort(pattern);
        while(buffer.available() >= 2)
        {
            xfer.rewind();
            buffer.put(xfer);
        }

        // 3. trailing partial value
        int partial = buffer.available();
        if(partial > 0)
        {
            putPart(buffer, pattern, 0, partial);
        }

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
    public static int fill(final WritableRecordBuffer buffer,
                           final int pattern, final int startByte)
    {

        // 1. leading partial value
        if(startByte > 0)
        {

            int numPartial = 4 - startByte;
            int writeBytes = Math.min(buffer.available(), numPartial);
            putPart(buffer, pattern, startByte, writeBytes);
            if(numPartial > writeBytes)
            {
                return (startByte+writeBytes) % 4;
            }
        }

        //2. whole values
        ByteBuffer xfer = ByteBuffer.allocate(4);
        xfer.putInt(pattern);
        while(buffer.available() >= 4)
        {
            xfer.rewind();
            buffer.put(xfer);
        }

        // 3. trailing partial value
        int partial = buffer.available();
        if(partial > 0)
        {
            putPart(buffer, pattern, 0, partial);
        }

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
    public static int fill(final WritableRecordBuffer buffer,
                           final long pattern, final int startByte)
    {

        // 1. leading partial value
        if(startByte > 0)
        {

            int numPartial = 8 - startByte;
            int writeBytes = Math.min(buffer.available(), numPartial);
            putPart(buffer, pattern, startByte, writeBytes);
            if(numPartial > writeBytes)
            {
                return (startByte+writeBytes) % 8;
            }
        }

        //2. whole values
        ByteBuffer xfer = ByteBuffer.allocate(8);
        xfer.putLong(pattern);
        while(buffer.available() >= 8)
        {
            xfer.rewind();
            buffer.put(xfer);
        }

        // 3. trailing partial value
        int partial = buffer.available();
        if(partial > 0)
        {
            putPart(buffer, pattern, 0, partial);
        }

        // return the ordinal of the next pattern byte.
        return partial;
    }

    /**
     * Write a portion of a short byte-wise into a record buffer
     */
    public static void putPart(WritableRecordBuffer buffer, short pattern,
                               int skipBytes, int numBytes)
    {
        ByteBuffer xfer = ByteBuffer.allocate(numBytes);
        BitUtility.putPart(xfer, pattern, skipBytes, numBytes,
                ByteOrder.BIG_ENDIAN);
        xfer.flip();
        buffer.put(xfer);
    }

    /**
     * Write a portion of an int byte-wise into a record buffer
     */
    public static void putPart(WritableRecordBuffer buffer, int pattern,
                               int skipBytes, int numBytes)
    {
        ByteBuffer xfer = ByteBuffer.allocate(numBytes);
        BitUtility.putPart(xfer, pattern, skipBytes, numBytes,
                ByteOrder.BIG_ENDIAN);
        xfer.flip();
        buffer.put(xfer);
    }

    /**
     * Write a portion of a long byte-wise into a record buffer
     */
    public static void putPart(WritableRecordBuffer buffer, long pattern,
                               int skipBytes, int numBytes)
    {
        ByteBuffer xfer = ByteBuffer.allocate(numBytes);
        BitUtility.putPart(xfer, pattern, skipBytes, numBytes,
                ByteOrder.BIG_ENDIAN);
        xfer.flip();
        buffer.put(xfer);
    }

}
