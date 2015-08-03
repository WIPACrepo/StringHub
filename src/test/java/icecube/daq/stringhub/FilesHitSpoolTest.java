package icecube.daq.stringhub;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.stringhub.test.MockAppender;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

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

        System.clearProperty(FilesHitSpool.PACK_HEADERS_PROPERTY);
    }

    @After
    public void tearDown() throws Exception
    {
        assertEquals("Bad number of log messages",
                     0, appender.getNumberOfMessages());
    }

    private void checkSpoolDir(File topDir, String runDir, long firstTime,
                               long curTime, long interval, int firstNum,
                               int numFiles, int maxNumberOfFiles)
        throws IOException
    {
        File path = new File(topDir, runDir);
        if (!path.exists()) {
            fail(runDir + " does not exist");
        }

        int lastNum = 0;
        ArrayList<String> expected = new ArrayList<String>();
        for (int i = 0; i < numFiles; i++) {
            int num = (firstNum + i) % maxNumberOfFiles;
            String name = "HitSpool-" + num + ".dat";
            if (!expected.contains(name)) {
                expected.add(name);
            }
            lastNum = num;
        }

        // check metadata file
        expected.add("info.txt");
        validateInfoTxt(path, firstTime, curTime, interval, lastNum,
                            maxNumberOfFiles);

        // make sure that expected files (and *only* those files) are present
        for (String name : path.list()) {
            if (!expected.remove(name)) {
                fail("Found unexpected file \"" + name + "\"");
            }
        }
        assertEquals("Missing data files" + expected,
                     0, expected.size());
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

    private static void validateInfoTxt(File spoolDir, long firstTime,
                                        long curTime, long interval,
                                        int curFile, int maxFiles)
        throws IOException
    {
        File path = new File(spoolDir, "info.txt");
        assertTrue("info.txt does not exist", path.exists());

        BufferedReader rdr = new BufferedReader(new FileReader(path));
        while (true) {
            String line = rdr.readLine();
            if (line == null) {
                break;
            }

            String[] flds = line.split("\\s+", 2);

            long val;
            try {
                val = Long.parseLong(flds[1]);
            } catch (NumberFormatException nfe) {
                fail("Cannot parse info.txt field \"" + flds[0] +
                     "\" value \"" + flds[1] + "\"");
                break; // compiler doesn't know that we never get here
            }

            if (flds[0].equals("T0")) {
                assertEquals("Bad " + flds[0] + " value", firstTime, val);
            } else if (flds[0].equals("CURT")) {
                assertEquals("Bad " + flds[0] + " value", curTime, val);
            } else if (flds[0].equals("IVAL")) {
                assertEquals("Bad " + flds[0] + " value", interval, val);
            } else if (flds[0].equals("CURF")) {
                assertEquals("Bad " + flds[0] + " value", curFile, (int) val);
            } else if (flds[0].equals("MAXF")) {
                assertEquals("Bad " + flds[0] + " value", maxFiles, (int) val);
            } else {
                fail("Unknown field " + flds[0]);
            }
        }

        rdr.close();
    }

    private static void validateInfoDB(File path)
    {
        fail("Unimplemented");
    }

    @Test
    public void testNormal()
        throws IOException
    {
        File configDir = null;
        File targetDir = createTempDirectory();
        long fileInterval = 10000000000L;
        int fileCount = 5;
        FilesHitSpool hitspool = new FilesHitSpool(null, configDir, targetDir,
                                                   fileInterval, fileCount);

        final int runNumber = 123456;
        hitspool.startRun(runNumber);

        // make sure only the 'currentRun' directory was created
        for (String name : targetDir.list()) {
            assertEquals("Unexpected file \"" + name + "\"",
                         "currentRun", name);
        }

        final long startTime = 1195000000L;
        final long endTime = startTime + fileInterval;
        final long dt = (fileInterval / 10) * 3;

        TimeWriter tw = new TimeWriter(hitspool, fileInterval, fileCount);
        tw.write(startTime, endTime, dt);

        checkSpoolDir(targetDir, "currentRun", startTime, tw.curTime,
                      fileInterval, 0, tw.numFiles, fileCount);

        // write another chunk of data
        hitspool.switchRun(runNumber + 1);

        // make sure both 'currentRun' and 'lastRun' exist
        for (String name : targetDir.list()) {
            if (!name.equals("currentRun") && !name.equals("lastRun")) {
                fail("Unexpected file \"" + name + "\"");
            }
        }

        final long startAgain = endTime + (fileInterval / 10);
        final long endAgain = startAgain + fileInterval;
        final long dt2 = (fileInterval / 2);

        TimeWriter tw2 = new TimeWriter(hitspool, fileInterval, fileCount);
        tw2.write(startAgain, endAgain, dt2);

        // check new data
        checkSpoolDir(targetDir, "currentRun", startAgain, tw2.curTime,
                      fileInterval, 0, tw2.numFiles, fileCount);

        // reverify old data
        checkSpoolDir(targetDir, "lastRun", startTime, tw.curTime,
                      fileInterval, 0, tw.numFiles, fileCount);

        // rotate directories again and do final verification
        hitspool.switchRun(runNumber + 10);
        checkSpoolDir(targetDir, "lastRun", startAgain, tw2.curTime,
                      fileInterval, 0, tw2.numFiles, fileCount);
    }


    @Test
    public void testRollover()
        throws IOException
    {
        final File configDir =
            new File(getClass().getResource("/config").getPath());

        File targetDir = createTempDirectory();
        long fileInterval = 10000000000L;
        int fileCount = 5;
        FilesHitSpool hitspool = new FilesHitSpool(null, configDir, targetDir,
                                                   fileInterval, fileCount);
        hitspool.startRun(123456);

        final long startTime = 1195000000L;
        final long endTime = startTime + (fileInterval * (fileCount + 1));
        final long dt = (fileInterval / 10) * 3;

        TimeWriter tw = new TimeWriter(hitspool, fileInterval, fileCount);
        tw.write(startTime, endTime, dt);

        // make sure only the 'currentRun' directory was created
        for (String name : targetDir.list()) {
            assertEquals("Unexpected file \"" + name + "\"",
                         "currentRun", name);
        }

        checkSpoolDir(targetDir, "currentRun", startTime, tw.curTime,
                      fileInterval, 0, tw.numFiles, fileCount);
    }

    @Test
    public void testPackHeaders()
        throws IOException
    {
        final File configDir =
            new File(getClass().getResource("/config").getPath());

        System.setProperty(FilesHitSpool.PACK_HEADERS_PROPERTY, "true");

        CountingConsumer cc = new CountingConsumer();
        File targetDir = createTempDirectory();
        long fileInterval = 10000000000L;
        int fileCount = 5;
        FilesHitSpool hitspool = new FilesHitSpool(cc, configDir, targetDir,
                                                   fileInterval, fileCount);
        hitspool.startRun(123456);

        final long startTime = 1195000000L;
        final long endTime = startTime + (fileInterval * (fileCount + 1));
        final long dt = (fileInterval / 10) * 3;

        TimeWriter tw = new TimeWriter(hitspool, fileInterval, fileCount);
        tw.write(startTime, endTime, dt);

        // make sure only the 'currentRun' directory was created
        for (String name : targetDir.list()) {
            assertEquals("Unexpected file \"" + name + "\"",
                         "currentRun", name);
        }

        assertEquals("Unexpected number of hits consumed",
                     (endTime - startTime) / dt, cc.getNumberConsumed());

        checkSpoolDir(targetDir, "currentRun", startTime, tw.curTime,
                      fileInterval, 0, tw.numFiles, fileCount);
    }

    @Test
    public void testShortBuffer()
        throws IOException
    {
        CountingConsumer cc = new CountingConsumer();
        FilesHitSpool hitspool = new FilesHitSpool(cc, null, null);

        ByteBuffer buf = ByteBuffer.allocate(30);
        hitspool.consume(buf);

        // make sure buffer was passed along before error was found
        assertEquals("Buffer was not passed to consumer",
                     1, cc.getNumberConsumed());

        // validate error message
        assertEquals("Unexpected number of log messages",
                     1, appender.getNumberOfMessages());
        final String expMsg =
            String.format("Skipping short buffer (%d bytes)", buf.limit());
        assertEquals("Bad log message",
                     expMsg, (String) appender.getMessage(0));
        appender.clear();
    }

    @Test
    public void testEmptyBuffer()
        throws IOException
    {
        CountingConsumer cc = new CountingConsumer();
        FilesHitSpool hitspool = new FilesHitSpool(cc, null, null);

        ByteBuffer buf = ByteBuffer.allocate(38);
        buf.limit(0);
        hitspool.consume(buf);

        // make sure buffer was passed along before error was found
        assertEquals("Buffer was not passed to consumer",
                     1, cc.getNumberConsumed());
    }
}

