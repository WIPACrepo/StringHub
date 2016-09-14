package icecube.daq.performance.binary.buffer;

import icecube.daq.performance.common.BufferContent;
import icecube.daq.performance.common.PowersOfTwo;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests RecordBuffers.java
 */
public class RecordBuffersTest
{

    @Test
    public void testWrap()
    {
        ///
        /// Tests the record wrapping
        ///
        byte[] garbage = randomArray(21353);
        for (int i = 0; i < garbage.length; i++)
        {
            garbage[i] = (byte) (Math.random() * 255);
        }

        {
            RecordBuffer wrap = RecordBuffers.wrap(garbage);
            assertEquals(garbage.length, wrap.getLength());
            for (int i = 0; i < garbage.length; i++)
            {
                assertEquals(garbage[i], wrap.getByte(i));
            }
        }

        {
            ByteBuffer bb = ByteBuffer.allocate(garbage.length);
            bb.put(garbage);
            RecordBuffer wrap = RecordBuffers.wrap(bb,
                    BufferContent.ZERO_TO_CAPACITY);
            assertEquals(garbage.length, wrap.getLength());
            for (int i = 0; i < garbage.length; i++)
            {
                assertEquals(garbage[i], wrap.getByte(i));
            }
        }

        {
            ByteBuffer bb = ByteBuffer.allocate(garbage.length);
            bb.put(garbage);
            bb.position(11);
            bb.limit(222);
            RecordBuffer wrap = RecordBuffers.wrap(bb,
                    BufferContent.POSITION_TO_LIMIT);
            assertEquals((222 - 11), wrap.getLength());
            for (int i = 11; i < 222; i++)
            {
                assertEquals(garbage[i], wrap.getByte(i-11));
            }
        }

    }

    @Test
    public void testChain()
    {
        ///
        /// Tests the record chaining factory
        ///

        byte[][] pieces = new byte[][]
                {
                        randomArray(3124),
                        randomArray(14),
                        randomArray(0),
                        randomArray(43524),
                        randomArray(18),
                };

        byte[] garbage = unite(pieces);


        {
            RecordBuffer chain = RecordBuffers.chain(pieces);
            assertEquals(garbage.length, chain.getLength());
            for (int i = 0; i < garbage.length; i++)
            {
                assertEquals(garbage[i], chain.getByte(i));
            }
        }


        {
            RecordBuffer[] wraps = new RecordBuffer[pieces.length];
            for (int i = 0; i < wraps.length; i++)
            {
                wraps[i] = RecordBuffers.wrap(pieces[i]);
            }

            RecordBuffer chain = RecordBuffers.chain(wraps);
            assertEquals(garbage.length, chain.getLength());
            for (int i = 0; i < garbage.length; i++)
            {
                assertEquals(garbage[i], chain.getByte(i));
            }
        }
    }

    private static byte[] randomArray(int size)
    {
        byte[] garbage = new byte[size];
        for (int i = 0; i < garbage.length; i++)
        {
            garbage[i] = (byte) (Math.random() * 255);
        }
        return garbage;
    }

    private byte[] unite(byte[][] pieces)
    {
        int size = 0;
        for (int i = 0; i < pieces.length; i++)
        {
            size += pieces[i].length;
        }

        byte[] unity = new byte[size];
        int offset = 0;
        for (int i = 0; i < pieces.length; i++)
        {
            System.arraycopy(pieces[i], 0, unity, offset, pieces[i].length);
            offset += pieces[i].length;
        }

        return unity;
    }

    @Test
    public void testWritableFactory()
    {
        WritableRecordBuffer product = RecordBuffers.writable(119);
        assertEquals(119, product.available());
    }

    @Test
    public void testRingFactory()
    {
        {
            WritableRecordBuffer.Prunable ring = RecordBuffers.ring(119);
            assertEquals(119, ring.available());
            assertTrue(ring instanceof RecordBuffers.RingBuffer);
        }

        {
            WritableRecordBuffer.Prunable ring = RecordBuffers.ring(PowersOfTwo._1024);
            assertEquals(PowersOfTwo._1024.value(), ring.available());
            assertTrue(ring instanceof RecordBuffers.FastRingBuffer);
        }

        {
            WritableRecordBuffer.Prunable ring = RecordBuffers.ring(1024);
            assertEquals(1024, ring.available());
            assertTrue(ring instanceof RecordBuffers.FastRingBuffer);
        }
    }

    @Test
    public void testAchieveCoverage()
    {
        // satisfies coverage to get to 100%
        RecordBuffers recordBuffers = new RecordBuffers();
    }

}
