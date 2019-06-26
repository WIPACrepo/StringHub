package icecube.daq.performance.binary.buffer;

import org.junit.Test;

import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static icecube.daq.performance.binary.buffer.test.BitUtility.*;
import static icecube.daq.performance.binary.buffer.test.BitUtility.getByteAtPosition;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;


/**
 * Common test methods based on a pattern-filled record buffer.
 *
 * Tests primitive reads of short, int and long values from a buffer
 * filled with a pattern value. Tests endian and edge related
 * conditions for all combinations of view/copy operations.
 *
 * Tests out-of-bounds behavior.
 */
public abstract class CommonRecordBufferTest
{

    // Subclasses implement these methods to provide an implementation
    // to be tested. Should return a buffer filled with the pattern value
    // starting at the alignment byte.
    //
    // Common view/copy tests will be run on the filled buffer at all
    // alignment combinations.
    //
    // subclasses choose the buffer size, often parameterized for many
    // sizes. Since the copy/view test iterations are O(n^2) the
    // size is checked and tests skipped if size >256 bytes;
    public abstract int subjectSize();
    public abstract RecordBuffer createSubject(short pattern, int alignment);
    public abstract RecordBuffer createSubject(int pattern, int alignment);
    public abstract RecordBuffer createSubject(long pattern, int alignment);

    // data primitives to test.
    private long LONG_PATTERN   =         0xABCDEF0102030405L;
    private int INT_PATTERN     =         0xABCDEF01;
    private short SHORT_PATTERN = (short) 0xABCD;


    @Test
    public void testSegmentPermute()
    {
        //
        // tests use an iterator in place of nested for loops
        // to ensure all permutations are covered and reduce
        // duplicated loop code.
        //
        // This tests the iterator for equivalency.
        //
        int size = subjectSize();
        if(size > 256)
        {
            System.out.println("Skipping testSegmentPermute() testing for size "
                    + size + " ... TO SAVE TIME!");
            return;
        }

        List<Segment> loop = new LinkedList<>();
        for(int startPos =0; (startPos<size || startPos==0); startPos++)
        {
            for(int length=0; length<=size-startPos; length++)
            {
                loop.add(new Segment(startPos, length));
            }
        }

        List<Segment> iter = new LinkedList<>();
        for(Segment segment : Segment.permute(size))
        {
            iter.add(segment);
        }

        assertEquals(loop.size(), iter.size());
        for(int i=0; i<loop.size(); i++)
        {
            Segment loopSegment = loop.get(i);
            Segment iterSegment = iter.get(i);
            assertEquals(loopSegment.startPos, iterSegment.startPos);
            assertEquals(loopSegment.length, iterSegment.length);
        }
    }

    @Test
    public void testGetLength()
    {
        int size = subjectSize();
        RecordBuffer subject = createSubject(SHORT_PATTERN, 0);
        assertEquals(size, subject.getLength());
    }

    @Test
    public void testGetByte()
    {
        //
        // Tests:
        // RecordBuffer.getByte(index pos)
        //
        // against buffers at all alignment options
        for(int alignment=0; alignment<2; alignment++)
        {
            checkGetByte(createSubject(SHORT_PATTERN, alignment),
                    SHORT_PATTERN, getStartByte(2, alignment));
        }
        for(int alignment=0; alignment<4; alignment++)
        {
            checkGetByte(createSubject(INT_PATTERN, alignment),
                    INT_PATTERN, getStartByte(4, alignment));
        }
        for(int alignment=0; alignment<8; alignment++)
        {
            checkGetByte(createSubject(LONG_PATTERN, alignment),
                    LONG_PATTERN, getStartByte(8, alignment));
        }
    }

    @Test
    public void testGetShort()
    {
        //
        // Tests:
        // RecordBuffer.getShort(index pos)
        //
        // against buffers at all alignment options
        for(int alignment=0; alignment<2; alignment++)
        {
            checkGetShort(createSubject(SHORT_PATTERN, alignment),
                    SHORT_PATTERN, getStartByte(2, alignment));
        }
    }

