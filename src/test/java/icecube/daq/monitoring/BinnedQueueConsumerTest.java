package icecube.daq.monitoring;

import icecube.daq.common.MockAppender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.BasicConfigurator;

import org.junit.After;
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

            MyCounter ctr = getContainer(nv.val, nv.name);
            ctr.inc();
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

        int binStart = 1;
        int binEnd = 9;
        for(MyReport report: con.reports)
        {
            assertEquals(report.toString(), binStart, report.binStart);
            assertEquals(report.toString(), binEnd, report.binEnd);
            binStart= binEnd+1;
            binEnd= binStart+9;
        }

        if (appender.getNumberOfMessages() > 0) {
            fail("Saw log message(s)");
        }

    }

}
