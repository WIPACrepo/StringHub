package icecube.daq.performance.binary.record.pdaq;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.common.BufferContent;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Tests DaqBufferRecordReader.java
 */
public class DaqBufferRecordReaderTest
{

    DaqBufferRecordReader subject = DaqBufferRecordReader.instance;

    /**
     * A daq buffer record
     * ----------------------------------------------------------------------
     * | length [uint4] |  type (uint4)  |         mbid[uint8]              |
     * ----------------------------------------------------------------------
     * |          padding [unit8]        |         utc[uint8]               |
     * ----------------------------------------------------------------------
     * |              ...                                                   |
     * ----------------------------------------------------------------------
     */
    ByteBuffer dummy;

    int DUMMY_LENGTH = 2435;
    int DUMMY_TYPE_ID = 65;
    long DUMMY_MBID = 23412341234L;
    long  DUMMY_PADDING = 1223413L;
    long DUMMY_UTC = 4325012459432145L;

    @Before
    public void setUp() throws Exception
    {
        dummy = ByteBuffer.allocate(DUMMY_LENGTH);
        dummy.putInt(DUMMY_LENGTH);
        dummy.putInt(DUMMY_TYPE_ID);
        dummy.putLong(DUMMY_MBID);
        dummy.putLong(DUMMY_PADDING);
        dummy.putLong(DUMMY_UTC);
        while(dummy.remaining() > 0)
        {
            dummy.put((byte) (Math.random() * 256));
        }
        dummy.flip();
    }

    @Test
    public void testByteBufferNoOffset()
    {
        assertEquals(DUMMY_LENGTH, subject.getLength(dummy));
        assertEquals(DUMMY_TYPE_ID, subject.getTypeId(dummy));
        assertEquals(DUMMY_MBID, subject.getDOMID(dummy));
        assertEquals(DUMMY_PADDING, subject.getPadding(dummy));
        assertEquals(DUMMY_UTC, subject.getUTC(dummy));
    }

    @Test
    public void testByteBufferWithOffset()
    {
        int OFFSET = 23423;
        ByteBuffer buf = ByteBuffer.allocate(DUMMY_LENGTH + OFFSET);
        buf.position(OFFSET);
        buf.put(dummy);
        buf.flip();

        assertEquals(DUMMY_LENGTH, subject.getLength(buf, OFFSET));
        assertEquals(DUMMY_TYPE_ID, subject.getTypeId(buf, OFFSET));
        assertEquals(DUMMY_MBID, subject.getDOMID(buf, OFFSET));
        assertEquals(DUMMY_PADDING, subject.getPadding(buf, OFFSET));
        assertEquals(DUMMY_UTC, subject.getUTC(buf, OFFSET));
    }

    @Test
    public void testRecordBufferNoOffset()
    {
        RecordBuffer rb =
                RecordBuffers.wrap(dummy, BufferContent.ZERO_TO_CAPACITY);

        assertEquals(DUMMY_LENGTH, subject.getLength(rb, 0));
        assertEquals(DUMMY_TYPE_ID, subject.getTypeId(rb, 0));
        assertEquals(DUMMY_MBID, subject.getDOMID(rb, 0));
        assertEquals(DUMMY_PADDING, subject.getPadding(rb, 0));
        assertEquals(DUMMY_UTC, subject.getUTC(rb, 0));
    }

    @Test
    public void testRecordBufferWithOffset()
    {
        int OFFSET = 23423;
        ByteBuffer buf = ByteBuffer.allocate(DUMMY_LENGTH + OFFSET);
        buf.position(OFFSET);
        buf.put(dummy);
        buf.flip();
        RecordBuffer rb =
                RecordBuffers.wrap(buf, BufferContent.ZERO_TO_CAPACITY);

        assertEquals(DUMMY_LENGTH, subject.getLength(rb, OFFSET));
        assertEquals(DUMMY_TYPE_ID, subject.getTypeId(rb, OFFSET));
        assertEquals(DUMMY_MBID, subject.getDOMID(rb, OFFSET));
        assertEquals(DUMMY_PADDING, subject.getPadding(rb, OFFSET));
        assertEquals(DUMMY_UTC, subject.getUTC(rb, OFFSET));
    }

}
