package icecube.daq.monitoring;

import org.junit.Test;
import static org.junit.Assert.*;

public class BinManagerTest
{
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

    @Test
    public void testNoBins()
    {
        MyBinManager mgr = new MyBinManager("NoBins", 1003L, 10L);
        mgr.clearBin(Long.MIN_VALUE, Long.MAX_VALUE);

        try {
            mgr.getPreviousStart();
            fail("Should not be able to get previous end index");
        } catch (Error err) {
            // expect this to fail
        }

        try {
            mgr.getPreviousEnd();
            fail("Should not be able to get previous end index");
        } catch (Error err) {
            // expect this to fail
        }

        try {
            mgr.getActiveStart();
            fail("Should not be able to get active end index");
        } catch (Error err) {
            // expect this to fail
        }

        try {
            mgr.getActiveEnd();
            fail("Should not be able to get active end index");
        } catch (Error err) {
            // expect this to fail
        }

        try {
            mgr.getActiveLatest();
            fail("Should not be able to get active latest index");
        } catch (Error err) {
            // expect this to fail
        }

        assertFalse("Should not have previous bin", mgr.hasPrevious());
        assertFalse("Should not have active bin", mgr.hasActive());

        assertNull("Got unexpected bin", mgr.getExisting(1010L, 1012L));
    }


    @Test
    public void testBins()
    {
        MyBinManager mgr = new MyBinManager("Bins", 1003L, 10L);
        mgr.reportEvent(1013L);
        assertTrue("Should have previous bin", mgr.hasPrevious());
        assertTrue("Should have active bin", mgr.hasActive());
        assertEquals("Bad previous start", 1003L, mgr.getPreviousStart());
        assertEquals("Bad previous end", 1010L, mgr.getPreviousEnd());
        assertEquals("Bad active start", 1010L, mgr.getActiveStart());
        assertEquals("Bad active end", 1020L, mgr.getActiveEnd());
        assertEquals("Bad active latest", 1013L, mgr.getActiveLatest());
    }

    @Test
    public void testFetchEarly()
    {
        MyBinManager mgr = new MyBinManager("FetchEarly", 1003L, 10L);

        try {
            mgr.reportEvent(963L);
            fail("Should not be able to get a bin before the start index");
        } catch (Error err) {
            // expect this to fail
        }
    }

    @Test
    public void testFetchAfterClear()
    {
        MyBinManager mgr = new MyBinManager("FetchAfter", 1003L, 10L);

        mgr.reportEvent(1004L).inc();

        assertEquals("Bad counter value", 1, mgr.reportEvent(1004L).getValue());

        mgr.clearBin(Long.MIN_VALUE, Long.MAX_VALUE);

        try {
            mgr.reportEvent(1006L);
            fail("Should not be able to get a bin before the last start");
        } catch (Error err) {
            // expect this to fail
        }
    }

    @Test
    public void testFetchTooMany()
    {
        MyBinManager mgr = new MyBinManager("TooMany", 1000L, 10L);

        mgr.reportEvent(1050L).inc();

        try {
            mgr.reportEvent(1060L);
            fail("Should not be able to get a bin with 2 active bins");
        } catch (Error err) {
            // expect this to fail
        }
    }
}