    @Test
    public void testGetInt()
    {
        //
        // Tests:
        // RecordBuffer.getInt(index pos)
        //
        // against buffers at all alignment options
        for(int alignment=0; alignment<4; alignment++)
        {
            checkGetInt(createSubject(INT_PATTERN, alignment),
                    INT_PATTERN, getStartByte(4, alignment));
        }
    }

    @Test
    public void testGetLong()
    {
        //
        // Tests:
        // RecordBuffer.getLong(index pos)
        //
        // against buffers at all alignment options
        for(int alignment=0; alignment<8; alignment++)
        {
            checkGetLong(createSubject(LONG_PATTERN, alignment),
                    LONG_PATTERN, getStartByte(8, alignment));
        }
    }

    @Test
    public void testGetBytes()
    {
        //
        // Tests:
        // RecordBuffer.getBytes(int start, int length)
        //
        // against buffers at all alignment options
        int size = subjectSize();
        if(size > 256)
        {
            System.out.println("Skipping testGetBytes() testing for size "
                    + size + " ... TO SAVE TIME!");
            return;
        }

        for(int alignment=0; alignment<2; alignment++)
        {
            checkGetBytes(createSubject(SHORT_PATTERN, alignment),
                    SHORT_PATTERN, getStartByte(2, alignment));
        }
        for(int alignment=0; alignment<4; alignment++)
        {
            checkGetBytes(createSubject(INT_PATTERN, alignment),
                    INT_PATTERN, getStartByte(4, alignment));
        }
        for(int alignment=0; alignment<8; alignment++)
        {
            checkGetBytes(createSubject(LONG_PATTERN, alignment),
                    LONG_PATTERN, getStartByte(8, alignment));
        }
    }

    @Test
    public void testCopyBytes()
    {
        //
        // Tests:
        // RecordBuffer.copyBytes(byte[] dest, int offset, int start, int length)
        //
        // against buffers at all alignment options
        int size = subjectSize();
        if(size > 256)
        {
            System.out.println("Skipping testCopyBytes() testing for size "
                    + size + " ... TO SAVE TIME!");
            return;
        }

        for(int alignment=0; alignment<2; alignment++)
        {
            checkCopyBytes(createSubject(SHORT_PATTERN, alignment),
                    SHORT_PATTERN, getStartByte(2, alignment));
        }
        for(int alignment=0; alignment<4; alignment++)
        {
            checkCopyBytes(createSubject(INT_PATTERN, alignment),
                    INT_PATTERN, getStartByte(4, alignment));
        }
        for(int alignment=0; alignment<8; alignment++)
        {
            checkCopyBytes(createSubject(LONG_PATTERN, alignment),
                    LONG_PATTERN, getStartByte(8, alignment));
        }
    }

    @Test
    public void testView()
    {
        //
        // Tests:
        // RecordBuffer.view(int start, int length)
        //
        // against buffers at all alignment options
        int size = subjectSize();
        if(size > 256)
        {
            System.out.println("Skipping testView() testing for size "
                    + size + " ... TO SAVE TIME!");
            return;
        }

        for(int alignment=0; alignment<2; alignment++)
        {
            checkView(createSubject(SHORT_PATTERN, alignment),
                    SHORT_PATTERN, getStartByte(2, alignment));
        }
        for(int alignment=0; alignment<4; alignment++)
        {
            checkView(createSubject(INT_PATTERN, alignment),
                    INT_PATTERN, getStartByte(4, alignment));
        }
        for(int alignment=0; alignment<8; alignment++)
        {
            checkView(createSubject(LONG_PATTERN, alignment),
                    LONG_PATTERN, getStartByte(8, alignment));
        }
    }

    @Test
    public void testCopy()
    {
        //
        // Tests:
        // RecordBuffer.copy(int start, int length)
        //
        // against buffers at all alignment options
        int size = subjectSize();
        if(size > 256)
        {
            System.out.println("Skipping testView() testing for size "
                    + size + " ... TO SAVE TIME!");
            return;
        }

        for(int alignment=0; alignment<2; alignment++)
        {
            checkCopy(createSubject(SHORT_PATTERN, alignment),
                    SHORT_PATTERN, getStartByte(2, alignment));
        }
        for(int alignment=0; alignment<4; alignment++)
        {
            checkCopy(createSubject(INT_PATTERN, alignment),
                    INT_PATTERN, getStartByte(4, alignment));
        }
        for(int alignment=0; alignment<8; alignment++)
        {
            checkCopy(createSubject(LONG_PATTERN, alignment),
                    LONG_PATTERN, getStartByte(8, alignment));
        }
    }

