package icecube.daq.performance.binary.buffer;


/**
 * Provides index instantiation services.
 *
 * The choice of indexing strategy is informed by the rate and type of
 * records being stored.  Generally for UTC ordered data records this
 * will be list-based sparse indexes.
 */
public interface IndexFactory
{
    /**
     * Create a new index.
     */
    public RecordBufferIndex.UpdatableIndex newIndex();


    /**
     * Provides the NULL index.
     */
    public static IndexFactory NO_INDEX = new IndexFactory()
    {
        @Override
        public RecordBufferIndex.UpdatableIndex newIndex()
        {
            return new RecordBufferIndex.NullIndex();
        }
    };


    /**
     * Provides index implementations for UTC ordered record stores.
     */
    public static enum UTCIndexMode implements IndexFactory
    {
        /**
         * No index.
         */
        NONE(Long.MAX_VALUE)
                {
                    @Override
                    public RecordBufferIndex.UpdatableIndex newIndex()
                    {
                        return new RecordBufferIndex.NullIndex();
                    }
                },
        /**
         * A sparse index at 1/100 second granularity.
         */
        SPARSE_ONE_HUNDREDTHS_SECOND(100000000L)
                {
                    @Override
                    public RecordBufferIndex.UpdatableIndex newIndex()
                    {
                        return new RecordBufferIndex.SparseBufferIndex(indexStride);
                    }
                },
        /**
         * A sparse index at 1/10 second granularity.
         */
        SPARSE_ONE_TENTH_SECOND(1000000000L)
                {
                    @Override
                    public RecordBufferIndex.UpdatableIndex newIndex()
                    {
                        return new RecordBufferIndex.SparseBufferIndex(indexStride);
                    }
                },
        /**
         * A sparse index at 1 second granularity.
         */
        SPARSE_ONE_SECOND(10000000000L)
                {
                    @Override
                    public RecordBufferIndex.UpdatableIndex newIndex()
                    {
                        return new RecordBufferIndex.SparseBufferIndex(indexStride);
                    }
                };

        public final long indexStride;


        private UTCIndexMode(long indexStride)
        {
            this.indexStride = indexStride;
        }

        @Override
        public abstract RecordBufferIndex.UpdatableIndex newIndex();

    }
}
