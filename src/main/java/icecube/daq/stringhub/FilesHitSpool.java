package icecube.daq.stringhub;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.log4j.Logger;

import icecube.daq.bindery.BufferConsumer;

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
public class FilesHitSpool implements BufferConsumer
{
    private BufferConsumer out;
    private long hitsPerFile;
    private long currentNumberOfHits;
    private int  maxNumberOfFiles = 100;
    private int  currentFileIndex = 0;
    private File targetDirectory;
    private FileChannel ch;
    private final static Logger logger = Logger.getLogger(FilesHitSpool.class);
    
    /**
     * Constructor with full options.
     * @param out   BufferConsumer object that will receive forwarded hits.  Can be null.
     * @param targetDir output directory on filesystem 
     * @param hitsPerFile number of hits per file
     * @param fileCount number of files in the spooling ensemble
     * @see BufferConsumer
     */
    public FilesHitSpool(BufferConsumer out, File targetDir, long hitsPerFile, int fileCount) 
    {
        this.out = out;
        this.hitsPerFile = hitsPerFile;
        this.targetDirectory = targetDir;
        openNewFile();
    }
    
    public FilesHitSpool(BufferConsumer out, File targetDir, long hitsPerFile)
    {
        this(out, targetDir, hitsPerFile, 100);
    }
    
    public FilesHitSpool(BufferConsumer out, File targetDir)
    {
        this(out, targetDir, 100000L);
    }
    
    public void consume(ByteBuffer buf) throws IOException
    {
        if (currentNumberOfHits++ == hitsPerFile)
        {
            ch.close();
            openNewFile();
        }
        while (buf.remaining() > 0) ch.write(buf);
        buf.rewind();
        if (null != out) out.consume(buf);
    }

    private void openNewFile()
    {
        String fileName = "HitSpool-" + currentFileIndex + ".dat";
        File newFile = new File(targetDirectory, fileName);
        try 
        {
            RandomAccessFile raFile = new RandomAccessFile(newFile, "rw");
            raFile.seek(0L);
            ch = raFile.getChannel();
            currentFileIndex++;
            if (currentFileIndex == maxNumberOfFiles) currentFileIndex = 0;
            currentNumberOfHits = 0;
        }
        catch (IOException iox)
        {
            logger.warn(iox);
        }
    }
}
