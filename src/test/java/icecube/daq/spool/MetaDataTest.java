package icecube.daq.spool;

import icecube.daq.common.MockAppender;
import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests Metadata.java
 */
public class MetaDataTest
{
    private final MockAppender appender = new MockAppender();

    File tempDir;
    File database;
    Metadata subject;

    @Before
    public void setUp() throws IOException, SQLException
    {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);

        Path tempDirectory = Files.createTempDirectory("MetaDataTest-");
        tempDir = tempDirectory.toFile();
        subject = new Metadata(tempDir);
        database = tempDirectory.resolve("hitspool.db").toFile();
    }
    @After
    public void tearDown() throws IOException
    {
        appender.assertNoLogMessages();

        if(subject != null)
        {
            subject.close();
        }

        if(database.exists())
        {
            database.delete();
        }

        if (tempDir.exists())
        {
            tempDir.delete();
        }
        assertFalse(tempDir.exists());

    }

    @Test
    public void testWrite() throws SQLException, IOException
    {
        ///
        /// Tests inserting/overwriting records
        ///

        subject.write("test", 11111, 20001);

        List<Metadata.HitSpoolRecord> query = subject.listRecords();
        assertEquals(1, query.size());
        Metadata.HitSpoolRecord record = query.get(0);
        assertEquals("test", record.filename);
        assertEquals(11111, record.startTick);
        assertEquals((11111+20001-1), record.stopTick);

        // test overwrite
        subject.write("test", 444444, 898989);

        query = subject.listRecords();
        assertEquals(1, query.size());
        record = query.get(0);
        assertEquals("test", record.filename);
        assertEquals(444444, record.startTick);
        assertEquals((444444+898989-1), record.stopTick);
    }

    @Test
    public void testUpdateStop() throws SQLException, IOException
    {
        ///
        /// Tests updating stop_tick of records
        ///

        subject.write("test", 11111, 20001);

        List<Metadata.HitSpoolRecord> query = subject.listRecords();
        assertEquals(1, query.size());
        Metadata.HitSpoolRecord record = query.get(0);
        assertEquals("test", record.filename);
        assertEquals(11111, record.startTick);
        assertEquals((11111+20001-1), record.stopTick);

        subject.updateStop("test", 987987987);
        query = subject.listRecords();
        assertEquals(1, query.size());
        record = query.get(0);
        assertEquals("test", record.filename);
        assertEquals(11111, record.startTick);
        assertEquals(987987987, record.stopTick);


        //update a non-existant record
        subject.updateStop("does_NOT_exist", 12312435123L);
        assertEquals(1, appender.getNumberOfMessages());
        assertEquals("Did not update filename does_NOT_exist stop 12312435123",
                appender.getMessage(0));
        appender.clear();

    }

    @Test
    public void testListRange() throws IOException
    {
        ///
        /// Tests a range-based listing
        ///

        subject.write("three", 33333, 10000);
        subject.write("one", 11111, 10000);
        subject.write("five", 55555, 10000);
        subject.write("four", 44444, 10000);
        subject.write("two", 22222, 10000);

        List<Metadata.HitSpoolRecord> records =
                subject.listRecords(22222, 44444);
        assertEquals(3, records.size());
        Metadata.HitSpoolRecord first = records.get(0);
        Metadata.HitSpoolRecord second = records.get(1);
        Metadata.HitSpoolRecord third = records.get(2);
        assertEquals("two", first.filename);
        assertEquals(22222, first.startTick);
        assertEquals(22222+10000-1, first.stopTick);
        assertEquals("three", second.filename);
        assertEquals(33333, second.startTick);
        assertEquals(33333+10000-1, second.stopTick);
        assertEquals("four", third.filename);
        assertEquals(44444, third.startTick);
        assertEquals(44444+10000-1, third.stopTick);
    }

    @Test
    public void testListAll() throws IOException
    {
        ///
        /// Tests that records are listed in order
        ///

        subject.write("three", 33333, 10000);
        subject.write("one", 11111, 10000);
        subject.write("five", 55555, 10000);
        subject.write("four", 44444, 10000);
        subject.write("two", 22222, 10000);

        List<Metadata.HitSpoolRecord> records = subject.listRecords();
        assertEquals(5, records.size());
        Metadata.HitSpoolRecord first = records.get(0);
        Metadata.HitSpoolRecord second = records.get(1);
        Metadata.HitSpoolRecord third = records.get(2);
        Metadata.HitSpoolRecord fourth = records.get(3);
        Metadata.HitSpoolRecord fifth = records.get(4);
        assertEquals("one", first.filename);
        assertEquals(11111, first.startTick);
        assertEquals(11111+10000-1, first.stopTick);
        assertEquals("two", second.filename);
        assertEquals(22222, second.startTick);
        assertEquals(22222+10000-1, second.stopTick);
        assertEquals("three", third.filename);
        assertEquals(33333, third.startTick);
        assertEquals(33333+10000-1, third.stopTick);
        assertEquals("four", fourth.filename);
        assertEquals(44444, fourth.startTick);
        assertEquals(44444+10000-1, fourth.stopTick);
        assertEquals("five", fifth.filename);
        assertEquals(55555, fifth.startTick);
        assertEquals(55555+10000-1, fifth.stopTick);
    }

    @Test
    public void testListAll2() throws IOException
    {
        ///
        /// Tests that records are listed in order
        ///

        for(int i = 0; i< 10000; i++)
        {
            long startTic = (long) (Math.random() * Long.MAX_VALUE);
            long interval = (long) (Math.random() * 150000000000L);
            String filename = "test-" + i;
            subject.write(filename, startTic, interval);
        }
        List<Metadata.HitSpoolRecord> records = subject.listRecords();
        assertEquals(10000, records.size());

        // assert that records are in ascending order
        long lastStart = Long.MIN_VALUE;
        for(Metadata.HitSpoolRecord record : records)
        {
            assertTrue(lastStart < record.startTick);
        }
    }


    @Test
    public void testConstructionOnExisting() throws SQLException, IOException
    {
        subject.write("test", 123, 456);
        subject.close();

        Metadata secondInstance = new Metadata(tempDir);
        List<Metadata.HitSpoolRecord> query = secondInstance.listRecords();

        assertEquals(1, query.size());
        Metadata.HitSpoolRecord record = query.get(0);
        assertEquals("test", record.filename);
        assertEquals(123, record.startTick);
        assertEquals((123+456-1), record.stopTick);

    }

}
