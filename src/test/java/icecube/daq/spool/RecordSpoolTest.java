package icecube.daq.spool;

import icecube.daq.common.MockAppender;
import icecube.daq.performance.binary.buffer.IndexFactory;
import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBufferIndex;
import icecube.daq.performance.binary.store.RecordStore;
import icecube.daq.performance.binary.store.impl.ExpandingMemoryRecordStore;
import icecube.daq.performance.binary.test.Assertions;
import icecube.daq.performance.binary.test.RandomOrderedValueSequence;
import icecube.daq.performance.binary.test.RecordGenerator;
import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.Assert.*;

/**
 * Tests RecordSpool.java
 */
public class RecordSpoolTest
{



    /**
     * Base class for managing temporary files.
     */
    public static class TempFileClient
    {
        // identify our temp directories for the sake of create/delete
        private static String TEST_FILE_PREFIX = "RecordSpoolTest";

        List<File> tempFiles = new ArrayList<>();

        @After
        public void tearDown() throws IOException
        {
            for(File file : tempFiles)
            {
                deleteTempDirectory(file);
                assertFalse(Files.exists(file.toPath()));
            }
            tempFiles.clear();
        }

        public File createTempDirectory()
                throws IOException
        {
            Path temp = Files.createTempDirectory(TEST_FILE_PREFIX);

            File file = temp.toFile();
            tempFiles.add(file);
            return file;
        }

        private static void deleteTempDirectory(File dir)
                throws IOException
        {
            // defense
            if( !dir.getName().startsWith(TEST_FILE_PREFIX))
            {
                throw new Error("Will not delete strange directory");
            }

            String[] list = dir.list();

            if(list.length == 0)
            {
                dir.delete();
                return;
            }
            else if( (list.length == 1) && list[0].equals("hitspool"))
            {
                Path hitspool = dir.toPath().resolve("hitspool");
                String[] files = hitspool.toFile().list();
                for (int i = 0; i < files.length; i++)
                {
                    Files.delete(hitspool.resolve(files[i]));
                }
                Files.delete(hitspool);
                Files.delete(dir.toPath());
            }
            else
            {
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                for (int i = 0; i < list.length; i++)
                {
                    sb.append(list[i]).append(",");
                }
                sb.append("]");

                throw new Error("Unexpected content in temp file: " +
                        sb.toString());
            }

        }

    }

    /**
     * Tests that the TempFileClient class works
     */
    public static class TempFileDeleteTest extends TempFileClient
    {

        private static File tempDirectory;

        @Before
        public void setUp() throws IOException
        {
            tempDirectory = createTempDirectory();
        }
        @AfterClass
        public static void classTearDown()
        {
            assertFalse("Temp Directory not deleted: " + tempDirectory,
                    tempDirectory.exists());
        }

        @Test
        public void testDelete() throws IOException
        {
            fill(tempDirectory);

        }

        private void fill(File temp) throws IOException
        {
            Path dir = temp.toPath();
            Path hitspool = Files.createDirectories(dir.resolve("hitspool"));
            Files.createFile(hitspool.resolve("a.dat"));
            Files.createFile(hitspool.resolve("b.dat"));
            Files.createFile(hitspool.resolve("c.dat"));
        }
    }


    /**
     * Tests that RecordSpool behaves identically to a memory-based
     * store.
     */
    @RunWith(value = Parameterized.class)
    public static class RecordStoreContract extends TempFileClient
    {
        private final MockAppender appender = new MockAppender();

        RecordSpool subject;
        RecordStore.OrderedWritable reference;

        long fileInterval = 10000000000L; // 1 sec
        int numFiles = 5;
        int maxStep = 10000000; // miliisecond

        public RecordGenerator dataType;

        @Parameterized.Parameter(0)
        public IndexFactory indexMode = IndexFactory.NO_INDEX;



        @Parameterized.Parameters(name = "RecordStoreContract:{0}")
        public static List<Object[]> sizes()
        {
            List<Object[]> cases = new ArrayList<Object[]>(5);
            cases.add(new Object[]{IndexFactory.NO_INDEX});
            cases.add(new Object[]{IndexFactory.UTCIndexMode.SPARSE_ONE_SECOND,});
            cases.add(new Object[]{IndexFactory.UTCIndexMode.SPARSE_ONE_THOUSANDTHS_SECOND});
            return cases;
        }


        class TeeStore implements RecordStore.Writable
        {
            final RecordStore.Writable left;
            final RecordStore.Writable right;

            TeeStore(final RecordStore.Writable left,
                     final RecordStore.Writable right)
            {
                this.left = left;
                this.right = right;
            }

