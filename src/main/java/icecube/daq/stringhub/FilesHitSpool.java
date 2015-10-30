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

    private static final Logger logger = Logger.getLogger(FilesHitSpool.class);

    private BufferConsumer out;
    private File topDir;

    private FileBundle files;
    private File targetDirectory;

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
        this.topDir = topDir;

        boolean packHeaders = Boolean.getBoolean(PACK_HEADERS_PROPERTY);
        boolean unifiedCache = Boolean.getBoolean(UNIFIED_CACHE_PROPERTY);

        // make sure hitspool directory exists
        if (topDir == null) {
            throw new IOException("Top directory cannot be null");
        }
        createDirectory(topDir);

        files = new FileBundle(configDir, fileInterval, maxNumberOfFiles,
                               unifiedCache, packHeaders);
    }

    public void consume(ByteBuffer buf) throws IOException
    {
        if (out != null) {
            // pass buffer to other consumers first
            out.consume(buf);
        }

        if (files.isHosed()) {
            return;
        }

        if (buf.limit() < 38) {
            if (buf.limit() != 0) {
                logger.error("Skipping short buffer (" + buf.limit() +
                             " bytes)");
            }
            return;
        }

        // bytes 24 .. 31 hold the 64-bit UTC clock value
        final long t = buf.getLong(24);

        // is this the END-OF-DATA marker?
        if (t == Long.MAX_VALUE) {
            files.close();
        } else {
            files.write(targetDirectory, buf, t);
        }
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

        files.close();
    }

    public static final String getFileName(int num)
    {
        if (num < 0) {
            throw new Error("File number cannot be less than zero");
        }

        return String.format("HitSpool-%d.dat", num);
    }

    /**
     * Reset internal counters
     */
    private void reset()
        throws IOException
    {
        if (files.isUnified()) {
            File newDir = new File(topDir, "hitspool");
            try {
                createDirectory(newDir);
                targetDirectory = newDir;
            } catch (IOException ioe) {
                logger.error("Cannot create " + newDir, ioe);
            }
        } else {
            synchronized (files) {
                rotateDirectories();
            }
        }
    }

    /**
     * Swap current and last directories.
     * TODO remove from New_Glarus
     *
     * @throws IOException if a file/directory cannot be created
     */
    private void rotateDirectories()
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
        files.close();

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
}

/**
 * Hitspool and metadata files
 */
class FileBundle
{
    private static final Logger logger =
        Logger.getLogger(FileBundle.class);

    private DOMRegistry registry;

    private long fileInterval;
    private int maxNumberOfFiles;
    private boolean unifiedCache;
    private boolean packHeaders;

    private OutputStream dataOut;
    private OldMetadata oldMetadata;
    private Metadata metadata;

    private int currentFileIndex;
    private long t0;

    private long prevT;
    private boolean isHosed;

    // scratch buffer used to transform input hit
    private byte[] iobuf = new byte[5000];

    FileBundle(File configDir, long fileInterval, int maxNumberOfFiles,
               boolean unifiedCache, boolean packHeaders)
    {
        this.fileInterval = fileInterval;
        this.maxNumberOfFiles = maxNumberOfFiles;
        this.unifiedCache = unifiedCache;
        this.packHeaders = packHeaders;

        loadRegistry(configDir);
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
        IOException savedEx = null;

        final int prevIndex = currentFileIndex;

        if (dataOut != null) {
            try {
                dataOut.flush();
            } catch (IOException ioe) {
                if (savedEx == null) {
                    savedEx = ioe;
                }
            }

            try {
                dataOut.close();
            } catch (IOException ioe) {
                if (savedEx == null) {
                    savedEx = ioe;
                }
            }

            dataOut = null;

            t0 = 0L;
            currentFileIndex = -1;
        }

        // flush current Metadata
        if (metadata != null) {
            // update final file's stop time
            metadata.updateStop(FilesHitSpool.getFileName(prevIndex), prevT);
            metadata.close();
            metadata = null;
        }

        if (savedEx != null) {
            throw savedEx;
        }
    }

    boolean isHosed()
    {
        return isHosed;
    }

    boolean isUnified()
    {
        return unifiedCache;
    }

    private void loadRegistry(File configDir)
    {
        if (packHeaders && configDir != null) {
            try {
                registry = DOMRegistry.loadRegistry(configDir);
            } catch (Exception x) {
                logger.error("Failed to load registry from " + configDir +
                             "; headers will not be packed", x);
                registry = null;
            }
        }

        logger.info("DOM registry " + (registry != null ? "" : "NOT") +
                    " found; header packing " + (packHeaders ? "" : "NOT") +
                    " activated.");
    }

    /**
     * Create a new hitspool data file and update the associated metadata file
     *
     * @throws IOException if there is a problem
     */
    private void openNewFile(File targetDirectory, long t, int fileNo)
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

        final String fileName = FilesHitSpool.getFileName(fileNo);

        // write old hitspool metadata
        if (!unifiedCache || FilesHitSpool.INCLUDE_OLD_METADATA) {
            if (oldMetadata == null) {
                oldMetadata = new OldMetadata(targetDirectory, fileInterval,
                                              maxNumberOfFiles, unifiedCache);
            }
            oldMetadata.write(t0, t, fileNo);
        }

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
        isHosed = false;
    }

    private int transform(ByteBuffer buf, byte[] iobuf)
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

    /**
     * Write the buffer to the proper hitspool file
     *
     * @param buf hit data
     * @param t hit time
     */
    synchronized void write(File targetDirectory, ByteBuffer buf, long t)
    {
        // get the current file number for this hit
        final int fileNo;
        if (unifiedCache) {
            fileNo = (int) ((t / fileInterval) % maxNumberOfFiles);
        } else {
            // set the base time
            if (t0 == 0L) {
                t0 = t;
            }

            fileNo = (int) (((t - t0) / fileInterval) % maxNumberOfFiles);
        }

        // if metadata needs to be updated...
        if (fileNo != currentFileIndex || t == Long.MAX_VALUE ||
            dataOut == null)
        {
            // ...update previous file's stop time
            if (metadata != null) {
                metadata.updateStop(FilesHitSpool.getFileName(currentFileIndex),
                                    prevT);
            }

            try {
                openNewFile(targetDirectory, t, fileNo);
            } catch (IOException iox) {
                logger.error("Failed to open hitspool file." +
                             " HitSpooling will be terminated.", iox);
                dataOut = null;
                isHosed = true;
                return;
            }

            // remember current file index
            currentFileIndex = fileNo;
        }

        // now I should be free to pack the buffer, if that is the
        // behavior desired.
        final int nw = transform(buf, iobuf);

        // finally ready to write the hit data to the hitspool file
        try {
            dataOut.write(iobuf, 0, nw);
        } catch (IOException iox) {
            logger.error("Write failed.  Hit spooling will be terminated.",
                         iox);
            dataOut = null;
            isHosed = true;
        }

        // save hit time so metadata can record last hit in file
        prevT = t;
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
                if (unifiedCache) {
                    info.println("T0   0");
                } else {
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
