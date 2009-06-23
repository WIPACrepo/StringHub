package icecube.daq.sender;

import icecube.daq.io.DAQOutputChannelManager;
import icecube.daq.io.OutputChannel;
import icecube.daq.payload.IDOMID;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.stringhub.test.MockAppender;
import icecube.daq.stringhub.test.MockBufferCache;
import icecube.daq.stringhub.test.MockReadoutRequest;
import icecube.daq.stringhub.test.MockUTCTime;
import icecube.daq.trigger.IReadoutRequest;
import icecube.daq.trigger.IReadoutRequestElement;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

interface ExpectedData
    extends Comparable
{
}

abstract class MockOutputChannel
    implements OutputChannel
{
    private String dataType;
    private boolean stopped;
    private List<ExpectedData> expected = new ArrayList<ExpectedData>();

    public MockOutputChannel(String dataType)
    {
        this.dataType = dataType;
    }

    void addExpectedData(ExpectedData data)
    {
        expected.add(data);
    }

    abstract ExpectedData getBufferData(ByteBuffer buf);

    int getNumExpected()
    {
        return expected.size();
    }

    public void receiveByteBuffer(ByteBuffer buf)
    {
        ExpectedData actual = getBufferData(buf);

        if (expected.size() == 0) {
            throw new Error("Received unexpected " + dataType + " " + actual);
        }

        ExpectedData exp = expected.remove(0);

        if (!exp.equals(actual)) {
            throw new Error("Expected " + dataType + " " + exp + ", not " +
                            actual);
        }
    }

    public void sendLastAndStop()
    {
        if (stopped) {
            throw new Error("Channel was already stopped");
        }

        stopped = true;
    }
}

class ExpectedHit
    implements ExpectedData
{
    private long domId;
    private long utcTime;
    private int trigType;
    private int cfgId;
    private int srcId;
    private int trigMode;

    ExpectedHit(long domId, long utcTime, int trigType, int cfgId, int srcId,
                int trigMode)
    {
        this.domId = domId;
        this.utcTime = utcTime;
        this.trigType = trigType;
        this.cfgId = cfgId;
        this.srcId = srcId;
        this.trigMode = trigMode;
    }

    ExpectedHit(ByteBuffer buf)
    {
        if (buf.getInt(0) != 38) {
            throw new Error("Expected hit payload length of 38, not " +
                            buf.getInt(0));
        }
        if (buf.getInt(4) != PayloadRegistry.PAYLOAD_ID_SIMPLE_HIT) {
            throw new Error("Bad hit payload type " + buf.getInt(4));
        }

        utcTime = buf.getLong(8);
        trigType = buf.getInt(16);
        cfgId = buf.getInt(20);
        srcId = buf.getInt(24);
        domId = buf.getLong(28);
        trigMode = buf.getShort(36);
    }

    public int compareTo(Object obj)
    {
        if (!(obj instanceof ExpectedHit)) {
            return getClass().getName().compareTo(obj.getClass().getName());
        }

        return compareTo((ExpectedHit) obj);
    }

    public int compareTo(ExpectedHit hit)
    {
        long lval;

        lval = domId - hit.domId;
        if (lval < 0L) {
            return -1;
        } else if (lval > 0L) {
            return 1;
        }

        lval = utcTime - hit.utcTime;
        if (lval < 0L) {
            return -1;
        } else if (lval > 0L) {
            return 1;
        }

        int val = trigType - hit.trigType;
        if (val == 0) {
            val = cfgId - hit.cfgId;
            if (val == 0) {
                val = srcId - hit.srcId;
                if (val == 0) {
                    val = trigMode - hit.trigMode;
                }
            }
        }

        return val;
    }

    public boolean equals(Object obj)
    {
        if (!(obj instanceof ExpectedHit)) {
            return getClass().getName().equals(obj.getClass().getName());
        }

        return compareTo((ExpectedHit) obj) == 0;
    }

    public String toString()
    {
        return "ExpHit@" + Long.toHexString(domId) + "[time " + utcTime +
            " type " + trigType + " cfg " + cfgId + " src " + srcId +
            " mode " + trigMode + "]";
    }
}

class MockHitChannel
    extends MockOutputChannel
{
    MockHitChannel()
    {
        super("hit");
    }

    void addExpectedHit(long domId, long utcTime, int trigType, int cfgId,
                        int srcId, int trigMode)
    {
        addExpectedData(new ExpectedHit(domId, utcTime, trigType, cfgId, srcId,
                                        trigMode));
    }

    ExpectedData getBufferData(ByteBuffer buf)
    {
        return new ExpectedHit(buf);
    }
}

