package icecube.daq.spool;

import icecube.daq.performance.binary.buffer.IndexFactory;
import icecube.daq.performance.binary.buffer.RangeSearch;
import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBufferIndex;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.binary.record.RecordReader;
import icecube.daq.performance.binary.store.RecordStore;
import icecube.daq.performance.common.BufferContent;
import org.apache.log4j.Logger;
import sun.nio.ch.DirectBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Implements a file-based record spool with readout support.
 *
 * Records are typically DAQHit records time-ordered by UTC timestamps
 * in 1/10th nanos, although any binary structure that defines a length
 * and ordering field can be spooled.
 *
 * Based on FilesHitSpool.java
 *
 * todo: extend "unmap" functionality to MemoryMode.SHARED_VIEW.
 */
public class RecordSpool implements RecordStore.OrderedWritable
{
    private static final Logger logger = Logger.getLogger(RecordSpool.class);

     /**
      * Defines the minimum required record structure for
      * iterating, spool rotation and range searching.
      */
    private final RecordReader recordReader;
    private final RecordReader.LongField orderingField;

    /** Provides indexing capability to spool files. */
    private final IndexFactory indexMode;

    /** Implements a range based readout of spool data. */
    private final RangeSearch search;

    /** Directory holding the spool files and metadata database. */
    private final File targetDirectory;

    /** Manages creation and rotation of spool files along with metadata. */
    private final FileBundle files;

    /**
     * Constructor with all parameters.
     *
     * @param recordReader Defines the basic record structure.
     * @param orderingField Defines the field by which the records are ordered,
     *                      (typically the UTC timestamp in 1/10 nanos).
     * @param topDir Top-level directory which holds hitspool directory.
     * @param fileInterval Range of values spanned by each spool file,
     *                     (typically UTC time in 1/10 nanos)
     * @param maxNumberOfFiles Number of files in the spooling ensemble.
     * @param indexMode Provides the indexing strategy for the spool files.
     * @throws java.io.IOException An error creating the spool directory or
     * accessing the metadata database.
     */
    public RecordSpool(final RecordReader recordReader,
                       final RecordReader.LongField orderingField,
                       final File topDir,
                       final long fileInterval, final int maxNumberOfFiles,
                       final IndexFactory indexMode)
            throws IOException
    {
        this.recordReader = recordReader;
        this.orderingField = orderingField;
        this.indexMode = indexMode;
        search = new RangeSearch.LinearSearch(recordReader, orderingField);

        // make sure hitspool directory exists
        if (topDir == null) {
            throw new IOException("Top directory cannot be null");
        }
        targetDirectory = new File(topDir, "hitspool");
        createDirectory(topDir);
        createDirectory(targetDirectory);

        try
        {
            files = new FileBundle(recordReader, orderingField, targetDirectory,
                    fileInterval, maxNumberOfFiles, indexMode);
        }
        catch (SQLException sqle)
        {
            throw new IOException(sqle);
        }
    }

    @Override
    public void store(final ByteBuffer buffer) throws IOException
    {
        final long t = orderingField.value(buffer, 0);
        files.write(buffer, t);
    }

    @Override
    public RecordBuffer extractRange(final long from, final long to)
            throws IOException
    {
        // NOTE: Read clients have no mechanism to synchronize against
        //       writes to the backing data after this method returns,
        //       Therefor a memory copy is utilized here.
        return files.queryFiles(targetDirectory.getPath(), from, to,
                RecordBuffer.MemoryMode.COPY);
    }

    @Override
    public void extractRange(final Consumer<RecordBuffer> target,
                             final long from, final long to) throws IOException
    {
        // NOTE: Per contract, clients may not access data outside the scope
        //       of the callback method. This synchronization block protects
        //       the stability of the data throughout the callback. Therefor
        //       a shared memory view is utilized here.
        synchronized (this)
        {
            RecordBuffer shared =
                    files.queryFiles(targetDirectory.getPath(), from, to,
                            RecordBuffer.MemoryMode.SHARED_VIEW);

            target.accept(shared);
        }
    }

    @Override
    public void forEach(final Consumer<RecordBuffer> action,
                        final long from, final long to) throws IOException
    {
        // NOTE: Per contract, clients may not access data outside the scope
        //       of the callback method. This synchronization block protects
        //       the stability of the data throughout the callback. Therefor
        //       a shared memory view is utilized here.
        synchronized (this)
        {
            RecordBuffer shared =
                    files.queryFiles(targetDirectory.getPath(), from, to,
                            RecordBuffer.MemoryMode.SHARED_VIEW);

            shared.eachRecord(recordReader).forEach(action);
        }
    }