    @Test
    public void testOutOfBounds()
    {
        //
        // Tests:
        // Out-of-bounds behavior
        //
        // against buffers at all alignment options
        for(int alignment=0; alignment<2; alignment++)
        {
            checkOutOfBounds(createSubject(SHORT_PATTERN, alignment));
        }
        for(int alignment=0; alignment<4; alignment++)
        {
            checkOutOfBounds(createSubject(INT_PATTERN, alignment));
        }
        for(int alignment=0; alignment<8; alignment++)
        {
            checkOutOfBounds(createSubject(LONG_PATTERN, alignment));
        }
    }

    protected static void checkGetByte(RecordBuffer buffer, short pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.getByte(index pos)
        //
        int patternByte = patternStartByte;
        for(int i=0; i<buffer.getLength(); i++)
        {
            byte actual = buffer.getByte(i);
            byte expected = getByteAtPosition(pattern, patternByte,
                    ByteOrder.BIG_ENDIAN);
            assertEquals(expected, actual);
            patternByte = (++patternByte % 2);
        }
    }

    protected static void checkGetByte(RecordBuffer buffer, int pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.getByte(index pos)
        //
        int patternByte = patternStartByte;
        for(int i=0; i<buffer.getLength(); i++)
        {
            byte actual = buffer.getByte(i);
            byte expected = getByteAtPosition(pattern, patternByte,
                    ByteOrder.BIG_ENDIAN);
            assertEquals(expected, actual);
            patternByte = (++patternByte % 4);
        }
    }

    protected static void checkGetByte(RecordBuffer buffer, long pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.getByte(index pos)
        //
        int patternByte = patternStartByte;
        for(int i=0; i<buffer.getLength(); i++)
        {
            byte actual = buffer.getByte(i);
            byte expected = getByteAtPosition(pattern, patternByte,
                    ByteOrder.BIG_ENDIAN);
            assertEquals(expected, actual);
            patternByte = (++patternByte % 8);
        }
    }

    protected static void checkGetShort(RecordBuffer buffer, short pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.getShort(index pos)
        //
        int start = 2-patternStartByte;
        int end = buffer.getLength()-2;
        for(int i=start; i<=end; i+=2)
        {
            short actual = buffer.getShort(i);
            assertEquals(pattern, actual);
        }
    }

    protected static void checkGetInt(RecordBuffer buffer, int pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.getInt(index pos)
        //
        int start = 4-patternStartByte;
        int end = buffer.getLength()-4;
        for(int i=start; i<=end; i+=4)
        {
            int actual = buffer.getInt(i);
            assertEquals(pattern, actual);
        }
    }
    protected static void checkGetLong(RecordBuffer buffer, long pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.getLong(index pos)
        //
        int start = 8-patternStartByte;
        int end = buffer.getLength()-8;
        for(int i=start; i<=end; i+=8)
        {
            long actual = buffer.getLong(i);
            assertEquals(pattern, actual);
        }
    }
    protected static void checkGetBytes(RecordBuffer buffer, long pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.getBytes(int start, int length)
        //
        for(Segment segment : Segment.permute(buffer.getLength()))
        {
            int startPos = segment.startPos;
            int length = segment.length;
            byte[] copy = buffer.getBytes(startPos, length );
            assertEquals(length, copy.length);
            assertFullPattern(copy, pattern, (startPos+patternStartByte)%8);

        }
    }

    protected static void checkGetBytes(RecordBuffer buffer, int pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.getBytes(int start, int length)
        //
        for(Segment segment : Segment.permute(buffer.getLength()))
        {
            int startPos = segment.startPos;
            int length = segment.length;
//                System.out.println("buffer = " + buffer.getLength() + " startPos:" + startPos +
//                        " patternStartByte: " + patternStartByte + length);

            byte[] copy = buffer.getBytes(startPos, length );
            assertEquals(length, copy.length);
            assertFullPattern(copy, pattern, (startPos+patternStartByte)%4);

        }
    }

