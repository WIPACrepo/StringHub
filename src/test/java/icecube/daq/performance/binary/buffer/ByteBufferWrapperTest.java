package icecube.daq.performance.binary.buffer;

import icecube.daq.performance.common.BufferContent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static icecube.daq.performance.binary.buffer.test.BitUtility.fill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


/**
 * Tests RecordBuffers.ByteBufferWrapper
 */
@RunWith(value = Parameterized.class)
public class ByteBufferWrapperTest extends CommonRecordBufferTest
{

    //test parameterized for these sizes
    private static int[] testSizes =
            new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 111, 128, 129};

    @Parameterized.Parameter(0)
    public int size;

    @Parameterized.Parameters(name = "ByteBufferWrapper[{0}]")
    public static List<Object[]> sizes()
    {
        List<Object[]> cases = new ArrayList<Object[]>(5);
        for (int i = 0; i < testSizes.length; i++)
        {
            cases.add(new Object[]{testSizes[i]});
        }
        return cases;
    }

    @Override
    int subjectSize()
    {
        return size;
    }

    @Override
    RecordBuffer createSubject(final short pattern, final int alignment)
    {
        ByteBuffer bb = ByteBuffer.allocate(size);
        fill(bb, pattern, 2-alignment);
       return new RecordBuffers.ByteBufferWrapper(bb, BufferContent.POSITION_TO_LIMIT);
    }

    @Override
    RecordBuffer createSubject(final int pattern, final int alignment)
    {
        ByteBuffer bb = ByteBuffer.allocate(size);
        fill(bb, pattern, 4-alignment);
        return new RecordBuffers.ByteBufferWrapper(bb, BufferContent.POSITION_TO_LIMIT);
    }

    @Override
    RecordBuffer createSubject(final long pattern, final int alignment)
    {
        ByteBuffer bb = ByteBuffer.allocate(size);
        fill(bb, pattern, 8-alignment);
        return new RecordBuffers.ByteBufferWrapper(bb, BufferContent.POSITION_TO_LIMIT);
    }

    @Test
    public void testConstruction()
    {
        // illegal endianess
        {
            ByteBuffer bb = ByteBuffer.allocate(size);
            try
            {
                RecordBuffer rb =
                        new RecordBuffers.ByteBufferWrapper(bb.order(ByteOrder.LITTLE_ENDIAN),
                                BufferContent.ZERO_TO_CAPACITY);
                fail("Little endian buffers are not supported");
            }
            catch (IllegalArgumentException iae)
            {
                // desired
            }
        }

        // construct from position to limit
        {
            ByteBuffer bb = ByteBuffer.allocate(129);
            bb.put(11, (byte) 123);
            bb.putShort(12, (short) 30881);
            bb.putInt(14, 0x7abcdef3);
            bb.putLong(18, 0x7abcdef987654321L);
            bb.position(11);
            bb.limit(27);
            RecordBuffer rb = new RecordBuffers.ByteBufferWrapper(bb,
                    BufferContent.POSITION_TO_LIMIT);

            assertEquals(bb.remaining(), rb.getLength());
            assertEquals((byte) 123, rb.getByte(0));
            assertEquals((short) 30881, rb.getShort(1));
            assertEquals(0x7abcdef3, rb.getInt(3));
            assertEquals(0x7abcdef987654321L, rb.getLong(7));
        }

        // construct from zero to limit
        {
            ByteBuffer bb = ByteBuffer.allocate(129);
            for(int i = 0; i<11; i++)
            {
                bb.put(i, (byte) -7);
            }
            bb.put(11, (byte) 123);
            bb.putShort(12, (short) 30881);
            bb.putInt(14, 0x7abcdef3);
            bb.putLong(18, 0x7abcdef987654321L);
            bb.position(11);
            bb.limit(27);
            RecordBuffer rb = new RecordBuffers.ByteBufferWrapper(bb,
                    BufferContent.ZERO_TO_LIMIT);

            assertEquals(bb.limit(), rb.getLength());
            for(int i = 0; i<11; i++)
            {
                assertEquals((byte) -7, rb.getByte(i));
            }
            assertEquals((byte) 123, rb.getByte(11));
            assertEquals((short) 30881, rb.getShort(12));
            assertEquals(0x7abcdef3, rb.getInt(14));
            assertEquals(0x7abcdef987654321L, rb.getLong(18));
        }

        // construct from zero to Capacity
        {
            ByteBuffer bb = ByteBuffer.allocate(129);
            for(int i = 0; i<11; i++)
            {
                bb.put(i, (byte) -7);
            }
            bb.put(11, (byte) 123);
            bb.putShort(12, (short) 30881);
            bb.putInt(14, 0x7abcdef3);
            bb.putLong(18, 0x7abcdef987654321L);
            for(int i = 27; i<129; i++)
            {
                bb.put(i, (byte) -13);
            }

            bb.position(99);
            bb.limit(103);

            RecordBuffer rb = new RecordBuffers.ByteBufferWrapper(bb,
                    BufferContent.ZERO_TO_CAPACITY);

            assertEquals(129, rb.getLength());
            for(int i = 0; i<11; i++)
            {
                assertEquals((byte) -7, rb.getByte(i));
            }
            assertEquals((byte) 123, rb.getByte(11));
            assertEquals((short) 30881, rb.getShort(12));
            assertEquals(0x7abcdef3, rb.getInt(14));
            assertEquals(0x7abcdef987654321L, rb.getLong(18));
            for(int i = 27; i<129; i++)
            {
                assertEquals((byte) -13, rb.getByte(i));

            }
        }

    }


    @Test
    public void testToString()
    {
        ByteBuffer bb = ByteBuffer.allocate(size);
        RecordBuffer rb = new RecordBuffers.ByteBufferWrapper(bb,
                BufferContent.POSITION_TO_LIMIT);

        String expected = "WRAPPED(" + bb.toString() +")";
        assertEquals(expected, rb.toString());
    }

}
