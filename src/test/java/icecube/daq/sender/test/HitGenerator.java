package icecube.daq.sender.test;

import icecube.daq.performance.binary.record.pdaq.DeltaCompressedHitRecordReader;
import icecube.daq.performance.binary.record.pdaq.DomHitRecordReader;

import java.nio.ByteBuffer;

/**
 *
 */
public class HitGenerator
{

    public DomHitRecordReader getType()
    {
        return DeltaCompressedHitRecordReader.instance;
    }

    public ByteBuffer generateHit()
    {
        return generateHit(true, (short)0);
    }

    public ByteBuffer generateHit(long utc)
    {
        return generateHit(utc, 123456, true, (short)0);
    }

    public ByteBuffer generateHit(long utc, long domID)
    {
        return generateHit(utc, domID, true, (short)0);
    }

    public ByteBuffer generateHit(boolean isLC, int triggerMode)
    {
        return generateHit(isLC, (short)triggerMode);
    }

    public ByteBuffer generateHit(boolean isLC, short triggerMode)
    {
        return generateHit(812371321234L, 123456, isLC, triggerMode);
    }

    public ByteBuffer generateHit(long utc,long domID,
                                  boolean isLC, short triggerMode)
    {
        ByteBuffer dummy;
        int LENGTH = 241;
        short FQP = 0x47;
        int WORD1 = 0x8004080C;
        int WORD3 = 0x3A111C8A;
        byte[] PAYLOAD = new byte[LENGTH -54];
        {
            for (int i = 0; i < PAYLOAD.length; i++)
            {
                PAYLOAD[i] = (byte)i;
            }
        }

        if(isLC)
        {
            WORD1 = insertLCMode(WORD1, (short) 1);
        }
        else
        {
            WORD1 = insertLCMode(WORD1, (short) 0);
        }
        WORD1 = insertTriggerMode(WORD1, triggerMode);

        dummy = ByteBuffer.allocate(LENGTH);
        dummy.putInt(LENGTH);
        dummy.putInt(3);              // type
        dummy.putLong(domID);         // mbid
        dummy.putLong(0);             // padding
        dummy.putLong(utc);           // utc
        dummy.putShort((short) 0x01); // BO mark
        dummy.putShort((short) 0x37); // version
        dummy.putShort(FQP);          // FQP
        dummy.putLong(783241891324L); // dom clock
        dummy.putInt(WORD1);          // word 1
        dummy.putInt(WORD3);          // word 3
        dummy.put(PAYLOAD);           // payload
        dummy.flip();

        return dummy;
    }

    static int insertTriggerMode(int word1, short triggerMode)
    {
        int clearBits = word1 & ~(0x1017 << 18);
        int setBits = 0;
        switch (triggerMode)
        {
            case 0:
                setBits = 0x0000;
                break;
            case 1:
                setBits = 0x0004;
                break;
            case 2:
                setBits = 0x0003;
                break;
            case 3:
                setBits = 0x0010;
                break;
            case 4:
                setBits = 0x1000;
                break;
        }

        return clearBits | (setBits << 18);
    }

    static int insertLCMode(int word1, short LCMode)
    {
        int bits = LCMode << 16;
        return (word1 & 0x0000FFFF) | bits;
    }

}
