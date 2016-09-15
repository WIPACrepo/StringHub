package icecube.daq.performance.binary.record.pdaq;
import icecube.daq.payload.impl.DOMHitFactory;
import icecube.daq.payload.impl.DeltaCompressedHit;
import icecube.daq.payload.impl.SourceID;
import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.binary.test.TestData;
import icecube.daq.performance.common.BufferContent;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;


/**
 * Tests DeltaCompressedHitRecordReader.java
 */
public class DeltaCompressedHitRecordReaderTest
{

    DeltaCompressedHitRecordReader subject =
            DeltaCompressedHitRecordReader.instance;

    ByteBuffer dummy;
    int DUMMY_LENGTH = 241;
    int DUMMY_TYPE = 3;
    long DUMMY_MBID = Long.MAX_VALUE;
    long DUMMY_PADDING = Long.MIN_VALUE;
    long DUMMY_UTC = Long.MAX_VALUE-1;
    char DUMMY_BOMARK = 0x01;
    char DUMMY_VERSION = 0x37;
    char DUMMY_FQP = 0x47;
    long DUMMY_DOMCLK = Long.MAX_VALUE-3;
    int DUMMY_WORD1 = 0x8004080C;
    int DUMMY_WORD3 = 0x3A111C8A;
    byte[] DUMMY_PAYLOAD = new byte[DUMMY_LENGTH -54];
    {
        for (int i = 0; i < DUMMY_PAYLOAD.length; i++)
        {
            DUMMY_PAYLOAD[i] = (byte)i;
        }
    }

    @Before
    public void setUp() throws Exception
    {

        dummy = ByteBuffer.allocate(DUMMY_LENGTH);
        dummy.putInt(DUMMY_LENGTH);
        dummy.putInt(DUMMY_TYPE);
        dummy.putLong(DUMMY_MBID);
        dummy.putLong(DUMMY_PADDING);
        dummy.putLong(DUMMY_UTC);
        dummy.putChar(DUMMY_BOMARK);
        dummy.putChar(DUMMY_VERSION);
        dummy.putChar(DUMMY_FQP);
        dummy.putLong(DUMMY_DOMCLK);
        dummy.putInt(DUMMY_WORD1);
        dummy.putInt(DUMMY_WORD3);
        dummy.put(DUMMY_PAYLOAD);
        dummy.flip();
    }


    @Test
    public void testByteBufferNoOffset()
    {
        /// test reading the record from a byte buffer w/out offset
        assertEquals(DUMMY_LENGTH, subject.getLength(dummy));
        assertEquals(DUMMY_TYPE, subject.getTypeId(dummy));
        assertEquals(DUMMY_MBID, subject.getDOMId(dummy));
        assertEquals(DUMMY_PADDING, subject.getPadding(dummy));
        assertEquals(DUMMY_UTC, subject.getUTC(dummy));
        assertEquals(DUMMY_BOMARK, subject.getByteOrderMark(dummy));
        assertEquals(DUMMY_VERSION, subject.getVersion(dummy));
        assertEquals(DUMMY_FQP, subject.getFQP(dummy));
        assertEquals(DUMMY_DOMCLK, subject.getDOMClock(dummy));
        assertEquals(DUMMY_WORD1, subject.getWord1(dummy));
        assertEquals(DUMMY_WORD3, subject.getWord3(dummy));
        assertArrayEquals(DUMMY_PAYLOAD, subject.getHitData(dummy));

        assertEquals(0, subject.getLCMode(dummy));
        assertEquals(2, subject.getTriggerMode(dummy));

        /// test independence of buffer position
        dummy.position(dummy.limit());
        assertEquals(DUMMY_LENGTH, subject.getLength(dummy));
        assertEquals(DUMMY_TYPE, subject.getTypeId(dummy));
        assertEquals(DUMMY_MBID, subject.getDOMId(dummy));
        assertEquals(DUMMY_PADDING, subject.getPadding(dummy));
        assertEquals(DUMMY_UTC, subject.getUTC(dummy));
        assertEquals(DUMMY_BOMARK, subject.getByteOrderMark(dummy));
        assertEquals(DUMMY_VERSION, subject.getVersion(dummy));
        assertEquals(DUMMY_FQP, subject.getFQP(dummy));
        assertEquals(DUMMY_DOMCLK, subject.getDOMClock(dummy));
        assertEquals(DUMMY_WORD1, subject.getWord1(dummy));
        assertEquals(DUMMY_WORD3, subject.getWord3(dummy));
        assertArrayEquals(DUMMY_PAYLOAD, subject.getHitData(dummy));
    }

    @Test
    public void testByteBufferWithOffset()
    {
        /// test reading the record from a byte buffer where
        /// the content is offset
        int offset = 999;
        ByteBuffer larger = ByteBuffer.allocate(dummy.capacity() + offset);
        larger.position(offset);
        dummy.rewind();
        larger.put(dummy);

        assertEquals(DUMMY_LENGTH, subject.getLength(larger, offset));
        assertEquals(DUMMY_TYPE, subject.getTypeId(larger, offset));
        assertEquals(DUMMY_MBID, subject.getDOMId(larger, offset));
        assertEquals(DUMMY_PADDING, subject.getPadding(larger, offset));
        assertEquals(DUMMY_UTC, subject.getUTC(larger, offset));
        assertEquals(DUMMY_BOMARK, subject.getByteOrderMark(larger, offset));
        assertEquals(DUMMY_VERSION, subject.getVersion(larger, offset));
        assertEquals(DUMMY_FQP, subject.getFQP(larger, offset));
        assertEquals(DUMMY_DOMCLK, subject.getDOMClock(larger, offset));
        assertEquals(DUMMY_WORD1, subject.getWord1(larger, offset));
        assertEquals(DUMMY_WORD3, subject.getWord3(larger, offset));
        assertArrayEquals(DUMMY_PAYLOAD, subject.getHitData(larger, offset));

        assertEquals(0, subject.getLCMode(larger, offset));
        assertEquals(2, subject.getTriggerMode(larger, offset));

    }