    @Override
    public int available()
    {
        return Integer.MAX_VALUE;
    }

    @Override
    public void closeWrite() throws IOException
    {
        files.close();
    }

    private static void createDirectory(File path)
            throws IOException
    {
        // ensure existing path is not a file
        if (path.exists()) {
            if (!path.isDirectory()) {
               throw new IOException("Can not create directory due to an" +
                       " existing file:[" + path.getCanonicalPath() + "]");
            }
        }

        // create the directory if needed
        if (!path.exists() && !path.mkdirs()) {
            throw new IOException("Cannot create " + path);
        }
    }


    /**
     * Manages the hitspool files and metadata database.
     */
    static class FileBundle
    {
        // hitspool directory
        private final File directory;

        // Spooling configuration
        private final long fileInterval;
        private final int maxNumberOfFiles;

        // Defines the minimum structure of the data required to
        // support range searches.
        private final RecordReader recordReader;
        private final RecordReader.LongField orderingField;
        private final RangeSearch search;

        // Database connection
        private final Metadata metadata;

        // Current spool file
        private MemoryMappedSpoolFile currentFile;
        private String currentFileName = NO_FILE;
        private long currentFileStartTick = Long.MAX_VALUE;

        // The value at which a data write will roll to the next
        // spool file.
        private long nextRollValue = Long.MIN_VALUE;

        // most recently written value
        private long prevT;

        // last read start value
        private long lastReadPoint = Long.MIN_VALUE;

        // index support
        private int currentPosition = 0;
        private RecordBufferIndex.UpdatableIndex currentIndex;
        private final IndexFactory indexMode;

        private final SpoolFileIndexCache indexCache =
                new SpoolFileIndexCache(MAX_CACHED_INDEXES);

        private static final int MAX_CACHED_INDEXES = Integer.getInteger(
                "icecube.daq.spool.RecordSpool.max-cached-indexes", 100);

        // pool of recently-mapped inactive files
        MappedBufferPool memoryMappedPool =
                new MappedBufferPool(MAX_MAPPED_FILES);

        // The number of inactive files whose mappings should be kept
        // in memory
        private static final int MAX_MAPPED_FILES = 2;


        // The allocation size for the memory mapped spool file
        private static final int BLOCK_SIZE = 20 * 1024 * 1024;

        // Sentinel values for when there is no current file
        private static final String NO_FILE = "NO_FILE";


        FileBundle(final RecordReader recordReader,
                   final RecordReader.LongField orderingField,
                   final File directory,
                   long fileInterval,
                   int maxNumberOfFiles,
                   final IndexFactory indexMode) throws SQLException
        {
            this.recordReader = recordReader;
            this.orderingField = orderingField;
            this.directory = directory;
            this.fileInterval = fileInterval;
            this.maxNumberOfFiles = maxNumberOfFiles;
            this.indexMode = indexMode;

            this.search =
                    new RangeSearch.LinearSearch(recordReader, orderingField);

            this.metadata = new Metadata(directory);
        }

        /**
         * Close output file and quit
         * NOTE: there could be multiple End-Of-Streams
         *
         * @throws IOException if the output stream could not be closed
         */
        synchronized void close()
                throws IOException
        {
            closeCurrentFile();

            //todo meta data should be left open for trailing reads. In
            //     order to save us from a readClose() type method on
            //     RecordStore ... consider removing close() from metadata
            //     and manage the prepared statement and connection
            //     lifecycle internal to that class.
            //metadata.close();
        }


        /**
         * Close current spool file if present and update metadata and
         * sentinel values.
         * @throws IOException
         */
        private void closeCurrentFile() throws IOException
        {
            if(currentFile == null)
            {
                return;
            }

            IOException savedEx = null;
            try {
                currentFile.close();
            } catch (IOException ioe) {
                savedEx = ioe;
            }

            // flush current Metadata
            // update final file's stop time
            metadata.updateStop(currentFileName, prevT);

            currentFile = null;
            currentFileName = NO_FILE;
            currentFileStartTick = Long.MAX_VALUE;

            if (savedEx != null) {
                throw new IOException(savedEx);
            }

        }

