package icecube.daq.sender;

import icecube.daq.io.DAQOutputChannelManager;
import icecube.daq.io.OutputChannel;
import icecube.daq.payload.IDOMID;
import icecube.daq.payload.IReadoutRequest;
import icecube.daq.payload.IReadoutRequestElement;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.impl.DeltaHitRecord;
import icecube.daq.payload.impl.EngineeringHitRecord;
import icecube.daq.stringhub.test.MockAppender;
import icecube.daq.stringhub.test.MockBufferCache;
import icecube.daq.stringhub.test.MockReadoutRequest;
import icecube.daq.stringhub.test.MockUTCTime;
import icecube.daq.util.DOMRegistry;

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

abstract class ExpectedData
    implements Comparable
{
    int compareLong(long l0, long l1)
    {
        long lval = l0 - l1;
        if (lval < 0L) {
            return -1;
        } else if (lval > 0L) {
            return 1;
        }

        return 0;
    }
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
    extends ExpectedData
{
    private long domId;
    private long utcTime;
    private int trigType;
    private int cfgId;
    private int srcId;
    private short trigMode;

    ExpectedHit(long domId, long utcTime, int trigType, int cfgId, int srcId,
                short trigMode)
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
        int val = compareLong(domId, hit.domId);
        if (val == 0) {
            val = compareLong(utcTime, hit.utcTime);
            if (val == 0) {
                val = trigType - hit.trigType;
                if (val == 0) {
                    val = cfgId - hit.cfgId;
                    if (val == 0) {
                        val = srcId - hit.srcId;
                        if (val == 0) {
                            val = trigMode - hit.trigMode;
                        }
                    }
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
                        int srcId, short trigMode)
    {
        addExpectedData(new ExpectedHit(domId, utcTime, trigType, cfgId, srcId,
                                        trigMode));
    }

    ExpectedData getBufferData(ByteBuffer buf)
    {
        return new ExpectedHit(buf);
    }
}

class ExpectedDeltaHit
    extends ExpectedData
{
    private byte flags;
    private short chanId;
    private long utcTime;
    private int word0;
    private int word2;
    private byte[] data;

    ExpectedDeltaHit(byte flags, short chanId, long utcTime,
                     int word0, int word2, byte[] data)
    {
        this.flags = flags;
        this.chanId = chanId;
        this.utcTime = utcTime;
        this.word0 = word0;
        this.word2 = word2;
        this.data = data;
    }

    ExpectedDeltaHit(ByteBuffer buf, int offset, long baseTime, int recLen)
    {
        flags = buf.get(offset + 3);
        chanId = buf.getShort(offset + 4);
        utcTime = baseTime + buf.getInt(offset + 6);
        word0 = buf.getInt(offset + 10);
        word2 = buf.getInt(offset + 14);

        final int origPos = buf.position();

        data = new byte[recLen - 18];
        buf.position(offset + 18);
        buf.get(data, 0, data.length);

        buf.position(origPos);
    }

    public int compareTo(Object obj)
    {
        if (!(obj instanceof ExpectedDeltaHit)) {
            return getClass().getName().compareTo(obj.getClass().getName());
        }

        return compareTo((ExpectedDeltaHit) obj);
    }

    public int compareTo(ExpectedDeltaHit hit)
    {
        int val = flags - hit.flags;
        if (val == 0) {
            val = chanId - hit.chanId;
            if (val == 0) {
                val = compareLong(utcTime, hit.utcTime);
                if (val == 0) {
                    val = word0 - hit.word0;
                    if (val == 0) {
                        val = word2 - hit.word2;
                    }
                }
            }
        }

        if (val == 0) {
            if (data == null) {
                if (hit.data == null) {
                    val = 0;
                } else {
                    val = 1;
                }
            } else if (hit.data == null) {
                val = -1;
            } else {
                val = data.length - hit.data.length;
                if (val == 0) {
                    for (int i = 0; val == 0 && i < data.length; i++) {
                        val = data[i] - hit.data[i];
                    }
                }
            }
        }

        return val;
    }

    public boolean equals(Object obj)
    {
        if (!(obj instanceof ExpectedDeltaHit)) {
            return getClass().getName().equals(obj.getClass().getName());
        }

        return compareTo((ExpectedDeltaHit) obj) == 0;
    }

    public String toString()
    {
        StringBuilder dataBuf = new StringBuilder(" data");
        if (data == null) {
            dataBuf.append(" NULL");
        } else if (data.length == 0) {
            dataBuf.append(" EMPTY");
        } else {
            for (int i = 0; i < data.length; i++) {
                if (i == 0) {
                    dataBuf.append('[');
                } else {
                    dataBuf.append(' ');
                }
                dataBuf.append(data[i]);
            }

            dataBuf.append(']');
        }

        return "ExpDeltaHit@" + chanId + "[flags " + flags +
            " time " + utcTime + " w0 " + word0 + " w2 " + word2 +
            dataBuf + "]";
    }
}

class ExpectedEngHit
    extends ExpectedData
{
    private short chanId;
    private long utcTime;
    private byte atwdChip;
    private byte lenFADC;
    private byte atwdFmt01;
    private byte atwdFmt23;
    private byte trigMode;
    private byte[] domClock;
    private byte[] waveformData;

    ExpectedEngHit(short chanId, long utcTime, byte atwdChip, byte lenFADC,
                   byte atwdFmt01, byte atwdFmt23, byte trigMode,
                   byte[] domClock, byte[] waveformData)
    {
        this.chanId = chanId;
        this.utcTime = utcTime;
        this.atwdChip = atwdChip;
        this.lenFADC = lenFADC;
        this.atwdFmt01 = atwdFmt01;
        this.atwdFmt23 = atwdFmt23;
        this.trigMode = trigMode;
        this.domClock = domClock;
        this.waveformData = waveformData;
    }

    ExpectedEngHit(ByteBuffer buf, int offset, long baseTime, int recLen)
    {
        chanId = buf.getShort(offset + 4);
        utcTime = baseTime + buf.getInt(offset + 6);

        atwdChip = buf.get(offset + 10);
        lenFADC = buf.get(offset + 11);
        atwdFmt01 = buf.get(offset + 12);
        atwdFmt23 = buf.get(offset + 13);
        trigMode = buf.get(offset + 14);

        final int origPos = buf.position();

        domClock = new byte[6];
        buf.position(offset + 16);
        buf.get(domClock, 0, domClock.length);

        waveformData = new byte[recLen - 22];
        buf.position(offset + 22);
        buf.get(waveformData, 0, waveformData.length);

        buf.position(origPos);
    }

    private static int compareArrays(byte[] array0, byte[] array1)
    {
        if (array0 == null) {
            if (array1 == null) {
                return 0;
            }

            return 1;
        } else if (array1 == null) {
            return -1;
        }

        int val = array0.length - array1.length;
        if (val != 0) {
            return val;
        }

        for (int i = 0; val == 0 && i < array0.length; i++) {
            val = array0[i] - array1[i];
        }

        return val;
    }

    public int compareTo(Object obj)
    {
        if (!(obj instanceof ExpectedEngHit)) {
            return getClass().getName().compareTo(obj.getClass().getName());
        }

        return compareTo((ExpectedEngHit) obj);
    }

    public int compareTo(ExpectedEngHit hit)
    {
        int val = chanId - hit.chanId;
        if (val == 0) {
            val = compareLong(utcTime, hit.utcTime);
            if (val == 0) {
                val = atwdChip - hit.atwdChip;
                if (val == 0) {
                    val = lenFADC - hit.lenFADC;
                    if (val == 0) {
                        val = atwdFmt01 - hit.atwdFmt01;
                        if (val == 0) {
                            val = atwdFmt23 - hit.atwdFmt23;
                            if (val == 0) {
                                val = trigMode - hit.trigMode;
                            }
                        }
                    }
                }
            }
        }

        if (val == 0) {
            val = compareArrays(domClock, hit.domClock);
            if (val == 0) {
                val = compareArrays(waveformData, hit.waveformData);
            }
        }

        return val;
    }

    public boolean equals(Object obj)
    {
        if (!(obj instanceof ExpectedEngHit)) {
            return getClass().getName().equals(obj.getClass().getName());
        }

        return compareTo((ExpectedEngHit) obj) == 0;
    }

    public String toString()
    {
        StringBuilder clockBuf = new StringBuilder(" clk");
        if (domClock == null) {
            clockBuf.append(" NULL");
        } else if (domClock.length == 0) {
            clockBuf.append(" EMPTY");
        } else {
            for (int i = 0; i < domClock.length; i++) {
                if (i == 0) {
                    clockBuf.append('[');
                } else {
                    clockBuf.append(' ');
                }
                clockBuf.append(domClock[i]);
            }

            clockBuf.append(']');
        }

        StringBuilder dataBuf = new StringBuilder(" data");
        if (waveformData == null) {
            dataBuf.append(" NULL");
        } else if (waveformData.length == 0) {
            dataBuf.append(" EMPTY");
        } else {
            for (int i = 0; i < waveformData.length; i++) {
                if (i == 0) {
                    dataBuf.append('[');
                } else {
                    dataBuf.append(' ');
                }
                dataBuf.append(waveformData[i]);
            }

            dataBuf.append(']');
        }

        return "ExpEngHit@" + chanId + "[time " + utcTime +
            " chip " + atwdChip + " fadc*" + lenFADC + " fmt01 " + atwdFmt01 +
            " fmt23 " + atwdFmt23 + " mode " + trigMode +
            clockBuf.toString() + dataBuf.toString() + "]";
    }
}

class ExpectedHitList
    extends ExpectedData
{
    private long utcTime;
    private int uid;
    private short payNum;
    private short payLast;
    private int srcId;
    private long firstTime;
    private long lastTime;
    private int numHits;
    private List<ExpectedData> hitList;

    ExpectedHitList(long utcTime, int uid, int srcId)
    {
        this.utcTime = utcTime;
        this.uid = uid;
        this.srcId = srcId;

        hitList = new ArrayList<ExpectedData>();
    }

    ExpectedHitList(ByteBuffer buf)
    {
        final int minBytes = 54;

        final int rdoutLen = buf.getInt(0);
        if (rdoutLen < minBytes) {
            throw new Error("Hit record list must contain at least " +
                            minBytes + " bytes, not " + rdoutLen);
        }

        final int type = buf.getInt(4);
        if (type != PayloadRegistry.PAYLOAD_ID_HIT_RECORD_LIST) {
            throw new Error("Bad hit record list payload type " + type);
        }

        utcTime = buf.getLong(8);

        uid = buf.getInt(16);
        srcId = buf.getInt(20);
        numHits = buf.getInt(24);

        hitList = new ArrayList<ExpectedData>(numHits);

        int offset = 28;
        for (int i = 0; i < numHits; i++) {
            int recLen = buf.getShort(offset + 0);
            int recType = buf.get(offset + 2);

            switch (recType) {
            case EngineeringHitRecord.HIT_RECORD_TYPE:
                hitList.add(new ExpectedEngHit(buf, offset, utcTime, recLen));
                break;
            case DeltaHitRecord.HIT_RECORD_TYPE:
                hitList.add(new ExpectedDeltaHit(buf, offset, utcTime, recLen));
                break;
            default:
                throw new Error("Unknown hit type #" + recType);
            }

            offset += recLen;
        }
    }

    void addHit(ExpectedData hit)
    {
        hitList.add(hit);
    }

    public int compareTo(Object obj)
    {
        if (obj == null) {
            return -1;
        }
        if (!(obj instanceof ExpectedHitList)) {
            return getClass().getName().compareTo(obj.getClass().getName());
        }

        return compareTo((ExpectedHitList) obj);
    }

    public int compareTo(ExpectedHitList ro)
    {
        int val = compareLong(utcTime, ro.utcTime);
        if (val == 0) {
            val = compareLong(firstTime, ro.firstTime);
            if (val == 0) {
                val = compareLong(lastTime, ro.lastTime);
                if (val == 0) {
                    val = uid - ro.uid;
                    if (val == 0) {
                        val = payNum - ro.payNum;
                        if (val == 0) {
                            val = payLast - ro.payLast;
                            if (val == 0) {
                                val = srcId - ro.srcId;
                            }
                        }
                    }
                }
            }
        }

        if (val == 0) {
            if (hitList == null) {
                if (ro.hitList == null) {
                    val = 0;
                } else {
                    val = 1;
                }
            } else if (ro.hitList == null) {
                val = 1;
            } else {
                val = hitList.size() - ro.hitList.size();
                for (int i = 0; val == 0 && i < hitList.size(); i++) {
                    val = hitList.get(i).compareTo(ro.hitList.get(i));
                    if (val != 0) {
                        System.err.println("CMP failed for " + hitList.get(i) +
                                           " vs. " + ro.hitList.get(i));
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
        if (!(obj instanceof ExpectedHitList)) {
            return getClass().getName().equals(obj.getClass().getName());
        }

        return compareTo((ExpectedHitList) obj) == 0;
    }

    public String toString()
    {
        return "ExpHitLst[#" + uid + " pay " + payNum + "/" + payLast +
            " src " + srcId + " hits*" + hitList.size() + "]";
    }
}

class MockReadoutChannel
    extends MockOutputChannel
{
    private ExpectedHitList recent;

    MockReadoutChannel()
    {
        super("readout");
    }

    void addExpectedHitList(long utcTime, int uid, short payNum, short payLast,
                            int srcId, long firstTime, long lastTime)
    {
        recent = new ExpectedHitList(utcTime, uid, srcId);

        addExpectedData(recent);
    }

    void addDeltaHit(byte flags, short chanId, long utcTime, int word0,
                     int word2, byte[] data)
    {
        if (recent == null) {
            throw new Error("No expected readout has been added");
        }

        recent.addHit(new ExpectedDeltaHit(flags, chanId, utcTime, word0,
                                           word2, data));
    }

    void addEngHit(short chanId, long utcTime, byte atwdChip, byte lenFADC,
                   byte atwdFmt01, byte atwdFmt23, byte trigMode,
                   byte[] domClock, byte[] waveformData)
    {
        if (recent == null) {
            throw new Error("No expected readout has been added");
        }

        recent.addHit(new ExpectedEngHit(chanId, utcTime, atwdChip, lenFADC,
                                         atwdFmt01, atwdFmt23, trigMode,
                                         domClock, waveformData));
    }

    ExpectedData getBufferData(ByteBuffer buf)
    {
        return new ExpectedHitList(buf);
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

    private static final MockAppender appender =
        //new MockAppender(org.apache.log4j.Level.ALL).setVerbose(true);
        new MockAppender();

    private static DOMRegistry domRegistry;

    public SenderTest()
    {
    }

    private static final ByteBuffer createDeltaHit(long domId, long utcTime,
                                                   short version,
                                                   short pedestal,
                                                   long domClock,
                                                   byte lcMode,
                                                   short trigMode,
                                                   short waveformFlags,
                                                   int peakInfo,
                                                   byte[] data)
    {
        final int sizeOfCompressedHeader = 12;

        int trigBits;
        switch ((int) trigMode) {
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
        buf.putShort(version);    // version
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
                                                 int atwdChip, short trigMode,
                                                 long domClock,
                                                 short[] fadcSamples,
                                                 Object atwd0Data,
                                                 Object atwd1Data,
                                                 Object atwd2Data,
                                                 Object atwd3Data)
    {
        if (fadcSamples == null) {
            throw new Error("Null FADC array");
        }

        int affByte0 = 0;
        int affByte1 = 0;
        int atwdLen = 0;

        Object[] atwdData =
            new Object[] { atwd0Data, atwd1Data, atwd2Data, atwd3Data };
        for (int i = 0; i < 4; i++) {
            int nybble;
            if (atwdData[i] == null) {
                nybble = 0;
            } else if (!atwdData[i].getClass().isArray()) {
                throw new Error("ATWD" + i +" object is not an array");
            } else {
                int len = Array.getLength(atwdData[i]);
                boolean isShort = atwdData[i] instanceof short[];
                if (!isShort && !(atwdData[i] instanceof byte[])) {
                    throw new Error("ATWD" + i + " array has an invalid type");
                }

                int lenBits;
                switch (len) {
                case 32:
                    lenBits = 0;
                    break;
                case 64:
                    lenBits = 1;
                    break;
                case 16:
                    lenBits = 2;
                    break;
                case 128:
                    lenBits = 3;
                    break;
                default:
                    throw new Error("ATWD" + i + " array has invalid length");
                }

                nybble = (lenBits << 2) | (isShort ? 0x2 : 0x0) | 0x1;

                if (isShort) {
                    atwdLen += len * 2;
                } else {
                    atwdLen += len;
                }
            }

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
        final int recLen = 16 + (fadcSamples.length * 2) + atwdLen;

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
        buf.put((byte) fadcSamples.length);
        buf.put((byte) affByte0);
        buf.put((byte) affByte1);
        buf.put((byte) trigMode);
        buf.put((byte) 0);
        putDomClock(buf, buf.position(), domClock);
        for (int i = 0; i < fadcSamples.length; i++) {
            buf.putShort(fadcSamples[i]);
        }
        for (int i = 0; i < 4; i++) {
            if (atwdData[i] == null) {
                continue;
            }

            int len = Array.getLength(atwdData[i]);

            if (atwdData[i] instanceof short[]) {
                short[] array = (short[]) atwdData[i];
                for (int j = 0; j < array.length; j++) {
                    buf.putShort(array[j]);
                }
            } else {
                byte[] array = (byte[]) atwdData[i];
                for (int j = 0; j < array.length; j++) {
                    buf.put(array[j]);
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

        if (domRegistry == null) {
            String configDir = getClass().getResource("/config").getPath();
System.err.println("CFGDIR "+configDir);
            try {
                domRegistry = DOMRegistry.loadRegistry(configDir);
            } catch (Exception ex) {
                throw new Error("Couldn't load DOM registry", ex);
            }
        }
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
        //sender.setDOMRegistry(domRegistry);

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
        //sender.setDOMRegistry(domRegistry);

        MockHitChannel hitChan = new MockHitChannel();
        sender.setHitOutput(new MockOutputChannelManager(hitChan));

        MockReadoutChannel rdoutChan = new MockReadoutChannel();
        sender.setDataOutput(new MockOutputChannelManager(rdoutChan));

        sender.startThread();

        long domId = 0xfedcba987654L;
        long utcTime = 123456789L;
        int atwdChip = 1;
        short trigMode = 4;
        long domClock = utcTime / 234L;
        short[] fadcSamples = new short[] { 1, 2, 3 };
        Object atwd0Data = new short[32];
        Object atwd1Data = null;
        Object atwd2Data = new byte[128];
        Object atwd3Data = new byte[16];

        ByteBuffer buf = createEngHit(domId, utcTime, atwdChip, trigMode,
                                      domClock, fadcSamples, atwd0Data,
                                      atwd1Data, atwd2Data, atwd3Data);

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
        //sender.setDOMRegistry(domRegistry);

        MockHitChannel hitChan = new MockHitChannel();
        sender.setHitOutput(new MockOutputChannelManager(hitChan));

        MockReadoutChannel rdoutChan = new MockReadoutChannel();
        sender.setDataOutput(new MockOutputChannelManager(rdoutChan));

        sender.startThread();

        long domId = 0xfedcba987654L;
        long utcTime = 123456789L;
        short version = 12;
        short pedestal = 34;
        long domClock = utcTime / 234L;
        byte lcMode = 3;
        short trigMode = 2;
        short waveformFlags = 15;
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
        sender.setDOMRegistry(domRegistry);

        MockHitChannel hitChan = new MockHitChannel();
        sender.setHitOutput(new MockOutputChannelManager(hitChan));

        MockReadoutChannel rdoutChan = new MockReadoutChannel();
        sender.setDataOutput(new MockOutputChannelManager(rdoutChan));

        sender.startThread();

        int numHitsSent = 0;
        long lastHitTime = 0L;

        ByteBuffer buf;

        long baseTime = 123456789L;
        short trigMode = 3;

        long engDomId = 0xfedcba987654L;
        short engChanId = 123;
        long engTime = baseTime + 100L;
        byte atwdChip = 1;
        long engClock = engTime / 234L;
        short[] fadcSamples = new short[] { 1, 2, 3 };
        Object atwd0Data = new byte[32];
        Object atwd1Data = new byte[16];
        Object atwd2Data = new short[16];
        Object atwd3Data = new short[32];

        byte lenFADC = 3;
        byte atwdFmt01 = (byte) -111;
        byte atwdFmt23 = 59;
        byte[] domClock = new byte[] {
            (byte) 0, (byte) 0, (byte) 0,
            (byte) 8, (byte) 12, (byte) 233,
        };
        byte[] waveformData = new byte[150];
        for (int i = 0; i < waveformData.length; i++) {
            waveformData[i] = (byte) 0;
        }
        waveformData[1] = (byte) 1;
        waveformData[3] = (byte) 2;
        waveformData[5] = (byte) 3;

        buf = createEngHit(engDomId, engTime, atwdChip, trigMode, engClock,
                           fadcSamples, atwd0Data, atwd1Data, atwd2Data,
                           atwd3Data);

        hitChan.addExpectedHit(engDomId, engTime, trigMode, 0, HUB_SRCID,
                               trigMode);

        sender.consume(buf);

        numHitsSent++;
        lastHitTime = engTime;

        long deltaDomId = 0xedcba9876543L;
        short deltaChanId = 124;
        long deltaTime = baseTime + 200L;
        short version = 1;
        short pedestal = 34;
        long deltaClock = deltaTime / 234L;
        byte lcMode = 3;
        short waveformFlags = 15;
        int peakInfo = 9876;
        byte[] data = new byte[32];

        int word0 = 4421676;
        int word2 = 9876;

        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        buf = createDeltaHit(deltaDomId, deltaTime, version, pedestal,
                             deltaClock, lcMode, trigMode, waveformFlags,
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

        rdoutChan.addExpectedHitList(roFirstTime, uid, (short) 0, (short) 1,
                                     HUB_SRCID, roFirstTime, roLastTime);
        rdoutChan.addEngHit(engChanId, engTime, atwdChip, lenFADC, atwdFmt01,
                            atwdFmt23, (byte) trigMode, domClock,
                            waveformData);
        rdoutChan.addDeltaHit((byte) (pedestal & 0x3), deltaChanId, deltaTime,
                              word0, word2, data);

        waitForRequestDequeued(sender);

        long flushDomId = 0xdcba98765432L;
        long flushTime = baseTime + 10000L;
        long flushClock = flushTime / 234L;

        buf = createEngHit(flushDomId, flushTime, atwdChip, trigMode, flushClock,
                           fadcSamples, atwd0Data, atwd1Data, atwd2Data,
                           atwd3Data);

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
}