    protected static void checkGetBytes(RecordBuffer buffer, short pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.getBytes(int start, int length)
        //
        for(Segment segment : Segment.permute(buffer.getLength()))
        {
            int startPos = segment.startPos;
            int length = segment.length;
            byte[] copy = buffer.getBytes(startPos, length );
            assertEquals(length, copy.length);
            assertFullPattern(copy, pattern, (startPos+patternStartByte)%2);

        }
    }

    public void checkCopyBytes(RecordBuffer buffer, short pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.copyBytes(byte[] dest, int offset, int start, int length)
        //
        for(Segment segment : Segment.permute(buffer.getLength()))
        {
            int offset = (int) (Math.random() * 100);
            int pad = (int) (Math.random() * 100);
            byte[] sink = new byte[segment.length + offset + pad];
            buffer.copyBytes(sink, offset, segment.startPos, segment.length);

            byte[] copy = new byte[segment.length];
            System.arraycopy(sink, offset, copy, 0, segment.length);
            assertFullPattern(copy, pattern, (segment.startPos + patternStartByte) % 2);
        }
    }

    public void checkCopyBytes(RecordBuffer buffer, int pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.copyBytes(byte[] dest, int offset, int start, int length)
        //
        for(Segment segment : Segment.permute(buffer.getLength()))
        {
            int offset = (int) (Math.random() * 100);
            int pad = (int) (Math.random() * 100);
            byte[] sink = new byte[segment.length + offset + pad];
            buffer.copyBytes(sink, offset, segment.startPos, segment.length);

            byte[] copy = new byte[segment.length];
            System.arraycopy(sink, offset, copy, 0, segment.length);
            assertFullPattern(copy, pattern, (segment.startPos + patternStartByte) % 4);
        }
    }

    protected static void checkCopyBytes(RecordBuffer buffer, long pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.copyBytes(byte[] dest, int offset, int start, int length)
        //
        for(Segment segment : Segment.permute(buffer.getLength()))
        {
            int offset = (int) (Math.random() * 100);
            int pad = (int) (Math.random() * 100);
            byte[] sink = new byte[segment.length + offset + pad];
            buffer.copyBytes(sink, offset, segment.startPos, segment.length);

            byte[] copy = new byte[segment.length];
            System.arraycopy(sink, offset, copy, 0, segment.length);
            assertFullPattern(copy, pattern, (segment.startPos + patternStartByte) % 8);
        }
    }

    protected static void checkView(RecordBuffer buffer, long pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.view(int start, int length)
        //
        for(Segment segment : Segment.permute(buffer.getLength()))
        {
            int startPos = segment.startPos;
            int length = segment.length;
            RecordBuffer view = buffer.view(startPos, length);
            assertEquals(length , view.getLength());
//                System.out.println("view: [" + startPos + " - " + endPos + "] offset: " + (startPos+patternStartByte)%8 + " length: " + view.getLength());
            assertFullPattern(view, pattern, (startPos+patternStartByte)%8);

            //recurse
//                if(view.getLength()< buffer.getLength())
//                {
//                    checkViews(view, pattern, (startPos+patternStartByte)%8);
//                }
        }
    }

    protected static void checkView(RecordBuffer buffer, int pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.view(int start, int length)
        //
        for(Segment segment : Segment.permute(buffer.getLength()))
        {
            int startPos = segment.startPos;
            int length = segment.length;
//                System.out.println("buffer = " + buffer.getLength() + " startPos:" + startPos +
//                        " patternStartByte: " + patternStartByte + " length: " + length);

            RecordBuffer view = buffer.view(startPos, length);
            assertEquals(length , view.getLength());
            assertFullPattern(view, pattern, (startPos+patternStartByte)%4);

            //recurse
//                if(view.getLength()< buffer.getLength())
//                {
//                    checkViews(view, pattern, (startPos+patternStartByte)%8);
//                }
        }

    }

