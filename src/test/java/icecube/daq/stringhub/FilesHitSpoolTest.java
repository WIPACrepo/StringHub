package icecube.daq.stringhub;

import icecube.daq.stringhub.test.MockAppender;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;

import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class FilesHitSpoolTest
{
    private static final MockAppender appender = new MockAppender();

    @Before
    public void setUp() throws Exception
    {
        appender.clear();

        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);
    }

    @After
    public void tearDown() throws Exception
    {
        assertEquals("Bad number of log messages",
                     0, appender.getNumberOfMessages());
    }

    // stolen from http://stackoverflow.com/questions/617414/
    private static File createTempDirectory()
        throws IOException
    {
        final File temp =
            File.createTempFile("temp", Long.toString(System.nanoTime()));

        if (!temp.delete()) {
            throw new IOException("Could not delete temp file: " +
                                  temp.getAbsolutePath());
        }

        if (!temp.mkdir()) {
            throw new IOException("Could not create temp directory: " +
                                  temp.getAbsolutePath());
        }

        return temp;
    }

    @Test
    public void testFilesHitSpoolRollover() throws IOException
    {
        File configDir = null;
        File targetDir = createTempDirectory();
        long fileInterval = 10000000000L;
        int fileCount = 5;
        FilesHitSpool hitspool = new FilesHitSpool(null, configDir, targetDir, fileInterval, fileCount);

        // regular buffers 100 bytes long 0.3 s apart starting at 0.1195 s
        ByteBuffer buf = ByteBuffer.allocate(100);
        long t  = 1195000000L;
        long dt = 3000000000L;
        long iBuf = 0L;

        while (t < 75000000000L)
        {
            buf.clear();
            buf.putInt(0, 100);
            buf.putInt(4, 302);
            buf.putLong(8, 0x12A4775C8BE0L);
            buf.putLong(16, iBuf++);
            buf.putLong(24, t); // set the time
            for (int i = 32; i < 100; i++) buf.put(i, (byte) i);
            hitspool.consume(buf);
            t += dt;
        }
    }

}