        /**
         * Create a new spool file, metadata and index info.
         * @throws IOException
         */
        private void openNewFile(long t, final String fileName)
                throws IOException
        {
            // close current file
            closeCurrentFile();


            // write new hitspool metadata
            metadata.write(fileName, t, fileInterval);

            // open new file
            File newFile = new File(directory, fileName);
            currentFile = MemoryMappedSpoolFile.createMappedSpoolFile(newFile,
                    BLOCK_SIZE);
            currentFileName = fileName;
            currentFileStartTick = t;

            // create indexing info
            currentPosition = 0;
            currentIndex = indexMode.newIndex();
            indexCache.cache(fileName, currentIndex,
                    currentFileStartTick, lastReadPoint);
        }

        /**
         * Write the buffer to the proper hitspool file
         *
         * @param buf hit data
         * @param t hit time
         */
        synchronized void write(ByteBuffer buf, long t)
                throws IOException
        {
            // roll file when needed
            if(t > nextRollValue)
            {
                // get the next file number
                final int fileNo =
                        (int) ((t / fileInterval) % maxNumberOfFiles);

                openNewFile(t, getFileName(fileNo));

                nextRollValue = ((t / fileInterval) + 1) * fileInterval - 1;
            }

            int numWritten = buf.limit();

            // finally ready to write the hit data to the hitspool file
            currentFile.write(buf);

            // save hit time so metadata can record last hit in file
            prevT = t;

            // maintain indexing of current file
            currentIndex.addIndex(currentPosition, t);
            currentPosition += numWritten;
        }

        /**
         * Extract a range of data from the spool.
         * @param targetDirectory The directory of the files.
         * @param from The beginning of the range.
         * @param to The end of the range.
         * @param mode Directive for whether to return a copy of the data
         *             or a shared view via a memory mapped file.
         * @return A record buffer containing the data.
         * @throws IOException An error accessing the data.
         */
        synchronized RecordBuffer queryFiles(final String targetDirectory,
                                             final long from, final long to,
                                             final RecordBuffer.MemoryMode mode)
                throws IOException
        {

            // track the latest read point
            lastReadPoint = from;

            // Note: Unlike SplitStore, each tick maps to a unique spool file,
            // so a particular value can not have duplicates that span files.
            // hence the >= segmenting comparison.

            // segment the query
            //
            if(from >= currentFileStartTick)
            {
                return queryActiveFile(from, to, mode);
            }
            else if (to < currentFileStartTick)
            {
                return queryInactiveFiles(targetDirectory, from, to, mode);
            }
            else
            {
                // a mixed read
                RecordBuffer inactive = queryInactiveFiles(targetDirectory,
                        from, currentFileStartTick-1, mode);
                RecordBuffer active = queryActiveFile(currentFileStartTick,
                        to, mode);

                RecordBuffer[] parts = {inactive, active};
                return RecordBuffers.chain(parts);
            }
        }

        /**
         * Extract a range of data from the active spool file.
         * @param from The beginning of the range.
         * @param to The end of the range.
         * @param mode Directive for whether to return a copy of the data
         *             or a shared view via a memory mapped file.
         * @return A record buffer containing the data.
         */
        private RecordBuffer queryActiveFile(final long from, final long to,
                                             final RecordBuffer.MemoryMode mode)
                throws IOException
        {
            return  search.extractRange(currentFile, mode, currentIndex,
                from, to);
        }

