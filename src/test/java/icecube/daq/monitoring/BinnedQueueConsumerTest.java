package icecube.daq.monitoring;

import icecube.daq.stringhub.test.MockAppender;

import java.util.ArrayList;

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
            for (String name: names) {
                MyCounter ctr = getExisting(name, binStart, binEnd);
            }
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
}
