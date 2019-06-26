package icecube.daq.performance.binary.record.pdaq;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.common.BufferContent;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

/**
 * Tests LengthPrependedRecordReader.java
 */
public class LengthPrependedRecordReaderTest
{

    LengthPrependedRecordReader subject = LengthPrependedRecordReader.instance;

    /**
     * A length-prepended record
     * ------------------------------
     * | length [uint4]    |  ...   |
     * -----------------------------
     */
    ByteBuffer dummy;

    int DUMMY_LENGTH = 2435;

    @Before
    public void setUp() throws Exception
    {
        dummy = ByteBuffer.allocate(DUMMY_LENGTH);
        dummy.putInt(DUMMY_LENGTH);
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
    }

    @Test
    public void testRecordBufferNoOffset()
    {
        RecordBuffer rb =
                RecordBuffers.wrap(dummy, BufferContent.ZERO_TO_CAPACITY);
        assertEquals(DUMMY_LENGTH, subject.getLength(rb, 0));
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
    }

}
