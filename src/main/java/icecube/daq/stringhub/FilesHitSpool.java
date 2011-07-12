package icecube.daq.stringhub;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.log4j.Logger;

import icecube.daq.bindery.BufferConsumer;

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
    
    public FilesHitSpool(BufferConsumer out, File targetDir, long hitsPerFile) 
    {
        this.out = out;
        this.hitsPerFile = hitsPerFile;
        this.targetDirectory = targetDir;
        openNewFile();
    }
    
    public void consume(ByteBuffer buf) throws IOException
    {
        // TODO Auto-generated method stub
        if (currentNumberOfHits++ == hitsPerFile)
        {
            ch.close();
            openNewFile();
        }
        while (buf.remaining() > 0) ch.write(buf);
        buf.rewind();
        out.consume(buf);
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
