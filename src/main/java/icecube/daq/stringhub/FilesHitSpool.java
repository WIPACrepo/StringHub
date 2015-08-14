package icecube.daq.stringhub;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.util.DOMRegistry;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileLock;
import java.sql.SQLException;

import org.apache.log4j.Logger;

/**
 * This class will accept ByteBuffers from whatever is feeding it
 * and spool the byte buffer contents (assumed to be hits but
 * really it can be anything) to a rotating list of files on
 * the filesystem.  Each file will have a certain number of buffers
 * written to it after which the file will be closed and the next
 * one opened up with a sequential identifier indicating that it is
 * the next file.  Once the maximum number of files is achieved,
 * the sequence will start from the beginning, overwriting files.
 *
 * @author kael hanson (khanson@ulb.ac.be)
 *
 */
public class FilesHitSpool
    implements BufferConsumer
{
    public static final String PACK_HEADERS_PROPERTY =
        "icecube.daq.stringhub.hitspool.packHeaders";

    public static final String UNIFIED_CACHE_PROPERTY =
        "icecube.daq.stringhub.hitspool.unified";

    public static boolean INCLUDE_OLD_METADATA = true;

    private BufferConsumer out;
    private int maxNumberOfFiles;
    private int currentFileIndex;
    private File topDir;
    private File targetDirectory;
    private OutputStream dataOut;
    private long t;
    private long t0;
    private long fileInterval;
    private DOMRegistry registry;
    private boolean packHeaders;
    private boolean unifiedCache;
    private byte[] iobuf;
    private boolean isHosed;
    private OldMetadata oldMetadata;
    private Metadata metadata;
    private long prevT;

    private static final Logger logger = Logger.getLogger(FilesHitSpool.class);

    /**
     * Minimal constructor.
     *
     * @param out BufferConsumer object that will receive forwarded hits.
     *            Can be null.
     * @param configDir directory holding configuration files
     * @param topDir top-level directory which holds hitspool directories
     *
     * @throws IOException if the target directory is null
     */
    public FilesHitSpool(BufferConsumer out, File configDir, File topDir)
        throws IOException
    {
        this(out, configDir, topDir, 100000L);
    }

    /**
     * Slightly less minimal constructor
     *
     * @param out BufferConsumer object that will receive forwarded hits.
     *            Can be null.
     * @param configDir directory holding configuration files
     * @param topDir top-level directory which holds hitspool directories
     * @param fileInterval number of DAQ ticks of objects in each file
     *
     * @throws IOException if the target directory is null
     */
    public FilesHitSpool(BufferConsumer out, File configDir, File topDir,
                         long fileInterval)
        throws IOException
    {
        this(out, configDir, topDir, fileInterval, 100);
    }

    /**
     * Constructor with all parameters.
     *
     * @param out BufferConsumer object that will receive forwarded hits.
     *            Can be null.
     * @param configDir directory holding configuration files
     * @param topDir top-level directory which holds hitspool directories
     * @param fileInterval number of DAQ ticks of objects in each file
     * @param maxNumberOfFiles number of files in the spooling ensemble
     *
     * @throws IOException if the target directory is null
     */
    public FilesHitSpool(BufferConsumer out, File configDir, File topDir,
                         long fileInterval, int maxNumberOfFiles)
        throws IOException
    {
        this.out = out;
        this.fileInterval = fileInterval;
        this.topDir = topDir;
        this.maxNumberOfFiles = maxNumberOfFiles;

        packHeaders = Boolean.getBoolean(PACK_HEADERS_PROPERTY);
        unifiedCache = Boolean.getBoolean(UNIFIED_CACHE_PROPERTY);

        if (packHeaders && configDir != null) {
            try {
                registry = DOMRegistry.loadRegistry(configDir);
            } catch (Exception x) {
                logger.error("Failed to load registry from " + configDir +
                             "; headers will not be packed", x);
                registry = null;
            }
        }

        logger.info("DOM registry " + (registry != null? "" : "NOT") +
                    " found; header packing " + (packHeaders ? "" : "NOT") +
                    " activated.");

        iobuf = new byte[5000];

        // make sure hitspool directory exists
        if (topDir == null) {
            throw new IOException("Top directory cannot be null");
        }
        createDirectory(topDir);
    }

    /**
     * Close output file and quit
     * NOTE: there could be multiple End-Of-Streams
     *
     * @throws IOException if the output stream could not be closed
     */
    private void closeAll()
        throws IOException
    {
        if (dataOut != null) {
            dataOut.flush();
            dataOut.close();
            dataOut = null;
        }

        // flush current Metadata
        if (metadata != null) {
            // update final file's stop time
            metadata.updateStop(getFileName(currentFileIndex), prevT);
            metadata.close();
            metadata = null;
        }
    }

    public void consume(ByteBuffer buf) throws IOException
    {
        if (null != out) {
            // pass buffer to other consumers first
            out.consume(buf);
        }

        if (isHosed) return;
        if (buf.limit() < 38) {
            if (buf.limit() != 0) {
                logger.error("Skipping short buffer (" + buf.limit() +
                             " bytes)");
            }
            return;
        }

        // bytes 24 .. 31 hold the 64-bit UTC clock value
        t = buf.getLong(24);

        // is this the END-OF-DATA marker?
        if (t == Long.MAX_VALUE) {
            closeAll();
            return;
        }

        // remember the first time
        if (t0 == 0L) t0 = t;

        // unified cache is not computed relative to run start
        final int fileNo;
        if (unifiedCache) {
            fileNo = (int) ((t / fileInterval) % maxNumberOfFiles);
        } else {
            fileNo = (int) (((t - t0) / fileInterval) % maxNumberOfFiles);
        }

        // make sure we write to the appropriate file
        if (fileNo != currentFileIndex || t == Long.MAX_VALUE) {
            // update previous file's stop time
            if (metadata != null) {
                metadata.updateStop(getFileName(currentFileIndex), prevT);
            }

            // remember current file index
            currentFileIndex = fileNo;
            try {
                openNewFile();
            } catch (IOException iox) {
                logger.error("openNewFile threw " + iox +
                             ". HitSpooling will be terminated.");
                dataOut = null;
                isHosed = true;
                return;
            }
        }

        // now I should be free to pack the buffer, if that is the
        // behavior desired.
        int nw = transform(buf);

        try {
            dataOut.write(iobuf, 0, nw);
        } catch (IOException iox) {
            logger.error("hit spool writing failed b/c " + iox.getMessage() +
                         ". Hit spooling will be terminated.");
            dataOut = null;
            isHosed = true;
        }

        // save hit so metadata can record last hit in file
        prevT = t;
    }

    private static void createDirectory(File path)
        throws IOException
    {
        // remove anything which isn't a directory
        if (path.exists()) {
            if (!path.isDirectory()) {
                if (!path.delete()) {
                    throw new IOException("Could not delete non-directory " +
                                          path);
                }
                logger.error("Deleted non-directory " + path);
            }
        }

        // create the directory if needed
        if (!path.exists() && !path.mkdirs()) {
            throw new IOException("Cannot create " + path);
        }
    }

    /**
     * There will be no more data.
     */
    public void endOfStream(long mbid)
        throws IOException
    {
        if (out != null) {
            out.endOfStream(mbid);
        }

        closeAll();
    }

    public static final String getFileName(int num)
    {
        return String.format("HitSpool-%d.dat", num);
    }

    /**
     * Create a new hitspool data file and update the associated metadata file
     *
     * @throws IOException if there is a problem
     */
    private synchronized void openNewFile()
        throws IOException
    {
        // close current file
        if (dataOut != null) {
            try {
                dataOut.flush();
                dataOut.close();
                dataOut = null;
            } catch (IOException ioe) {
                logger.error("Failed to close previous hitspool file", ioe);
            }
        }

        // write old hitspool metadata
        if (INCLUDE_OLD_METADATA) {
            if (oldMetadata == null) {
                oldMetadata = new OldMetadata(targetDirectory, fileInterval,
                                              maxNumberOfFiles, unifiedCache);
            }
            oldMetadata.write(t0, t, currentFileIndex);
        }

        final String fileName = getFileName(currentFileIndex);

        // write new hitspool metadata
        if (unifiedCache) {
            try {
                if (metadata == null) {
                    metadata = new Metadata(targetDirectory);
                }
                metadata.write(fileName, t, fileInterval);
            } catch (SQLException se) {
                logger.error("Cannot update metadata", se);
            }
        }

        // open new file
        File newFile = new File(targetDirectory, fileName);
        dataOut =
            new BufferedOutputStream(new FileOutputStream(newFile), 32768);
    }

    /**
     * Reset internal counters
     */
    private void reset()
        throws IOException
    {
        closeAll();

        if (unifiedCache) {
            targetDirectory = new File(topDir, "hitspool");
            try {
                createDirectory(targetDirectory);
            } catch (IOException ioe) {
                logger.error("Cannot create " + targetDirectory, ioe);
            }
        } else {
            rotateDirectories();
        }

        t0 = 0L;
        currentFileIndex = -1;
    }

    /**
     * Swap current and last directories.
     * TODO remove from New_Glarus
     *
     * synchronized to avoid clashes with openNewFile()
     *
     * @throws IOException if a file/directory cannot be created
     */
    private synchronized void rotateDirectories()
        throws IOException
    {
        // create and delete a temporary file in the hitspool directory
        File hitSpoolTemp = File.createTempFile("HitSpool", ".tmp",
                                                topDir);
        if (!hitSpoolTemp.delete()) {
            throw new IOException("Could not delete temporary file " +
                                  hitSpoolTemp);
        }

        // create and remove a temporary directory
        if (!hitSpoolTemp.exists()) {
            if (!hitSpoolTemp.mkdirs()) {
                throw new IOException("Could not create temporary directory " +
                                      hitSpoolTemp);
            }
        }
        if (!hitSpoolTemp.delete()) {
            throw new IOException("Could not delete temporary directory " +
                                  hitSpoolTemp);
        }

        // Note that renameTo and mkdir return false on failure

        // Rename lastRun to temporary directory name
        File hitSpoolLast = new File(topDir, "lastRun");
        if (hitSpoolLast.exists() && !hitSpoolLast.renameTo(hitSpoolTemp)) {
            logger.error("hitSpoolLast renameTo failed");
        }

        // Rename currentRun to lastRun
        File hitSpoolCurrent = new File(topDir, "currentRun");
        if (hitSpoolCurrent.exists() &&
            !hitSpoolCurrent.renameTo(hitSpoolLast))
        {
            logger.error("hitSpoolCurrent renameTo failed!");
        }

        // Rename lastRun to currentRun
        if (hitSpoolTemp.exists() && !hitSpoolTemp.renameTo(hitSpoolCurrent)) {
            logger.debug("hitSpoolTemp renameTo failed!");
        }

        // if currentRun doesn't exist yet, create it now
        if (!hitSpoolCurrent.exists() && !hitSpoolCurrent.mkdir()) {
            logger.error("hitSpoolCurrent mkdir failed!");
        }

        // finally set target directory to currentRun
        targetDirectory = hitSpoolCurrent;
    }

    /**
     * Start a new run
     *
     * @param runNumber run number
     *
     * @throws IOException if hitspool directories cannot be created
     */
    public void startRun(int runNumber)
        throws IOException
    {
        reset();
    }

    /**
     * Switch to a new run
     *
     * @param runNumber run number
     *
     * @throws IOException if hitspool directories cannot be created
     */
    public void switchRun(int runNumber)
        throws IOException
    {
        reset();
    }

    private int transform(ByteBuffer buf)
    {
        int cpos = 0;
        // Here's your chance to compactify the buffer
        if (registry != null && packHeaders) {
            buf.putShort(0, (short)(0xC000 | (buf.getInt(0)-24)));
            long mbid = buf.getLong(8);
            short chid = registry.getChannelId(mbid);
            buf.putShort(2, chid);
            buf.putLong(4, buf.getLong(24));
            // pack in the version (bytes 34-35) and
            // the PED / ATWD-chargestamp flags (36-37)
            buf.putShort(12, (short)((buf.getShort(34) & 0xff) |
                                     ((buf.getShort(36) & 0xff) << 8)));
            buf.get(iobuf, 0, 14);
            buf.position(38);
            cpos = 14;
        }
        int br = buf.remaining();
        buf.get(iobuf, cpos, br);
        buf.rewind();
        return br + cpos;
    }
}