            @Override
            public void store(final ByteBuffer buffer) throws IOException
            {
                left.store(buffer);
                buffer.rewind();
                right.store(buffer);
            }

            @Override
            public int available()
            {
                return Math.min(left.available(), right.available());
            }

            @Override
            public void closeWrite() throws IOException
            {
                left.closeWrite();
                right.closeWrite();
            }
        }


        @Before
        public void setUp() throws Exception
        {
            BasicConfigurator.resetConfiguration();
            BasicConfigurator.configure(appender);

            dataType = new RecordGenerator.RandomLengthRecordProvider(32);
            subject = new RecordSpool(dataType.recordReader(),
                    dataType.orderingField(), createTempDirectory(),
                    fileInterval, numFiles, indexMode);

            reference = new ExpandingMemoryRecordStore(dataType.recordReader(),
                    dataType.orderingField(), (1024*1024), indexMode);
        }

        @Override
        @After
        public void tearDown()
        {
            appender.assertNoLogMessages();
        }

        @Test
        public void testIdenticalReadouts() throws IOException
        {
            ///
            /// Stream records to both stores and compare readouts.
            ///

            // load the spool and reference store
            TeeStore tee = new TeeStore(reference, subject);
            long startTick = (long) (Math.random() * 183279239493L);
            long lastTick = simulateHitStream(tee, dataType, startTick,
                    (fileInterval * (numFiles -1) ), maxStep);

            // run a large permutation of queries against both stores
            // and compare results
            long[][] queries =
                    generateRangeQueries(startTick, lastTick, maxStep*100);
            System.out.println("Executing " + queries.length +
                    " queries against the spool ..." );

            for (int i = 0; i < queries.length; i++)
            {
                long[] query = queries[i];

//                System.out.printf("XXX Spool query %-6d [%-15d - %-15d]" +
//                        "width: %-15d%n",
//                        i,  query[0], query[1], (query[1] - query[0]));

                final String msg = "Spool query " + i + "[" + query[0] + " - "
                        + query[1] + "] did not match reference";

                // copy extract
                RecordBuffer expected =
                        reference.extractRange(query[0], query[1]);
               testCopy(query[0], query[1], expected, msg);

                // shared view
               testSharedView(query[0], query[1], expected, msg);

                // shared iteration
                testSharedIterate(query[0], query[1], expected, msg);

            }


            // repeat after closing the store for write
            subject.closeWrite();

            System.out.println("Executing " + queries.length +
                    " queries against the spool ..." );

            for (int i = 0; i < queries.length; i++)
            {
                long[] query = queries[i];

                final String msg = "Spool query " + i + "[" + query[0] + " - "
                        + query[1] + "] did not match reference";

                // copy extract
                RecordBuffer expected =
                        reference.extractRange(query[0], query[1]);
                testCopy(query[0], query[1], expected, msg);

                // shared view
                testSharedView(query[0], query[1], expected, msg);

                // shared iteration
                testSharedIterate(query[0], query[1], expected, msg);

            }


            int numLogged = appender.getNumberOfMessages();
            for(int i = 0; i < numLogged; i++)
            {
                String message = (String) appender.getMessage(i);
                String failedMsg = "Unexpected log [" + message + "]";
                assertTrue(failedMsg,
                        message.startsWith("Unindexed read of file "));
            }
            appender.clear();

        }

        // executes a copy extraction and compares to a reference.
        private void testCopy(final long from, final long too,
                              final RecordBuffer expected, final String msg)
                throws IOException
        {
            RecordBuffer actual = subject.extractRange(from, too);

            Assertions.assertSame(msg, expected, actual);
        }

        // executes a shared view extraction and compares to a reference.
        private void testSharedView(final long from, final long too,
                                    final RecordBuffer expected,
                                    final String msg)
                throws IOException
        {
            subject.extractRange(new Consumer<RecordBuffer>()
            {
                @Override
                public void accept(final RecordBuffer actual)
                {
                    Assertions.assertSame(msg, expected, actual);
                }
            }, from, too);
        }

        // executes a shared iterator extraction and compares to a reference.
        private void testSharedIterate(final long from, final long too,
                                       final RecordBuffer expected,
                                       final String msg)
                throws IOException
        {
            final Iterator<RecordBuffer> expectedIt =
                    expected.eachRecord(dataType.recordReader()).iterator();
            subject.forEach(new Consumer<RecordBuffer>()
            {
                @Override
                public void accept(final RecordBuffer actual)
                {
                    Assertions.assertSame(msg, expectedIt.next(), actual);
                }
            }, from, too);
        }

    }


