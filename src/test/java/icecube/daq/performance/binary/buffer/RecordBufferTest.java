package icecube.daq.performance.binary.buffer;
import icecube.daq.performance.binary.buffer.test.DummyRecord;
import icecube.daq.performance.binary.record.RecordReader;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests RecordBuffer.java
 */
public class RecordBufferTest
{

    final int NUM_RECORDS = 10000;

    class Record
    {
        final byte[] data;

        Record(final byte[] data)
        {
            this.data = data;
        }

        void assertSameContent(RecordBuffer rb)
        {
            assertEquals(data.length, rb.getLength());
            assertArrayEquals(data, rb.getBytes(0, data.length));
        }

        void assertSameContent(ByteBuffer bb)
        {
            assertEquals(0, bb.position());
            assertEquals(data.length, bb.capacity());
            assertEquals(data.length, bb.limit());
            assertArrayEquals(data, bb.array());
        }
    }

    List<Record> testRecords = new ArrayList<>(NUM_RECORDS);

    RecordBuffer recordBuffer;
    RecordReader recordReader;

    RecordBuffer corrupted;  // a record buffer with zero-ed out data
                             // at the end

    @Before
    public void setUp() throws Exception
    {
        testRecords = new ArrayList<>(NUM_RECORDS);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        long utc = 12312123;
        for(int i = 0; i<NUM_RECORDS; i++)
        {
            utc += Math.random() * 12312;
            ByteBuffer buffer = DummyRecord.generateDummyBuffer(i, utc);
            byte[] rec = buffer.array();
            bos.write(rec);

            testRecords.add(new Record(rec));
        }


        byte[] original = bos.toByteArray();
        recordBuffer = RecordBuffers.wrap(original);

        byte[] truncated = new byte[original.length + 8192];
        System.arraycopy(original, 0, truncated, 0, original.length);
        corrupted = RecordBuffers.wrap(truncated);

        recordReader = DummyRecord.instance;
    }



