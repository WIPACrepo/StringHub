package icecube.daq.performance.binary.buffer;

import org.junit.Test;



/**
 * Tests IndexFactory.java
 */
public class IndexFactoryTest
{

    @Test
    public void testNullIndex()
    {
        RecordBufferIndex.UpdatableIndex subject =
                IndexFactory.NO_INDEX.newIndex();

        RecordBufferIndexTest.checkNullIndex(subject);
    }

    @Test
    public void testSparseUTCIndex_NONE()
    {
        ///
        /// Tests ArrayListIndex
        ///
        RecordBufferIndex.UpdatableIndex subject =
                IndexFactory.UTCIndexMode.NONE.newIndex();

        RecordBufferIndexTest.checkNullIndex(subject);
    }

    @Test
    public void testSparseUTCIndex_SPARSE_ONE_SECOND()
    {
        ///
        /// Tests ArrayListIndex
        ///
        RecordBufferIndex.UpdatableIndex subject =
                IndexFactory.UTCIndexMode.SPARSE_ONE_SECOND.newIndex();

        RecordBufferIndexTest.checkSparseBufferIndex(subject, 10000000000L);
    }

    @Test
    public void testSparseUTCIndex_SPARSE_ONE_TENTH_SECOND()
    {
        ///
        /// Tests ArrayListIndex
        ///
        RecordBufferIndex.UpdatableIndex subject =
                IndexFactory.UTCIndexMode.SPARSE_ONE_TENTH_SECOND.newIndex();

        RecordBufferIndexTest.checkSparseBufferIndex(subject, 1000000000L);
    }

    @Test
    public void testSparseUTCIndex_SPARSE_ONE_HUNDREDTHS_SECOND()
    {
        ///
        /// Tests ArrayListIndex
        ///
        RecordBufferIndex.UpdatableIndex subject =
                IndexFactory.UTCIndexMode.SPARSE_ONE_HUNDREDTHS_SECOND.newIndex();
        RecordBufferIndexTest.checkSparseBufferIndex(subject, 100000000L);

    }


}
