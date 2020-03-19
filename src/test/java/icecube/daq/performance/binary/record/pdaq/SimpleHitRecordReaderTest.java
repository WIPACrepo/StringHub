package icecube.daq.performance.binary.record.pdaq;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.common.BufferContent;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

/**
 * Tests SimpleHitRecordReader.java
 */
public class SimpleHitRecordReaderTest
{

    SimpleHitRecordReader subject = SimpleHitRecordReader.instance;

    /**
     * A simple hit record
     * -----------------------------------------------------------------------------
     * | length [uint4]    |  type [uint4]=1   |           utc [uint8]             |
     * -----------------------------------------------------------------------------
     * | trig-type [uint4] | config-id [uint4] |  srcid [uint4] |  mbid [uint8]... |
     * -----------------------------------------------------------------------------
     * | ...               | trig-mode [uint4] |
     * -----------------------------------------
     */
    ByteBuffer dummy;


    int DUMMY_LENGTH = 38;
    int DUMMY_TYPE = 1;
    long DUMMY_UTC = Long.MAX_VALUE-1;
    int DUMMY_TRIGGER_TYPE = 2341235;
    int DUMMY_CONFIG_ID=23412;
    int DUMMY_SOURCE_ID=31324;
    long DUMMY_MBID = Long.MAX_VALUE;
    short DUMMY_TRIGGER_MODE = 32513;

    @Before
    public void setUp() throws Exception
    {
        dummy = ByteBuffer.allocate(DUMMY_LENGTH);
        dummy.putInt(DUMMY_LENGTH);
        dummy.putInt(DUMMY_TYPE);
        dummy.putLong(DUMMY_UTC);
        dummy.putInt(DUMMY_TRIGGER_TYPE);
        dummy.putInt(DUMMY_CONFIG_ID);
        dummy.putInt(DUMMY_SOURCE_ID);
        dummy.putLong(DUMMY_MBID);
        dummy.putShort(DUMMY_TRIGGER_MODE);
        dummy.flip();
    }

    @Test
    public void testByteBufferNoOffset()
    {
        assertEquals(DUMMY_LENGTH, subject.getLength(dummy));
        assertEquals(DUMMY_TYPE, subject.getTypeId(dummy));
        assertEquals(DUMMY_UTC, subject.getUTC(dummy));
        assertEquals(DUMMY_TRIGGER_TYPE, subject.getTriggerType(dummy));
        assertEquals(DUMMY_CONFIG_ID, subject.getConfigId(dummy));
        assertEquals(DUMMY_SOURCE_ID, subject.getSourceId(dummy));
        assertEquals(DUMMY_MBID, subject.getDOMID(dummy));
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
        assertEquals(DUMMY_TRIGGER_TYPE, subject.getTriggerType(buf, OFFSET));
        assertEquals(DUMMY_CONFIG_ID, subject.getConfigId(buf, OFFSET));
        assertEquals(DUMMY_SOURCE_ID, subject.getSourceId(buf, OFFSET));
        assertEquals(DUMMY_MBID, subject.getDOMID(buf, OFFSET));
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
        assertEquals(DUMMY_TRIGGER_TYPE, subject.getTriggerType(rb, 0));
        assertEquals(DUMMY_CONFIG_ID, subject.getConfigId(rb, 0));
        assertEquals(DUMMY_SOURCE_ID, subject.getSourceId(rb, 0));
        assertEquals(DUMMY_MBID, subject.getDOMID(rb, 0));
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
        assertEquals(DUMMY_TRIGGER_TYPE, subject.getTriggerType(rb, OFFSET));
        assertEquals(DUMMY_CONFIG_ID, subject.getConfigId(rb, OFFSET));
        assertEquals(DUMMY_SOURCE_ID, subject.getSourceId(rb, OFFSET));
        assertEquals(DUMMY_MBID, subject.getDOMID(rb, OFFSET));
        assertEquals(DUMMY_TRIGGER_MODE, subject.getTriggerMode(rb, OFFSET));
    }

}