class TimeWriter
{
    private FilesHitSpool hitspool;
    private long fileInterval;
    private int fileCount;

    private ByteBuffer buf = ByteBuffer.allocate(100);

    long curTime = Long.MAX_VALUE;
    int numFiles;

    TimeWriter(FilesHitSpool hitspool, long fileInterval, int fileCount)
    {
        this.hitspool = hitspool;
        this.fileInterval = fileInterval;
        this.fileCount = fileCount;
    }

    private static ByteBuffer buildHit(ByteBuffer buf, int index, long time)
    {
        final long domId = 0x09aa4670f9feL;

        buf.clear();
        buf.putInt(0, 100);
        buf.putInt(4, 302);
        buf.putLong(8, domId);
        buf.putLong(16, index);
        buf.putLong(24, time);
        for (int i = 32; i < 100; i++) {
            buf.put(i, (byte) i);
        }
        return buf;
    }

    void write(long startTime, long endTime, long dt)
       throws IOException
    {
        int curFile = Integer.MIN_VALUE;

        int index = 0;
        for (long t = startTime; t < endTime; t += dt) {
            // save time when file number changes
            final int tmpNum = (int) ((t - startTime) / fileInterval);
            final int newNum = tmpNum % fileCount;
            if (newNum != curFile) {
                curTime = t;
                curFile = newNum;
                numFiles++;
            }

            hitspool.consume(buildHit(buf, index++, t));
        }
    }
}

class CountingConsumer
    implements BufferConsumer
{
    private int numConsumed;

    public void consume(ByteBuffer buf)
    {
        numConsumed++;
    }

    public void endOfStream(long token)
    {
        // do nothing
    }

    public int getNumberConsumed()
    {
        return numConsumed;
    }
}
