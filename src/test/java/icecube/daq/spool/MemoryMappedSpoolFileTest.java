package icecube.daq.spool;

import icecube.daq.performance.binary.buffer.CommonRecordBufferTest;
import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.test.BitUtility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests MemoryMappedSpoolFile.java
 */
public class MemoryMappedSpoolFileTest
{

    /**
     * Tests that subject meets the RecordBuffer contract.
     */
    @RunWith(value = Parameterized.class)
    public static class RecordBufferContractTest extends CommonRecordBufferTest
    {

        @Parameterized.Parameter(0)
        public int size;

        @Parameterized.Parameter(1)
        public int blockSize;

        @Parameterized.Parameters(name = "WritableByteBuffer[{0}]")
        public static List<Object[]> sizes()
        {
            List<Object[]> cases = new ArrayList<Object[]>(5);
            cases.add(new Object[]{0,0});
            cases.add(new Object[]{0,128});
            cases.add(new Object[]{1,5});
            cases.add(new Object[]{2,1024});
            cases.add(new Object[]{3, 333});
            cases.add(new Object[]{4, 4});
            cases.add(new Object[]{5, 100});
            cases.add(new Object[]{6, 128});
            cases.add(new Object[]{7, 33});
            cases.add(new Object[]{8, 1});
            cases.add(new Object[]{111, 50});
            cases.add(new Object[]{5000, 1111});
            cases.add(new Object[]{8192, 1024});
            return cases;
        }

        @Override
        public int subjectSize()
        {
            return size;
        }

        @Override
        public RecordBuffer createSubject(final short pattern, final int alignment)
        {
            try
            {
                File tempFile = createTempFile();
                MemoryMappedSpoolFile subject =
                        MemoryMappedSpoolFile.createMappedSpoolFile(tempFile,
                                blockSize);
                ByteBuffer tmp = ByteBuffer.allocate(size);
                BitUtility.fill(tmp, pattern, 2 - alignment);
                subject.write(tmp);
                return subject;
            }
            catch (IOException e)
            {
                throw new Error(e);
            }
        }

        @Override
        public RecordBuffer createSubject(final int pattern, final int alignment)
        {
            try
            {
                File tempFile = createTempFile();
                MemoryMappedSpoolFile subject =
                        MemoryMappedSpoolFile.createMappedSpoolFile(tempFile,
                                blockSize);
                ByteBuffer tmp = ByteBuffer.allocate(size);
                BitUtility.fill(tmp, pattern, 4-alignment);
                subject.write(tmp);
                return subject;
            }
            catch (IOException e)
            {
                throw new Error(e);
            }
        }

        @Override
        public RecordBuffer createSubject(final long pattern, final int alignment)
        {
            try
            {
                File tempFile = createTempFile();
                MemoryMappedSpoolFile subject =
                        MemoryMappedSpoolFile.createMappedSpoolFile(tempFile,
                                blockSize);
                ByteBuffer tmp = ByteBuffer.allocate(size);
                BitUtility.fill(tmp, pattern, 8-alignment);
                subject.write(tmp);
                return subject;
            }
            catch (IOException e)
            {
                throw new Error(e);
            }
        }


    }

    /**
     * Test the additional MemoryMappedSpoolFile behaviors
     */
    public static class SpoolFileTests
    {
        private MemoryMappedSpoolFile subject;
        private File subjectBackingFile;

        private final int blockSize = 1024*1024;

        @Before
        public void setUp() throws IOException
        {
            subjectBackingFile = createTempFile();
            subject =
                    MemoryMappedSpoolFile.createMappedSpoolFile(
                            subjectBackingFile, blockSize);
        }

        @Test
        public void testConstructor() throws IOException
        {
            try
            {
                File tempFile = createTempFile();
                MemoryMappedSpoolFile.createMappedSpoolFile(tempFile, 0);
                fail("Accepted a zero-block size");
            }
            catch (IllegalArgumentException e)
            {
                String expected = "Minimum block size is one";
               assertEquals(expected, e.getMessage());
            }
        }

        @Test
        public void testTruncate() throws IOException
        {
            //
            // tests that a non-written spool is truncated to
            // a zero-length file at close.
            //
            assertEquals(blockSize, subjectBackingFile.length());
            subject.close();
            assertEquals(0, subjectBackingFile.length());
        }

        @Test
        public void testTruncate2() throws IOException
        {
            //
            // tests that a half-block written spool is truncated to
            // the right length.
            //
            assertEquals(blockSize, subjectBackingFile.length());
            int halfBlock = blockSize / 2;
            subject.write(randomData(halfBlock));
            subject.close();
            assertEquals(halfBlock, subjectBackingFile.length());
        }

