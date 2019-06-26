package icecube.daq.performance.common;

import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.*;

/**
 * Tests BufferContent.java
 */
public class BufferContentTest
{

    class TestData
    {
        final byte[] original;
        final byte[] copy;
        int pos;
        int limit;

        TestData(int size)
        {
            if(limit<pos)
            {
                throw new Error("pos " + pos + " < limit " + limit);
            }

            original = new byte[size];

            for (int i = 0; i < original.length; i++)
            {
                original[i] = (byte) (Math.random() * 255);
            }
            copy = new byte[original.length];
            System.arraycopy(original, 0, copy, 0, original.length);
        }

        void position(int position)
        {
            this.pos = position;
        }

        void limit(int limit)
        {
            this.limit = limit;
        }

        ByteBuffer asBuffer()
        {
            if(limit<pos)
            {
                throw new Error("pos " + pos + " < limit " + limit);
            }
            ByteBuffer bb = ByteBuffer.wrap(copy);
            bb.position(pos);
            bb.limit(limit);
            return bb;
        }

        byte[] subContent(int offset, int length)
        {
            byte[] subcontent = new byte[length];
            System.arraycopy(original, offset, subcontent, 0, length);
            return subcontent;
        }


        void assertContentUnmodified()
        {
            for (int i=0; i< original.length; i++)
            {
                assertEquals(original[i], copy[i]);
            }
        }
    }


    @Before
    public void setUp()
    {
    }


    @Test
    public void test_ZERO_TO_CAPACITY()
    {
        ///
        /// Tests BufferContent.ZERO_TO_CAPACITY
        ///
        /// Should always address the full buffer regardless of
        /// position and limit settings

        BufferContent ZTC = BufferContent.ZERO_TO_CAPACITY;
        TestData data = new TestData(1024);

        {
            data.position(0);
            data.limit(1024);
            testOperations(ZTC, data, data.subContent(0, 1024));
        }
        {
            data.position(0);
            data.limit(0);
            testOperations(ZTC, data, data.subContent(0, 1024));
        }
        {
            data.position(1024);
            data.limit(1024);
            testOperations(ZTC, data, data.subContent(0, 1024));
        }

        // random positions and limits
        for(int i = 0; i<100; i++)
        {
            int pos = (int) (Math.random() * 1024);
            int lim = (int) (Math.random() * (1024-pos)) + pos;  //must be >pos

            data.position(pos);
            data.limit(lim);
            testOperations(ZTC, data, data.subContent(0, 1024));
        }
    }

    @Test
    public void test_POSITION_TO_LIMIT()
    {
        ///
        /// Tests BufferContent.POSITION_TO_LIMIT
        ///
        /// Should always address the the content between position
        /// and limit regardless of capacity.

        BufferContent PTL = BufferContent.POSITION_TO_LIMIT;

        TestData data = new TestData(1024);

        {
            data.position(0);
            data.limit(1024);

            testOperations(PTL, data, data.subContent(0, 1024));
        }
        {
            data.position(0);
            data.limit(0);
            testOperations(PTL, data, data.subContent(0, 0));
        }
        {
            data.position(1024);
            data.limit(1024);
            testOperations(PTL, data, data.subContent(1024, 0));
        }

        {
            data.position(5);
            data.limit(6);
            testOperations(PTL, data, data.subContent(5, 1));
        }

        // random positions and limits
        for(int i = 0; i<100; i++)
        {
            int pos = (int) (Math.random() * 1024);
            int lim = (int) (Math.random() * (1024-pos)) + pos;  //must be >pos

            data.position(pos);
            data.limit(lim);
            testOperations(PTL, data, data.subContent(pos, (lim-pos) ));
        }
    }

    @Test
    public void test_ZERO_TO_LIMIT()
    {
        ///
        /// Tests BufferContent.ZERO_TO_LIMIT
        ///
        /// Should always address the the content between zero
        /// and limit regardless of position or capacity.

        BufferContent ZTL = BufferContent.ZERO_TO_LIMIT;

        TestData data = new TestData(1024);

        {
            data.position(0);
            data.limit(1024);

            testOperations(ZTL, data, data.subContent(0, 1024));
        }
        {
            data.position(0);
            data.limit(0);
            testOperations(ZTL, data, data.subContent(0, 0));
        }
        {
            data.position(1024);
            data.limit(1024);
            testOperations(ZTL, data, data.subContent(0, 1024));
        }

        {
            data.position(5);
            data.limit(6);
            testOperations(ZTL, data, data.subContent(0, 6));
        }

        // random positions and limits
        for(int i = 0; i<100; i++)
        {
            int pos = (int) (Math.random() * 1024);
            int lim = (int) (Math.random() * (1024-pos)) + pos;  //must be >pos

            data.position(pos);
            data.limit(lim);
            testOperations(ZTL, data, data.subContent(0, lim) );
        }
    }

