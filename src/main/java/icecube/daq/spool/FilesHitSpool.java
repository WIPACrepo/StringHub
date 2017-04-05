package icecube.daq.spool;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.util.DOMRegistryFactory;
import icecube.daq.util.IDOMRegistry;

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
     * @param topDir top-level directory which holds hitspool directories
     *
     * @throws IOException if the target directory is null
     */
    public FilesHitSpool(BufferConsumer out, File topDir)
        throws IOException
    {
        this(out, topDir, 100000L);
    }

    /**
     * Slightly less minimal constructor
     *
     * @param out BufferConsumer object that will receive forwarded hits.
     *            Can be null.
     * @param topDir top-level directory which holds hitspool directories
     * @param fileInterval number of DAQ ticks of objects in each file
     *
     * @throws IOException if the target directory is null
     */
    public FilesHitSpool(BufferConsumer out, File topDir,
                         long fileInterval)
        throws IOException
    {
        this(out, topDir, fileInterval, 100);
    }

    /**
     * Constructor with all parameters.
     *
     * @param out BufferConsumer object that will receive forwarded hits.
     *            Can be null.
     * @param topDir top-level directory which holds hitspool directories
     * @param fileInterval number of DAQ ticks of objects in each file
     * @param maxNumberOfFiles number of files in the spooling ensemble
     *
     * @throws IOException if the target directory is null
     */
    public FilesHitSpool(BufferConsumer out, File topDir,
                         long fileInterval, int maxNumberOfFiles)
        throws IOException
    {
        this.out = out;
        this.topDir = topDir;

        // make sure hitspool directories exists
        if (topDir == null) {
            throw new IOException("Top directory cannot be null");
        }
        targetDirectory = new File(topDir, "hitspool");
        createDirectory(topDir);
        createDirectory(targetDirectory);

        files = new FileBundle(fileInterval, maxNumberOfFiles);
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

}

/**
 * Hitspool and metadata files
 */
class FileBundle
{
    private static final Logger logger =
        Logger.getLogger(FileBundle.class);

    private long fileInterval;
    private int maxNumberOfFiles;

    private OutputStream dataOut;
    private Metadata metadata;

    private int currentFileIndex;
    private long t0;

    private long prevT;
    private boolean isHosed;

    FileBundle(long fileInterval, int maxNumberOfFiles)
    {
        this.fileInterval = fileInterval;
        this.maxNumberOfFiles = maxNumberOfFiles;
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

        // write hitspool metadata
        try {
            if (metadata == null) {
                metadata = new Metadata(targetDirectory);
            }
            metadata.write(fileName, t, fileInterval);
        } catch (SQLException se) {
            logger.error("Cannot update metadata", se);
        }

        // open new file
        File newFile = new File(targetDirectory, fileName);
        dataOut =
            new BufferedOutputStream(new FileOutputStream(newFile), 32768);
        isHosed = false;
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
        final int fileNo = (int) ((t / fileInterval) % maxNumberOfFiles);

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

        // finally ready to write the hit data to the hitspool file
        try {
            dataOut.write(buf.array(), 0, buf.limit());
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
