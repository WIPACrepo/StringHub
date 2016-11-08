package icecube.daq.performance.binary.buffer;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;


/**
 * Tests RecordBufferIndex.java
 */
public class RecordBufferIndexTest
{

    @Test
    public void testNullIndex()
    {
        RecordBufferIndex.UpdatableIndex subject =
                new RecordBufferIndex.NullIndex();

       checkNullIndex(subject);
    }

    @Test
    public void testArrayListIndex()
    {
        ///
        /// Tests ArrayListIndex
        ///

        RecordBufferIndex.ArrayListIndex subject =
                new RecordBufferIndex.ArrayListIndex();

        // no values
        assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
        assertEquals(-1, subject.lessThan(-5));
        assertEquals(-1, subject.lessThan(0));
        assertEquals(-1, subject.lessThan(5));
        assertEquals(-1, subject.lessThan(Long.MAX_VALUE));
        subject.update(Integer.MIN_VALUE);
        subject.update(Integer.MAX_VALUE);
        subject.clear();

        // one value
        subject.addIndex(123, 9999);
        assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
        assertEquals(-1, subject.lessThan(-5));
        assertEquals(-1, subject.lessThan(0));
        assertEquals(-1, subject.lessThan(5));
        assertEquals(-1, subject.lessThan(9998));
        assertEquals(-1, subject.lessThan(9999));
        assertEquals(123, subject.lessThan(10000));
        assertEquals(123, subject.lessThan(Long.MAX_VALUE));

        // update
        subject.update(100);
        assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
        assertEquals(-1, subject.lessThan(-5));
        assertEquals(-1, subject.lessThan(0));
        assertEquals(-1, subject.lessThan(5));
        assertEquals(-1, subject.lessThan(9998));
        assertEquals(-1, subject.lessThan(9999));
        assertEquals(23, subject.lessThan(10000));
        assertEquals(23, subject.lessThan(Long.MAX_VALUE));

        subject.update(10);
        assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
        assertEquals(-1, subject.lessThan(-5));
        assertEquals(-1, subject.lessThan(0));
        assertEquals(-1, subject.lessThan(5));
        assertEquals(-1, subject.lessThan(9998));
        assertEquals(-1, subject.lessThan(9999));
        assertEquals(13, subject.lessThan(10000));
        assertEquals(13, subject.lessThan(Long.MAX_VALUE));

        subject.update(13);
        assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
        assertEquals(-1, subject.lessThan(-5));
        assertEquals(-1, subject.lessThan(0));
        assertEquals(-1, subject.lessThan(5));
        assertEquals(-1, subject.lessThan(9998));
        assertEquals(-1, subject.lessThan(9999));
        assertEquals(0, subject.lessThan(10000));
        assertEquals(0, subject.lessThan(Long.MAX_VALUE));

        subject.update(1);
        assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
        assertEquals(-1, subject.lessThan(-5));
        assertEquals(-1, subject.lessThan(0));
        assertEquals(-1, subject.lessThan(5));
        assertEquals(-1, subject.lessThan(9998));
        assertEquals(-1, subject.lessThan(9999));
        assertEquals(-1, subject.lessThan(10000));
        assertEquals(-1, subject.lessThan(Long.MAX_VALUE));

        // many values
        subject.addIndex(1000, 11111);
        subject.addIndex(2000, 22222);
        subject.addIndex(3000, 33333);
        subject.addIndex(4000, 44444);
        subject.addIndex(5000, 55555);
        subject.addIndex(6000, 66666);
        assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
        assertEquals(-1, subject.lessThan(-5));
        assertEquals(-1, subject.lessThan(0));
        assertEquals(-1, subject.lessThan(5));

        assertEquals(-1, subject.lessThan(11111));
        assertEquals(1000, subject.lessThan(11112));

        assertEquals(1000, subject.lessThan(22222));
        assertEquals(2000, subject.lessThan(22223));

        assertEquals(2000, subject.lessThan(33333));
        assertEquals(3000, subject.lessThan(33334));

        assertEquals(3000, subject.lessThan(44444));
        assertEquals(4000, subject.lessThan(44445));

        assertEquals(4000, subject.lessThan(55555));
        assertEquals(5000, subject.lessThan(55556));

        assertEquals(5000, subject.lessThan(66666));
        assertEquals(6000, subject.lessThan(66667));

        assertEquals(6000, subject.lessThan(Long.MAX_VALUE));

        //update
        subject.update(1000);

        assertEquals(-1, subject.lessThan(11111));
        assertEquals(0, subject.lessThan(11112));

        assertEquals(0, subject.lessThan(22222));
        assertEquals(1000, subject.lessThan(22223));

        assertEquals(1000, subject.lessThan(33333));
        assertEquals(2000, subject.lessThan(33334));

        assertEquals(2000, subject.lessThan(44444));
        assertEquals(3000, subject.lessThan(44445));

        assertEquals(3000, subject.lessThan(55555));
        assertEquals(4000, subject.lessThan(55556));

        assertEquals(4000, subject.lessThan(66666));
        assertEquals(5000, subject.lessThan(66667));

        subject.update(1000);

        assertEquals(-1, subject.lessThan(11111));
        assertEquals(-1, subject.lessThan(11112));

        assertEquals(-1, subject.lessThan(22222));
        assertEquals(0, subject.lessThan(22223));

        assertEquals(0, subject.lessThan(33333));
        assertEquals(1000, subject.lessThan(33334));

        assertEquals(1000, subject.lessThan(44444));
        assertEquals(2000, subject.lessThan(44445));

        assertEquals(2000, subject.lessThan(55555));
        assertEquals(3000, subject.lessThan(55556));

        assertEquals(3000, subject.lessThan(66666));
        assertEquals(4000, subject.lessThan(66667));

        // clear
        subject.clear();
        assertEquals(-1, subject.lessThan(11111));
        assertEquals(-1, subject.lessThan(11112));

        assertEquals(-1, subject.lessThan(22222));
        assertEquals(-1, subject.lessThan(22223));

        assertEquals(-1, subject.lessThan(33333));
        assertEquals(-1, subject.lessThan(33334));

        assertEquals(-1, subject.lessThan(44444));
        assertEquals(-1, subject.lessThan(44445));

        assertEquals(-1, subject.lessThan(55555));
        assertEquals(-1, subject.lessThan(55556));

        assertEquals(-1, subject.lessThan(66666));
        assertEquals(-1, subject.lessThan(66667));

    }

