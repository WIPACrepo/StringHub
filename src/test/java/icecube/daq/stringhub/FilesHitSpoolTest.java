package icecube.daq.stringhub;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

public class FilesHitSpoolTest
{

    @Before
    public void setUp() throws Exception
    {
    }

    @Test
    public void testFilesHitSpoolRollover() throws IOException
    {
        File configDir = null;
        File targetDir = new File("hitspool-tmp");
        targetDir.mkdir();
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