    protected static void checkView(RecordBuffer buffer, short pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.view(int start, int length)
        //
        for(Segment segment : Segment.permute(buffer.getLength()))
        {
            int startPos = segment.startPos;
            int length = segment.length;
            RecordBuffer view = buffer.view(startPos, length);
            assertEquals(length , view.getLength());
            assertFullPattern(view, pattern, (startPos+patternStartByte)%2);

            //recurse
//                if(view.getLength()< buffer.getLength())
//                {
//                    checkViews(view, pattern, (startPos+patternStartByte)%8);
//                }
        }

    }

    protected static void checkCopy(RecordBuffer buffer, long pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.copy(int start, int length)
        //
        for(Segment segment : Segment.permute(buffer.getLength()))
        {
            int startPos = segment.startPos;
            int length = segment.length;
            RecordBuffer copy = buffer.copy(startPos, length);
            assertEquals(length , copy.getLength());
            assertFullPattern(copy, pattern, (startPos+patternStartByte)%8);
        }
    }

    protected static void checkCopy(RecordBuffer buffer, int pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.copy(int start, int length)
        //
        for(Segment segment : Segment.permute(buffer.getLength()))
        {
            int startPos = segment.startPos;
            int length = segment.length;
            RecordBuffer copy = buffer.copy(startPos, length);
            assertEquals(length , copy.getLength());
            assertFullPattern(copy, pattern, (startPos+patternStartByte)%4);
        }

    }

    protected static void checkCopy(RecordBuffer buffer, short pattern, int patternStartByte)
    {
        //
        // Tests:
        // RecordBuffer.copy(int start, int length)
        //
        for(Segment segment : Segment.permute(buffer.getLength()))
        {
            int startPos = segment.startPos;
            int length = segment.length;
            RecordBuffer copy = buffer.copy(startPos, length);
            assertEquals(length , copy.getLength());
            assertFullPattern(copy, pattern, (startPos+patternStartByte)%2);
        }

    }


    private static void assertFullPattern(RecordBuffer buffer, long pattern, int patternBeginByte)
    {
        StorageInfo info = new StorageInfo(buffer.getLength(), 8, patternBeginByte);

        // read the partial value at the start of the buffer
        for(int i=0; i < info.leadingPartial.length; i++)
        {
            byte out = buffer.getByte(i);
            byte expected = getByteAtPosition(pattern,
                    info.leadingPartial[i], ByteOrder.BIG_ENDIAN);
            assertEquals(formatError(expected, out), expected,  out);
        }

        // read the whole values
        for(int pos=info.leadingPartial.length; info.capacity-pos>8; pos+=8 )
        {
            long out = buffer.getLong(pos);
            assertEquals(formatError(pattern, out), pattern, out);
        }

        // read the partial value at the end of the buffer
        int startPos = info.capacity - info.trailingPartial.length;
        for(int i=0; i < info.trailingPartial.length; i++)
        {
            byte out = buffer.getByte(i + startPos);
            byte expected = getByteAtPosition(pattern,
                    info.trailingPartial[i], ByteOrder.BIG_ENDIAN);
            assertEquals(formatError(expected, out), expected,  out);
        }
    }

    private static void assertFullPattern(RecordBuffer buffer, int pattern, int patternBeginByte)
    {
        StorageInfo info = new StorageInfo(buffer.getLength(), 4, patternBeginByte);

        // read the partial value at the start of the buffer
        for(int i=0; i < info.leadingPartial.length; i++)
        {
            byte out = buffer.getByte(i);
            byte expected = getByteAtPosition(pattern,
                    info.leadingPartial[i], ByteOrder.BIG_ENDIAN);
            assertEquals(formatError(expected, out), expected,  out);
        }

        // read the whole values
        for(int pos=info.leadingPartial.length; info.capacity-pos>4; pos+=4 )
        {
            int out = buffer.getInt(pos);
            assertEquals(formatError(pattern, out), pattern, out);
        }

        // read the partial value at the end of the buffer
        int startPos = info.capacity - info.trailingPartial.length;
        for(int i=0; i < info.trailingPartial.length; i++)
        {
            byte out = buffer.getByte(i + startPos);
            byte expected = getByteAtPosition(pattern,
                    info.trailingPartial[i], ByteOrder.BIG_ENDIAN);
            assertEquals(formatError(expected, out), expected,  out);
        }
    }