        @Test
        public void testTruncate3() throws IOException
        {
            //
            // tests that a multi-block written spool is truncated to
            // the right length.
            //
            assertEquals(blockSize, subjectBackingFile.length());
            subject.write(randomData(blockSize));
            subject.write(randomData(blockSize));
            int halfBlock = blockSize / 2;
            subject.write(randomData(halfBlock));
            subject.close();
            int expected = blockSize * 2 + halfBlock;
            assertEquals(expected, subjectBackingFile.length());
        }


        @Test
        public void testExpand() throws IOException
        {
            //
            // tests that a spool expands a block at a time when writes
            // exceed the current capacity.
            //
            assertEquals(blockSize, subjectBackingFile.length());

            subject.write(randomData(blockSize));
            assertEquals(blockSize, subjectBackingFile.length());

            subject.write(randomData(1));
            assertEquals(blockSize*2, subjectBackingFile.length());

            subject.write(randomData(blockSize-1));
            assertEquals(blockSize*2, subjectBackingFile.length());

            subject.write(randomData(1));
            assertEquals(blockSize*3, subjectBackingFile.length());

            subject.close();
            int expected = blockSize * 2 + 1;
            assertEquals(expected, subjectBackingFile.length());
        }

        @Test
        public void testUseAfterClose() throws IOException
        {
            //
            // tests that use after closing cases exception
            //
            assertEquals(blockSize, subjectBackingFile.length());

            subject.write(randomData(blockSize));
            assertEquals(blockSize, subjectBackingFile.length());
            subject.close();
            int expected = blockSize;
            assertEquals(expected, subjectBackingFile.length());

            try
            {
                subject.write(randomData(1));
                fail("Accepted a write after close");
            }
            catch (IOException ioe)
            {
                //desired
                assertEquals("File is closed", ioe.getMessage());
            }
        }

        @Test
        public void testFlush() throws IOException
        {
            //
            // Tests flush(), can only really exercise call
            // and rely on the contract of force.
            //
            assertEquals(blockSize, subjectBackingFile.length());

            subject.write(randomData(blockSize/2));
            subject.flush();
            subject.write(randomData(blockSize/2));
            subject.flush();
            subject.write(randomData(blockSize/2));
            subject.flush();
            subject.close();
            int expected = (blockSize/2) * 3;
            assertEquals(expected, subjectBackingFile.length());
        }

        @Test
        public void testPersisitence() throws IOException
        {
            //
            // tests that content written to the spool file makes
            // it to the file-system.
            //
            assertEquals(blockSize, subjectBackingFile.length());

            int halfBlock = blockSize / 2;
            ByteBuffer write1 = randomData(blockSize);
            ByteBuffer write2 = randomData(blockSize);
            ByteBuffer write3 = randomData(halfBlock);

            subject.write(write1);
            subject.write(write2);
            subject.write(write3);
            subject.close();
            int expected = blockSize * 2 + halfBlock;
            assertEquals(expected, subjectBackingFile.length());

            int totalSize = write1.capacity() + write2.capacity() +
                    write3.capacity();
            ByteBuffer acc = ByteBuffer.allocate(totalSize);
            acc.put((ByteBuffer) write1.rewind());
            acc.put((ByteBuffer) write2.rewind());
            acc.put((ByteBuffer) write3.rewind());

            FileInputStream fis = new FileInputStream(subjectBackingFile);
            for (int i = 0; i < totalSize; i++)
            {
                byte fromWrite = acc.get(i);
                byte fromFile = (byte) (fis.read() & 0xFF);
                assertEquals(fromWrite, fromFile);
            }

            fis.close();

        }

        @Test
        public void testToString() throws IOException
        {
            subject.write(randomData(1923));

            int capacity = blockSize;
            int limit=capacity;

            String expected = "MemoryMappedFileRecordBuffer(" +
                    "java.nio.DirectByteBuffer[pos=1923 " +
                    "lim=" + limit + " cap="+capacity+"])";
            assertEquals(expected, subject.toString());

            subject.close();

            expected = "MemoryMappedFileRecordBuffer(closed)";
            assertEquals(expected, subject.toString());
        }

    }

    static ByteBuffer randomData(int size)
    {
        ByteBuffer data = ByteBuffer.allocate(size);
        for(int i = 0; i< size; i++)
        {
            data.put((byte) ((Math.random() * 256) - 127));
        }
        data.flip();
        return data;
    }

    static File createTempFile() throws IOException
    {
        File tempFile = File.createTempFile("WritableStoreTest-", ".dat");
        tempFile.deleteOnExit();
        return tempFile;
    }


}