    /**
     * Tests miscellaneous aspects of the records spool
     */
    public static class MiscTests extends TempFileClient
    {
        private final MockAppender appender = new MockAppender();

        RecordSpool subject;
        RecordStore.OrderedWritable reference;

        long fileInterval = 10000000000L; // 1 sec
        int numFiles = 5;

        public RecordGenerator dataType;

        @Parameterized.Parameter(0)
        public IndexFactory indexMode = IndexFactory.NO_INDEX;
        private File topDirectory;
        private File spoolDirectory;

        @Parameterized.Parameters(name = "RecordStoreContract:{0}")
        public static List<Object[]> sizes()
        {
            List<Object[]> cases = new ArrayList<Object[]>(5);
            cases.add(new Object[]{IndexFactory.NO_INDEX});
            cases.add(new Object[]{IndexFactory.UTCIndexMode.SPARSE_ONE_SECOND,});
            cases.add(new Object[]{IndexFactory.UTCIndexMode.SPARSE_ONE_THOUSANDTHS_SECOND});
            return cases;
        }



        @Before
        public void setUp() throws Exception
        {
            BasicConfigurator.resetConfiguration();
            BasicConfigurator.configure(appender);

            topDirectory = createTempDirectory();
            spoolDirectory = topDirectory.toPath().resolve("hitspool").toFile();

            dataType = new RecordGenerator.RandomLengthRecordProvider(32);
            subject = new RecordSpool(dataType.recordReader(),
                    dataType.orderingField(), topDirectory,
                    fileInterval, numFiles, indexMode);

            reference = new ExpandingMemoryRecordStore(dataType.recordReader(),
                    dataType.orderingField(), (1024*1024), indexMode);
        }

        @Override
        @After
        public void tearDown()
        {
            appender.assertNoLogMessages();
        }

        @Test
        public void testAvailable()
        {
            assertEquals(Integer.MAX_VALUE, subject.available());
        }

        @Test
        public void testRollover() throws IOException, SQLException
        {
            Map<String, Metadata.HitSpoolRecord> booked = new HashMap<>();
            for( int i = 0; i< numFiles; i++)
            {
                long firstValue = i*fileInterval;
                long lastValue = ((i+1)*fileInterval) -1;
                String fileName = "HitSpool-" + (i%numFiles) + ".dat";

                subject.store(dataType.generate(firstValue));
                booked.put(fileName, new Metadata.HitSpoolRecord(fileName,
                        firstValue, lastValue));
                assertMetaDataContent(booked);


                assertFilesPresent(booked.keySet());
                assertMetaDataContent(booked);

                // update booked entry with actual last value
                booked.put(fileName, new Metadata.HitSpoolRecord(fileName,
                        firstValue, firstValue));


            }

            // wrap-around overwrite
            for( int i = numFiles; i< 2*numFiles; i++)
            {
                long firstValue = i*fileInterval;
                long lastValue = ((i+1)*fileInterval) -1;
                String fileName = "HitSpool-" + (i%numFiles) + ".dat";

                subject.store(dataType.generate(firstValue));
                subject.store(dataType.generate(lastValue));
                booked.put(fileName, new Metadata.HitSpoolRecord(fileName,
                        firstValue, lastValue));
                assertMetaDataContent(booked);


                assertFilesPresent(booked.keySet());
                assertMetaDataContent(booked);
            }

        }

        private void assertFilesPresent(Set<String> fileNames)
        {
            String[] spoolFiles = spoolDirectory.list(new FilenameFilter()
            {
                @Override
                public boolean accept(final File dir, final String name)
                {
                    return name.startsWith("HitSpool-") &&
                            name.endsWith(".dat");
                }
            });

            assertEquals("unexpected number of files",
                    fileNames.size(), spoolFiles.length);

            Set<String> found = new HashSet();
            for (int i = 0; i < spoolFiles.length; i++)
            {
                found.add(spoolFiles[i]);
            }

            for (String expected : fileNames)
            {
                assertTrue("Missing file: " + expected,
                        found.contains(expected));
            }

        }

        private void assertMetaDataContent(Map<String,
                Metadata.HitSpoolRecord> expected)
                throws SQLException, IOException
        {
            Metadata db = new Metadata(spoolDirectory);
            List<Metadata.HitSpoolRecord> actual = db.listRecords();

            assertEquals("unexpected number of hitspool entries",
                    expected.size(), actual.size());

            for(Metadata.HitSpoolRecord act : actual)
            {
                assertTrue(expected.containsKey(act.filename));

                Metadata.HitSpoolRecord exp = expected.get(act.filename);
                assertEquals(exp.startTick, act.startTick);
                assertEquals(exp.stopTick, act.stopTick);
            }
        }
    }