    private static void assertFullPattern(RecordBuffer buffer, short pattern, int patternBeginByte)
    {
        StorageInfo info = new StorageInfo(buffer.getLength(), 2, patternBeginByte);

        // read the partial value at the start of the buffer
        for(int i=0; i < info.leadingPartial.length; i++)
        {
            byte out = buffer.getByte(i);
            byte expected = getByteAtPosition(pattern,
                    info.leadingPartial[i], ByteOrder.BIG_ENDIAN);
            assertEquals(formatError(expected, out), expected,  out);
        }

        // read the whole values
        for(int pos=info.leadingPartial.length; info.capacity-pos>2; pos+=2 )
        {
            short out = buffer.getShort(pos);
            assertEquals(formatError(pattern, out), pattern, out);
        }

        // read the partial value at the end of the buffer
        int startPos = info.capacity - info.trailingPartial.length;
        for(int i=0; i < info.trailingPartial.length; i++)
        {
            byte out = buffer.getByte(i + startPos);
            byte expected = getByteAtPosition(pattern,
                    info.trailingPartial[i], ByteOrder.BIG_ENDIAN);
            assertEquals(formatError(expected, out), expected,  out);
        }
    }

    private static void assertFullPattern(byte[] buffer, long pattern, int patternBeginByte)
    {
        StorageInfo info = new StorageInfo(buffer.length, 8, 0);
        int headRemnant = patternBeginByte>0?(8-patternBeginByte):0;


        // read the head remnant
        if(patternBeginByte > 0)
        {
            for(int pos=0; pos< (8-patternBeginByte); pos++)
            {
                if(pos == info.capacity)
                {
                    return;
                }

                byte out = buffer[pos];
                byte expected = getByteAtPosition(pattern, patternBeginByte+pos,
                        ByteOrder.BIG_ENDIAN);
                assertEquals(expected,  out);
            }
        }

        //read whole longs
        for(int pos=headRemnant; info.capacity-pos >8; pos+=8 )
        {
            long out = readLong(buffer, pos, ByteOrder.BIG_ENDIAN);
            assertEquals(formatError(pattern, out), pattern, out);
        }

        // read the tail remnant
        int startIDX = info.capacity-(info.partialWritten -headRemnant);

        for(int pos=startIDX; pos < info.capacity; pos++ )
        {
            byte out = buffer[pos];
            byte expected = getByteAtPosition(pattern, pos - startIDX,
                    ByteOrder.BIG_ENDIAN);
            assertEquals(expected,  out);
        }
    }

    private static void assertFullPattern(byte[] buffer, int pattern, int patternBeginByte)
    {
        StorageInfo info = new StorageInfo(buffer.length, 4, 0);
        int headRemnant = patternBeginByte>0?(4-patternBeginByte):0;


        // read the head remnant
        if(patternBeginByte > 0)
        {
            for(int pos=0; pos< (4-patternBeginByte); pos++)
            {
                if(pos == info.capacity)
                {
                    return;
                }

                byte out = buffer[pos];
                byte expected = getByteAtPosition(pattern, patternBeginByte+pos,
                        ByteOrder.BIG_ENDIAN);
                assertEquals(expected,  out);

            }
        }

        //read whole ints
        for(int pos=headRemnant; info.capacity-pos >4; pos+=4 )
        {
            long out = readInt(buffer, pos, ByteOrder.BIG_ENDIAN);
            assertEquals(formatError(pattern, out), pattern, out);
        }

        // read the tail remnant
        int startIDX = info.capacity-(info.partialWritten -headRemnant);

        for(int pos=startIDX; pos < info.capacity; pos++ )
        {
            byte out = buffer[pos];
            byte expected = getByteAtPosition(pattern, pos - startIDX,
                    ByteOrder.BIG_ENDIAN);
            assertEquals(expected,  out);
        }
    }

