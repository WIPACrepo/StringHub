package icecube.daq.performance.binary.buffer;


import icecube.daq.performance.binary.buffer.test.BitUtility;
import icecube.daq.performance.binary.buffer.test.RecordBufferUtility;
import icecube.daq.performance.common.BufferContent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Base tests for WritableRecordBuffers implementations.
 */
public abstract class CommonWritableRecordBufferTest
        extends CommonRecordBufferTest
{

    // subclasses implement this provide an implementation
    // to be tested
    abstract WritableRecordBuffer createSubject();


    @Override
    public RecordBuffer createSubject(final short pattern, final int alignment)
    {
        WritableRecordBuffer rb  = createSubject();

        RecordBufferUtility.fill(rb, pattern, 2 - alignment);
        return rb;
    }

    @Override
    public RecordBuffer createSubject(final int pattern, final int alignment)
    {
        WritableRecordBuffer rb  = createSubject();

        RecordBufferUtility.fill(rb, pattern, 4 - alignment);
        return rb;
    }

    @Override
    public RecordBuffer createSubject(final long pattern, final int alignment)
    {
        WritableRecordBuffer rb  = createSubject();

        RecordBufferUtility.fill(rb, pattern, 8 - alignment);
        return rb;
    }


    @Test
    public void testFillBytes()
    {
        WritableRecordBuffer subject = createSubject();
        assertEquals(subject.getLength(), 0);

        int size = subject.available();

        ByteBuffer transfer = ByteBuffer.allocate(1);
        for (int i=0; i<size; i++)
        {
            transfer.clear();
            transfer.put((byte) i);
            transfer.flip();
            subject.put(transfer);
        }

        assertEquals(size, subject.getLength());
        assertEquals(0, subject.available());

        for (int i=0; i<size; i++)
        {
            byte roundTrip = subject.getByte(i);
            assertEquals((byte)i, roundTrip);
        }
    }

    @Test
    public void testFillShorts()
    {
        WritableRecordBuffer subject = createSubject();
        assertEquals(subject.getLength(), 0);

        final int size = subject.available();
        final int notUsable = size%2;
        final int usable = size - notUsable;

        ByteBuffer transfer = ByteBuffer.allocate(2);
        for (int i=0; i<usable; i+=2)
        {
            transfer.clear();
            transfer.putShort((short) i);
            transfer.flip();
            subject.put(transfer);
        }

        assertEquals(usable, subject.getLength());
        assertEquals(notUsable, subject.available());

        for (int i=0; i<usable; i+=2)
        {
            short roundTrip = subject.getShort(i);
            assertEquals((short)i, roundTrip);
        }
    }

    @Test
    public void testFillInts()
    {
        WritableRecordBuffer subject = createSubject();
        assertEquals(subject.getLength(), 0);

        final int size = subject.available();
        final int notUsable = size%8;
        final int usable = size - notUsable;

        ByteBuffer transfer = ByteBuffer.allocate(4);
        for (int i=0; i<usable; i+=4)
        {
            transfer.clear();
            transfer.putInt(i);
            transfer.flip();
            subject.put(transfer);
        }

        assertEquals(usable, subject.getLength());
        assertEquals(notUsable, subject.available());

        for (int i=0; i<usable; i+=4)
        {
            int roundTrip = subject.getInt(i);
            assertEquals(i, roundTrip);
        }
    }

    @Test
    public void testFillLongs()
    {
        WritableRecordBuffer subject =createSubject();
        assertEquals(subject.getLength(), 0);

        final int size = subject.available();
        final int notUsable = size%8;
        final int usable = size - notUsable;

        ByteBuffer transfer = ByteBuffer.allocate(8);
        for (int i=0; i<usable; i+=8)
        {
            transfer.clear();
            transfer.putLong(i);
            transfer.flip();
            subject.put(transfer);
        }

        assertEquals(usable, subject.getLength());
        assertEquals(notUsable, subject.available());

        for (int i=0; i<usable; i+=8)
        {
            long roundTrip = subject.getLong(i);
            assertEquals(i, roundTrip);
        }
    }

    @Test
    public void testFillBulk()
    {
        WritableRecordBuffer subject = createSubject();
        assertEquals(subject.getLength(), 0);

        int size = subject.available();

        final int msgSize = Math.min(8192, size);

        byte[] msg = new byte[msgSize];
        for (int i = 0; i < msg.length; i++)
        {
            msg[i] = (byte) (Math.random() * 128);
        }

        subject.put(ByteBuffer.wrap(msg));


        byte[] copy = subject.getBytes(0, msgSize);
        assertArrayEquals(msg, copy);

    }


    @Test
    public void testOverflow()
    {
        //
        // Write more data than available
        //

        WritableRecordBuffer subject = createSubject();


        // fill to one short (or full for 1 byte buffer)
        RecordBufferUtility.fill(subject, subject.available() - Math.min(1, subject.available()));

        try
        {
            subject.put(ByteBuffer.wrap(new byte[2]));
            fail("Overflow permitted");
        }
        catch (BufferOverflowException boe)
        {
            //desired
        }
    }

    /**
     * Tests WritableRecordBuffer.WritableByteBuffer
     */
    @RunWith(value = Parameterized.class)
    public static class WritableByteBufferTest extends CommonWritableRecordBufferTest
    {

        @Parameterized.Parameter
        public int size;


        @Parameterized.Parameters(name = "WritableByteBuffer[{0}]")
        public static List<Object[]> sizes()
        {
            List<Object[]> cases = new ArrayList<Object[]>(5);
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
            cases.add(new Object[]{5000});
            cases.add(new Object[]{8192});
            return cases;
        }

        @Override
        public int subjectSize()
        {
            return size;
        }

        @Override
        WritableRecordBuffer createSubject()
        {
            return new RecordBuffers.WritableByteBuffer(size);
        }

        @Test
        public void testToString()
        {
            int size = subjectSize();
            String expected="WritableByteBuffer(" +
                    "java.nio.HeapByteBuffer[pos=0 lim="+size+" cap="+size+"])";
            WritableRecordBuffer subject = createSubject();
            String str = subject.toString();
            assertEquals(expected, str);
        }

    }


}
