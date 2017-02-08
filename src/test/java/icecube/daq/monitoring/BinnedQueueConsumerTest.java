package icecube.daq.monitoring;

import icecube.daq.common.MockAppender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.BasicConfigurator;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class BinnedQueueConsumerTest
{
    private static final MockAppender appender = new MockAppender();

    class MyCounter
    {
        private int count;

        int getValue()
        {
            return count;
        }

        void inc()
        {
            count++;
        }

        public String toString()
        {
            return String.format("MyCounter#%d", count);
        }
    }

    class MyBinManager
        extends BinManager<MyCounter>
    {
        MyBinManager(String name, long binStart, long binWidth)
        {
            super(name, binStart, binWidth);
        }

        MyCounter createBinContainer()
        {
            return new MyCounter();
        }
    }

    class NameVal
    {
        String name;
        long val;

        NameVal(String name, long val)
        {
            this.name = name;
            this.val = val;
        }

        public String toString()
        {
            return String.format("%s<%d>", name, val);
        }
    }

    class MyConsumer
        extends BinnedQueueConsumer<NameVal, String, MyCounter>
    {
        private ArrayList<String> names = new ArrayList<String>();

        private ArrayList<MyReport> reports = new ArrayList<MyReport>();

        private ArrayList<ExpiredRange> rejections = new ArrayList<>();

        MyConsumer(IRunMonitor parent, long binWidth)
        {
            super(parent, binWidth);
        }

        @Override
        public BinManager<MyCounter> createBinManager(String name,
                                                      long binStart,
                                                      long binWidth)
        {
            return new MyBinManager(name, binStart, binWidth);
        }

        @Override
        void process(NameVal nv)
        {
            if (!names.contains(nv.name)) {
                names.add(nv.name);
            }

            try
            {
                MyCounter ctr = reportEvent(nv.val, nv.name);
                ctr.inc();
            }
            catch (ExpiredRange e)
            {
                rejections.add(e);
            }
        }

        void pushData(String name, long value)
        {
            push(new NameVal(name, value));
        }

        @Override
        public void sendData(long binStart, long binEnd)
        {
            Map<String, Integer> counts = new HashMap<>();
            for (String name: names) {
                MyCounter ctr = getExisting(name, binStart, binEnd);
                if(ctr != null)
                {
                    counts.put(name, ctr.getValue());
                }
            }
            MyReport myReport = new MyReport(binStart, binEnd, counts);
            reports.add(myReport);
        }

        void clearReports()
        {
            reports = new ArrayList<>();
        }

        void clearRejections()
        {
            rejections = new ArrayList<>();
        }
    }

    class MyReport
    {
        final long binStart;
        final long binEnd;
        final Map<String, Integer> counts;

        MyReport(final long binStart, final long binEnd, final Map<String, Integer> counts)
        {
            this.binStart = binStart;
            this.binEnd = binEnd;
            this.counts = counts;
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("BIN-> [").append(binStart).append( " to ");
            sb.append(binEnd).append("] counts ").append("{");
            boolean first = true;
            for (Map.Entry<String, Integer> entry: counts.entrySet())
            {
                sb.append(first ? "" : ", ");
                sb.append(entry.getKey()).append(": ");
                sb.append(entry.getValue());
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
    }

    @BeforeClass
    public static void setupClass()
    {
        appender.setVerbose(true);

        // exercise logging calls, but output to nowhere
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);
    }

    @Test
    public void testNormal()
    {
        String[] names = new String[] { "abc", "def" };
        MyConsumer con = new MyConsumer(null, 10L);
        for (int i = 10; i < 50; i += 5) {
            for (String name : names) {
                con.pushData(name, (long) i);
                if (con.holdValue()) {
                    con.processHeldValue();
                }
            }
        }

        assertEquals(0, con.rejections.size());
        assertEquals(2, con.reports.size());
        assertEquals("BIN-> [10 to 19] counts {abc: 2, def: 2}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [20 to 29] counts {abc: 2, def: 2}",
                con.reports.get(1).toString());

        con.clearReports();
        con.clearRejections();
        con.sendRunData();

        assertEquals(0, con.rejections.size());
        assertEquals(2, con.reports.size());
        assertEquals("BIN-> [30 to 39] counts {abc: 2, def: 2}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [40 to 45] counts {abc: 2, def: 2}",
                con.reports.get(1).toString());

        if (appender.getNumberOfMessages() > 0) {
            fail("Saw log message(s)");
        }
    }

    @Test
    public void testAbnormal()
    {
        String[] names = new String[] { "abc", "def", "ghi" };
        MyConsumer con = new MyConsumer(null, 10L);
        for (int i = 10; i < 50; i += 5) {
            for (String name : names) {
                if (name.equals("def") && i > 15 && i < 45) {
                    // skip middle values for "def"
                    continue;
                }

                con.pushData(name, (long) i);
                if (con.holdValue()) {
                    con.processHeldValue();
                }

                if (appender.getNumberOfMessages() > 0) {
                    fail("Saw log message(s)");
                }
            }
        }

        assertEquals(0, con.rejections.size());
        assertEquals(2, con.reports.size());
        assertEquals("BIN-> [10 to 19] counts {abc: 2, def: 2, ghi: 2}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [20 to 29] counts {abc: 2, ghi: 2}",
                con.reports.get(1).toString());

        con.clearReports();
        con.clearRejections();
        con.sendRunData();

        assertEquals(0, con.rejections.size());
        assertEquals(2, con.reports.size());
        assertEquals("BIN-> [30 to 39] counts {abc: 2, ghi: 2}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [40 to 45] counts {abc: 2, def: 1, ghi: 2}",
                con.reports.get(1).toString());

        if (appender.getNumberOfMessages() > 0) {
            fail("Saw log message(s)");
        }
    }

    @Test
    public void testReset()
    {
        String[] names = new String[] { "abc", "def" };
        MyConsumer con = new MyConsumer(null, 10L);
        for (int i = 10; i < 50; i += 5) {
            for (String name : names) {
                con.pushData(name, (long) i);
                if (con.holdValue()) {
                    con.processHeldValue();
                }
            }
        }

        assertEquals(0, con.rejections.size());
        assertEquals(2, con.reports.size());
        assertEquals("BIN-> [10 to 19] counts {abc: 2, def: 2}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [20 to 29] counts {abc: 2, def: 2}",
                con.reports.get(1).toString());

        con.clearReports();
        con.clearRejections();
        con.sendRunData();

        assertEquals(0, con.rejections.size());
        assertEquals(2, con.reports.size());
        assertEquals("BIN-> [30 to 39] counts {abc: 2, def: 2}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [40 to 45] counts {abc: 2, def: 2}",
                con.reports.get(1).toString());

        if (appender.getNumberOfMessages() > 0) {
            fail("Saw log message(s)");
        }


        con.clearRejections();
        con.clearReports();

        con.reset();
        con.pushData("abc", 48);
        con.pushData("def", 48);
        con.pushData("abc", 49);
        con.pushData("abc", 50);
        con.pushData("abc", 51);
        con.pushData("abc", 52);
        con.pushData("def", 55);
        con.pushData("def", 55);
        con.pushData("def", 55);
        con.pushData("def", 56);
        con.processAll();
        con.sendRunData();

        assertEquals(0, con.rejections.size());
        assertEquals(2, con.reports.size());
        assertEquals("BIN-> [48 to 49] counts {abc: 2, def: 1}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [50 to 56] counts {abc: 3, def: 4}",
                con.reports.get(1).toString());

        if (appender.getNumberOfMessages() > 0) {
            fail("Saw log message(s)");
        }
    }

    @Test
    public void testDelayedChannel_0()
    {
        //
        // less than one bin behind
        //
        MyConsumer con = new MyConsumer(null, 10L);

        long[] aaa = {1, 3, 5,  10, 11, 18,  21, 23, 28,  30, 31, 37};
        long[] bbb = {1, 3, 5,  10, 11, 18,  21, 23, 28,  30, 31, 37};
        long[] ccc = {1, 3, 5,  -1, -1, 10,  11, 18,  21, 23 , 28,  30, 31, 37};
        for(int idx=0; idx<ccc.length; idx++)
        {
            if(idx<aaa.length && aaa[idx] > 0)
            {
                con.pushData("aaa", aaa[idx]);
            }
            if(idx<bbb.length && bbb[idx] > 0)
            {
                con.pushData("bbb", bbb[idx]);
            }
            if(idx<ccc.length && ccc[idx] > 0)
            {
                con.pushData("ccc", ccc[idx]);
            }
        }

        con.processAll();

        int binStart = 1;
        int binEnd = 9;
        for(MyReport report: con.reports)
        {
            assertEquals(report.toString(), binStart, report.binStart);
            assertEquals(report.toString(), binEnd, report.binEnd);
            binStart= binEnd+1;
            binEnd= binStart+9;
        }

        assertEquals(0, con.rejections.size());
        assertEquals(2, con.reports.size());
        assertEquals("BIN-> [1 to 9] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [10 to 19] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(1).toString());

        if (appender.getNumberOfMessages() > 0) {
            fail("Saw log message(s)");
        }

        con.clearReports();
        con.clearRejections();
        con.sendRunData();

        assertEquals(0, con.rejections.size());
        assertEquals(2, con.reports.size());
        assertEquals("BIN-> [20 to 29] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [30 to 37] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(1).toString());

        if (appender.getNumberOfMessages() > 0) {
            fail("Saw log message(s)");
        }
    }


    @Test
    public void testDelayedChannel_1()
    {
        //
        // one bin behind
        //
        MyConsumer con = new MyConsumer(null, 10L);

        long[] aaa = {1, 3, 5,  10, 11, 18,  21, 23, 28,  30, 31, 37};
        long[] bbb = {1, 3, 5,  10, 11, 18,  21, 23, 28,  30, 31, 37};
        long[] ccc = {1, 3, 5,  -1, -1, -1,  10, 11, 18,  21, 23 , 28,  30, 31, 37};
        for(int idx=0; idx<ccc.length; idx++)
        {
            if(idx<aaa.length && aaa[idx] > 0)
            {
                con.pushData("aaa", aaa[idx]);
            }
            if(idx<bbb.length && bbb[idx] > 0)
            {
                con.pushData("bbb", bbb[idx]);
            }
            if(idx<ccc.length && ccc[idx] > 0)
            {
                con.pushData("ccc", ccc[idx]);
            }
        }

        con.processAll();

        int binStart = 1;
        int binEnd = 9;
        for(MyReport report: con.reports)
        {
            assertEquals(report.toString(), binStart, report.binStart);
            assertEquals(report.toString(), binEnd, report.binEnd);
            binStart= binEnd+1;
            binEnd= binStart+9;
        }

        assertEquals(0, con.rejections.size());
        assertEquals(2, con.reports.size());
        assertEquals("BIN-> [1 to 9] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [10 to 19] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(1).toString());

        if (appender.getNumberOfMessages() > 0) {
            fail("Saw log message(s)");
        }

        con.clearReports();
        con.clearRejections();
        con.sendRunData();

        assertEquals(0, con.rejections.size());
        assertEquals(2, con.reports.size());
        assertEquals("BIN-> [20 to 29] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [30 to 37] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(1).toString());

        if (appender.getNumberOfMessages() > 0) {
            fail("Saw log message(s)");
        }
    }

    @Test
    public void testDelayedChannel_2()
    {
        //
        // two bin behind
        //
        MyConsumer con = new MyConsumer(null, 10L);

        long[] aaa = {1, 3, 5,  10, 11, 18,  21, 23, 28,  30, 31, 37};
        long[] bbb = {1, 3, 5,  10, 11, 18,  21, 23, 28,  30, 31, 37};
        long[] ccc = {1, 3, 5,  -1, -1, -1,  -1, -1, -1,  10, 11, 18,  21, 23 , 28,  30, 31, 37};
        for(int idx=0; idx<ccc.length; idx++)
        {
            if(idx<aaa.length && aaa[idx] > 0)
            {
                con.pushData("aaa", aaa[idx]);
            }
            if(idx<bbb.length && bbb[idx] > 0)
            {
                con.pushData("bbb", bbb[idx]);
            }
            if(idx<ccc.length && ccc[idx] > 0)
            {
                con.pushData("ccc", ccc[idx]);
            }
        }

        con.processAll();

        int binStart = 1;
        int binEnd = 9;
        for(MyReport report: con.reports)
        {
            assertEquals(report.toString(), binStart, report.binStart);
            assertEquals(report.toString(), binEnd, report.binEnd);
            binStart= binEnd+1;
            binEnd= binStart+9;
        }

        assertEquals(3, con.rejections.size());
        assertEquals("Index 10 is earlier than the end of the last reported bin range 19",
                con.rejections.get(0).getMessage());
        assertEquals("Index 11 is earlier than the end of the last reported bin range 19"
                , con.rejections.get(1).getMessage());
        assertEquals("Index 18 is earlier than the end of the last reported bin range 19",
                con.rejections.get(2).getMessage());
        assertEquals(2, con.reports.size());
        assertEquals("BIN-> [1 to 9] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [10 to 19] counts {aaa: 3, bbb: 3}",
                con.reports.get(1).toString());

        if (appender.getNumberOfMessages() > 0) {
            fail("Saw log message(s)");
        }

        con.clearReports();
        con.clearRejections();
        con.sendRunData();

        assertEquals(0, con.rejections.size());
        assertEquals(2, con.reports.size());
        assertEquals("BIN-> [20 to 29] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [30 to 37] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(1).toString());

        if (appender.getNumberOfMessages() > 0) {
            fail("Saw log message(s)");
        }
    }

    @Test
    public void testDelayedChannel_3()
    {
        //
        // two bin behind, no initial bin
        //
        MyConsumer con = new MyConsumer(null, 10L);

        long[] aaa = {1, 3, 5,  10, 11, 18,  21, 23, 28,  30, 31, 37,  40, 45, 46};
        long[] bbb = {1, 3, 5,  10, 11, 18,  21, 23, 28,  30, 31, 37,  40, 45, 46};
        long[] ccc = {-1,-1,-1, -1, -1, -1,   1,  3,  5,  10, 11, 18,  21, 23, 28,  30, 31, 37,  40, 45, 46};
        for(int idx=0; idx<ccc.length; idx++)
        {
            if(idx<aaa.length && aaa[idx] > 0)
            {
                con.pushData("aaa", aaa[idx]);
            }
            if(idx<bbb.length && bbb[idx] > 0)
            {
                con.pushData("bbb", bbb[idx]);
            }
            if(idx<ccc.length && ccc[idx] > 0)
            {
                con.pushData("ccc", ccc[idx]);
            }
        }

        con.processAll();


        assertEquals(9, con.rejections.size());
        assertEquals("Index 1 is earlier than the end of the last reported bin range 9",
                con.rejections.get(0).getMessage());
        assertEquals("Index 3 is earlier than the end of the last reported bin range 9"
                , con.rejections.get(1).getMessage());
        assertEquals("Index 5 is earlier than the end of the last reported bin range 9",
                con.rejections.get(2).getMessage());
        assertEquals("Index 10 is earlier than the end of the last reported bin range 19",
                con.rejections.get(3).getMessage());
        assertEquals("Index 11 is earlier than the end of the last reported bin range 19",
                con.rejections.get(4).getMessage());
        assertEquals("Index 18 is earlier than the end of the last reported bin range 19",
                con.rejections.get(5).getMessage());
        assertEquals("Index 21 is earlier than the end of the last reported bin range 29",
                con.rejections.get(6).getMessage());
        assertEquals("Index 23 is earlier than the end of the last reported bin range 29",
                con.rejections.get(7).getMessage());
        assertEquals("Index 28 is earlier than the end of the last reported bin range 29",
                con.rejections.get(8).getMessage());

        assertEquals(3, con.reports.size());
        assertEquals("BIN-> [1 to 9] counts {aaa: 3, bbb: 3}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [10 to 19] counts {aaa: 3, bbb: 3}",
                con.reports.get(1).toString());
        assertEquals("BIN-> [20 to 29] counts {aaa: 3, bbb: 3}",
                con.reports.get(2).toString());

        if (appender.getNumberOfMessages() > 0) {
            fail("Saw log message(s)");
        }

        con.clearReports();
        con.clearRejections();
        con.sendRunData();

        assertEquals(0, con.rejections.size());
        assertEquals(2, con.reports.size());
        assertEquals("BIN-> [30 to 39] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [40 to 46] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(1).toString());

        if (appender.getNumberOfMessages() > 0) {
            fail("Saw log message(s)");
        }
    }


    @Test
    public void testSwitchRun()
    {
        //
        // Test the following details that arise when calling
        // sendRunData() at a switch run:
        //
        // 1) The last bin width needs to be adjusted to match the
        //    actual interval of data
        //
        // 2) Late hits (from channels buffered at any of the
        //    various buffering points from LBM to RunMonitor)
        //    will need to be dropped.

        //
        // two bin behind, no initial bin
        //
        MyConsumer con = new MyConsumer(null, 10L);

        // run 1...
        long[] aaa = {1, 3, 5,  10, 11, 18,  21, 23, 28,  30, 31, 37,  40, 45, 46};
        long[] bbb = {2, 3, 5,  10, 11, 18,  21, 23, 28,  30, 31, 37,  40, 45, 47};
        long[] ccc = {3, 3, 5,  10, 11, 18,  21, 23, 28,  30, 31, 37,  41, -1, -1};
        for(int idx=0; idx<ccc.length; idx++)
        {
            if(idx<aaa.length && aaa[idx] > 0)
            {
                con.pushData("aaa", aaa[idx]);
            }
            if(idx<bbb.length && bbb[idx] > 0)
            {
                con.pushData("bbb", bbb[idx]);
            }
            if(idx<ccc.length && ccc[idx] > 0)
            {
                con.pushData("ccc", ccc[idx]);
            }
        }

        con.processAll();
        con.sendRunData();

        assertEquals(0, con.rejections.size());
        assertEquals(5, con.reports.size());
        assertEquals("BIN-> [1 to 9] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [10 to 19] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(1).toString());
        assertEquals("BIN-> [20 to 29] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(2).toString());
        assertEquals("BIN-> [30 to 39] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(3).toString());
        assertEquals("BIN-> [40 to 47] counts {aaa: 3, ccc: 1, bbb: 3}",
                con.reports.get(4).toString());

        if (appender.getNumberOfMessages() > 0) {
            fail("Saw log message(s)");
        }

        // run 2 (a switch run)
        con.clearReports();
        aaa = new long[]{-1, -1, -1, 50, 52, 58, 61};
        bbb = new long[]{50, 52, 58};
        ccc = new long[]{41, 45, 46, 47, 48, 50, 52, 58};
        for(int idx=0; idx<ccc.length; idx++)
        {
            if(idx<aaa.length && aaa[idx] > 0)
            {
                con.pushData("aaa", aaa[idx]);
            }
            if(idx<bbb.length && bbb[idx] > 0)
            {
                con.pushData("bbb", bbb[idx]);
            }
            if(idx<ccc.length && ccc[idx] > 0)
            {
                con.pushData("ccc", ccc[idx]);
            }
        }

        con.processAll();
        con.sendRunData();
        assertEquals(4, con.rejections.size());
        assertEquals(3, con.reports.size());
        assertEquals("BIN-> [48 to 49] counts {ccc: 1}",
                con.reports.get(0).toString());
        assertEquals("BIN-> [50 to 59] counts {aaa: 3, ccc: 3, bbb: 3}",
                con.reports.get(1).toString());
        assertEquals("BIN-> [60 to 61] counts {aaa: 1}",
                con.reports.get(2).toString());

        if (appender.getNumberOfMessages() > 0) {
            fail("Saw log message(s)");
        }
    }


}