    @Test
    public void testRecordBufferNoOffset()
    {
        /// test reading from a record buffer with an offset of 0

        RecordBuffer rb = RecordBuffers.wrap(dummy,
                BufferContent.ZERO_TO_CAPACITY);

        assertEquals(DUMMY_LENGTH, subject.getLength(rb, 0));
        assertEquals(DUMMY_TYPE, subject.getTypeId(rb, 0));
        assertEquals(DUMMY_MBID, subject.getDOMId(rb, 0));
        assertEquals(DUMMY_PADDING, subject.getPadding(rb, 0));
        assertEquals(DUMMY_UTC, subject.getUTC(rb, 0));
        assertEquals(DUMMY_BOMARK, subject.getByteOrderMark(rb, 0));
        assertEquals(DUMMY_VERSION, subject.getVersion(rb, 0));
        assertEquals(DUMMY_FQP, subject.getFQP(rb, 0));
        assertEquals(DUMMY_DOMCLK, subject.getDOMClock(rb, 0));
        assertEquals(DUMMY_WORD1, subject.getWord1(rb, 0));
        assertEquals(DUMMY_WORD3, subject.getWord3(rb, 0));
        assertArrayEquals(DUMMY_PAYLOAD, subject.getHitData(rb, 0));

        assertEquals(0, subject.getLCMode(rb, 0));
        assertEquals(2, subject.getTriggerMode(rb, 0));
    }

    @Test
    public void testRecordBufferWithOffset()
    {
        /// test reading from a record buffer with an offset

        int offset = 999;
        ByteBuffer larger = ByteBuffer.allocate(dummy.capacity() + offset);
        larger.position(offset);
        dummy.rewind();
        larger.put(dummy);

        RecordBuffer rb = RecordBuffers.wrap(larger,
                BufferContent.ZERO_TO_CAPACITY);

        assertEquals(DUMMY_LENGTH, subject.getLength(rb, offset));
        assertEquals(DUMMY_TYPE, subject.getTypeId(rb, offset));
        assertEquals(DUMMY_MBID, subject.getDOMId(rb, offset));
        assertEquals(DUMMY_PADDING, subject.getPadding(rb, offset));
        assertEquals(DUMMY_UTC, subject.getUTC(rb, offset));
        assertEquals(DUMMY_BOMARK, subject.getByteOrderMark(rb, offset));
        assertEquals(DUMMY_VERSION, subject.getVersion(rb, offset));
        assertEquals(DUMMY_FQP, subject.getFQP(rb, offset));
        assertEquals(DUMMY_DOMCLK, subject.getDOMClock(rb, offset));
        assertEquals(DUMMY_WORD1, subject.getWord1(rb, offset));
        assertEquals(DUMMY_WORD3, subject.getWord3(rb, offset));
        assertArrayEquals(DUMMY_PAYLOAD, subject.getHitData(rb, offset));

        assertEquals(0, subject.getLCMode(rb, offset));
        assertEquals(2, subject.getTriggerMode(rb, offset));
    }

    @Test
    public void testGetTriggerMode()
    {
        assertEquals(4, subject.getTriggerMode(0x1000 << 18));
        assertEquals(3, subject.getTriggerMode(0x0010 << 18));
        assertEquals(2, subject.getTriggerMode(0x0003 << 18));
        assertEquals(1, subject.getTriggerMode(0x0004 << 18));
        assertEquals(0, subject.getTriggerMode(0x0000));

    }


    @Test
    public void testProductionData() throws Exception
    {

        ///
        /// Compare DeltaCompressedHitRecordReader against DeltaCompressedHit
        /// using production data

        ByteBuffer productionData =
                TestData.DELTA_COMPRESSED.toByteBuffer();

        RecordBuffer rb = RecordBuffers.wrap(productionData,
                BufferContent.ZERO_TO_CAPACITY);

        for (ByteBuffer buffer : rb.eachBuffer(subject))
        {
            DeltaCompressedHit standard =
                    (DeltaCompressedHit) DOMHitFactory.getHit(new SourceID(99),
                            buffer, 0);
            buffer.rewind();

            assertEquals(standard.length(), subject.getLength(buffer));
            assertEquals(3, subject.getTypeId(buffer));
            assertEquals(standard.getDomId(), subject.getDOMId(buffer));
//            assertEquals(subject.getPadding(buffer), ); //not exposed
            assertEquals(standard.getUTCTime(), subject.getUTC(buffer));
            assertEquals(0x01, subject.getByteOrderMark(buffer));
            assertEquals(2, subject.getVersion(buffer));
//            assertEquals(subject.getFQP(buffer), ); //not exposed
//            assertEquals(subject.getDOMClock(buffer), ); //not exposed
//            assertEquals(subject.getWord1(buffer), ); //not exposed
//            assertEquals(subject.getWord3(buffer), ); //not exposed
            assertArrayEquals(standard.getCompressedData(),
                    subject.getHitData(buffer));

            assertEquals(standard.getLocalCoincidenceMode(),
                    subject.getLCMode(buffer));
            assertEquals(standard.getTriggerMode(),
                    subject.getTriggerMode(buffer));
        }
    }

}
