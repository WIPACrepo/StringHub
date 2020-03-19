package icecube.daq.sender;

import icecube.daq.common.MockAppender;
import icecube.daq.payload.IReadoutRequest;
import icecube.daq.payload.IReadoutRequestElement;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.PayloadException;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.impl.DOMHit;
import icecube.daq.payload.impl.DOMHitFactory;
import icecube.daq.payload.impl.ReadoutRequest;
import icecube.daq.payload.impl.ReadoutRequestElement;
import icecube.daq.payload.impl.SourceID;
import icecube.daq.stringhub.test.MockReadoutRequest;
import icecube.daq.util.DOMRegistryException;
import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests SenderMethods.java
 */
public class SenderMethodsTest
{

    private final MockAppender appender = new MockAppender();


    @Before
    public void setUp() throws IOException, DOMRegistryException, PayloadException
    {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);
    }

    @After
    public void tearDown()
    {
        appender.assertNoLogMessages();
    }


    @Test
    public void testExtractTimeRange()
    {
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        long baseTime = (long) (Math.random() * 3.1536E17);
        long[][] testTimes = new long[10][];
        for (int i = 0; i < testTimes.length; i++)
        {

            long from = (long) (baseTime + Math.random() * 1000000);
            long too = (long) (from + Math.random() * 1000000);
            testTimes[i] = new long[]{from, too};

            minTime = Math.min(from, minTime);
            maxTime = Math.max(too, maxTime);
        }

        IReadoutRequest req = new MockReadoutRequest(-1, -1);
        for (int i = 0; i < testTimes.length; i++)
        {
            req.addElement(-1, -1, testTimes[i][0], testTimes[i][1], -1);
        }

        SenderMethods.TimeRange timeRange = SenderMethods.extractTimeRange(req);

        assertEquals(minTime, timeRange.startUTC);
        assertEquals(maxTime, timeRange.endUTC);
    }

    @Test
    public void testIsRequested() throws PayloadException
    {
        SourceID sourceA = new SourceID(SourceIdRegistry.STRING_HUB_SOURCE_ID + 13);
        SourceID sourceB = new SourceID(SourceIdRegistry.ICETOP_DATA_HANDLER_SOURCE_ID + 23);
        long domA1 = 12345;
        long domA2 = 67890;
        long domB1 = 44444;
        long domB2 = 88888;
        long dataStart = (long) (Math.random() * 3.1536E17);
        long dataMiddle = dataStart + 10000;
        long dataEnd = dataMiddle + (long) (Math.random() * 10000);
        DOMHit srcA_dom1_inRange = generateHit(sourceA, domA1, dataMiddle);
        DOMHit srcA_dom2_inRange = generateHit(sourceA, domA2, dataMiddle);
        DOMHit srcB_dom1_inRange = generateHit(sourceB, domB1, dataMiddle);
        DOMHit srcB_dom2_inRange = generateHit(sourceB, domB2, dataMiddle);

        DOMHit srcA_dom1_before = generateHit(sourceA, domA1, dataStart-1);
        DOMHit srcA_dom2_before = generateHit(sourceA, domA2, dataStart-1);
        DOMHit srcB_dom1_before = generateHit(sourceB, domB1, dataStart-1);
        DOMHit srcB_dom2_before = generateHit(sourceB, domB2, dataStart-1);

        DOMHit srcA_dom1_after = generateHit(sourceA, domA1, dataEnd+1);
        DOMHit srcA_dom2_after = generateHit(sourceA, domA2, dataEnd+1);
        DOMHit srcB_dom1_after = generateHit(sourceB, domB1, dataEnd+1);
        DOMHit srcB_dom2_after = generateHit(sourceB, domB2, dataEnd+1);


        //test global
        {
            IReadoutRequest requestA = createRequest(111, 222,
                    new ReadoutRequestElement(IReadoutRequestElement.READOUT_TYPE_GLOBAL,
                            sourceA.getSourceID(), dataStart, dataMiddle, IReadoutRequestElement.NO_DOM));
            IReadoutRequest requestB = createRequest(111, 222,
                    new ReadoutRequestElement(IReadoutRequestElement.READOUT_TYPE_GLOBAL,
                            sourceA.getSourceID(), dataStart, dataMiddle, IReadoutRequestElement.NO_DOM));
            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom1_before));
            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom2_before));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom1_before));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom2_before));

            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom1_after));
            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom2_after));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom1_after));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom2_after));

            assertTrue(SenderMethods.isRequested(sourceA, requestA, srcA_dom1_inRange));
            assertTrue(SenderMethods.isRequested(sourceA, requestA, srcA_dom2_inRange));
            assertTrue(SenderMethods.isRequested(sourceB, requestB, srcB_dom1_inRange));
            assertTrue(SenderMethods.isRequested(sourceB, requestB, srcB_dom2_inRange));
        }

        //test IT global
        {
            IReadoutRequest requestA = createRequest(111, 222,
                    new ReadoutRequestElement(IReadoutRequestElement.READOUT_TYPE_IT_GLOBAL,
                            sourceA.getSourceID(), dataStart, dataMiddle, IReadoutRequestElement.NO_DOM));
            IReadoutRequest requestB = createRequest(111, 222,
                    new ReadoutRequestElement(IReadoutRequestElement.READOUT_TYPE_IT_GLOBAL,
                            sourceB.getSourceID(), dataStart, dataMiddle, IReadoutRequestElement.NO_DOM));
            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom1_before));
            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom2_before));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom1_before));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom2_before));

            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom1_after));
            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom2_after));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom1_after));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom2_after));

            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom1_inRange));
            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom2_inRange));
            assertTrue(SenderMethods.isRequested(sourceB, requestB, srcB_dom1_inRange));
            assertTrue(SenderMethods.isRequested(sourceB, requestB, srcB_dom2_inRange));
        }

        //test II string
        {
            IReadoutRequest requestA = createRequest(111, 222,
                    new ReadoutRequestElement(IReadoutRequestElement.READOUT_TYPE_II_STRING,
                            sourceA.getSourceID(), dataStart, dataMiddle, domA1));
            IReadoutRequest requestB = createRequest(111, 222,
                    new ReadoutRequestElement(IReadoutRequestElement.READOUT_TYPE_II_STRING,
                            sourceB.getSourceID(), dataStart, dataMiddle, domB1));
            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom1_before));
            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom2_before));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom1_before));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom2_before));

            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom1_after));
            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom2_after));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom1_after));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom2_after));

            assertTrue(SenderMethods.isRequested(sourceA, requestA, srcA_dom1_inRange));
            assertTrue(SenderMethods.isRequested(sourceA, requestA, srcA_dom2_inRange));
            assertTrue(SenderMethods.isRequested(sourceB, requestB, srcB_dom1_inRange));
            assertTrue(SenderMethods.isRequested(sourceB, requestB, srcB_dom2_inRange));

            assertFalse(SenderMethods.isRequested(sourceB, requestA, srcA_dom1_inRange));
            assertFalse(SenderMethods.isRequested(sourceB, requestA, srcA_dom2_inRange));
            assertFalse(SenderMethods.isRequested(sourceA, requestB, srcB_dom1_inRange));
            assertFalse(SenderMethods.isRequested(sourceA, requestB, srcB_dom2_inRange));
        }

        //test IT module
        {
            IReadoutRequest requestA = createRequest(111, 222,
                    new ReadoutRequestElement(IReadoutRequestElement.READOUT_TYPE_IT_MODULE,
                            sourceA.getSourceID(), dataStart, dataMiddle, domA1));
            IReadoutRequest requestB = createRequest(111, 222,
                    new ReadoutRequestElement(IReadoutRequestElement.READOUT_TYPE_IT_MODULE,
                            sourceB.getSourceID(), dataStart, dataMiddle, domB1));
            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom1_before));
            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom2_before));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom1_before));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom2_before));

            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom1_after));
            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom2_after));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom1_after));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom2_after));

            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom1_inRange));
            assertFalse(SenderMethods.isRequested(sourceA, requestA, srcA_dom2_inRange));
            assertTrue(SenderMethods.isRequested(sourceB, requestB, srcB_dom1_inRange));
            assertFalse(SenderMethods.isRequested(sourceB, requestB, srcB_dom2_inRange));

        }

        //test unknown request type
        {
            IReadoutRequest badRequest = createRequest(111, 222,
                    new ReadoutRequestElement(438,
                            sourceA.getSourceID(), dataStart, dataMiddle, domA1));
            assertFalse(SenderMethods.isRequested(sourceA, badRequest, srcA_dom1_inRange));

            assertEquals(1, appender.getNumberOfMessages());
            String logMessage = (String) appender.getMessage(0);
            assertEquals("Unknown request type #438", logMessage);
            appender.clear();

        }

    }


    /**
     * Tests the SenderMethods.HitSorter class.
     */
    public static class HitSorterTest
    {
        @Test
        public void testHitSorter() throws PayloadException
        {
            SenderMethods.HitSorter hitSorter = new SenderMethods.HitSorter();

            ISourceID sourceOne    = new SourceID(1);
            ISourceID sourceTwo    = new SourceID(2000);
            ISourceID sourceThree  = new SourceID(3333);
            long mbidOne1   = 0xAAAAAA;
            long mbidOne2   = 0xAAAAAB;
            long mbidOne3   = 0xAAAAAC;
            long mbidTwo1   = 0xBBBBBB;
            long mbidTwo2   = 0xBBBBBC;
            long mbidTwo3   = 0xBBBBBD;
            long mbidThree1 = 0xCCCCCC;
            long mbidThree2 = 0xCCCCCD;
            long mbidThree3 = 0xCCCCCE;
            long startVal = (long)(Math.random() * Long.MAX_VALUE);

            // expected ordering is by time, source, mbid
            List<DOMHit> ordered = new ArrayList<>();
            ordered.add(generateHit(sourceOne, mbidOne1, startVal));
            ordered.add(generateHit(sourceOne, mbidOne2, startVal));
            ordered.add(generateHit(sourceOne, mbidOne3, startVal));
            ordered.add(generateHit(sourceTwo, mbidTwo1, startVal));
            ordered.add(generateHit(sourceTwo, mbidTwo2, startVal));
            ordered.add(generateHit(sourceTwo, mbidTwo3, startVal));
            ordered.add(generateHit(sourceThree, mbidThree1, startVal));
            ordered.add(generateHit(sourceThree, mbidThree2, startVal));
            ordered.add(generateHit(sourceThree, mbidThree3, startVal));

            ordered.add(generateHit(sourceTwo, mbidTwo1, startVal + 1111));
            ordered.add(generateHit(sourceTwo, mbidTwo2, startVal+ 1111));
            ordered.add(generateHit(sourceTwo, mbidTwo3, startVal+ 1111));

            ordered.add(generateHit(sourceOne, mbidOne3, startVal + 1112));
            ordered.add(generateHit(sourceOne, mbidOne1, startVal + 1113));
            ordered.add(generateHit(sourceOne, mbidOne2, startVal + 1114));

            ordered.add(generateHit(sourceThree, mbidThree2, startVal + 1115));
            ordered.add(generateHit(sourceThree, mbidThree3, startVal+ 1115));
            ordered.add(generateHit(sourceThree, mbidThree1, startVal+ 1116));

            List<DOMHit> shuffled = new ArrayList<>(ordered);
            Collections.shuffle(shuffled);


            try
            {
                assertArrayEquals(ordered.toArray(), shuffled.toArray());
                fail("Bad shuffle?");
            }
            catch (Throwable th)
            {
                // desired to prove shuffling
            }


            Collections.sort(shuffled, hitSorter);

            assertArrayEquals(ordered.toArray(), shuffled.toArray());
        }

    }

    private IReadoutRequest createRequest(long time,
                                          int uid, IReadoutRequestElement... elements)
    {
        ReadoutRequest rr = new ReadoutRequest(time, uid, -1);
        for (int i = 0; i < elements.length; i++)
        {
            IReadoutRequestElement element = elements[i];
            rr.addElement(element.getReadoutType(),
                    element.getSourceID().getSourceID(),
                    element.getFirstTime(), element.getLastTime(),
                    element.getDOMID().longValue());
        }
        return rr;
    }

    static DOMHit generateHit(ISourceID srcID, long mbid, long utc) throws PayloadException
    {

        int LENGTH = 213;
        ByteBuffer bb = ByteBuffer.allocate(LENGTH);
        bb.putInt(LENGTH);              // length
        bb.putInt(3);                   // type
        bb.putLong(mbid);               // mbid
        bb.putLong(0);                  // pad
        bb.putLong(utc);                // utc
        bb.putChar((char) 0x01);        // BO mark
        bb.putChar((char) 0x12);        // version
        bb.putChar((char) 0x47);        // FQP
        bb.putLong(198328973);          // domclk
        bb.putInt(0x8004080C);          // word-1
        bb.putInt(0x3A111C8A);          // word-3
        bb.put(new byte[LENGTH - 54]);  // payload
        bb.flip();

        return DOMHitFactory.getHit(srcID, bb, 0);
    }



}
