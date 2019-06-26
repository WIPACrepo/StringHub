package icecube.daq.spool;

import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.common.BufferContent;
import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.record.RecordReader;
import icecube.daq.performance.binary.buffer.RangeSearch;
import icecube.daq.performance.binary.store.RecordStore;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Provides query access to a directory of spooled data.
 *
 * A utility class for reading data from an inactive spool
 * of data. This class does not support querying a spool that
 * is actively being written.
 */
public class SpoolReader implements RecordStore.Ordered
{
    /**
     * Defines the format and ordering field  of the spooled records
     */
    private final RecordReader recordReader;
    private final RecordReader.LongField orderingField;
    private final RangeSearch search;

    private final File directory;
    private final Metadata metadata;


    /**
     * Constructor.
     * @param recordReader Defines the basic record structure.
     * @param orderingField Defines the field by which the records are ordered,
     *                      (typically the UTC timestamp in 1/10 nanos).
     * @param spoolDirectory directory holding the spool files
     * @throws SQLException An error connecting to the database.
     */
    public SpoolReader(final RecordReader recordReader,
                       final RecordReader.LongField orderingField,
                       final String spoolDirectory) throws SQLException
    {
        this.recordReader = recordReader;
        this.orderingField = orderingField;
        this.search = new RangeSearch.LinearSearch(recordReader, orderingField);

        this.directory = new File(spoolDirectory);

        metadata = new Metadata(directory);
    }

    @Override
    public RecordBuffer extractRange(final long from, final long to)
            throws IOException
    {
        // Note: assumed ordered temporally
        final List<Metadata.HitSpoolRecord> records =
                metadata.listRecords(from, to);

        // by copy
        List<RecordBuffer> views = new LinkedList<RecordBuffer>();
        for(Metadata.HitSpoolRecord record : records)
        {
            ByteBuffer fileBuffer = loadFile(directory, record.filename);
            RecordBuffer rb = RecordBuffers.wrap(fileBuffer,
                    BufferContent.POSITION_TO_LIMIT);
            RecordBuffer view = search.extractRange(rb,
                    RecordBuffer.MemoryMode.COPY,
                    from, to);
            views.add(view);
        }

        RecordBuffer[] segments = views.toArray(new RecordBuffer[views.size()]);
        return RecordBuffers.chain(segments);
    }

    @Override
    public void extractRange(final Consumer<RecordBuffer> target,
                             final long from, final long to)
            throws IOException
    {
        // Note: assumed ordered temporally
        final List<Metadata.HitSpoolRecord> records =
                metadata.listRecords(from, to);

        // by copy
        List<RecordBuffer> views = new LinkedList<RecordBuffer>();
        for(Metadata.HitSpoolRecord record : records)
        {
            ByteBuffer fileBuffer = loadFile(directory, record.filename);
            RecordBuffer rb = RecordBuffers.wrap(fileBuffer,
                    BufferContent.POSITION_TO_LIMIT);
            RecordBuffer view = search.extractRange(rb,
                    RecordBuffer.MemoryMode.COPY,
                    from, to);
            views.add(view);
        }

        RecordBuffer[] segments = views.toArray(new RecordBuffer[views.size()]);
        target.accept(RecordBuffers.chain(segments));
    }

    @Override
    public void forEach(final Consumer<RecordBuffer> action,
                        final long from, final long to) throws IOException
    {
        // Note: assumed ordered temporally
        final List<Metadata.HitSpoolRecord> records =
                metadata.listRecords(from, to);

        // by shared view
        List<RecordBuffer> views = new LinkedList<RecordBuffer>();
        for(Metadata.HitSpoolRecord record : records)
        {
            ByteBuffer fileBuffer = loadFile(directory, record.filename);
            RecordBuffer rb = RecordBuffers.wrap(fileBuffer,
                    BufferContent.POSITION_TO_LIMIT);
            RecordBuffer view = search.extractRange(rb,
                    RecordBuffer.MemoryMode.SHARED_VIEW,
                    from, to);
            views.add(view);
        }

        RecordBuffer[] segments = views.toArray(new RecordBuffer[views.size()]);
        RecordBuffer shared = RecordBuffers.chain(segments);

        shared.eachRecord(recordReader).forEach(action);
    }

    /**
     * load a file into a memory-mapped buffer.  The buffer is released
     * by the at finalization by the garbage collector. If use pattern
     * changes, explicit release can be coerced by non-standard means.
     *
     *
     * @param directory File directory.
     * @param filename File name.
     * @return A memory-mapped ByteBuffer of the file content.
     * @throws IOException An error accessing the file.
     */
    private static ByteBuffer loadFile(File directory, String filename)
            throws IOException
    {
        Path filePath = directory.toPath().resolve(filename);
        FileChannel fc =
                FileChannel.open(filePath, StandardOpenOption.READ);
        MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0,
                filePath.toFile().length());
        fc.close();

        return bb;
    }


}