class ExpectedReadout
    implements ExpectedData
{
    private long utcTime;
    private int uid;
    private short payNum;
    private short payLast;
    private int srcId;
    private long firstTime;
    private long lastTime;
    private int numHits;

    ExpectedReadout(long utcTime, int uid, short payNum, short payLast,
                    int srcId, long firstTime, long lastTime, int numHits)
    {
        this.utcTime = utcTime;
        this.uid = uid;
        this.payNum = payNum;
        this.payLast = payLast;
        this.srcId = srcId;
        this.firstTime = firstTime;
        this.lastTime = lastTime;
        this.numHits = numHits;
    }

    ExpectedReadout(ByteBuffer buf)
    {
        final int minBytes = 54;
        if (buf.getInt(0) < minBytes) {
            throw new Error("Readout payload must contain at least " +
                            minBytes + " bytes, not " + buf.getInt(0));
        }
        if (buf.getInt(4) != PayloadRegistry.PAYLOAD_ID_READOUT_DATA) {
            throw new Error("Bad readout data payload type " + buf.getInt(4));
        }
        if (buf.getShort(16) != 1) {
            throw new Error("Bad readout record type " + buf.getInt(16));
        }

        utcTime = buf.getLong(8);
        uid = buf.getInt(18);
        payNum = buf.getShort(22);
        payLast = buf.getShort(24);
        srcId = buf.getInt(26);
        firstTime = buf.getLong(30);
        lastTime = buf.getLong(38);

        numHits = buf.getShort(52);
    }

    public int compareTo(Object obj)
    {
        if (obj == null) {
            return -1;
        }
        if (!(obj instanceof ExpectedReadout)) {
            return getClass().getName().compareTo(obj.getClass().getName());
        }

        return compareTo((ExpectedReadout) obj);
    }

    public int compareTo(ExpectedReadout ro)
    {
        long lval;

        lval = utcTime - ro.utcTime;
        if (lval < 0L) {
            return -1;
        } else if (lval > 0L) {
            return 1;
        }

        lval = firstTime - ro.firstTime;
        if (lval < 0L) {
            return -1;
        } else if (lval > 0L) {
            return 1;
        }

        lval = lastTime - ro.lastTime;
        if (lval < 0L) {
            return -1;
        } else if (lval > 0L) {
            return 1;
        }

        int val = uid - ro.uid;
        if (val == 0) {
            val = payNum - ro.payNum;
            if (val == 0) {
                val = payLast - ro.payLast;
                if (val == 0) {
                    val = srcId - ro.srcId;
                    if (val == 0) {
                        val = numHits - ro.numHits;
                    }
                }
            }
        }

        return val;
    }

    public boolean equals(Object obj)
    {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof ExpectedReadout)) {
            return getClass().getName().equals(obj.getClass().getName());
        }

        return compareTo((ExpectedReadout) obj) == 0;
    }

    public String toString()
    {
        return "ExpRdout[#" + uid + " pay " + payNum + "/" + payLast +
            " src " + srcId + " hits*" + numHits + "]";
    }
}

class MockReadoutChannel
    extends MockOutputChannel
{
    MockReadoutChannel()
    {
        super("readout");
    }

    void addExpectedReadout(long utcTime, int uid, short payNum, short payLast,
                            int srcId, long firstTime, long lastTime,
                            int numHits)
    {
        addExpectedData(new ExpectedReadout(utcTime, uid, payNum, payLast,
                                            srcId, firstTime, lastTime,
                                            numHits));
    }

    ExpectedData getBufferData(ByteBuffer buf)
    {
        return new ExpectedReadout(buf);
    }
}

class MockOutputChannelManager
    implements DAQOutputChannelManager
{
    private OutputChannel chan;

    MockOutputChannelManager(OutputChannel chan)
    {
        this.chan = chan;
    }

    public OutputChannel getChannel()
    {
        return chan;
    }
}

public class SenderTest
{
    private static final int HUB_SRCID =
        SourceIdRegistry.STRING_HUB_SOURCE_ID + 1;
    private static final int IITRIG_SRCID =
        SourceIdRegistry.INICE_TRIGGER_SOURCE_ID;

    private static final int TYPE_ENG_HIT = 2;
    private static final int TYPE_DELTA_HIT = 17;