    @Test
    public void testArrayListIndex2()
    {
        ///
        /// Tests ArrayListIndex with a random set of positions/values
        ///

        RecordBufferIndex.ArrayListIndex subject =
                new RecordBufferIndex.ArrayListIndex();

        int[] positions = randomIntProgression(1000, false);
        long[] values = randomLongProgression(1000);

        for (int i = 0; i < values.length; i++)
        {
            subject.addIndex(positions[i], values[i]);
            checkIndex(subject, sub(positions,0,i+1), sub(values,0,i+1));
        }

        checkUpdateIndex(subject, positions, values);
    }

    @Test
    public void testArrayListIndex3()
    {
        ///
        /// Tests ArrayListIndex.toString()
        ///

        RecordBufferIndex.ArrayListIndex subject =
                new RecordBufferIndex.ArrayListIndex();

        int[] positions = new int[] {111,222,333};
        long[] values = new long[] { 666666, 777777, 888888};

        for (int i = 0; i < values.length; i++)
        {
            subject.addIndex(positions[i], values[i]);
        }

        String expected =
                "ArrayListIndex:[[111, 666666][222, 777777][333, 888888]]";
        assertEquals(expected, subject.toString());
    }


    @Test
    public void testSparseBufferIndex()
    {
        ///
        /// Test a sparse buffer index covering ever value in the
        /// stride
        ///

        int STRIDE = 1000;
        RecordBufferIndex.UpdatableIndex subject =
                new RecordBufferIndex.SparseBufferIndex(STRIDE);

        // no values
        assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
        assertEquals(-1, subject.lessThan(-5));
        assertEquals(-1, subject.lessThan(0));
        assertEquals(-1, subject.lessThan(5));
        assertEquals(-1, subject.lessThan(Long.MAX_VALUE));
        subject.update(Integer.MIN_VALUE);
        subject.update(Integer.MAX_VALUE);
        subject.clear();


        // first value
        subject.addIndex(555, 1);
        assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
        assertEquals(-1, subject.lessThan(-5));
        assertEquals(-1, subject.lessThan(0));
        assertEquals(-1, subject.lessThan(1));
        assertEquals(555, subject.lessThan(2));
        assertEquals(555, subject.lessThan(Long.MAX_VALUE));

        //skip sparse values
        for(long val=2; val< (STRIDE-1); val++)
        {
            subject.addIndex(666, val);
            assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
            assertEquals(-1, subject.lessThan(-5));
            assertEquals(-1, subject.lessThan(0));
            assertEquals(-1, subject.lessThan(1));
            assertEquals(555, subject.lessThan(2));
            assertEquals(555, subject.lessThan(val));
            assertEquals(555, subject.lessThan(val+1));
            assertEquals(555, subject.lessThan(Long.MAX_VALUE));
        }

        // next sparse value
        subject.addIndex(777, 1 + STRIDE);
        assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
        assertEquals(-1, subject.lessThan(-5));
        assertEquals(-1, subject.lessThan(0));
        assertEquals(-1, subject.lessThan(1));
        assertEquals(555, subject.lessThan(2));
        assertEquals(555, subject.lessThan(1 + STRIDE));
        assertEquals(777, subject.lessThan(2 + STRIDE));
        assertEquals(777, subject.lessThan(Long.MAX_VALUE));

        // update
        subject.update(555);
        assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
        assertEquals(-1, subject.lessThan(-5));
        assertEquals(-1, subject.lessThan(0));
        assertEquals(-1, subject.lessThan(1));
        assertEquals(0, subject.lessThan(2));
        assertEquals(0, subject.lessThan(1 + STRIDE));
        assertEquals(222, subject.lessThan(2 + STRIDE));
        assertEquals(222, subject.lessThan(Long.MAX_VALUE));

        subject.update(1);
        assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
        assertEquals(-1, subject.lessThan(-5));
        assertEquals(-1, subject.lessThan(0));
        assertEquals(-1, subject.lessThan(1));
        assertEquals(-1, subject.lessThan(2));
        assertEquals(-1, subject.lessThan(1 + STRIDE));
        assertEquals(221, subject.lessThan(2 + STRIDE));
        assertEquals(221, subject.lessThan(Long.MAX_VALUE));

        //clear
        subject.clear();
        assertEquals(-1, subject.lessThan(-5));
        assertEquals(-1, subject.lessThan(0));
        assertEquals(-1, subject.lessThan(1));
        assertEquals(-1, subject.lessThan(2));
        assertEquals(-1, subject.lessThan(1 + STRIDE));
        assertEquals(-1, subject.lessThan(2 + STRIDE));
        assertEquals(-1, subject.lessThan(Long.MAX_VALUE));
    }

