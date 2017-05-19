package icecube.daq.performance.binary.store;

import icecube.daq.omicrond.prototype.sender.cache.spool.RecordSpool;
import icecube.daq.performance.binary.buffer.IndexFactory;
import icecube.daq.performance.binary.record.RecordReader;
import icecube.daq.performance.binary.record.UTCRecordReader;
import icecube.daq.performance.binary.store.impl.AutoPruningRecordStore;
import icecube.daq.performance.binary.store.impl.ExpandingMemoryRecordStore;
import icecube.daq.performance.binary.store.impl.RecordValidator;
import icecube.daq.performance.binary.store.impl.RingBufferRecordStore;
import icecube.daq.performance.binary.store.impl.SplitStore;
import icecube.daq.performance.common.PowersOfTwo;

import java.io.File;
import java.io.IOException;

import static icecube.daq.performance.binary.record.RecordReader.LongField;
import static icecube.daq.performance.binary.store.RecordStore.OrderedWritable;
import static icecube.daq.performance.binary.store.RecordStore.Prunable;
import static icecube.daq.performance.binary.record.UTCRecordReader.UTCField;

/**
 * Organizes record store construction into categories.
 *
 * Characteristics to consider when building a record store are:
 * <dl>
 * <dt>Record Type</dt>
 * <dd>The format of the record, in particular the location of the ordering
 * field</dd>
 * <dt>Storage Type</dt>
 * <dd>Data can be stored in memory, files or a combination of both.</dd>
 * <dt>Storage Size</dt>
 * <dd>The storage size can be fixed or dynamic.</dd>
 * <dt>Indexing</dt>
 * <dd>The data can be indexed at increasing granularity to aid queries.</dd>
 * <dt>Pruning</dt>
 * <dd>An automated eviction strategy can be selected (sized-based, time-based
 *     or pruned based on the progression of the query stream).</dd>
 * </dl>
 */
public class RecordStoreFactory
{

    /**
     * File based store, aka hitspool.
     */
    public static class FILE
    {

        /**
         * @param utcOrderedRecord Defines the record type.
         * @param indexFactory Specifies an indexing strategy.
         * @param directory The directory of the store.
         * @param fileInterval The span of time each file will hold.
         * @param numFiles The maximum number of files before the spool
         *                 rolls over and files are overwritten.
         * @param synchronize Whether to synchronize the result. When composing
         *                    stores only the outer instances requires
         *                    synchronization.
         * @return A file-based record spool.
         * @throws IOException An error creating the spool.
         */
        public static <T extends RecordReader & UTCRecordReader>
        OrderedWritable createFileSpool(T utcOrderedRecord,
                                        IndexFactory indexFactory,
                                        File directory,
                                        long fileInterval,
                                        int numFiles,
                                        boolean synchronize)
                throws IOException
        {
            OrderedWritable spool = createFileSpool(utcOrderedRecord,
                    new UTCField(utcOrderedRecord),
                    indexFactory, directory, fileInterval, numFiles);

            if(synchronize)
            {
                return RecordStore.synchronizedStore(spool);
            }
            else
            {
                return spool;
            }

        }

        private static OrderedWritable
        createFileSpool(RecordReader recordReader, LongField orderingField,
                        IndexFactory indexFactory, File directory,
                        long fileInterval, int numFiles)
                throws IOException
        {
            return new RecordSpool(recordReader, orderingField,
                    directory,  fileInterval, numFiles, indexFactory);
        }

    }


    /**
     * In-Memory record stores.
     *
     * Records can be stored in a ring buffer or in expanding flat memory.
     * The client is responsible for applying an appropriate pruning strategy.
     *
     */
    public static class MEMORY
    {

        /**
         * @param utcOrderedRecord Defines the record type.
         * @param indexFactory Specifies an indexing strategy.
         * @param size The size of the ring.
         * @param synchronize Whether to synchronize the result. When composing
         *                    stores only the outer instances requires
         *                    synchronization.
         * @return An in-memory ring-based store.
         */
        public static <T extends RecordReader & UTCRecordReader>
        Prunable createRing(T utcOrderedRecord, IndexFactory indexFactory,
                            PowersOfTwo size, boolean synchronize)
        {
            Prunable ring = createRing(utcOrderedRecord,
                    new UTCField(utcOrderedRecord), indexFactory, size);
            if(synchronize)
            {
                return RecordStore.synchronizedStore(ring);
            }
            else
            {
                return ring;
            }
        }