/**
 * Old hitspool metadata.
 *
 * TODO remove from New_Glarus
 */
class OldMetadata
{
    private File directory;
    private long fileInterval;
    private int maxNumberOfFiles;
    private boolean unifiedCache;

    OldMetadata(File directory, long fileInterval, int maxNumberOfFiles,
                boolean unifiedCache)
    {
        this.directory = directory;
        this.fileInterval = fileInterval;
        this.maxNumberOfFiles = maxNumberOfFiles;
        this.unifiedCache = unifiedCache;
    }

    void write(long firstTime, long currentTime, int currentFileIndex)
        throws IOException
    {
        File infFile = new File(directory, "info.txt");
        FileOutputStream ostr = new FileOutputStream(infFile);
        FileLock lock = ostr.getChannel().lock();
        try {
            PrintStream info = new PrintStream(ostr);
            try {
                if (!unifiedCache) {
                    info.printf("T0   %20d\n", firstTime);
                }
                info.printf("CURT %20d\n", currentTime);
                info.printf("IVAL %20d\n", fileInterval);
                info.printf("CURF %4d\n", currentFileIndex);
                info.printf("MAXF %4d\n", maxNumberOfFiles);
            } finally {
                info.close();
            }
        } finally {
            if (lock.isValid()) {
                lock.release();
            }
        }
    }
}