    private static final int NUM_ATWD_CHANNELS = 4;

    private static final int[] ATWD_SAMPLE_LENGTH = { 32, 64, 16, 128 };

    private static final MockAppender appender =
        //new MockAppender(org.apache.log4j.Level.ALL).setVerbose(true);
        new MockAppender();

    public SenderTest()
    {
    }

    private static final ByteBuffer createDeltaHit(long domId, long utcTime,
                                                   short version,
                                                   short pedestal,
                                                   long domClock,
                                                   byte lcMode,
                                                   int trigMode,
                                                   short waveformFlags,
                                                   int peakInfo,
                                                   byte[] data)
    {
        final int sizeOfCompressedHeader = 12;

        int trigBits;
        switch (trigMode) {
        case 1:
            trigBits = 0x0004;
            break;
        case 2:
            trigBits = 0x0003;
            break;
        case 3:
            trigBits = 0x0010;
            break;
        case 4:
            trigBits = 0x1000;
            break;
        default:
            throw new Error("Unknown trigger mode " + trigMode);
        }

        if (lcMode < 0 || lcMode > 3) {
            throw new Error("Bad LC mode " + lcMode);
        }

        int word0 = (trigBits << 18) +
            (((int) lcMode & 0x3) << 16) +
            (((int) waveformFlags & 0x1f) << 11) +
            ((data.length + sizeOfCompressedHeader) & 0x7ff);
        final int recLen = 22 + data.length;

        final int totLen = 32 + recLen;

        ByteBuffer buf = ByteBuffer.allocate(totLen);
        buf.putInt(totLen);
        buf.putInt(TYPE_DELTA_HIT);
        buf.putLong(domId);
        buf.putLong(0L);
        buf.putLong(utcTime);

        final ByteOrder origOrder = buf.order();

        buf.order(ByteOrder.BIG_ENDIAN);

        buf.putShort((short) 1);       // used to test byte order
        buf.putShort((short) 1234);    // version
        buf.putShort(pedestal);
        buf.putLong(domClock);
        buf.putInt(word0);
        buf.putInt(peakInfo);
        for (int i = 0; i < data.length; i++) {
            buf.put(data[i]);
        }

        buf.flip();

        buf.order(origOrder);

        if (buf.limit() != buf.capacity()) {
            throw new Error("Expected payload length is " + buf.capacity() +
                            ", actual length is " + buf.limit());
        }

        return buf;
    }