    @Test
    public void test_ZERO_TO_POSITION()
    {
        ///
        /// Tests BufferContent.ZERO_TO_POSITION
        ///
        /// Should always address the the content between zero
        /// and position regardless of limit or capacity.

        BufferContent ZTP = BufferContent.ZERO_TO_POSITION;

        TestData data = new TestData(1024);

        {
            data.position(0);
            data.limit(1024);

            testOperations(ZTP, data, data.subContent(0, 0));
        }
        {
            data.position(0);
            data.limit(0);
            testOperations(ZTP, data, data.subContent(0, 0));
        }
        {
            data.position(1024);
            data.limit(1024);
            testOperations(ZTP, data, data.subContent(0, 1024));
        }

        {
            data.position(5);
            data.limit(6);
            testOperations(ZTP, data, data.subContent(0, 5));
        }

        // random positions and limits
        for(int i = 0; i<100; i++)
        {
            int pos = (int) (Math.random() * 1024);
            int lim = (int) (Math.random() * (1024-pos)) + pos;  //must be >pos

            data.position(pos);
            data.limit(lim);
            testOperations(ZTP, data, data.subContent(0, pos) );
        }
    }

    @Test
    public void testViewAbsolute()
    {
        ///
        /// Tests the viewAbsolute method which creates arbitrary slices
        ///
        ///
        TestData data = new TestData(1024);

        {
            data.position(0);
            data.limit(1024);

            ByteBuffer buffer = data.asBuffer();
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 0, 0),
                    data.subContent(0, 0));
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 0, 55),
                    data.subContent(0, 55));
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 13, 99),
                    data.subContent(13, 99));
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 1024, 0),
                    data.subContent(1024, 0));
        }
        {
            data.position(0);
            data.limit(0);
            ByteBuffer buffer = data.asBuffer();
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 0, 0),
                    data.subContent(0, 0));
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 0, 55),
                    data.subContent(0, 55));
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 13, 99),
                    data.subContent(13, 99));
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 1024, 0),
                    data.subContent(1024, 0));
        }
        {
            data.position(1024);
            data.limit(1024);
            ByteBuffer buffer = data.asBuffer();
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 0, 0),
                    data.subContent(0, 0));
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 0, 55),
                    data.subContent(0, 55));
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 13, 99),
                    data.subContent(13, 99));
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 1024, 0),
                    data.subContent(1024, 0));
        }

        {
            data.position(5);
            data.limit(6);
            ByteBuffer buffer = data.asBuffer();
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 0, 0),
                    data.subContent(0, 0));
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 0, 55),
                    data.subContent(0, 55));
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 13, 99),
                    data.subContent(13, 99));
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 1024, 0),
                    data.subContent(1024, 0));
        }

        // random positions and limits
        for(int i = 0; i<100; i++)
        {
            int pos = (int) (Math.random() * 1024);
            int lim = (int) (Math.random() * (1024-pos)) + pos;  //mist be >pos

            data.position(pos);
            data.limit(lim);
            ByteBuffer buffer = data.asBuffer();
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 0, 0),
                    data.subContent(0, 0));
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 0, 55),
                    data.subContent(0, 55));
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 13, 99),
                    data.subContent(13, 99));
            assertNormalizedContent(BufferContent.viewAbsolute(buffer, 1024, 0),
                    data.subContent(1024, 0));
        }
    }

    public void testOperations(BufferContent contentSpecifier,
                               TestData data,
                               byte[] content)
    {
        ///
        /// Test all operations of the content specifier on the buffer.
        ///
        /// By design, the operations should automatically work as
        /// if the buffer was normalized to only contain the given
        /// content.

        ByteBuffer buffer = data.asBuffer();

        // normalize
        assertNormalizedContent(contentSpecifier.normalize(buffer), content);

        // copy
        ByteBuffer copy = contentSpecifier.copy(buffer);
        assertNormalizedContent(copy, content);
        fill(copy, (byte) 33);
        data.assertContentUnmodified();


        //slicing
        for(int i = 0; i<content.length; i++)
        {
            assertNormalizedContent(contentSpecifier.view(buffer, 0, i), content, 0, i);
            assertNormalizedContent(contentSpecifier.view(buffer, i, (content.length - i)),
                    content, i, (content.length - i));
        }

        // copy slicing
        for(int i = 0; i<content.length; i++)
        {

            // copies contain view
            assertNormalizedContent(contentSpecifier.copy(buffer, 0, i),
                    content, 0, i);
            assertNormalizedContent(contentSpecifier.copy(buffer, i, (content.length - i)),
                    content, i, (content.length - i));

            // copies cant modify original
            fill(contentSpecifier.copy(buffer, 0, i), (byte) 33);
            data.assertContentUnmodified();
            fill(contentSpecifier.copy(buffer, i, (content.length - i)), (byte) 99);
            data.assertContentUnmodified();

        }

        // arbitrary bulk transfer
        for(int i = 0; i<content.length; i++)
        {

            int OFFSET = 0;
            byte[] target = new byte[i];
            contentSpecifier.get(buffer, target, OFFSET, 0, i);
            assertContent(target, OFFSET, content, 0, i);

            OFFSET = 999;
            byte[] target2 = new byte[i + OFFSET];
            contentSpecifier.get(buffer, target2, OFFSET, 0, i);
            assertContent(target2, OFFSET, content, 0, i);
            assertStaticContent(target, OFFSET, 0, (byte) 0);

            OFFSET = 0;
            byte[] target3 = new byte[content.length-i];
            contentSpecifier.get(buffer, target3, OFFSET, i, (content.length-i));
            assertContent(target3, OFFSET, content, i, (content.length - i));

            OFFSET = 123;
            byte[] target4 = new byte[(content.length-i) + OFFSET];
            contentSpecifier.get(buffer, target4, OFFSET, i, (content.length-i));
            assertContent(target4, OFFSET, content, i, (content.length - i));
            assertStaticContent(target, OFFSET, 0, (byte) 0);
        }


        // test endianess preservation
        buffer.order(ByteOrder.BIG_ENDIAN);
        assertEquals(ByteOrder.BIG_ENDIAN,
                contentSpecifier.copy(buffer).order());
        assertEquals(ByteOrder.BIG_ENDIAN,
                contentSpecifier.copy(buffer, 0, content.length).order());
        assertEquals(ByteOrder.BIG_ENDIAN,
                contentSpecifier.normalize(buffer).order());
        assertEquals(ByteOrder.BIG_ENDIAN,
                contentSpecifier.view(buffer, 0, content.length).order());

        buffer.order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(ByteOrder.LITTLE_ENDIAN,
                contentSpecifier.copy(buffer).order());
        assertEquals(ByteOrder.LITTLE_ENDIAN,
                contentSpecifier.copy(buffer, 0, content.length).order());
        assertEquals(ByteOrder.LITTLE_ENDIAN,
                contentSpecifier.normalize(buffer).order());
        assertEquals(ByteOrder.LITTLE_ENDIAN,
                contentSpecifier.view(buffer, 0, content.length).order());

    }

    /**
     * Asserts that a buffer contains the content at position 0-limit, and that
     * limit==capacity.
     */
    private static void assertNormalizedContent(ByteBuffer bb, byte[] content)
    {
       assertNormalizedContent(bb, content, 0, content.length);
    }

    /**
     * Asserts that a buffer contains the content subsequences at position 0-limit,
     * and that
     * limit==capacity.
     */
    private static void assertNormalizedContent(ByteBuffer bb, byte[] content,
                                                int start, int length)
    {
        assertEquals(0, bb.position());
        assertEquals(length, bb.remaining());
        assertEquals(length, bb.capacity());

        for (int i = 0; i<length; i++)
        {
            assertEquals(content[i+start], bb.get(i));
        }
    }