    private static void assertFullPattern(byte[] buffer, short pattern, int patternBeginByte)
    {
        StorageInfo info = new StorageInfo(buffer.length, 2, 0);
        int headRemnant = patternBeginByte>0?(2-patternBeginByte):0;


        // read the head remnant
        if(patternBeginByte > 0)
        {
            for(int pos=0; pos< (2-patternBeginByte); pos++)
            {
                if(pos == info.capacity)
                {
                    return;
                }

                byte out = buffer[pos];
                byte expected = getByteAtPosition(pattern, patternBeginByte+pos,
                        ByteOrder.BIG_ENDIAN);
                assertEquals(expected,  out);

            }
        }

        //read whole shorts
        for(int pos=headRemnant; info.capacity-pos >2; pos+=2 )
        {
            long out = readShort(buffer, pos, ByteOrder.BIG_ENDIAN);
            assertEquals(formatError(pattern, out), pattern, out);
        }

        // read the tail remnant
        int startIDX = info.capacity-(info.partialWritten -headRemnant);

        for(int pos=startIDX; pos < info.capacity; pos++ )
        {
            byte out = buffer[pos];
            byte expected = getByteAtPosition(pattern, pos - startIDX,
                    ByteOrder.BIG_ENDIAN);
            assertEquals(expected,  out);
        }
    }