        /**
         * Extract a range of data from inactive spool files.
         *
         * Note: This is a relatively expensive implementation due to:
         *
         *          The metadata query is repeated each invocation.
         *          The file mapping is repeated each invocation.
         *          Inactive or older files are not indexed.
         *
         *       This should not be an issue since readouts from the inactive
         *       files should be rare. If this assumption changes and support
         *       for sustained, high readout rate of inactive data ranges
         *       is required, there are more efficient implementation
         *       strategies available:
         *
         *          The metadata can be cached and reused.
         *
         *          The file mappings can be cached and reused.
         *
         *          For large data requests spanning 3 or more inactive
         *          files, an index can be constructed based on the first
         *          value in each file to prevent iterating the internal
         *          files.
         *
         *          A reduced granularity index could be maintained
         *          (in-memory or on-disk) for older files.
         *
         * @param targetDirectory The directory of the files.
         * @param from The beginning of the range.
         * @param to The end of the range.
         * @param mode Directive for whether to return a copy of the data
         *             or a shared view via a memory mapped file.
         * @return A record buffer containing the data.
         * @throws IOException An error accessing the data.
         */
        private RecordBuffer queryInactiveFiles(final String targetDirectory,
                                            final long from, final long to,
                                            final RecordBuffer.MemoryMode mode)
                throws IOException
        {

            // Note: This list is assumed ordered temporally
            final List<Metadata.HitSpoolRecord> records =
                    metadata.listRecords(from, to);

            // Perform a range query on each file
            List<RecordBuffer> views = new LinkedList<RecordBuffer>();
            int size = 0;
            for(Metadata.HitSpoolRecord record : records)
            {
                String file = record.filename;
                RecordBufferIndex index = indexCache.get(file);

                // Older (unindexed) file queries are expected to be rare,
                // log a warning.
                if(index == null)
                {
                    String msg = String.format("Unindexed read of file %s" +
                            ", req [%d-%d], lastReadPoint[%d]",
                            file, from, to, lastReadPoint);
                    logger.warn(msg);
                    index = new RecordBufferIndex.NullIndex();
                }

                final RecordBuffer fileContent;

                // Be defensive. This method can only handle inactive files.
                // Appearance of the active file indicates that the caller
                // did not partition the query correctly or the meta database
                // is not in agreement with the program state.
                if(file.equals(currentFileName))
                {
                    throw new Error("Inactive range query" +
                            " ["+from + "-" + to + "] included active spool" +
                            " file " + currentFileName);
                }
                else
                {
                    // Note: We map the whole file, but only pages hit
                    //       by the index should actually result in a
                    //       file read or memory load.
                    //
                    //       If we are using a copy mode, we set up a forced
                    //       un-mapping of the buffer to conserve system
                    //       memory.
                    final boolean safeToManage =
                            (mode == RecordBuffer.MemoryMode.COPY);
                    ByteBuffer onDisk =
                            memoryMappedPool.getMappedBuffer(targetDirectory,
                            file, safeToManage);
                    fileContent = RecordBuffers.wrap(onDisk,
                            BufferContent.ZERO_TO_CAPACITY);
                }
                RecordBuffer view = search.extractRange(fileContent, mode,
                        index, from, to);
                views.add(view);
                size+=view.getLength();
            }

            RecordBuffer[] segments =
                    views.toArray(new RecordBuffer[views.size()]);
            return RecordBuffers.chain(segments);
        }

        /**
         * Map File number to file name.
         */
        private static String getFileName(int num)
        {
            if (num < 0) {
                throw new Error("File number cannot be less than zero" +
                        " [" + num + "]");
            }

            return String.format("HitSpool-%d.dat", num);
        }

    }



    /**
     * Holds pool from previously mapped files so that we can periodically
     * force an unmap.
     *
     * This implementation balances two goals:
     *
     * 1. Maintain file mapping for a period so that sequential readouts are
     *    efficient
     * 2. Control the ammount of memory used for stale file mappings.
     *
     * The underlying behavior of (most sun-based) JVMs is to keep the file
     * mapping until the mapped buffer is garbage collected.  This covers
     * goal #1, but can lead to significant memory used for stale file mappings.
     *
     * To deal with that, we will periodically force an unmapping of old
     * pool. Caller must be aware of this and utilize mapping references in
     * a "one-at-a-time" fashion.
     */
    static class MappedBufferPool
    {
        private HashMap<File, MappedByteBuffer> pool = new HashMap<>();
        private final int maxPooledFiles;

        MappedBufferPool(final int maxPooledFiles)
        {
            this.maxPooledFiles = maxPooledFiles;
        }

        /**
         * Get a memory-mapped buffer for a file from the pool, creating the
         * mapping if required. As a side effect, older mappings will be
         * forcibly unmapped.  Caller must be aware of this and utilize
         * mapping references in a "one-at-a-time" fashion.
         *
         * @param directory File directory.
         * @param filename File name.
         * @param manage if true the buffer will be managed and automatically
         *               unmapped at an indeterminate time in the future (but
         *               not before a subsequent call to this method requesting
         *               a different mapping). If false, the mapping will
         *               remain valid until the buffer reference is garbage
         *               collected.
         * @return A memory-mapped ByteBuffer of the file content.
         * @throws IOException An error accessing the file.
         */
        ByteBuffer getMappedBuffer(String directory, String filename,
                                         boolean manage)
                throws IOException
        {
            File file = new File( directory + "/" + filename );

            if (!manage)
            {
                return loadFile(file);
            }

            MappedByteBuffer mbb = pool.get(file);
            if(mbb != null)
            {
                return mbb.slice();
            }
            else
            {
                if (pool.size() >= maxPooledFiles)
                {
                    purge();
                }

                mbb = loadFile(file);
                pool.put(file, mbb);
                return mbb.slice();
            }
        }

