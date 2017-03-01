package icecube.daq.stringhub;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.common.MockAppender;

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
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);
    }

    @After
    public void tearDown() throws Exception
    {
        appender.assertNoLogMessages();
    }

    @Test
    public void testNullTopDir()
        throws IOException
    {
        try {
            new FilesHitSpool(null, null);
            fail("This should fail");
        } catch (IOException ioe) {
            final String errMsg = "Top directory cannot be null";
            assertEquals("Unexpected exception",
                         errMsg, ioe.getMessage());
        }
    }

    @Test
    public void testNormal()
        throws IOException
    {
        Runner r = new Runner(10000000000L, 5);

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
        Runner r = new Runner(10000000000L, 5);

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
        FilesHitSpool hitspool = new FilesHitSpool(cc, new File("/tmp"));

        ByteBuffer buf = ByteBuffer.allocate(30);
        hitspool.consume(buf);

        // make sure buffer was passed along before error was found
        int numHits = 1;
        assertEquals("Buffer was not passed to consumer",
                     numHits, cc.getNumberConsumed());

        // validate error message
        final String expMsg =
            String.format("Skipping short buffer (%d bytes)", buf.limit());
        appender.assertLogMessage(expMsg);
        appender.assertNoLogMessages();
    }

    @Test
    public void testEmptyBuffer()
        throws IOException
    {
        CountingConsumer cc = new CountingConsumer();
        FilesHitSpool hitspool = new FilesHitSpool(cc, new File("/tmp"));

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
    private static HashMap<String, DBTimes> shadowDB =
        new HashMap<String, DBTimes>();

    private long fileInterval;
    private int maxNumberOfFiles;
    private long startTime;
    private long endTime;
    private long timeStep;

    private ByteBuffer buf = ByteBuffer.allocate(100);

    private long curTime = Long.MAX_VALUE;
    private int numFiles;

    TimeWriter(long fileInterval, int maxNumberOfFiles, long startTime,
               long timeStep)
    {
        this.fileInterval = fileInterval;
        this.maxNumberOfFiles = maxNumberOfFiles;
        this.startTime = startTime;
        this.timeStep = timeStep;
    }

    private void addTime(int fileNum, long startTime, long fileInterval)
    {
        final String name = FilesHitSpool.getFileName(fileNum);
        final DBTimes times = new DBTimes(startTime, fileInterval);

        shadowDB.put(name, times);
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

    void checkSpoolDir(File topDir)
        throws IOException
    {
        String runDir = "hitspool";

        File path = new File(topDir, runDir);
        if (!path.exists()) {
            fail(runDir + " does not exist");
        }

        // first file number
        final int firstNum = (int) ((startTime / fileInterval) %
                                    maxNumberOfFiles);

        // maximum number of cached files
        final int numToCheck;
        if (numFiles < maxNumberOfFiles) {
            numToCheck = numFiles;
        } else {
            numToCheck = maxNumberOfFiles;
        }

        ArrayList<String> expected = new ArrayList<String>();
        for (int i = 0; i < numToCheck; i++) {
            final int num = (firstNum + i) % maxNumberOfFiles;
            final String name = FilesHitSpool.getFileName(num);
            if (!expected.contains(name)) {
                expected.add(name);
            }
        }

        // check metadata file
        expected.add("hitspool.db");
        validateInfoDB(path, startTime, curTime);

        // make sure that expected files (and *only* those files) are present
        for (String name : path.list()) {
            if (!expected.remove(name) && !shadowDB.containsKey(name)) {
                fail("Found unexpected file \"" + name + "\"");
            }
        }
        assertEquals("Missing data files" + expected,
                     0, expected.size());
    }

    /**
     * Set stop time for final entry
     *
     * @param lastTime final run time
     * @param fileInterval time interval for each file
     * @param maxNumberOfFiles maximum number of files in hitspool directory
     */
    private void setStopTime(long lastTime, long fileInterval,
                             int maxNumberOfFiles)
    {
        final int lastNum = ((int) (lastTime / fileInterval)) %
            maxNumberOfFiles;
        final String lastName = FilesHitSpool.getFileName(lastNum);
        if (!shadowDB.containsKey(lastName)) {
            fail(String.format("Missing %s entry (last time %d)", lastName,
                               lastTime));
        }

        shadowDB.get(lastName).stop = lastTime;
    }

    private void validateInfoDB(File spoolDir, long startTime, long stopTime)
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
            }

            final String sql =
                "select filename, start_tick, stop_tick from hitspool" +
                " order by start_tick";
            Statement stmt = conn.createStatement();
            try {
                ResultSet rs = stmt.executeQuery(sql);
                while (rs.next()) {
                    final String filename = rs.getString(1);
                    final long start_tick = rs.getLong(2);
                    final long stop_tick = rs.getLong(3);

                    assertTrue("Unexpected entry for " + filename,
                               shadowDB.containsKey(filename));

                    final DBTimes expTimes = shadowDB.get(filename);
                    assertEquals("Bad start time for " + filename,
                                 expTimes.start, start_tick);
                    assertEquals("Bad stop time for " + filename,
                                 expTimes.stop, stop_tick);
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
        long lastTime = Long.MAX_VALUE;
        for (long t = startTime; t < endTime; t += timeStep) {
            final int newNum = (int) ((t / fileInterval) % maxNumberOfFiles);

            // save times when file number changes
            if (newNum != curFile) {
                if (lastTime != Long.MAX_VALUE) {
                    setStopTime(lastTime, fileInterval, maxNumberOfFiles);
                }
                addTime(newNum, t, fileInterval);

                curFile = newNum;
                curTime = t;
                numFiles++;
            }
            lastTime = t;

            hitspool.consume(buildHit(buf, index++, t));
        }

        // send end-of-stream marker
        hitspool.consume(buildHit(buf, index++, Long.MAX_VALUE));

        if (lastTime != Long.MAX_VALUE) {
            setStopTime(lastTime, fileInterval, maxNumberOfFiles);
        }
    }

    class DBTimes
    {
        long start;
        long stop;

        DBTimes(long start, long interval)
        {
            this.start = start;
            this.stop = start + (interval - 1);
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
    private long fileInterval = 10000000000L;
    private int maxNumberOfFiles = 5;

    private File topDir;

    private CountingConsumer cc = new CountingConsumer();

    private FilesHitSpool hitspool;

    private int totalHits;

    private TimeWriter previousWriter;
    private TimeWriter currentWriter;

    Runner(long fileInterval, int maxNumberOfFiles)
        throws IOException
    {
        this.fileInterval = fileInterval;
        this.maxNumberOfFiles = maxNumberOfFiles;

        topDir = createTempDirectory();
    }

    private void checkTopDir(boolean includeLast)
    {
        for (String name : topDir.list()) {
            if (!name.equals("hitspool")) {
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

        hitspool = new FilesHitSpool(cc, topDir, fileInterval,
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

    void startRun()
        throws IOException
    {

        // check spool directories
        checkTopDir(currentWriter != null);

        // save previous run data
        if (currentWriter != null) {
            previousWriter = currentWriter;
            previousWriter.checkSpoolDir(topDir);
            currentWriter = null;
        }
    }

    void switchRun()
        throws IOException
    {

        // make sure both 'currentRun' and 'lastRun' exist
        checkTopDir(true);

        // save previous run data
        if (currentWriter != null) {
            previousWriter = currentWriter;
            previousWriter.checkSpoolDir(topDir);
            currentWriter = null;
        }
    }

    void writeHits(long startTime, long endTime, long timeStep)
        throws IOException
    {
        currentWriter = new TimeWriter(fileInterval, maxNumberOfFiles,
                                       startTime, timeStep);
        currentWriter.write(hitspool, endTime);

        // verify that only expected spool directories exist
        checkTopDir(previousWriter != null);

        totalHits += (int) ((endTime - startTime + timeStep - 1) / timeStep);
        totalHits += 1; // include STOP msg
        assertEquals("Unexpected number of hits consumed",
                     totalHits, cc.getNumberConsumed());

        currentWriter.checkSpoolDir(topDir);
    }
}