    @Test
    public void testSparseBufferIndex2()
    {
        ///
        /// Test a sparse buffer index dsampling values within the stride
        ///
        long STRIDE = (long) (Math.random() * 10000000000L);
        RecordBufferIndex.UpdatableIndex subject =
                new RecordBufferIndex.SparseBufferIndex(STRIDE);

        checkSparseBufferIndex(subject, STRIDE);
    }

    protected static void checkNullIndex(RecordBufferIndex.UpdatableIndex subject)
    {
        ///
        /// check that an index is null-functioning
        ///

        ///
        /// added values should be ignored
        ///
        assertEquals(-1, subject.lessThan(Long.MAX_VALUE));
        assertEquals(-1, subject.lessThan(0));

        for(int i=0; i<1000; i ++)
        {
            subject.addIndex(999, 100);
            assertEquals(-1, subject.lessThan(Long.MAX_VALUE));
            assertEquals(-1, subject.lessThan(0));
        }

        subject.addIndex(0, Long.MAX_VALUE);

        assertEquals(-1, subject.lessThan(Long.MAX_VALUE));
        assertEquals(-1, subject.lessThan(0));

        ///
        /// should support full interface
        ///
        subject.update(3243);
        subject.clear();
    }

    private static void checkIndex(RecordBufferIndex.UpdatableIndex subject,
                                         List<Integer> indexedPositions,
                                         List<Long> indexedValues)
    {
        int[] positions = new int[indexedPositions.size()];
        long[] values = new long[indexedValues.size()];
        for (int i = 0; i < positions.length; i++)
        {
            positions[i] = indexedPositions.get(i);
            values[i] = indexedValues.get(i);

        }
        checkIndex(subject, positions, values);
    }

