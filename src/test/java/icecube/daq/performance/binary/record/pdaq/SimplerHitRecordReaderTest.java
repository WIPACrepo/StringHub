package icecube.daq.performance.binary.record.pdaq;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.common.BufferContent;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

/**
 * Tests SimplerHitRecordReader.java
 */
public class SimplerHitRecordReaderTest
{

    SimplerHitRecordReader subject = SimplerHitRecordReader.instance;

    /**
     * A simpler hit record
     * -----------------------------------------------------------------------------
     * | length [uint4]    |  type [uint4]=1   |           utc [uint8]             |
     * -----------------------------------------------------------------------------
     * | channelID [uint2] | trig-mode [uint2] |
     * -----------------------------------------
     */
    ByteBuffer dummy;


    int DUMMY_LENGTH = 20;
    int DUMMY_TYPE = 5;
    long DUMMY_UTC = Long.MAX_VALUE-1;
    short DUMMY_CHANNEL_ID = 55;
    short DUMMY_TRIGGER_MODE = 32513;

    @Before
    public void setUp() throws Exception
    {
        dummy = ByteBuffer.allocate(DUMMY_LENGTH);
        dummy.putInt(DUMMY_LENGTH);
        dummy.putInt(DUMMY_TYPE);
        dummy.putLong(DUMMY_UTC);
        dummy.putShort(DUMMY_CHANNEL_ID);
        dummy.putShort(DUMMY_TRIGGER_MODE);
        dummy.flip();
    }

    @Test
    public void testByteBufferNoOffset()
    {
        assertEquals(DUMMY_LENGTH, subject.getLength(dummy));
        assertEquals(DUMMY_TYPE, subject.getTypeId(dummy));
        assertEquals(DUMMY_UTC, subject.getUTC(dummy));
        assertEquals(DUMMY_CHANNEL_ID, subject.getChannelId(dummy));
        assertEquals(DUMMY_TRIGGER_MODE, subject.getTriggerMode(dummy));
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
        assertEquals(DUMMY_TYPE, subject.getTypeId(buf, OFFSET));
        assertEquals(DUMMY_UTC, subject.getUTC(buf, OFFSET));
        assertEquals(DUMMY_CHANNEL_ID, subject.getChannelId(buf, OFFSET));
        assertEquals(DUMMY_TRIGGER_MODE, subject.getTriggerMode(buf, OFFSET));
    }

    @Test
    public void testRecordBufferNoOffset()
    {
        RecordBuffer rb =
                RecordBuffers.wrap(dummy, BufferContent.ZERO_TO_CAPACITY);
        assertEquals(DUMMY_LENGTH, subject.getLength(rb, 0));
        assertEquals(DUMMY_TYPE, subject.getTypeId(rb, 0));
        assertEquals(DUMMY_UTC, subject.getUTC(rb, 0));
        assertEquals(DUMMY_CHANNEL_ID, subject.getChannelId(rb, 0));
        assertEquals(DUMMY_TRIGGER_MODE, subject.getTriggerMode(rb, 0));
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
        assertEquals(DUMMY_TYPE, subject.getTypeId(rb, OFFSET));
        assertEquals(DUMMY_UTC, subject.getUTC(rb, OFFSET));
        assertEquals(DUMMY_CHANNEL_ID, subject.getChannelId(rb, OFFSET));
        assertEquals(DUMMY_TRIGGER_MODE, subject.getTriggerMode(rb, OFFSET));
    }

}