    /**
     * Tests that the TempFileClient class works
     */
    public static class SpoolFileIndexCacheTest extends TempFileClient
    {

        RecordSpool.SpoolFileIndexCache subject;
        static final int MAX_CACHED_FILES = 10;

        @Before
        public void setUp() throws IOException
        {
            subject = new RecordSpool.SpoolFileIndexCache(MAX_CACHED_FILES);
        }

        @Test
        public void testNominal() throws IOException
        {
            // non existent lookup
            RecordBufferIndex resp = subject.get("foo");
            assertNull(resp);

            RecordBufferIndex.NullIndex any =
                    new RecordBufferIndex.NullIndex();
            subject.cache("foo", any, 11111111, 22222222, 0);

            resp = subject.get("foo");
            assertSame(resp, any);
        }

        @Test
        public void testMax() throws IOException
        {
            long startVal = 91234121;
            long stride = 11111111;
            // test max capacity
            RecordBufferIndex[] actual = new RecordBufferIndex[MAX_CACHED_FILES];
            for(int i = 0; i<MAX_CACHED_FILES; i++)
            {
                long endValue = (startVal+stride) -1;

                actual[i] = new RecordBufferIndex.NullIndex();
                subject.cache("foo-"+i, actual[i], startVal, endValue, 0);
                startVal = endValue+1;
            }

            // bar-n should cause foo-n to be pruned
            for(int i = 0; i<MAX_CACHED_FILES; i++)
            {
                assertSame(actual[i], subject.get("foo-" + i));

                long endValue = (startVal+stride) -1;
                RecordBufferIndex.NullIndex any =
                        new RecordBufferIndex.NullIndex();
                subject.cache("bar-"+i, any, startVal, endValue, 0);
                startVal = endValue+1;

                assertNull(subject.get("foo-" + i));
            }
        }

        @Test
        public void testPruning() throws IOException
        {

            RecordBufferIndex.NullIndex any =
                    new RecordBufferIndex.NullIndex();

            subject.cache("one", any, 0, 1999, 0);
            subject.cache("two", any, 2000, 2999, 0);
            subject.cache("three", any, 3000, 3999, 0);
            subject.cache("four", any, 4000, 4999, 0);

            assertNotNull(subject.get("one"));
            assertNotNull(subject.get("two"));
            assertNotNull(subject.get("three"));
            assertNotNull(subject.get("four"));

            // should prune #1 and #2
            subject.cache("five", any, 5000, 5999, 3333);

            assertNull(subject.get("one"));
            assertNull(subject.get("two"));
            assertNotNull(subject.get("three"));
            assertNotNull(subject.get("four"));
            assertNotNull(subject.get("five"));

            // should all but #5
            subject.cache("six", any, 6000, 6999, Long.MAX_VALUE);

            assertNull(subject.get("one"));
            assertNull(subject.get("two"));
            assertNull(subject.get("three"));
            assertNull(subject.get("four"));
            assertNotNull(subject.get("five"));
            assertNotNull(subject.get("six"));
        }

    }


    /**
     * Stream a prescribe interval of records to a store. The sequence will
     * be randomly spaced.
     *
     * @param store The target store.
     * @param startTic The value of the first record.
     * @param interval The interval of data, the last reocrd will be within
     *                 maxStep of (startTic + interval) but is unlikely
     *                 to match exactly
     * @param maxStep The maximum spacing of random record values.
     *                @return The actual value of the last record.
     * @throws IOException
     */
    private static long simulateHitStream(RecordStore.Writable store,
                                          RecordGenerator recordGenerator,
                                          long startTic, long interval,
                                          long maxStep)
            throws IOException
    {

        RandomOrderedValueSequence sequence =
                new RandomOrderedValueSequence(startTic, maxStep);

        long pit = sequence.next();
        long lastValue = Long.MIN_VALUE;
        while (pit < (startTic + interval))
        {
            store.store(recordGenerator.generate(pit));
            lastValue = pit;
            pit = sequence.next();
        }

        return lastValue;
    }


    private static long[][] generateRangeQueries(long startValue,
                                                 long stopValue, long startStep)
    {
        long interval = stopValue - startValue;

        List<long[]> acc = new ArrayList<>();

        long width = startStep;
        while(width < interval)
        {
            for(long i = startValue; i < stopValue; i+=width)
            {
                acc.add(new long[]{i, i+width});
            }
            width +=startStep;
        }

        long[][] result = new long[acc.size()][];
        for (int i = 0; i < result.length; i++)
        {
           result[i] = acc.get(i);

        }
        return result;

    }


}