    private static final ByteBuffer createEngHit(long domId, long utcTime,
                                                 int atwdChip, int trigMode,
                                                 long domClock,
                                                 Object fadcObj,
                                                 Object atwdObj)
    {
        if (fadcObj == null || !(fadcObj.getClass().isArray())) {
            throw new Error("Invalid FADC array object " + fadcObj);
        }

        final int lenFADC = Array.getLength(fadcObj);

        if (atwdObj == null || !(atwdObj.getClass().isArray())) {
            throw new Error("Invalid ATWD array object " + atwdObj);
        }

        final int lenATWD = Array.getLength(atwdObj);
        if (lenATWD != NUM_ATWD_CHANNELS) {
            throw new Error("Expected " + NUM_ATWD_CHANNELS +
                            " ATWD channels, not " + lenATWD);
        }

        final boolean isATWDShort = atwdObj instanceof short[][];
        if (!isATWDShort && !(atwdObj instanceof byte[][])) {
            throw new Error("Invalid ATWD array type");
        }

        int affByte0 = 0;
        int affByte1 = 0;

        int numATWDSamples = -1;
        for (int i = 0; i < lenATWD; i++) {
            Object subATWD = Array.get(atwdObj, i);
            if (subATWD == null || !subATWD.getClass().isArray()) {
                throw new Error("Invalid ATWD channel#" + i);
            }

            final int subLen = Array.getLength(subATWD);
            if (numATWDSamples < 0) {
                numATWDSamples = subLen;
            } else if (numATWDSamples != subLen) {
                throw new Error("Expected " + numATWDSamples +
                                " samples for ATWD channel#" + i + ", not " +
                                subLen);
            }

            int sampLen = -1;
            for (int j = 0; j < ATWD_SAMPLE_LENGTH.length; j++) {
                if (subLen == ATWD_SAMPLE_LENGTH[j]) {
                    sampLen = j;
                    break;
                }
            }
            if (sampLen < 0) {
                throw new Error("Unknown sample length " + subLen +
                                " for ATWD channel#" + i);
            }

            int nybble = 1 | (isATWDShort ? 2 : 0) | (sampLen * 4);
            switch (i) {
            case 0:
                affByte0 |= nybble;
                break;
            case 1:
                affByte0 |= (nybble << 4);
                break;
            case 2:
                affByte1 |= nybble;
                break;
            case 3:
                affByte1 |= (nybble << 4);
                break;
            }
        }

        final int recLen = 16 + (lenFADC * 2) +
            (lenATWD * numATWDSamples * (isATWDShort ? 2 : 1));

        final int totLen = 32 + recLen;

        ByteBuffer buf = ByteBuffer.allocate(totLen);
        buf.putInt(totLen);
        buf.putInt(TYPE_ENG_HIT);
        buf.putLong(domId);
        buf.putLong(0L);
        buf.putLong(utcTime);

        final ByteOrder origOrder = buf.order();

        buf.order(ByteOrder.BIG_ENDIAN);

        buf.putShort((short) recLen);  // record length
        buf.putShort((short) 1);       // used to test byte order
        buf.put((byte) atwdChip);
        buf.put((byte) lenFADC);
        buf.put((byte) affByte0);
        buf.put((byte) affByte1);
        buf.put((byte) trigMode);
        buf.put((byte) 0);
        putDomClock(buf, buf.position(), domClock);
        for (int i = 0; i < lenFADC; i++) {
            buf.putShort(Array.getShort(fadcObj, i));
        }
        for (int i = 0; i < lenATWD; i++) {
            Object samples = Array.get(atwdObj, i);
            for (int j = 0; j < numATWDSamples; j++) {
                if (isATWDShort) {
                    buf.putShort(Array.getShort(samples, j));
                } else {
                    buf.put(Array.getByte(samples, j));
                }
            }
        }

        buf.flip();

        buf.order(origOrder);

        if (buf.limit() != buf.capacity()) {
            throw new Error("Expected payload length is " + buf.capacity() +
                            ", actual length is " + buf.limit());
        }

        return buf;
    }

    private static final ByteBuffer createStop()
    {
        ByteBuffer buf = ByteBuffer.allocate(32);
        buf.putInt(0, 32);
        buf.putLong(24, Long.MAX_VALUE);
        return buf;
    }

    public static void putDomClock(ByteBuffer bb, int offset, long domClock)
    {
        int shift = 40;
        for (int i = 0; i < 6; i++) {
            bb.put(offset + i, (byte) ((int) (domClock >> shift) & 0xff));
            shift -= 8;
        }
        bb.position(offset + 6);
    }