        /**
         * Un-map all pooled pool and remove from mappings.
         */
        private void purge()
        {

            for(Map.Entry<File,MappedByteBuffer> entry : pool.entrySet())
            {
                forceUnmap(entry.getValue());
            }

            pool.clear();
        }

        /**
         * load a file into a memory-mapped buffer.  The buffer is released
         * by the garbage collector when the buffer is no longer referenced,
         * UNLESS explicitly unmapped by forceUnmap.
         *
         * @param file The file.
         * @return A memory-mapped ByteBuffer of the file content.
         * @throws IOException An error accessing the file.
         */
        private static MappedByteBuffer loadFile(File file)
                throws IOException
        {
            FileChannel fc =
                    FileChannel.open(file.toPath(), StandardOpenOption.READ);
            MappedByteBuffer bb = fc.map( FileChannel.MapMode.READ_ONLY, 0,
                    file.length() );
            fc.close();

            return bb;
        }

        /**
         * Force a memory-mapped buffer to be unmapped.
         *
         * ALERT: This method utilizes some trickery to accomplish what the
         *        java engineers have deemed unsafe. Caller must ensure that
         *        the buffer is never utilized after this call.
         *
         * @param buffer The mapped buffer to unmap.
         */
        private static void forceUnmap(MappedByteBuffer buffer)
        {
            try
            {
                sun.misc.Cleaner cleaner = ((DirectBuffer) buffer).cleaner();
                cleaner.clean();
            }
            catch (Throwable th)
            {
                logger.error("Error unmapping buffer", th);
            }
        }

    }

    /**
     * Caches indexes for inactive spool files.
     *
     * This implementation balances memory utilization will the need
     * for efficient querying of data for ranges earlier than the active
     * spool file.
     *
     */
    static class SpoolFileIndexCache
    {
        private final int maxCached;

        //MUST be a linked map for pruning mechanism.
        private final LinkedHashMap<String, SpoolFileIndex> fileIndexes;


        static class SpoolFileIndex
        {
            final String fileName;
            final RecordBufferIndex index;
            final long startValue;

            SpoolFileIndex(final String fileName,
                           final RecordBufferIndex index, final long startTick)
            {
                this.fileName = fileName;
                this.index = index;
                this.startValue = startTick;
            }
        }
        SpoolFileIndexCache(final int maxCached)
        {
            this.maxCached = maxCached;
            fileIndexes = new LinkedHashMap<>(maxCached);
        }

        void cache(final String fileName, final RecordBufferIndex index,
                   final long startValue, long lastReadValue)
        {
            prune(lastReadValue);

            if(fileIndexes.size() >= maxCached)
            {
                // This should prune the oldest inactive file index.
                for( String file : fileIndexes.keySet())
                {
                    fileIndexes.remove(file);
                    break;
                }
            }
            fileIndexes.put(fileName,
                    new SpoolFileIndex(fileName, index, startValue));
        }

        RecordBufferIndex get(final String fileName)
        {
            SpoolFileIndex record = fileIndexes.get(fileName);
            if(record == null)
            {
                return null;
            }
            else
            {
                return record.index;
            }
        }

        void prune(long pruneValue)
        {

            // make a list of indexes of spools with data ranges
            // earlier that the pruneValue.  This is dependent on fifo
            // iteration.
            //
            // we don't know the range of a spool file until we see the
            // start tick of the next file, hence the lastRecord shenanigans.
            List<SpoolFileIndex> toPrune = new ArrayList<>(fileIndexes.size());
            SpoolFileIndex lastRecord = null;
            for(Map.Entry<String,SpoolFileIndex> entry: fileIndexes.entrySet())
            {
                SpoolFileIndex curRecord = entry.getValue();
                if(curRecord.startValue >= pruneValue)
                {
                    break;
                }
                else
                {
                    if(lastRecord != null)
                    {
                        toPrune.add(lastRecord);
                    }
                    lastRecord = curRecord;
                }
            }

            for (SpoolFileIndex record : toPrune)
            {
                fileIndexes.remove(record.fileName);
            }
        }
    }


}