//    private static void assertNormalizedContent(ByteBuffer bb, int from, int length)
//    {
//        assertEquals(bb.position(), 0);
//        assertEquals(bb.remaining(), length);
//        assertEquals(bb.capacity(), length);
//
//        for (int i = 0; i<length; i++)
//        {
//            assertEquals( (from + i), bb.get(i));
//        }
//    }

    /**
     * assert that a subsequence of a source array
     * is present in a target array
     */
    private static void assertContent(byte[] target, int targetOffset,
                                      byte[] source, int sourceOffset, int length)
    {
        assertTrue(target.length >= (targetOffset+length));
        for (int i = 0; i<length; i++)
        {
            assertEquals( source[sourceOffset+i], target[i+targetOffset]);
        }
    }

    private static void assertSequenceContent(byte[] ba, int offset,
                                              int from, int length)
    {

        assertEquals(ba.length, (offset+length));
        for (int i = 0; i<length; i++)
        {
            assertEquals( (from + i), ba[i+offset]);
        }
    }

    private static void assertStaticContent(byte[] ba, int offset,
                                            int length, byte val)
    {
        for (int i = 0; i<length; i++)
        {
            assertEquals( ba[i+offset], val);
        }
    }

    private static void fill(ByteBuffer bb, byte val)
    {
        for(int i = 0; i< bb.capacity(); i++)
        {
            bb.put(i, val);
        }
    }

}