    @Before
    public void setUp()
    {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);
    }

    @After
    public void tearDown()
    {
        for (int i = 0; i < appender.getNumberOfMessages(); i++) {
            String msg = (String) appender.getMessage(i);

            if (!msg.startsWith("Found Stop request in ") &&
                !msg.startsWith("Found Stop data in ") &&
                !msg.startsWith("Adding data stop while thread "))
            {
                fail("Bad log message#" + i + ": " + appender.getMessage(i));
            }
        }
    }

    @Test
    public void testConsumeStop()
    {
        MockBufferCache cache = new MockBufferCache();
        Sender sender = new Sender(HUB_SRCID % 1000, cache);

        MockHitChannel hitChan = new MockHitChannel();
        sender.setHitOutput(new MockOutputChannelManager(hitChan));

        MockReadoutChannel rdoutChan = new MockReadoutChannel();
        sender.setDataOutput(new MockOutputChannelManager(rdoutChan));

        sender.startThread();

        ByteBuffer buf = createStop();
        sender.consume(buf);

        waitForDataStop(sender, 1);

        sender.stopThread();

        waitForSenderStop(sender);

        assertEquals("Did not receive request stop",
                     1L, sender.getTotalRequestStopsReceived());
        assertEquals("Did not send stop",
                     1L, sender.getTotalStopsSent());

        appender.clear();
    }

    @Test
    public void testConsumeEngHit()
    {
        MockBufferCache cache = new MockBufferCache();
        Sender sender = new Sender(HUB_SRCID % 1000, cache);

        MockHitChannel hitChan = new MockHitChannel();
        sender.setHitOutput(new MockOutputChannelManager(hitChan));

        MockReadoutChannel rdoutChan = new MockReadoutChannel();
        sender.setDataOutput(new MockOutputChannelManager(rdoutChan));

        sender.startThread();

        long domId = 0xfedcba987654L;
        long utcTime = 123456789L;
        int atwdChip = 1;
        int trigMode = 4;
        long domClock = utcTime;
        Object fadcSamples = new short[] { 1, 2, 3 };
        Object atwdSamples = new short[NUM_ATWD_CHANNELS][32];

        ByteBuffer buf = createEngHit(domId, utcTime, atwdChip, trigMode,
                                      domClock, fadcSamples, atwdSamples);

        hitChan.addExpectedHit(domId, utcTime, trigMode, 0, HUB_SRCID,
                               trigMode);

        sender.consume(buf);

        sender.stopThread();

        waitForSenderStop(sender);

        assertEquals("Did not receive hit",
                     1L, sender.getNumHitsReceived());
        assertEquals("Bad hit time",
                     utcTime, sender.getLatestHitTime());

        assertEquals("Did not receive data stop",
                     1L, sender.getTotalDataStopsReceived());
        assertEquals("Did not receive request stop",
                     1L, sender.getTotalRequestStopsReceived());
        assertEquals("Did not send stop",
                     1L, sender.getTotalStopsSent());

        assertEquals("Not all expected hits were received",
                     0, hitChan.getNumExpected());
        assertEquals("Not all expected readouts were received",
                     0, rdoutChan.getNumExpected());
    }

    @Test
    public void testConsumeDeltaHit()
    {
        MockBufferCache cache = new MockBufferCache();
        Sender sender = new Sender(HUB_SRCID % 1000, cache);

        MockHitChannel hitChan = new MockHitChannel();
        sender.setHitOutput(new MockOutputChannelManager(hitChan));

        MockReadoutChannel rdoutChan = new MockReadoutChannel();
        sender.setDataOutput(new MockOutputChannelManager(rdoutChan));

        sender.startThread();

        long domId = 0xfedcba987654L;
        long utcTime = 123456789L;
        short version = 12;
        short pedestal = 34;
        long domClock = utcTime;
        byte lcMode = 3;
        int trigMode = 4;
        short waveformFlags = 78;
        int peakInfo = 9876;
        byte[] data = new byte[32];

        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        ByteBuffer buf = createDeltaHit(domId, utcTime, version, pedestal,
                                        domClock, lcMode, trigMode,
                                        waveformFlags, peakInfo, data);

        hitChan.addExpectedHit(domId, utcTime, trigMode, 0, HUB_SRCID,
                               trigMode);

        sender.consume(buf);

        sender.stopThread();

        waitForSenderStop(sender);

        assertEquals("Did not receive hit",
                     1L, sender.getNumHitsReceived());
        assertEquals("Bad hit time",
                     utcTime, sender.getLatestHitTime());

        assertEquals("Did not receive data stop",
                     1L, sender.getTotalDataStopsReceived());
        assertEquals("Did not receive request stop",
                     1L, sender.getTotalRequestStopsReceived());
        assertEquals("Did not send stop",
                     1L, sender.getTotalStopsSent());

        assertEquals("Not all expected hits were received",
                     0, hitChan.getNumExpected());
        assertEquals("Not all expected readouts were received",
                     0, rdoutChan.getNumExpected());
    }

    @Test
    public void testConsumeHitsAndRequest()
    {
        MockBufferCache cache = new MockBufferCache();
        Sender sender = new Sender(HUB_SRCID % 1000, cache);

        MockHitChannel hitChan = new MockHitChannel();
        sender.setHitOutput(new MockOutputChannelManager(hitChan));

        MockReadoutChannel rdoutChan = new MockReadoutChannel();
        sender.setDataOutput(new MockOutputChannelManager(rdoutChan));

        sender.startThread();

        int numHitsSent = 0;
        long lastHitTime = 0L;

        ByteBuffer buf;

        long baseTime = 123456789L;
        int trigMode = 4;

        long engDomId = 0xfedcba987654L;
        long engTime = baseTime + 100L;
        int atwdChip = 1;
        Object fadcSamples = new short[] { 1, 2, 3 };
        Object atwdSamples = new short[NUM_ATWD_CHANNELS][32];

        buf = createEngHit(engDomId, engTime, atwdChip, trigMode, engTime,
                           fadcSamples, atwdSamples);

        hitChan.addExpectedHit(engDomId, engTime, trigMode, 0, HUB_SRCID,
                               trigMode);

        sender.consume(buf);

        numHitsSent++;
        lastHitTime = engTime;

        long deltaDomId = 0xedcba9876543L;
        long deltaTime = baseTime + 200L;
        short version = 1;
        short pedestal = 34;
        byte lcMode = 3;
        short waveformFlags = 78;
        int peakInfo = 9876;
        byte[] data = new byte[32];

        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        buf = createDeltaHit(deltaDomId, deltaTime, version, pedestal,
                             deltaTime, lcMode, trigMode, waveformFlags,
                             peakInfo, data);

        hitChan.addExpectedHit(deltaDomId, deltaTime, trigMode, 0, HUB_SRCID,
                               trigMode);

        sender.consume(buf);

        numHitsSent++;
        lastHitTime = deltaTime;

        final int uid = 123;
        final long roFirstTime = baseTime - 100L;
        final long roLastTime = lastHitTime + 100L;

        MockReadoutRequest req = new MockReadoutRequest(uid, IITRIG_SRCID);
        req.addElement(IReadoutRequestElement.READOUT_TYPE_GLOBAL,
                       roFirstTime, roLastTime, -1L, HUB_SRCID);

        sender.addRequest(req);

        rdoutChan.addExpectedReadout(roFirstTime, uid, (short) 0, (short) 1,
                                     HUB_SRCID, roFirstTime, roLastTime,
                                     numHitsSent);

        waitForRequestDequeued(sender);

        long flushDomId = 0xdcba98765432L;
        long flushTime = baseTime + 10000L;

        buf = createEngHit(flushDomId, flushTime, atwdChip, trigMode, flushTime,
                           fadcSamples, atwdSamples);

        hitChan.addExpectedHit(flushDomId, flushTime, trigMode, 0, HUB_SRCID,
                               trigMode);

        sender.consume(buf);

        numHitsSent++;
        lastHitTime = flushTime;

        waitForReadoutSent(sender);

        sender.stopThread();

        waitForSenderStop(sender);

        assertEquals("Did not receive hit",
                     (long) numHitsSent, sender.getNumHitsReceived());
        assertEquals("Bad hit time",
                     lastHitTime, sender.getLatestHitTime());

        assertEquals("Did not receive data stop",
                     1L, sender.getTotalDataStopsReceived());
        assertEquals("Did not receive request stop",
                     1L, sender.getTotalRequestStopsReceived());
        assertEquals("Did not send stop",
                     1L, sender.getTotalStopsSent());

        assertEquals("Not all expected hits were received",
                     0, hitChan.getNumExpected());
        assertEquals("Not all expected readouts were received",
                     0, rdoutChan.getNumExpected());
    }

    private static final void waitForDataStop(Sender sender, long numStops)
    {
        for (int i = 0; i < 20; i++) {
            if (sender.getTotalDataStopsReceived() == numStops) {
                break;
            }

            try {
                Thread.sleep(100);
            } catch (Exception ex) {
                // ignore interrupts
            }
        }
        assertEquals("Data stop was not received",
                     numStops, sender.getTotalDataStopsReceived());
    }

    private static final void waitForReadoutSent(Sender sender)
    {
        for (int i = 0; i < 10; i++) {
            if (sender.getNumReadoutsSent() == 1L) {
                break;
            }

            try {
                Thread.sleep(100);
            } catch (Exception ex) {
                // ignore interrupts
            }
        }
        assertEquals("Readout was not sent",
                     1L, sender.getNumReadoutsSent());
    }

    private static final void waitForRequestDequeued(Sender sender)
    {
        for (int i = 0; i < 10; i++) {
            if (sender.getNumRequestsQueued() == 0) {
                break;
            }

            try {
                Thread.sleep(100);
            } catch (Exception ex) {
                // ignore interrupts
            }
        }
        assertEquals("Request was not dequeued",
                     0, sender.getNumRequestsQueued());
    }

    private static final void waitForSenderStop(Sender sender)
    {
        for (int i = 0; i < 10; i++) {
            if (!sender.isRunning()) {
                break;
            }

            try {
                Thread.sleep(100);
            } catch (Exception ex) {
                // ignore interrupts
            }
        }
        assertFalse("Sender did not stop", sender.isRunning());
    }

    private static final void dumpSender(String title, Sender sender)
    {
        System.err.println(title);
        if (sender.getAverageHitsPerReadout() != 0L)
            System.err.println("  AverageHitsPerReadout " + sender.getAverageHitsPerReadout());
        if (sender.getAverageOutputDataPayloads() != 0L)
            System.err.println("  AverageOutputDataPayloads " + sender.getAverageOutputDataPayloads());
        System.err.println("  *BackEndState " + sender.getBackEndState());
        System.err.println("  *BackEndTiming " + sender.getBackEndTiming());
        if (sender.getDataPayloadsPerSecond() != 0.0)
            System.err.println("  DataPayloadsPerSecond " + sender.getDataPayloadsPerSecond());
        if (sender.getHitsPerSecond() != 0.0)
            System.err.println("  HitsPerSecond " + sender.getHitsPerSecond());
        if (sender.getLatestHitTime() != 0L)
            System.err.println("  LatestHitTime " + sender.getLatestHitTime());
        System.err.println("  *LatestReadoutTimes " + sender.getLatestReadoutTimes());
        if (sender.getNumBadDataPayloads() != 0L)
            System.err.println("  NumBadDataPayloads " + sender.getNumBadDataPayloads());
        if (sender.getNumBadHits() != 0L)
            System.err.println("  NumBadHits " + sender.getNumBadHits());
        if (sender.getNumBadReadoutRequests() != 0L)
            System.err.println("  NumBadReadoutRequests " + sender.getNumBadReadoutRequests());
        if (sender.getNumBadRequests() != 0L)
            System.err.println("  NumBadRequests " + sender.getNumBadRequests());
        if (sender.getNumDataPayloadsCached() != 0)
            System.err.println("  NumDataPayloadsCached " + sender.getNumDataPayloadsCached());
        if (sender.getNumDataPayloadsDiscarded() != 0L)
            System.err.println("  NumDataPayloadsDiscarded " + sender.getNumDataPayloadsDiscarded());
        if (sender.getNumDataPayloadsDropped() != 0L)
            System.err.println("  NumDataPayloadsDropped " + sender.getNumDataPayloadsDropped());
        if (sender.getNumDataPayloadsQueued() != 0)
            System.err.println("  NumDataPayloadsQueued " + sender.getNumDataPayloadsQueued());
        if (sender.getNumDataPayloadsReceived() != 0L)
            System.err.println("  NumDataPayloadsReceived " + sender.getNumDataPayloadsReceived());
        if (sender.getNumEmptyLoops() != 0L)
            System.err.println("  NumEmptyLoops " + sender.getNumEmptyLoops());
        if (sender.getNumHitsCached() != 0)
            System.err.println("  NumHitsCached " + sender.getNumHitsCached());
        if (sender.getNumHitsDiscarded() != 0L)
            System.err.println("  NumHitsDiscarded " + sender.getNumHitsDiscarded());
        if (sender.getNumHitsDropped() != 0L)
            System.err.println("  NumHitsDropped " + sender.getNumHitsDropped());
        if (sender.getNumHitsQueued() != 0)
            System.err.println("  NumHitsQueued " + sender.getNumHitsQueued());
        if (sender.getNumHitsReceived() != 0L)
            System.err.println("  NumHitsReceived " + sender.getNumHitsReceived());
        if (sender.getNumNullDataPayloads() != 0L)
            System.err.println("  NumNullDataPayloads " + sender.getNumNullDataPayloads());
        if (sender.getNumNullHits() != 0L)
            System.err.println("  NumNullHits " + sender.getNumNullHits());
        if (sender.getNumNullOutputs() != 0L)
            System.err.println("  NumNullOutputs " + sender.getNumNullOutputs());
        if (sender.getNumNullReadouts() != 0L)
            System.err.println("  NumNullReadouts " + sender.getNumNullReadouts());
        if (sender.getNumOutputsFailed() != 0L)
            System.err.println("  NumOutputsFailed " + sender.getNumOutputsFailed());
        if (sender.getNumOutputsIgnored() != 0L)
            System.err.println("  NumOutputsIgnored " + sender.getNumOutputsIgnored());
        if (sender.getNumOutputsSent() != 0L)
            System.err.println("  NumOutputsSent " + sender.getNumOutputsSent());
        if (sender.getNumReadoutRequestsDropped() != 0L)
            System.err.println("  NumReadoutRequestsDropped " + sender.getNumReadoutRequestsDropped());
        if (sender.getNumReadoutRequestsQueued() != 0L)
            System.err.println("  NumReadoutRequestsQueued " + sender.getNumReadoutRequestsQueued());
        if (sender.getNumReadoutRequestsReceived() != 0L)
            System.err.println("  NumReadoutRequestsReceived " + sender.getNumReadoutRequestsReceived());
        if (sender.getNumReadoutsFailed() != 0L)
            System.err.println("  NumReadoutsFailed " + sender.getNumReadoutsFailed());
        if (sender.getNumReadoutsIgnored() != 0L)
            System.err.println("  NumReadoutsIgnored " + sender.getNumReadoutsIgnored());
        if (sender.getNumReadoutsSent() != 0L)
            System.err.println("  NumReadoutsSent " + sender.getNumReadoutsSent());
        if (sender.getNumRecycled() != 0L)
            System.err.println("  NumRecycled " + sender.getNumRecycled());
        if (sender.getNumRequestsDropped() != 0L)
            System.err.println("  NumRequestsDropped " + sender.getNumRequestsDropped());
        if (sender.getNumRequestsQueued() != 0)
            System.err.println("  NumRequestsQueued " + sender.getNumRequestsQueued());
        if (sender.getNumRequestsReceived() != 0L)
            System.err.println("  NumRequestsReceived " + sender.getNumRequestsReceived());
        if (sender.getOutputsPerSecond() != 0.0)
            System.err.println("  OutputsPerSecond " + sender.getOutputsPerSecond());
        if (sender.getReadoutRequestsPerSecond() != 0.0)
            System.err.println("  ReadoutRequestsPerSecond " + sender.getReadoutRequestsPerSecond());
        if (sender.getReadoutsPerSecond() != 0.0)
            System.err.println("  ReadoutsPerSecond " + sender.getReadoutsPerSecond());
        if (sender.getRequestsPerSecond() != 0.0)
            System.err.println("  RequestsPerSecond " + sender.getRequestsPerSecond());
        if (sender.getTotalBadDataPayloads() != 0L)
            System.err.println("  TotalBadDataPayloads " + sender.getTotalBadDataPayloads());
        if (sender.getTotalBadHits() != 0L)
            System.err.println("  TotalBadHits " + sender.getTotalBadHits());
        if (sender.getTotalDataPayloadsDiscarded() != 0L)
            System.err.println("  TotalDataPayloadsDiscarded " + sender.getTotalDataPayloadsDiscarded());
        if (sender.getTotalDataPayloadsReceived() != 0L)
            System.err.println("  TotalDataPayloadsReceived " + sender.getTotalDataPayloadsReceived());
        if (sender.getTotalDataStopsReceived() != 0L)
            System.err.println("  TotalDataStopsReceived " + sender.getTotalDataStopsReceived());
        if (sender.getTotalHitsDiscarded() != 0L)
            System.err.println("  TotalHitsDiscarded " + sender.getTotalHitsDiscarded());
        if (sender.getTotalHitsReceived() != 0L)
            System.err.println("  TotalHitsReceived " + sender.getTotalHitsReceived());
        if (sender.getTotalOutputsFailed() != 0L)
            System.err.println("  TotalOutputsFailed " + sender.getTotalOutputsFailed());
        if (sender.getTotalOutputsIgnored() != 0L)
            System.err.println("  TotalOutputsIgnored " + sender.getTotalOutputsIgnored());
        if (sender.getTotalOutputsSent() != 0L)
            System.err.println("  TotalOutputsSent " + sender.getTotalOutputsSent());
        if (sender.getTotalReadoutRequestsReceived() != 0L)
            System.err.println("  TotalReadoutRequestsReceived " + sender.getTotalReadoutRequestsReceived());
        if (sender.getTotalReadoutsFailed() != 0L)
            System.err.println("  TotalReadoutsFailed " + sender.getTotalReadoutsFailed());
        if (sender.getTotalReadoutsIgnored() != 0L)
            System.err.println("  TotalReadoutsIgnored " + sender.getTotalReadoutsIgnored());
        if (sender.getTotalReadoutsSent() != 0L)
            System.err.println("  TotalReadoutsSent " + sender.getTotalReadoutsSent());
        if (sender.getTotalRequestStopsReceived() != 0L)
            System.err.println("  TotalRequestStopsReceived " + sender.getTotalRequestStopsReceived());
        if (sender.getTotalRequestsReceived() != 0L)
            System.err.println("  TotalRequestsReceived " + sender.getTotalRequestsReceived());
        if (sender.getTotalStopsSent() != 0L)
            System.err.println("  TotalStopsSent " + sender.getTotalStopsSent());
   }
}