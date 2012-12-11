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
    private int  maxNumberOfFiles;
    private int  currentFileIndex;
    private File targetDirectory;
    private OutputStream dataOut;
    private long t;
    private long t0;
    private long fileInterval;
    private DOMRegistry reg;
    private boolean packHeaders = false;
    private byte[] iobuf;
    
    /**
     * Constructor with full options.
     * @param out   BufferConsumer object that will receive forwarded hits.  Can be null.
     * @param targetDir output directory on filesystem 
     * @param hitsPerFile number of hits per file
     * @param fileCount number of files in the spooling ensemble
     * @see BufferConsumer
     */
    public FilesHitSpool(BufferConsumer out, File targetDir, long fileInterval, int fileCount) throws IOException
    {
        this.out = out;
        this.t0  = 0L;
        this.dataOut            = null;
        this.currentFileIndex   = -1;
        this.fileInterval       = fileInterval;
        this.targetDirectory    = targetDir;
        this.maxNumberOfFiles   = fileCount;
        this.packHeaders        = Boolean.getBoolean("icecube.daq.stringhub.hitspool,packHeaders");
        
        try {
            this.reg = DOMRegistry.loadRegistry(System.getenv("PDAQ_HOME"));
        } catch (Exception x) {
            this.reg = null;
        }
        
        iobuf = new byte[5000];
    }
    
    public FilesHitSpool(BufferConsumer out, File targetDir, long hitsPerFile) throws IOException
    {
        this(out, targetDir, hitsPerFile, 100);
    }
    
    public FilesHitSpool(BufferConsumer out, File targetDir) throws IOException
    {
        this(out, targetDir, 100000L);
    }
    
    private void transform(ByteBuffer buf)
    {
        int cpos = 0;
        // Here's your chance to compactify the buffer
        if (reg != null && packHeaders)
        {
            buf.putShort(0, (short)(0xC000 | buf.getLong(0)));
            long mbid = buf.getLong(8);
            short chid = reg.getChannelId(String.format("%012.12x", mbid));
            buf.putShort(2, chid);
            buf.putLong(4, buf.getLong(24));
            // pack in the version (bytes 34-35) and the PED / ATWD-chargestamp flags (36-37)
            buf.putShort((short)((buf.getShort(34) & 0xff) | ((buf.getShort(36) & 0xff) << 8)));
            buf.get(iobuf, 0, 14);
            buf.position(38);
            cpos = 14;
        }
        buf.get(iobuf, cpos, buf.remaining());
    }

    public void consume(ByteBuffer buf) throws IOException
    {
        if (null != out) out.consume(buf);
        
        // bytes 24 .. 31 hold the 64-bit UTC clock value
        t = buf.getLong(24);
        
        if (t == Long.MAX_VALUE) 
        {
            // this is the END-OF-DATA marker - close file and quit
            // note there could be multiple EODs
            if (dataOut != null) {
                dataOut.close();
                dataOut = null;
            }
            return;
        }
        
        if (t0 == 0L) t0 = t;
        long deltaT = t - t0;
        int fileNo = ((int) (deltaT / fileInterval)) % maxNumberOfFiles; 
        
        if (fileNo != currentFileIndex)
        {
            currentFileIndex = fileNo;
            openNewFile();
        }
                
        // now I should be free to pack the buffer, if that is the
        // behavior desired.
        transform(buf);

        dataOut.write(iobuf);
    }

    private void openNewFile() throws IOException
    {
        String fileName = "HitSpool-" + currentFileIndex + ".dat";
        File newFile = new File(targetDirectory, fileName);
        File infFile = new File(targetDirectory, "info.txt");
        
        FileOutputStream ostr = new FileOutputStream(infFile);
        FileLock lock = ostr.getChannel().lock();
        PrintStream info = new PrintStream(ostr);
        
        if (dataOut != null) { dataOut.flush(); dataOut.close(); }
        try 
        {
            info.println(String.format("T0   %20d", t0));
            info.println(String.format("CURT %20d", t));
            info.println(String.format("IVAL %20d", fileInterval));
            info.println(String.format("CURF %4d", currentFileIndex));
            info.println(String.format("MAXF %4d", maxNumberOfFiles));
        }
        finally
        {
            lock.release();
            info.close();
        }
        dataOut = new BufferedOutputStream(new FileOutputStream(newFile), 32768); 
    }
}