    private static void checkIndex(RecordBufferIndex subject,
                           int[] indexedPositions, long[] indexedValues)
    {
        ///
        /// checks that an index functions as expected
        ///
        assertEquals(-1, subject.lessThan(Long.MIN_VALUE));

        int lastPos=-1;
        for (int i = 0; i < indexedPositions.length; i++)
        {
            int pos = indexedPositions[i];
            long val = indexedValues[i];

            assertEquals(lastPos, subject.lessThan(val));
            assertEquals(pos, subject.lessThan(val+1));

            lastPos = pos;
        }
    }

    // Note: destructive to subject
    private static void checkUpdateIndex(RecordBufferIndex.UpdatableIndex subject,
                                   List<Integer> indexedPositions,
                                   List<Long> indexedValues)
    {
        int[] positions = new int[indexedPositions.size()];
        long[] values = new long[indexedValues.size()];
        for (int i = 0; i < positions.length; i++)
        {
            positions[i] = indexedPositions.get(i);
            values[i] = indexedValues.get(i);

        }
        checkUpdateIndex(subject, positions, values);
    }

    // Note: destructive to subject
    private static void checkUpdateIndex(RecordBufferIndex.UpdatableIndex subject,
                                 int[] indexedPositions, long[] indexedValues)
    {

        ///
        /// checks that a progressively updated index
        /// functions as expected.
        ///
        checkIndex(subject, indexedPositions, indexedValues);

        int[] mutatedPositions = Arrays.copyOf(indexedPositions, indexedPositions.length);
        long[] mutatedValues = Arrays.copyOf(indexedValues, indexedValues.length);

        while (mutatedPositions.length > 0)
        {
            int pos = mutatedPositions[0];

            // update so first pos is 0;
            subject.update(pos);
            shiftValues(mutatedPositions, pos);
            checkIndex(subject, mutatedPositions, mutatedValues);

            //update so first pos falls off
            subject.update(1);
            mutatedPositions = trimFirstValue(mutatedPositions);
            mutatedValues = trimFirstValue(mutatedValues);
            shiftValues(mutatedPositions, 1);
            checkIndex(subject, mutatedPositions, mutatedValues);

        }

    }

