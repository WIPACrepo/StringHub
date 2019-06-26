package icecube.daq.performance.binary.buffer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static icecube.daq.performance.binary.buffer.test.BitUtility.*;
import static icecube.daq.performance.binary.buffer.test.BitUtility.getByteAtPosition;
import static org.junit.Assert.assertEquals;

/**
 * Tests BitUtility.java.
 *
 * The other tests in this package rely on the bit-twiddling utilities
 * found in BitUtility, so it is also tested independently.
 */
@RunWith(Parameterized.class)
public class BitUtilityTest
{

    long longPattern = 0xABCDEF0102030405L;
    int intPattern = 0xABCDEF01;
    short shortPattern = (short) 0xABCD;

    /// tests auto-generated for each endian
    private static final ByteOrder[] ENDIANS =
            new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN};
    /// tests auto-generated for each buffer size
    private static final int[] BUFFER_SIZES =
            {0,1,2,3,4,5,6,7,8, 13, 99, 111, 222, 525};

    @Parameterized.Parameter(0)
    public int bufferSize;

    @Parameterized.Parameter(1)
    public ByteOrder endian;

    @Parameterized.Parameters(name = "ByteBuffer[{0}] {1}")
    public static List<Object[]> sizes()
    {
        // parameterize for each buffer size in each endianess
        List<Object[]> cases =
                new ArrayList<Object[]>(BUFFER_SIZES.length * ENDIANS.length);


        for (int i = 0; i < BUFFER_SIZES.length; i++)
        {
            for (int j = 0; j < ENDIANS.length; j++)
            {
                cases.add(new Object[]{BUFFER_SIZES[i], ENDIANS[j]});
            }
        }
        return cases;
    }


    //#############################
    //# longs
    //#############################
    @Test
    public void testPartialLong()
    {

        // manually write a long at byte alignment 0-7
        // while splitting writes from [8,0] to [0,8]
        for (int align=0; align<8; align++)
        {
            if(align+8 > bufferSize){break;}

            for (int j=8; j>=0; j--)
            {
                ByteBuffer allocate = ByteBuffer.allocate(bufferSize);
                allocate.order(endian);
                allocate.position(align);
                putPart(allocate, longPattern, j, endian);
                putPart(allocate, longPattern,j, 8-j, endian);

                long out = allocate.duplicate().order(endian).getLong(align);

                assertEquals(formatError(longPattern, out), longPattern, out);
            }
        }
    }

    @Test
    public void testFillLong()
    {
        // fill a buffer at all value alignments
        for (int align=0; align<8; align++)
        {
            StorageInfo storage = new StorageInfo(bufferSize, 8, align);
            ByteBuffer allocate = ByteBuffer.allocate(storage.capacity);
            allocate.order(endian);
            final int next = fill(allocate, longPattern, align);

            assertEquals(storage.nextOrdinal, next);

            // read the partial value at the start of the buffer
            for(int i=0; i < storage.leadingPartial.length; i++)
            {
                byte out = allocate.get(i);
                byte expected = getByteAtPosition(longPattern,
                        storage.leadingPartial[i], endian);
                assertEquals(formatError(expected, out), expected,  out);
            }

            // read the whole values
            for(int pos=storage.leadingPartial.length; storage.capacity-pos>8; pos+=8 )
            {
                long out = allocate.getLong(pos);
                assertEquals(formatError(longPattern, out), longPattern, out);
            }

            // read the partial value at the end of the buffer
            int startPos = storage.capacity - storage.trailingPartial.length;
            for(int i=0; i < storage.trailingPartial.length; i++)
            {
                byte out = allocate.get(i + startPos);
                byte expected = getByteAtPosition(longPattern,
                        storage.trailingPartial[i], endian);
                assertEquals(formatError(expected, out), expected,  out);
            }
        }
    }

    //#############################
    //# ints
    //#############################

    @Test
    public void testPartialInt()
    {

        // manually write an int at byte alignment 0-3
        // while splitting writes from [4,0] to [0,4]

        for (int align=0; align<4; align++)
        {
            if(align+4 > bufferSize){break;}

            for (int j=4; j>=0; j--)
            {
                ByteBuffer allocate = ByteBuffer.allocate(bufferSize);
                allocate.order(endian);
                allocate.position(align);
                putPart(allocate, intPattern, j, endian);
                putPart(allocate, intPattern,j, 4-j, endian);

                int out = allocate.duplicate().order(endian).getInt(align);

                assertEquals(formatError(intPattern, out), intPattern, out);
            }
        }

    }


    @Test
    public void testFillInt()
    {
        // fill a buffer at all value alignments
        for (int align=0; align<4; align++)
        {
            StorageInfo storage = new StorageInfo(bufferSize, 4, align);
            ByteBuffer allocate = ByteBuffer.allocate(storage.capacity);
            allocate.order(endian);
            final int next = fill(allocate, intPattern, align);

            assertEquals(storage.nextOrdinal, next);

            // read the partial value at the start of the buffer
            for(int i=0; i < storage.leadingPartial.length; i++)
            {
                byte out = allocate.get(i);
                byte expected = getByteAtPosition(intPattern,
                        storage.leadingPartial[i], endian);
                assertEquals(formatError(expected, out), expected,  out);
            }

            // read the whole values
            for(int pos=storage.leadingPartial.length; storage.capacity-pos>4; pos+=4 )
            {
                int out = allocate.getInt(pos);
                assertEquals(formatError(intPattern, out), intPattern, out);
            }

            // read the partial value at the end of the buffer
            int startPos = storage.capacity - storage.trailingPartial.length;
            for(int i=0; i < storage.trailingPartial.length; i++)
            {
                byte out = allocate.get(i + startPos);
                byte expected = getByteAtPosition(intPattern,
                        storage.trailingPartial[i], endian);
                assertEquals(formatError(expected, out), expected,  out);
            }
        }
    }

    //#############################
    //# shorts
    //#############################

    @Test
    public void testPartialShort()
    {
        // manually write a short at byte alignment 0-2
        // while splitting writes from [2,0] to [0,2]
        for (int align=0; align<2; align++)
        {
            if(align+2 > bufferSize){break;}

            for (int j=2; j>=0; j--)
            {
                ByteBuffer allocate = ByteBuffer.allocate(bufferSize);
                allocate.order(endian);
                allocate.position(align);
                putPart(allocate, shortPattern, j, endian);
                putPart(allocate, shortPattern,j, 2-j, endian);

                short out = allocate.duplicate().order(endian).getShort(align);

                assertEquals(formatError(shortPattern, out), shortPattern, out);
            }
        }
    }


    @Test
    public void testFillShort()
    {
        // fill a buffer at all value alignments
        for (int align=0; align<2; align++)
        {

            StorageInfo storage = new StorageInfo(bufferSize, 2, align);
            ByteBuffer allocate = ByteBuffer.allocate(storage.capacity);
            allocate.order(endian);
            final int next = fill(allocate, shortPattern, align);

            assertEquals(storage.nextOrdinal, next);

            // read the partial value at the start of the buffer
            for(int i=0; i < storage.leadingPartial.length; i++)
            {
                byte out = allocate.get(i);
                byte expected = getByteAtPosition(shortPattern,
                        storage.leadingPartial[i], endian);
                assertEquals(formatError(expected, out), expected,  out);
            }

            // read the whole values
            for(int pos=storage.leadingPartial.length; storage.capacity-pos>2; pos+=2 )
            {
                short out = allocate.getShort(pos);
                assertEquals(formatError(shortPattern, out), shortPattern, out);
            }

            // read the partial value at the end of the buffer
            int startPos = storage.capacity - storage.trailingPartial.length;
            for(int i=0; i < storage.trailingPartial.length; i++)
            {
                byte out = allocate.get(i + startPos);
                byte expected = getByteAtPosition(shortPattern,
                        storage.trailingPartial[i], endian);
                assertEquals(formatError(expected, out), expected,  out);
            }
        }
    }


    private static String formatError(long expected, long actual)
    {
        return "expected: " + Long.toHexString(expected) +
                ", actual: " + Long.toHexString(actual);
    }
    private static String formatError(int expected, int actual)
    {
        return "expected: " + Integer.toHexString(expected) +
                ", actual: " + Integer.toHexString(actual);
    }
    private static String formatError(short expected, short actual)
    {
        return "expected: " + Integer.toHexString(Short.toUnsignedInt(expected)) + "," +
                " actual: " + Integer.toHexString(Short.toUnsignedInt(actual));
    }

    private static String formatError(byte expected, byte actual)
    {
        return "expected: " + Integer.toHexString(Byte.toUnsignedInt(expected)) + "," +
                " actual: " + Integer.toHexString(Byte.toUnsignedInt(actual));
    }

}
