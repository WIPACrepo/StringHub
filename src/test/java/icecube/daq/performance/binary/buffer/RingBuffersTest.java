package icecube.daq.performance.binary.buffer;

import icecube.daq.performance.binary.buffer.test.RecordBufferUtility;
import icecube.daq.performance.common.PowersOfTwo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static icecube.daq.performance.binary.buffer.test.RecordBufferUtility.*;

import static org.junit.Assert.assertEquals;


/**
 * Tests RecordBuffers.RingBuffer and RecordBuffers.FastRingBuffer
 */
public abstract class RingBuffersTest extends CommonWritableRecordBufferTest
{

    abstract WritableRecordBuffer.Prunable makeRing();


    @Override
    WritableRecordBuffer createSubject()
    {
        return makeRing();
    }

    @Test
    public void testWrappingWrites()
    {
        //
        // test writing multibyte values that wrap around the ring
        //

        short shortValue = Short.MAX_VALUE;
        ByteBuffer shortNumber = ByteBuffer.allocate(2);
        shortNumber.putShort(shortValue);

        // write a short at the ring boundary at alignment (0-1)
        for(int alignment=0; alignment<2; alignment++ )
        {
            WritableRecordBuffer.Prunable ring = makeRing();

            // support pathological test cases of small subjects
            if(ring.available() < 2){ continue;}

            fill(ring, ring.available()-alignment);
            ring.prune((2 - alignment));
            int position = ring.getLength();
            shortNumber.rewind();
            ring.put(shortNumber);

            assertEquals(shortValue, ring.getShort(position));
        }


        int intValue = Integer.MIN_VALUE + 109;
        ByteBuffer intNumber = ByteBuffer.allocate(4);
        intNumber.putInt(intValue);

        // write an int at the ring boundary at alignment (0-3)
        for(int alignment=0; alignment<4; alignment++ )
        {
            WritableRecordBuffer.Prunable ring = makeRing();

            // support pathological test cases of small subjects
            if(ring.available() < 4){ continue;}

            fill(ring, ring.available()-alignment);
            ring.prune((4 - alignment));
            int position = ring.getLength();
            intNumber.rewind();
            ring.put(intNumber);

            assertEquals(intValue, ring.getInt(position));
        }

        long longValue = Long.MAX_VALUE - 18;
        ByteBuffer longNumber = ByteBuffer.allocate(8);
        longNumber.putLong(longValue);

        // write a long at the ring boundary at alignment (0-7)
        for(int alignment=0; alignment<8; alignment++ )
        {
            WritableRecordBuffer.Prunable ring = makeRing();

            // support pathological test cases of small subjects
            if(ring.available() < 8){ continue;}

            fill(ring, ring.available()-alignment);
            ring.prune((8 - alignment));
            int position = ring.getLength();
            longNumber.rewind();
            ring.put(longNumber);

            assertEquals(longValue, ring.getLong(position));
        }

    }

    @Test
    public void testWrappedCondition()
    {
        ///
        /// rerun the common test for a buffer that has wrapped
        ///
        WritableRecordBuffer.Prunable ring = makeRing();

        // fill halfway then prune
        int size = ring.available();
        RecordBufferUtility.fill(ring, size/2);
        ring.prune(size/2);

        // pattern fill and then test copies/views
        long PATTERN = 0x129A4FCD568ACD35L;
        RecordBufferUtility.fill(ring, PATTERN, 0);

        checkOutOfBounds(ring);

        if(size < 256)
        {
            checkGetByte(ring, PATTERN, 0);
            checkGetLong(ring, PATTERN, 0);
            checkGetBytes(ring, PATTERN, 0);
            checkCopyBytes(ring, PATTERN, 0);
            checkView(ring, PATTERN, 0);
            checkCopy(ring, PATTERN, 0);
        }
    }

    @Test
    public void testCapacityView()
    {
        ///
        /// Test the special case of full-capacity views
        ///
        WritableRecordBuffer.Prunable ring = makeRing();
        RecordBufferUtility.fill(ring, ring.available());

        {
            RecordBuffer view = ring.view(0, ring.getLength());
            assertEquals(view.getLength(), ring.getLength());
            assertEquals(view.getLength(), subjectSize());
        }

        // test at all wrapping points
        for(int i=0; i<ring.getLength(); i++)
        {
            ring.prune(1);
            ring.put(ByteBuffer.allocate(1));
            RecordBuffer view = ring.view(0, ring.getLength());
            assertEquals(view.getLength(), ring.getLength());
            assertEquals(view.getLength(), subjectSize());
        }
    }

