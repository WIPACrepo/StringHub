package icecube.daq.performance.binary.record.pdaq;

import icecube.daq.performance.binary.buffer.RecordBuffer;

import java.nio.ByteBuffer;

/**
 * A PDAQ delta compressed hit record.
 *
 * -----------------------------------------------------------------------------
 * | length [uint4]   |  type (uint4)=3  |           mbid[uint8]              |
 * -----------------------------------------------------------------------------
 * |          padding [unit8]            |           utc[uint8]                |
 * -----------------------------------------------------------------------------
 * |bo[uint2]|v[uint2]|fqp[uint2]|  domclk [uint8]                  | word-1...|                                    |
 * -----------------------------------------------------------------------------
 * |...[byte[4]|  word-3[byte[4] |            hit-data[byte[N]]...             |                                   |
 * -----------------------------------------------------------------------------
 * |              ...                                                          |
 * -----------------------------------------------------------------------------
 * |              ...                                                          |
 * -----------------------------------------------------------------------------
 *
 * fqp:[&0x01=pedestal subtraction, &0x02=atwd charge stamp]
 * word1:
 * word3:
 */
public class DeltaCompressedHitRecordReader extends DomHitRecordReader
{

    public static final DeltaCompressedHitRecordReader instance =
            new DeltaCompressedHitRecordReader();


    protected DeltaCompressedHitRecordReader(){};

    public short getByteOrderMark(final ByteBuffer buffer)
    {
        return buffer.getShort(32);
    }
    public short getByteOrderMark(final ByteBuffer buffer, final int offset)
    {
        return buffer.getShort(offset + 32);
    }
    public short getByteOrderMark(final RecordBuffer buffer, final int offset)
    {
        return buffer.getShort(offset + 32);
    }

    public short getVersion(final ByteBuffer buffer)
    {
        return buffer.getShort(34);
    }
    public short getVersion(final ByteBuffer buffer, final int offset)
{
    return buffer.getShort(offset + 34);
}
    public short getVersion(final RecordBuffer buffer, final int offset)
    {
        return buffer.getShort(offset + 34);
    }

    public short getFQP(final ByteBuffer buffer)
    {
        return buffer.getShort(36);
    }
    public short getFQP(final ByteBuffer buffer, final int offset)
    {
        return buffer.getShort(offset + 36);
    }
    public short getFQP(final RecordBuffer buffer, final int offset)
    {
        return buffer.getShort(offset + 36);
    }

    public long getDOMClock(final ByteBuffer buffer)
    {
        return buffer.getLong(38);
    }
    public long getDOMClock(final ByteBuffer buffer, final int offset)
    {
        return buffer.getLong(offset + 38);
    }
    public long getDOMClock(final RecordBuffer buffer, final int offset)
    {
        return buffer.getLong(offset + 38);
    }

    public int getWord1(final ByteBuffer buffer)
    {
        return buffer.getInt(46);
    }
    public int getWord1(final ByteBuffer buffer, final int offset)
    {
        return buffer.getInt(offset + 46);
    }
    public int getWord1(final RecordBuffer buffer, final int offset)
    {
        return buffer.getInt(offset + 46);
    }


    public int getWord3(final ByteBuffer buffer)
    {
        return buffer.getInt(50);
    }
    public int getWord3(final ByteBuffer buffer, final int offset)
    {
        return buffer.getInt(offset + 50);
    }
    public int getWord3(final RecordBuffer buffer, final int offset)
    {
        return buffer.getInt(offset + 50);
    }

    public byte[] getHitData(final ByteBuffer buffer)
    {
        // Note: there may be no data
        byte[] data = new byte[getLength(buffer)-54];
        for (int i = 0; i < data.length; i++)
        {
           data[i] = buffer.get(54 + i);
        }
        return data;
    }
    public byte[] getHitData(final ByteBuffer buffer, final int offset)
    {
        // Note: there may be no data
        byte[] data = new byte[getLength(buffer, offset)- 54];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.get(offset + 54 + i);
        }
        return data;
    }
    public byte[] getHitData(final RecordBuffer buffer, final int offset)
    {
        // Note: there may be no data
        byte[] data = new byte[getLength(buffer, offset)- 54];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.getByte(offset + 54 + i);
        }
        return data;
    }

    // bit fields

    public short getTriggerMode(final ByteBuffer buffer)
    {
        return getTriggerMode(getWord1(buffer));
    }
    public short getTriggerMode(final ByteBuffer buffer, final int offset)
    {
        return getTriggerMode(getWord1(buffer, offset));
    }
    public short getTriggerMode(final RecordBuffer buffer, final int offset)
    {
        return getTriggerMode(getWord1(buffer, offset));
    }

    public short getTriggerMode(final int word0)
    {
        // Note that comparisons need to be in this order or
        // the wrong trigger mode will be returned
        int modeBits = (word0 >> 18) & 0x1017;
        if ((modeBits & 0x1000) == 0x1000) {
            return (short) 4;
        } else if ((modeBits & 0x0010) == 0x0010) {
            return (short) 3;
        } else if ((modeBits & 0x0003) != 0) {
            return (short) 2;
        } else if ((modeBits & 0x0004) == 0x0004) {
            return (short) 1;
        } else {
            return (short) 0;
        }
    }

    public short getLCMode(final ByteBuffer buffer)
    {
        return getLCMode(getWord1(buffer));
    }
    public short getLCMode(final ByteBuffer buffer, final int offset)
    {
        return getLCMode(getWord1(buffer, offset));
    }
    public short getLCMode(final RecordBuffer buffer, final int offset)
    {
        return getLCMode(getWord1(buffer, offset));
    }
    public short getLCMode(final int word1)
    {
        return (short) ((word1 >> 16) & 0x3);
    }



}