    protected static void checkSparseBufferIndex(
            RecordBufferIndex.UpdatableIndex subject,
            long stride)
    {
        ///
        /// Tests that a sparse index selectively indexes values
        /// in accordance with the given stride
        ///

        long VALUES_STEP = Math.max(stride/10, 1);
        int INDEX_STEP = (int) (Math.random() * 1000);

        // no values
        assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
        assertEquals(-1, subject.lessThan(-5));
        assertEquals(-1, subject.lessThan(0));
        assertEquals(-1, subject.lessThan(5));
        assertEquals(-1, subject.lessThan(Long.MAX_VALUE));
        subject.update(Integer.MIN_VALUE);
        subject.update(Integer.MAX_VALUE);
        subject.clear();

        List<Integer> expectedIndexedPositions = new ArrayList<>(11);
        List<Long> expectedIndexedValues = new ArrayList<>(11);

        // first value
        long FIRST_VALUE= (long) (Math.random() * 10000000);
        int FIRST_INDEX_POS = (int) (Math.random() * 100000);
        subject.addIndex(FIRST_INDEX_POS, FIRST_VALUE);
        expectedIndexedPositions.add(FIRST_INDEX_POS);
        expectedIndexedValues.add(FIRST_VALUE);
        assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
        assertEquals(-1, subject.lessThan(FIRST_VALUE));
        assertEquals(FIRST_INDEX_POS, subject.lessThan(FIRST_VALUE + 1));
        assertEquals(FIRST_INDEX_POS, subject.lessThan(Long.MAX_VALUE));

        checkIndex(subject, expectedIndexedPositions, expectedIndexedValues);

        // next 10
        int lastIndexPosition = FIRST_INDEX_POS;
        long lastValue = FIRST_VALUE;
        for (int index=0; index<10; index++)
        {
            int oneBack = lastIndexPosition;
            int twoback = (index>0) ? lastIndexPosition-INDEX_STEP : -1;
            // values withing stride of last value ignored
            for(long value=lastValue+1; value<(lastValue+stride); value+=VALUES_STEP)
            {
                subject.addIndex(lastIndexPosition+1, value);
                assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
                assertEquals(twoback, subject.lessThan(lastValue));
                assertEquals(oneBack, subject.lessThan(lastValue + 1));
                assertEquals(oneBack, subject.lessThan(Long.MAX_VALUE));
            }

            // values at/past the stride should be indexed
            int nextIndexPos = lastIndexPosition + INDEX_STEP;
            long nextValue = lastValue + stride;
            subject.addIndex(nextIndexPos, nextValue);
            expectedIndexedPositions.add(nextIndexPos);
            expectedIndexedValues.add(nextValue);
            assertEquals(-1, subject.lessThan(Long.MIN_VALUE));
            assertEquals(twoback, subject.lessThan(lastValue));
            assertEquals(oneBack, subject.lessThan(lastValue + 1));
            assertEquals(oneBack, subject.lessThan(nextValue));
            assertEquals(nextIndexPos, subject.lessThan(nextValue + 1));
            assertEquals(nextIndexPos, subject.lessThan(Long.MAX_VALUE));
            checkIndex(subject, expectedIndexedPositions, expectedIndexedValues);

            lastIndexPosition = nextIndexPos;
            lastValue = nextValue;
        }

        // index should be updateable
        checkUpdateIndex(subject, expectedIndexedPositions,
                expectedIndexedValues); // destructive to subject
    }



    private static int[] sub(int[] original, int from, int length)
    {
        int[] copy = new int[length];
        System.arraycopy(original, from, copy, 0, copy.length);
        return copy;
    }

    private static long[] sub(long[] original, int from, int length)
    {
        long[] copy = new long[length];
        System.arraycopy(original, from, copy, 0, copy.length);
        return copy;
    }

    private static int[] trimFirstValue(int[] original)
    {
        int[] copy = new int[original.length-1];
        System.arraycopy(original, 1, copy, 0, copy.length);
        return copy;
    }

    private static long[] trimFirstValue(long[] original)
    {
        long[] copy = new long[original.length-1];
        System.arraycopy(original, 1, copy, 0, copy.length);
        return copy;
    }

    private static void shiftValues(int[] values, int offset)
    {
        for (int i = 0; i < values.length; i++)
        {
            values[i] = values[i]-offset;
        }
    }

    private static int[] randomIntProgression(int size, boolean allowDuplicates)
    {
        int[] values = new int[size];

        // ensure room!
        values[0] = (int) (Math.random() * Integer.MAX_VALUE-size);
        int MAX_STEP = (int) (Math.random() * (Integer.MAX_VALUE -values[0])/size);

        for (int i = 1; i < values.length; i++)
        {
            final int step;
            if(allowDuplicates)
            {
                step = (int) (Math.random() * MAX_STEP);
            }
            else
            {
                step = (int) (Math.random() * (MAX_STEP-1)) + 1;
            }
            values[i] = values[i-1] + step;
        }


        return values;
    }

    private static long[] randomLongProgression(int size)
    {
        long[] values = new long[size];

        // ensure room!
        values[0] = (long) (Math.random() * Long.MAX_VALUE-size);
        long MAX_STEP = (long) (Math.random() * (Long.MAX_VALUE -values[0])/size);

        for (int i = 1; i < values.length; i++)
        {
            long step = (long) (Math.random() * MAX_STEP);
            values[i] = values[i-1] + step;
        }


        return values;
    }

}