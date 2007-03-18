package icecube.daq.bindery;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

import icecube.daq.payload.ByteBufferCache;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.MasterPayloadFactory;
import icecube.daq.splicer.Spliceable;
import icecube.daq.splicer.SpliceableFactory;
import icecube.daq.splicer.SplicedAnalysis;
import icecube.daq.splicer.Splicer;
import icecube.daq.splicer.StrandTail;

import org.apache.log4j.BasicConfigurator;
import org.junit.Before;
import org.junit.Test;

public class HKN1SplicerTest implements SplicedAnalysis
{

    SpliceableFactory factory;
    IByteBufferCache  cacheMgr;
    HKN1Splicer       splicer;
    boolean           isOrdered = false;
    int               outputCount;

    public HKN1SplicerTest()
    {
        cacheMgr = new ByteBufferCache(256, 2500000, 2000000);
        factory = new MasterPayloadFactory(cacheMgr);
        BasicConfigurator.configure();
    }

    @Before
    public void setUp() throws Exception
    {
        isOrdered = true;
        outputCount = 0;
    }

    @Test
    public void basicUnloadedTest() throws Exception
    {
        splicer = new HKN1Splicer(this);
        StrandTail tail0 = splicer.beginStrand();
        StrandTail tail1 = splicer.beginStrand();
        splicer.start();
        for (int i = 0; i < 100; i++)
        {
            Random r = new Random();
            double u = r.nextDouble();
            Thread.sleep((long) (30.0 * u));
            if (r.nextBoolean())
                tail1.push(new TimeStamp());
            else
                tail0.push(new TimeStamp());
        }
        tail0.push(Splicer.LAST_POSSIBLE_SPLICEABLE);
        tail1.push(Splicer.LAST_POSSIBLE_SPLICEABLE);
        splicer.stop();
        Thread.sleep(100);
        System.out.println("splicer contents = " + splicer.getCount());
        assertTrue(isOrdered);
        assertEquals(100, outputCount);
    }

    /**
     * A trivial execute method which simply truncates the splicer and makes
     * sure output is truly ordered.
     */
    public void execute(List splicedObjects, int decrement)
    {
        Iterator it = splicedObjects.iterator();
        TimeStamp lastObj = null;
        while (it.hasNext())
        {
            TimeStamp obj = (TimeStamp) it.next();
            if (lastObj != null)
            {
                if (obj.compareTo(lastObj) < 0)
                {
                    System.err.println(
                            "ERROR: objects exiting splicer are not ordered: ["
                            + lastObj.timestamp + ", " 
                            + obj.timestamp + "].");
                    isOrdered = false;
                }
            }
            lastObj = obj;
            outputCount++;
        }
        int n = splicedObjects.size();
        if (n > 0) splicer.truncate((Spliceable) splicedObjects.get(n - 1));
        System.out.println("execute called with # items = " + n + " decrement = " + decrement);
    }

    public SpliceableFactory getFactory()
    {
        // TODO Auto-generated method stub
        return factory;
    }

}

class TimeStamp implements Spliceable
{
    public long timestamp;

    public TimeStamp()
    {
        timestamp = System.currentTimeMillis();
    }

    public int compareTo(Object o)
    {
        
        TimeStamp ts = (TimeStamp) o;
        if (timestamp < ts.timestamp) return -1;
        if (timestamp > ts.timestamp) return +1;
        return 0;
    }

}
