package icecube.daq.stringhub;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.stringhub.test.MockAppender;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

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
        System.clearProperty(FilesHitSpool.UNIFIED_CACHE_PROPERTY);
    }

    @After
    public void tearDown() throws Exception
    {
        assertEquals("Bad number of log messages",
                     0, appender.getNumberOfMessages());
    }

    @Test
    public void testNullTopDir()
        throws IOException
    {
        try {
            new FilesHitSpool(null, null, null);
            fail("This should fail");
        } catch (IOException ioe) {
            final String errMsg = "Top directory cannot be null";
            assertEquals("Unexpected exception",
                         errMsg, ioe.getMessage());
        }
    }

    @Test
    public void testBadConfigDir()
        throws IOException
    {
        final File badConfigDir = new File("/does/not/compute");

        new FilesHitSpool(null, badConfigDir, new File("/tmp"));

        // nothing logged if headers are not packed
        assertEquals("Bad number of log messages",
                     0, appender.getNumberOfMessages());

        System.setProperty(FilesHitSpool.PACK_HEADERS_PROPERTY, "true");

        new FilesHitSpool(null, badConfigDir, new File("/tmp"));

        assertEquals("Bad number of log messages",
                     1, appender.getNumberOfMessages());
        final String expMsg = "Failed to load registry from " + badConfigDir +
            "; headers will not be packed";
        assertEquals("Bad log message",
                     expMsg, (String) appender.getMessage(0));
        appender.clear();
    }

    @Test
    public void testNormal()
        throws IOException
    {
        Runner r = new Runner(10000000000L, 5, false);

        r.create();
        r.startRun();

        final long startTime = 1195000000L;
        final long endTime = startTime + r.fileInterval();
        final long timeStep = (r.fileInterval() / 10) * 3;

        r.writeHits(startTime, endTime, timeStep);

        r.switchRun();

        final long startAgain = endTime + (r.fileInterval() / 10);
        final long endAgain = startAgain + r.fileInterval();
        final long timeStep2 = (r.fileInterval() / 2);

        r.writeHits(startAgain, endAgain, timeStep2);

        // rotate directories again and do final verification
        r.switchRun();
    }

    @Test
    public void testNormalUnified()
        throws IOException
    {
        Runner r = new Runner(10000000000L, 5, true);

        r.create();
        r.startRun();

        final long startTime = 1195000000L;
        final long endTime = startTime + r.fileInterval();
        final long timeStep = (r.fileInterval() / 10) * 3;

        r.writeHits(startTime, endTime, timeStep);

        r.switchRun();

        final long startAgain = endTime + (r.fileInterval() / 10);
        final long endAgain = startAgain + r.fileInterval();
        final long timeStep2 = (r.fileInterval() / 2);

        r.writeHits(startAgain, endAgain, timeStep2);

        // rotate directories again and do final verification
        r.switchRun();
    }

    @Test
    public void testRollover()
        throws IOException
    {
        Runner r = new Runner(10000000000L, 5, false);

        r.create();
        r.startRun();

        final long startTime = 1195000000L;
        final long endTime = startTime + (r.fileInterval() *
                                          (r.maxNumberOfFiles() + 1));
        final long timeStep = (r.fileInterval() / 10) * 3;

        r.writeHits(startTime, endTime, timeStep);
    }

    @Test
    public void testRolloverUnified()
        throws IOException
    {
        Runner r = new Runner(10000000000L, 5, true);

        r.create();
        r.startRun();

        final long startTime = 1195000000L;
        final long endTime = startTime + (r.fileInterval() *
                                          (r.maxNumberOfFiles() + 1));
        final long timeStep = (r.fileInterval() / 10) * 3;

        r.writeHits(startTime, endTime, timeStep);
    }

    @Test
    public void testPackHeaders()
        throws IOException
    {
        Runner r = new Runner(10000000000L, 5, false);
        r.packHeaders();

        r.create();
        r.startRun();

        final long startTime = 1195000000L;
        final long endTime = startTime + (r.fileInterval() *
                                          (r.maxNumberOfFiles() + 1));
        final long timeStep = (r.fileInterval() / 10) * 3;

        r.writeHits(startTime, endTime, timeStep);
    }

    @Test
    public void testShortBuffer()
        throws IOException
    {
        CountingConsumer cc = new CountingConsumer();
        FilesHitSpool hitspool = new FilesHitSpool(cc, null, new File("/tmp"));

        ByteBuffer buf = ByteBuffer.allocate(30);
        hitspool.consume(buf);

        // make sure buffer was passed along before error was found
        int numHits = 1;
        assertEquals("Buffer was not passed to consumer",
                     numHits, cc.getNumberConsumed());

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
        FilesHitSpool hitspool = new FilesHitSpool(cc, null, new File("/tmp"));

        ByteBuffer buf = ByteBuffer.allocate(38);
        buf.limit(0);
        hitspool.consume(buf);

        // make sure buffer was passed along before error was found
        int numHits = 1;
        assertEquals("Buffer was not passed to consumer",
                     numHits, cc.getNumberConsumed());
    }
}