        /**
         * @param utcOrderedRecord Defines the record type.
         * @param indexFactory Specifies an indexing strategy.
         * @param incrementSize The size of each memory segment.
         * @param synchronize Whether to synchronize the result. When composing
         *                    stores only the outer instances requires
         *                    synchronization.
         * @return An in-memory store using an expanding list of flat memory
         *         segments
         */
        public static <T extends RecordReader & UTCRecordReader>
        Prunable createExpandingArray(T utcOrderedRecord,
                                      IndexFactory indexFactory,
                                      int incrementSize, boolean synchronize)
        {
            Prunable expandingArray = createExpandingArray(utcOrderedRecord,
                    new UTCField(utcOrderedRecord), indexFactory,
                    incrementSize);
            if (synchronize)
            {
                return RecordStore.synchronizedStore(expandingArray);
            }
            else
            {
                return expandingArray;
            }
        }

        private static Prunable createRing(final RecordReader recordReader,
                                           final LongField orderingField,
                                           final IndexFactory indexFactory,
                                           PowersOfTwo size)
        {
            return new RingBufferRecordStore(recordReader, orderingField,
                    indexFactory, size );
        }

        private static Prunable
        createExpandingArray(final RecordReader recordReader,
                             final LongField orderingField,
                             final IndexFactory indexFactory,
                             int incrementSize)
        {
            return  new ExpandingMemoryRecordStore(recordReader, orderingField,
                    incrementSize, indexFactory);
        }

    }


    /**
     * Wrap a record store so that it is automatically pruned in reaction
     * to data queries.
     *
     * This models the original in-memory store implemented as a list
     * of DomHits held within the Sender object.
     */
    public static class QUERY_PRUNED
    {

        /**
         * @param delegate The backing store to wrap.
         * @param synchronize Whether to synchronize the result. When composing
         *                    stores only the outer instances requires
         *                    synchronization.
         * @return An in-memory ring-buffer store automatically pruned in
         *         reaction to data queries
         */
        public static OrderedWritable wrap(Prunable delegate,
                                           boolean synchronize)
        {
            AutoPruningRecordStore autoPruned =
                    new AutoPruningRecordStore(delegate);

            if (synchronize)
            {
                return RecordStore.synchronizedStore(autoPruned);
            }
            else
            {
                return autoPruned;
            }
        }

    }


    /**
     * A specialized store that uses a primary in-memory store and a secondary
     * on-disk store. records will be written to both stores. Data readouts
     * will be resolved against the in-memory primary first, falling back to
     * the on-disk store for records that have expired from the in-memory
     * store.
     */
    public static class READ_THROUGH
    {

        /**
         * @param utcOrderedRecord Defines the record type.
         * @param primary The in-memory store.
         * @param secondary The on-disk store.
         * @param minSpan The minimum data span that will be held in the
         *                primary store;
         * @param maxSpan The maximum data span that will be held in the
         *                primary store;
         * @param synchronize Whether to synchronize the result. When composing
         *                    stores only the outer instances requires
         *                    synchronization.
         * @return A read-through store utilizing the primary and secondary
         *         stores.
         */
        public static <T extends RecordReader & UTCRecordReader>
        OrderedWritable createReadThrough(T utcOrderedRecord,
                                             final Prunable primary,
                                             final OrderedWritable secondary,
                                             final long minSpan,
                                             final long maxSpan,
                                             boolean synchronize)
        {
            OrderedWritable readThrough = createReadThrough(utcOrderedRecord,
                    new UTCField(utcOrderedRecord), primary, secondary,
                    minSpan, maxSpan);
            if (synchronize)
            {
                return RecordStore.synchronizedStore(readThrough);
            }
            else
            {
                return readThrough;
            }
        }

        private static OrderedWritable
        createReadThrough(final RecordReader recordReader,
                             final LongField orderingField,
                             final Prunable primary,
                             final OrderedWritable secondary,
                             final long minSpan,
                             final long maxSpan)
        {
            return  new SplitStore(recordReader, orderingField,
                    primary, secondary, minSpan, maxSpan );
        }


    }


    /**
     * Adds length and ordering validation to a store.
     *
     * Used for development and debug to diagnose record management issues.
     */
    public static class VALIDATING
    {

        /**
         * @param utcOrderedRecord Defines the record type.
         * @param store The underlying store.
         * @param synchronize Whether to synchronize the result. When composing
         *                    stores only the outer instances requires
         *                    synchronization.
         */
        public static <T extends RecordReader & UTCRecordReader>
        OrderedWritable wrapStore(T utcOrderedRecord,
                                  final OrderedWritable store,
                                  boolean synchronize)
        {
            OrderedWritable validated = wrapStore(utcOrderedRecord,
                    new UTCField(utcOrderedRecord), store);
            if (synchronize)
            {
                return RecordStore.synchronizedStore(validated);
            }
            else
            {
                return validated;
            }
        }

        private static OrderedWritable
        wrapStore(final RecordReader recordReader,
                          final LongField orderingField,
                          final OrderedWritable store)
        {
            OrderedWritable lenValidate =
                    RecordValidator.lengthValidate(recordReader, store);
            OrderedWritable orderValidate =
                    RecordValidator.orderValidate(orderingField, lenValidate);
            return orderValidate;
        }

    }

}