    @Test
    public void TestRecordIteration()
    {
        ///
        /// view iteration
        ///

        int count = 0;
        for(RecordBuffer buffer: recordBuffer.eachRecord(recordReader))
        {
            testRecords.get(count).assertSameContent(buffer);
            count++;
        }

        try
        {
            for(RecordBuffer buffer: corrupted.eachRecord(recordReader))
            {
                //nothing to do
            }
            fail("iteration should fail");
        }
        catch (Throwable e)
        {
            String expected = "Zero-length record at index: " +
                    recordBuffer.getLength();
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void TestRecordCopyIteration()
    {
        ///
        /// copy iteration
        ///

        int count = 0;
        for(RecordBuffer buffer: recordBuffer.eachCopy(recordReader))
        {
            testRecords.get(count).assertSameContent(buffer);
            count++;
        }

        try
        {
            for(RecordBuffer buffer: corrupted.eachCopy(recordReader))
            {
                //nothing to do
            }
            fail("iteration should fail");
        }
        catch (Throwable e)
        {
            String expected = "Zero-length record at index: " +
                    recordBuffer.getLength();
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void TestBufferIteration()
    {
        ///
        /// ByteBuffer iteration
        ///

        int count = 0;
        for(ByteBuffer buffer: recordBuffer.eachBuffer(recordReader))
        {
            testRecords.get(count).assertSameContent(buffer);
            count++;
        }

        try
        {
            for(ByteBuffer buffer: corrupted.eachBuffer(recordReader))
            {
                //nothing to do
            }
            fail("iteration should fail");
        }
        catch (Throwable e)
        {
            String expected = "Zero-length record at index: " +
                    recordBuffer.getLength();
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void TestIndexIteration()
    {
        ///
        /// Index iteration
        ///
        int count = 0;
        int index = 0;
        for(Integer idx: recordBuffer.eachIndex(recordReader))
        {
            // predicted index
            assertEquals(index, idx.intValue());

            Record master = testRecords.get(count);

            // record length matches master
            int recordLength = recordReader.getLength(recordBuffer, index);
            assertEquals(master.data.length, recordLength);

            // record content matches master
            master.assertSameContent(recordBuffer.view(idx, recordLength));

            index+=recordLength;
            count++;
        }

        try
        {
            for(Integer idx: corrupted.eachIndex(recordReader))
            {
                //nothing to do
            }
            fail("iteration should fail");
        }
        catch (Throwable e)
        {
            String expected = "Zero-length record at index: " +
                    recordBuffer.getLength();
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void testMemoryModes()
    {
        ///
        /// Tests RecordBuffer.MemoryMode
        ///

        // MemoryMode.COPY
        {
            byte[] originalData = new byte[21353];
            for (int i = 0; i < originalData.length; i++)
            {
                originalData[i] = (byte) (Math.random() * 255);
            }

            byte[] volatileCopy = new byte[originalData.length];
            System.arraycopy(originalData, 0, volatileCopy, 0, originalData.length);


            RecordBuffer rb = RecordBuffers.wrap(volatileCopy);
            RecordBuffer copy =
                    RecordBuffer.MemoryMode.COPY.extractData(rb, 0,
                            volatileCopy.length);

            // eradicate the volatile
            for (int i = 0; i < volatileCopy.length; i++)
            {
                volatileCopy[i] = (byte) (Math.random() * 255);
            }

            // copy should still match original
            assertSameContent(copy, originalData);

        }


        // MemoryMode.SHARED_VIEW
        {
            byte[] originalData = new byte[21353];
            for (int i = 0; i < originalData.length; i++)
            {
                originalData[i] = (byte) (Math.random() * 255);
            }

            byte[] volatileCopy = new byte[originalData.length];
            System.arraycopy(originalData, 0, volatileCopy, 0, originalData.length);


            RecordBuffer rb = RecordBuffers.wrap(volatileCopy);
            RecordBuffer shared =
                    RecordBuffer.MemoryMode.SHARED_VIEW.extractData(rb, 0,
                            volatileCopy.length);

            // eradicate the volatile
            for (int i = 0; i < volatileCopy.length; i++)
            {
                volatileCopy[i] = (byte) (Math.random() * 255);
            }

            // shared should not match original
            assertDifferentContent(shared, originalData);

            // shared should still match volatile
            assertSameContent(shared, volatileCopy);

            // eradicate the volatile again
            for (int i = 0; i < volatileCopy.length; i++)
            {
                volatileCopy[i] = (byte) (Math.random() * 255);
            }

            // shared should still match volatile
            assertSameContent(shared, volatileCopy);

            // shared should not match original
            assertDifferentContent(shared, originalData);


            //restore the original
            System.arraycopy(originalData, 0, volatileCopy, 0, originalData.length);

            // shared should  match volatile and original
            assertSameContent(shared, volatileCopy);
            assertSameContent(shared, originalData);
        }

    }



    private void assertSameContent(RecordBuffer rb, byte[] content)
    {
        assertEquals(content.length, rb.getLength());
        for (int i = 0; i < content.length; i++)
        {
            assertEquals(content[i], rb.getByte(i));
        }
    }

    private void assertDifferentContent(RecordBuffer rb, byte[] content)
    {
        assertEquals(content.length, rb.getLength());

        boolean different = false;
        for (int i = 0; i < content.length; i++)
        {
            if(content[i] != rb.getByte(i))
            {
                different=true;
                break;
            }
        }
        assertTrue(different);
    }


    @Test
    public void testBoundsCheck()
    {
        ///
        /// Tests that the bounds checks work as expected.
        ///

//        int LENGTH = 99876;
        int LENGTH = 0;
        int LAST_IDX = Math.max(LENGTH-1, 0);
        WritableRecordBuffer lengthMock = new WritableRecordBuffer()
        {
            @Override
            public int getLength()
            {
                return LENGTH;
            }

            @Override
            public void put(final ByteBuffer data)
            {
                throw new Error("Not Implemented");
            }

            @Override
            public int available()
            {
                return LENGTH;
            }

            @Override
            public byte getByte(final int index)
            {
                throw new Error("Not Implemented");
            }

            @Override
            public short getShort(final int index)
            {
                throw new Error("Not Implemented");
            }

            @Override
            public int getInt(final int index)
            {
                throw new Error("Not Implemented");
            }

            @Override
            public long getLong(final int index)
            {
                throw new Error("Not Implemented");
            }

            @Override
            public byte[] getBytes(final int start, final int length)
            {
                throw new Error("Not Implemented");
            }

            @Override
            public void copyBytes(final byte[] dest, final int offset, final int start, final int length)
            {
                throw new Error("Not Implemented");
            }

            @Override
            public RecordBuffer view(final int start, final int length)
            {
                throw new Error("Not Implemented");
            }

            @Override
            public RecordBuffer copy(final int start, final int length)
            {
                throw new Error("Not Implemented");
            }
        };

        RecordBuffer.BoundsCheck.checkBounds(0, LENGTH, lengthMock);

        try
        {
            RecordBuffer.BoundsCheck.checkBounds(0, LENGTH+1, lengthMock);
            fail("bounds vioaltion passed");
        }
        catch (IndexOutOfBoundsException ioobe)
        {
            String expected = "[0 - "+(LENGTH+1)+"] of [0 - "+LAST_IDX+"]";
            assertEquals(expected, ioobe.getMessage());
        }

        try
        {
            RecordBuffer.BoundsCheck.checkBounds(LENGTH, LENGTH+1, lengthMock);
            fail("bounds vioaltion passed");
        }
        catch (IndexOutOfBoundsException ioobe)
        {
            String expected = "["+LENGTH+" - "+(LENGTH+LENGTH+1)+"] of [0 - "+LAST_IDX+"]";
            assertEquals(expected, ioobe.getMessage());
        }

        try
        {
            RecordBuffer.BoundsCheck.checkBounds(-9, LENGTH, lengthMock);
            fail("bounds vioaltion passed");
        }
        catch (IndexOutOfBoundsException ioobe)
        {
            String expected = "[-9 - "+(-9+LENGTH)+"] of [0 - "+LAST_IDX+"]";
            assertEquals(expected, ioobe.getMessage());
        }

        try
        {
            RecordBuffer.BoundsCheck.checkBounds(0, -13, lengthMock);
            fail("bounds vioaltion passed");
        }
        catch (IndexOutOfBoundsException ioobe)
        {
            String expected = "[0 - -13] of [0 - "+LAST_IDX+"]";
            assertEquals(expected, ioobe.getMessage());
        }


        RecordBuffer.BoundsCheck.ensureReadAvailable(LENGTH, lengthMock);

        try
        {
            RecordBuffer.BoundsCheck.ensureReadAvailable(LENGTH+1, lengthMock);
            fail("bounds vioaltion passed");
        }
        catch (BufferUnderflowException bue)
        {
           //desired
        }

        RecordBuffer.BoundsCheck.ensureWriteAvailable(LENGTH, lengthMock);

        try
        {
            RecordBuffer.BoundsCheck.ensureWriteAvailable(LENGTH+1, lengthMock);
            fail("bounds vioaltion passed");
        }
        catch (BufferOverflowException boe)
        {
            //desired
        }

    }

}