    @Test
    public void testCapacityCopy()
    {
        ///
        /// Test the special case of full-capacity copies
        ///
        WritableRecordBuffer.Prunable ring = makeRing();
        RecordBufferUtility.fill(ring, ring.available());

        {
            RecordBuffer view = ring.copy(0, ring.getLength());
            assertEquals(view.getLength(), ring.getLength());
            assertEquals(view.getLength(), subjectSize());
        }

        // test at all wrapping points
        for(int i=0; i<ring.getLength(); i++)
        {
            ring.prune(1);
            ring.put(ByteBuffer.allocate(1));
            RecordBuffer view = ring.copy(0, ring.getLength());
            assertEquals(view.getLength(), ring.getLength());
            assertEquals(view.getLength(), subjectSize());
        }
    }


    @Test
    public void testCapacityBulkGet()
    {
        ///
        /// Test the special case of full-capacity copies
        ///
        WritableRecordBuffer.Prunable ring = makeRing();
        RecordBufferUtility.fill(ring, ring.available());

        {
            byte[] bytes = ring.getBytes(0, ring.getLength());
            assertEquals(bytes.length, ring.getLength());
            assertEquals(bytes.length, subjectSize());
        }

        // test at all wrapping points
        for(int i=0; i<ring.getLength(); i++)
        {
            ring.prune(1);
            ring.put(ByteBuffer.allocate(1));
            byte[] bytes = ring.getBytes(0, ring.getLength());
            assertEquals(bytes.length, ring.getLength());
            assertEquals(bytes.length, subjectSize());
        }
    }

    @Test
    public void testCapacityBulkTransfer()
    {
        ///
        /// Test the special case of full-capacity copies
        ///
        WritableRecordBuffer.Prunable ring = makeRing();
        RecordBufferUtility.fill(ring, ring.available());

        {
            byte[] xfer = new byte[ring.getLength()];
            ring.copyBytes(xfer, 0, 0, ring.getLength());
        }

        // test at all wrapping points
        for(int i=0; i<ring.getLength(); i++)
        {
            ring.prune(1);
            ring.put(ByteBuffer.allocate(1));
            byte[] xfer = new byte[ring.getLength()];
            ring.copyBytes(xfer, 0, 0, ring.getLength());
        }
    }

    /**
     * Tests WritableRecordBuffer.FastRingBuffer
     */
    @RunWith(value = Parameterized.class)
    public static class FastRingBufferTest extends RingBuffersTest
    {

        @Parameterized.Parameter
        public PowersOfTwo size;


        @Parameterized.Parameters(name = "FastRingBuffer[{0}]")
        public static List<Object[]> sizes()
        {
            List<Object[]> cases = new ArrayList<Object[]>(5);
            cases.add(new Object[]{PowersOfTwo._1});
            cases.add(new Object[]{PowersOfTwo._2});
            cases.add(new Object[]{PowersOfTwo._4});
            cases.add(new Object[]{PowersOfTwo._8});
            cases.add(new Object[]{PowersOfTwo._128});
            cases.add(new Object[]{PowersOfTwo._256});
            cases.add(new Object[]{PowersOfTwo._8192});
            return cases;
        }

        @Override
        int subjectSize()
        {
            return size.value();
        }

        @Override
        WritableRecordBuffer.Prunable makeRing()
        {
            return new RecordBuffers.FastRingBuffer(size);
        }

        @Test
        public void testFullView()
        {

        }

    }

    /**
     * Tests WritableRecordBuffer.RingBuffer
     */
    @RunWith(value = Parameterized.class)
    public static class RingBufferTest extends RingBuffersTest
    {
        @Parameterized.Parameter
        public int size;


        @Parameterized.Parameters(name = "RingBuffer[{0}]")
        public static List<Object[]> sizes()
        {
            List<Object[]> cases = new ArrayList<Object[]>(13);
            cases.add(new Object[]{0});
            cases.add(new Object[]{1});
            cases.add(new Object[]{2});
            cases.add(new Object[]{3});
            cases.add(new Object[]{4});
            cases.add(new Object[]{5});
            cases.add(new Object[]{6});
            cases.add(new Object[]{7});
            cases.add(new Object[]{8});
            cases.add(new Object[]{111});
            cases.add(new Object[]{128});
            cases.add(new Object[]{5000});
            cases.add(new Object[]{8192});
            return cases;
        }

        @Override
        int subjectSize()
        {
            return size;
        }

        @Override
        WritableRecordBuffer.Prunable makeRing()
        {
            return  new RecordBuffers.RingBuffer(size);
        }
    }


}