    protected static void checkOutOfBounds(RecordBuffer buffer)
    {
        //
        // Out of bounds conditions expected to generation exceptions
        // matching the contract of ByteBuffer
        //

        try
        {
            buffer.getLong(-1);
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }
        try
        {
            buffer.getLong(buffer.getLength());
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }
        try
        {
            buffer.getInt(-1);
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }
        try
        {
            buffer.getInt(buffer.getLength());
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }
        try
        {
            buffer.getShort(-1);
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }
        try
        {
            buffer.getShort(buffer.getLength());
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }
        try
        {
            buffer.getByte(-1);
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }
        try
        {
            buffer.getByte(buffer.getLength());
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        try
        {
            buffer.view(-1, -1);
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        try
        {
            buffer.view(0, buffer.getLength() + 1);
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        try
        {
            buffer.view(buffer.getLength()+1, buffer.getLength()+1);
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        try
        {
            buffer.view(0, -3);
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        try
        {
            buffer.copy(0, -3);
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        try
        {
            buffer.copy(20, buffer.getLength());
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        try
        {
            buffer.copy(-9, 8);
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        try
        {
            buffer.getBytes(0, -3);
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        try
        {
            buffer.getBytes(20, buffer.getLength());
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        try
        {
            buffer.getBytes(-9, 8);
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }


        try
        {
            buffer.copyBytes(new byte[10], 0, 0, -3);
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        try
        {
            buffer.copyBytes(new byte[buffer.getLength()], 0, 20, buffer.getLength());
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        try
        {
            if(buffer.getLength() != 0)
            {
                buffer.copyBytes(new byte[buffer.getLength() - 1], 0, 0, buffer.getLength());
                fail("Bounds check failed");
            }
            else
            {
                //cant test condition on empty buffer
            }
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        try
        {
            buffer.copyBytes(new byte[10], 3, 0, 8);
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        try
        {
            buffer.copyBytes(new byte[10], 11, 0, 10);
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        try
        {
            buffer.copyBytes(new byte[10], 0, -9, 8);
            fail("Bounds check failed");
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }


        // odd cases, range [0,0] is allowed on ByteBuffer[0]
        // but [n,0] is out of bounds for ByteBuffer[n]
        try
        {
                buffer.copyBytes(new byte[0], 0,  buffer.getLength(), 0);

                if(buffer.getLength() != 0)
                {
                    fail("Bounds check failed");
                }
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        // odd cases, range [0,0] is allowed on ByteBuffer[0]
        // but [n,0] is out of bounds for ByteBuffer[n]
        try
        {
            buffer.getBytes(buffer.getLength(), 0);

            if(buffer.getLength() != 0)
            {
                fail("Bounds check failed");
            }
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        // odd cases, range [0,0] is allowed on ByteBuffer[0]
        // but [n,0] is out of bounds for ByteBuffer[n]
        try
        {
            buffer.view(buffer.getLength(), 0);

            if(buffer.getLength() != 0)
            {
                fail("Bounds check failed");
            }
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

        // odd cases, range [0,0] is allowed on ByteBuffer[0]
        // but [n,0] is out of bounds for ByteBuffer[n]
        try
        {
            buffer.copy(buffer.getLength(), 0);

            if(buffer.getLength() != 0)
            {
                fail("Bounds check failed");
            }
        }
        catch (IndexOutOfBoundsException e)
        {
            // desired
        }

    }

    /**
     * get the ordinal of the first byte in a primitive shifted
     * to realize a given alignment.
     *
     * Integer Example:
     *
     * Value:   AB CD EF 01
     * Ordinal: 0  1  2  3
     *
     * alig
     *
     * alignment 0, start 0: AB CD EF 01 AB CD EF 01
     * alignment 1, start 3: 01 AB CD EF 01
     * alignment 2, start 2: EF 01 AB CD EF 01
     * alignment 3, start 1: CD EF 01 AB CD EF 01
     */
    private static int getStartByte(int width, int aligment)
    {
        return (width-aligment) % width;
    }

    private static String formatError(long expected, long actual)
    {
        return "expected: " + Long.toHexString(expected) +
                ", actual: " + Long.toHexString(actual);
    }
    private static String formatError(int expected, int actual)
    {
        return "expected: " + Integer.toHexString(expected) +
                ", actual: " + Integer.toHexString(actual);
    }
    private static String formatError(short expected, short actual)
    {
        return "expected: " + Integer.toHexString(Short.toUnsignedInt(expected)) + "," +
                " actual: " + Integer.toHexString(Short.toUnsignedInt(actual));
    }

    private static String formatError(byte expected, byte actual)
    {
        return "expected: " + Integer.toHexString(Byte.toUnsignedInt(expected)) + "," +
                " actual: " + Integer.toHexString(Byte.toUnsignedInt(actual));
    }


    private static class Segment
    {
        final int startPos;
        final int length;

        Segment(final int startPos, final int length)
        {
            this.startPos = startPos;
            this.length = length;
        }

        /**
         *  Produce an Iterable that replaces this loop:
         *
         *  for(int startPos =0; (startPos<size || startPos==0); startPos++)
         *  {
         *      for(int length=0; length<=size-startPos; length++)
         *       {
         *           //action on (startPos, length)
         *       }
         *  }
         */
        static Iterable<Segment> permute(final int size)
        {
            return new Iterable<Segment>()
            {
                @Override
                public Iterator<Segment> iterator()
                {
                    return new Iterator<Segment>()
                    {
                        int start = 0;
                        int length = 0;
                        @Override
                        public boolean hasNext()
                        {
                            //Note: the last term codes the pathological case
                            //      of segment (0,0) from a zero-length buffer
                            //      which is an allowable range for segments
                            //      int java.nio.ByteBuffer
                            return start<size || length<(size-start) ||
                                    (start==0 && length==0 && size==0);
                        }

                        @Override
                        public Segment next()
                        {
                            Segment next = new Segment(start, length);
                            if(length == (size-start))
                            {
                                length = 0;
                                start++;
                            }
                            else
                            {
                                length++;
                            }

                            return next;
                        }
                    };
                }
            };
        }

    }


    /**
     * Tests RecordBuffers.EmptyBuffer
     */
    public static class EmptyBufferTest extends CommonRecordBufferTest
    {
        @Override
        public int subjectSize()
        {
            return 0;
        }

        @Override
        public RecordBuffer createSubject(final short pattern, final int alignment)
        {
            return new RecordBuffers.EmptyBuffer();
        }

        @Override
        public RecordBuffer createSubject(final int pattern, final int alignment)
        {
            return new RecordBuffers.EmptyBuffer();
        }

        @Override
        public RecordBuffer createSubject(final long pattern, final int alignment)
        {
            return new RecordBuffers.EmptyBuffer();
        }

        @Test
        public void testNilRequests()
        {
            ///
            /// It is an oddity that zero-based, zero-length requests
            /// are permitted on a a zero-length buffer
            ///
            RecordBuffer subject = new RecordBuffers.EmptyBuffer();

            byte[] bytes = subject.getBytes(0, 0);
            subject.copyBytes(new byte[111], 0, 0, 0);
            RecordBuffer copy = subject.copy(0, 0);
            RecordBuffer view = subject.view(0, 0);
        }
    }


}