class TimeWriter
{
    private static File dbPath;
    private static Connection conn;
    private static HashMap<String, Long> shadowDB =
        new HashMap<String, Long>();

    private long fileInterval;
    private int maxNumberOfFiles;
    private long startTime;
    private long endTime;
    private long timeStep;
    private boolean unifiedCache;

    private ByteBuffer buf = ByteBuffer.allocate(100);

    private long curTime = Long.MAX_VALUE;
    private int numFiles;

    TimeWriter(long fileInterval, int maxNumberOfFiles, long startTime,
               long timeStep, boolean unifiedCache)
    {
        this.fileInterval = fileInterval;
        this.maxNumberOfFiles = maxNumberOfFiles;
        this.startTime = startTime;
        this.timeStep = timeStep;
        this.unifiedCache = unifiedCache;
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

    void checkSpoolDir(File topDir, String runDir)
        throws IOException
    {
        final boolean isLastRun = runDir.equals("lastRun");

        File path;
        if (unifiedCache) {
            path = new File(topDir, "hitspool");
        } else {
            path = new File(topDir, runDir);
        }

        if (!path.exists()) {
            fail(runDir + " does not exist");
        }

        // first file number
        final int firstNum;
        if (unifiedCache) {
            firstNum = (int) ((startTime / fileInterval) % maxNumberOfFiles);
        } else {
            firstNum = 0;
        }

        // maximum number of cached files
        final int numToCheck;
        if (numFiles < maxNumberOfFiles) {
            numToCheck = numFiles;
        } else {
            numToCheck = maxNumberOfFiles;
        }

        ArrayList<String> expected = new ArrayList<String>();
        for (int i = 0; i < numToCheck; i++) {
            int num = (firstNum + i) % maxNumberOfFiles;
            String name = "HitSpool-" + num + ".dat";
            if (!expected.contains(name)) {
                expected.add(name);
            }
        }

        // check metadata file
        if (unifiedCache) {
            expected.add("hitspool.db");
            if (!isLastRun) {
                validateInfoDB(path, startTime, curTime);
            }
        }

        // check old metadata file
        if (!unifiedCache || FilesHitSpool.INCLUDE_OLD_METADATA) {
            // last file number
            final int lastNum = (firstNum + numFiles - 1) % maxNumberOfFiles;

            expected.add("info.txt");
            if (!unifiedCache || !isLastRun) {
                validateInfoTxt(path, startTime, curTime, fileInterval,
                                lastNum, maxNumberOfFiles);
            }
        }

        // make sure that expected files (and *only* those files) are present
        for (String name : path.list()) {
            if (!expected.remove(name) && !shadowDB.containsKey(name)) {
                fail("Found unexpected file \"" + name + "\"");
            }
        }
        assertEquals("Missing data files" + expected,
                     0, expected.size());
    }

    private void validateInfoDB(File spoolDir, long startTime, long curTime)
    {
        File path = new File(spoolDir, "hitspool.db");
        assertTrue("hitspool.db does not exist", path.exists());

        try {
            // open connection to DB
            if (dbPath == null || !dbPath.equals(path)) {
                // if we've opened a connection to a different file, close it
                if (conn != null) {
                    conn.close();
                }

                // open a connection to this file
                final String jdbcURL = "jdbc:sqlite:" + path;
                conn = DriverManager.getConnection(jdbcURL);

                // remember the path to the current DB file
                dbPath = path;

                // clear out the shadow database
                shadowDB.clear();
            }

            int prevNum = Integer.MIN_VALUE;
            for (long t = startTime; t <= curTime; t += timeStep) {
                final int num = (int) ((t / fileInterval) % maxNumberOfFiles);
                if (num != prevNum) {
                    String name = "HitSpool-" + num + ".dat";
                    shadowDB.put(name, t);
                    prevNum = num;
                }
            }

            final String sql =
                "select filename, timestamp from hitspool" +
                " order by timestamp";
            Statement stmt = conn.createStatement();
            try {
                ResultSet rs = stmt.executeQuery(sql);
                while (rs.next()) {
                    final String filename = rs.getString(1);
                    final long timestamp = rs.getLong(2);

                    assertTrue("Unexpected entry for " + filename,
                               shadowDB.containsKey(filename));

                    final long expStamp = shadowDB.get(filename);
                    assertEquals("Bad timestamp for " + filename,
                                 expStamp, timestamp);
                }
            } finally {
                stmt.close();
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Error("Cannot validate metadata", se);
        }
    }

    private static void validateInfoTxt(File spoolDir, long startTime,
                                        long curTime, long fileInterval,
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
                assertEquals("Bad " + flds[0] + " value", startTime, val);
            } else if (flds[0].equals("CURT")) {
                assertEquals("Bad " + flds[0] + " value", curTime, val);
            } else if (flds[0].equals("IVAL")) {
                assertEquals("Bad " + flds[0] + " value", fileInterval, val);
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

    void write(FilesHitSpool hitspool, long endTime)
       throws IOException
    {
        int curFile = Integer.MIN_VALUE;

        int index = 0;
        for (long t = startTime; t < endTime; t += timeStep) {
            final int newNum;
            if (unifiedCache) {
                newNum = (int) ((t / fileInterval) % maxNumberOfFiles);
            } else {
                newNum = (int) (((t - startTime) / fileInterval) %
                                maxNumberOfFiles);
            }

            // save time when file number changes
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

class Runner
{
    private boolean unifiedCache;
    private long fileInterval = 10000000000L;
    private int maxNumberOfFiles = 5;

    private File configDir;
    private File topDir;

    private CountingConsumer cc = new CountingConsumer();
    private int runNumber = 123456;

    private FilesHitSpool hitspool;

    private int totalHits;

    private TimeWriter previousWriter;
    private TimeWriter currentWriter;

    Runner(long fileInterval, int maxNumberOfFiles, boolean unifiedCache)
        throws IOException
    {
        this.fileInterval = fileInterval;
        this.maxNumberOfFiles = maxNumberOfFiles;
        this.unifiedCache = unifiedCache;

        configDir = new File(getClass().getResource("/config").getPath());
        topDir = createTempDirectory();

        if (unifiedCache) {
            System.setProperty(FilesHitSpool.UNIFIED_CACHE_PROPERTY, "true");
        }
    }

    private void checkTopDir(boolean includeLast)
    {
        for (String name : topDir.list()) {
            if (!(unifiedCache ? name.equals("hitspool") :
                  (name.equals("currentRun") ||
                   (includeLast && name.equals("lastRun")))))
            {
                fail("Unexpected file \"" + name + "\"");
            }
        }
    }

    void create()
        throws IOException
    {
        if (hitspool != null) {
            throw new Error("Hitspool has already been created!");
        }

        hitspool = new FilesHitSpool(cc, configDir, topDir, fileInterval,
                                     maxNumberOfFiles);

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

    int maxNumberOfFiles()
    {
        return maxNumberOfFiles;
    }

    long fileInterval()
    {
        return fileInterval;
    }

    void packHeaders()
    {
        System.setProperty(FilesHitSpool.PACK_HEADERS_PROPERTY, "true");
    }

    void startRun()
        throws IOException
    {
        hitspool.startRun(runNumber++);

        // check spool directories
        checkTopDir(currentWriter != null);

        // save previous run data
        if (currentWriter != null) {
            previousWriter = currentWriter;
            previousWriter.checkSpoolDir(topDir, "lastRun");
            currentWriter = null;
        }
    }

    void switchRun()
        throws IOException
    {
        // write another chunk of data
        hitspool.switchRun(runNumber++);

        // make sure both 'currentRun' and 'lastRun' exist
        checkTopDir(true);

        // save previous run data
        if (currentWriter != null) {
            previousWriter = currentWriter;
            previousWriter.checkSpoolDir(topDir, "lastRun");
            currentWriter = null;
        }
    }

    void writeHits(long startTime, long endTime, long timeStep)
        throws IOException
    {
        currentWriter = new TimeWriter(fileInterval, maxNumberOfFiles,
                                       startTime, timeStep, unifiedCache);
        currentWriter.write(hitspool, endTime);

        // verify that only expected spool directories exist
        checkTopDir(previousWriter != null);

        totalHits += (int) ((endTime - startTime + timeStep - 1) / timeStep);
        assertEquals("Unexpected number of hits consumed",
                     totalHits, cc.getNumberConsumed());

        currentWriter.checkSpoolDir(topDir, "currentRun");
        if (previousWriter != null) {
            previousWriter.checkSpoolDir(topDir, "lastRun");
        }
    }
}
