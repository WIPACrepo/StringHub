package icecube.daq.performance.binary.record.pdaq;

import icecube.daq.performance.binary.buffer.RecordBuffer;

import java.nio.ByteBuffer;

/**
 * A PDAQ engineering hit record.
 *
 * -----------------------------------------------------------------------------
 * | length [uint4]   |  type (uint4)=2  |           mbid[uint8]               |
 * -----------------------------------------------------------------------------
 * |          padding [unit8]            |           utc[uint8]                |
 * -----------------------------------------------------------------------------
 * |   hrl  |  bom   | a b c d e f | domclk [uint6] | N FADC samples ...       |
 * -----------------------------------------------------------------------------
 * |              ...                                                          |
 * -----------------------------------------------------------------------------
 * |  ATWD data ...                                                            |
 * -----------------------------------------------------------------------------
 * |              ...                                                          |
 * -----------------------------------------------------------------------------
 *
 * hrl: [uint2] hit record length
 * bom: [uint2] byte order mark (should be 01)
 * a:   [byte]  ATWD chip
 * b:   [byte]  number of FADC samples N
 * c:   [byte]  ATWD format flag byte #0 - controls ch0/1
 * d:   [byte]  ATWD format flag byte #1 - controls ch2/3
 * e:   [byte]  trigger flag
 * f:   [byte]  padding byte
 */
public class EngineeringHitRecordReader extends DomHitRecordReader
{

    public static final EngineeringHitRecordReader instance =
            new EngineeringHitRecordReader();

    public short getHitRecordLength(final ByteBuffer buffer)
    {
        return buffer.getShort(32);
    }
    public short getHitRecordLength(final ByteBuffer buffer, final int offset)
    {
        return buffer.getShort(offset + 32);
    }
    public short getHitRecordLength(final RecordBuffer buffer, final int offset)
    {
        return buffer.getShort(offset + 32);
    }

    public short getByteOrderMark(final ByteBuffer buffer)
    {
        return buffer.getShort(34);
    }
    public short getByteOrderMark(final ByteBuffer buffer, final int offset)
    {
        return buffer.getShort(offset + 34);
    }
    public short getByteOrderMark(final RecordBuffer buffer, final int offset)
    {
        return buffer.getShort(offset + 34);
    }

    public byte getATWDChip(final ByteBuffer buffer)
    {
        return buffer.get(36);
    }
    public byte getATWDChip(final ByteBuffer buffer, final int offset)
    {
        return buffer.get(offset + 36);
    }
    public byte getATWDChip(final RecordBuffer buffer, final int offset)
    {
        return buffer.getByte(offset + 36);
    }

    public byte getNumFADCSamples(final ByteBuffer buffer)
    {
        return buffer.get(37);
    }
    public byte getNumFADCSamples(final ByteBuffer buffer, final int offset)
    {
        return buffer.get(offset + 37);
    }
    public byte getNumFADCSamples(final RecordBuffer buffer, final int offset)
    {
        return buffer.getByte(offset + 37);
    }

    public byte getATWDFormatFlag0(final ByteBuffer buffer)
    {
        return buffer.get(38);
    }
    public byte getATWDFormatFlag0(final ByteBuffer buffer, final int offset)
    {
        return buffer.get(offset + 38);
    }
    public byte getATWDFormatFlag0(final RecordBuffer buffer, final int offset)
    {
        return buffer.getByte(offset + 38);
    }

    public byte getATWDFormatFlag1(final ByteBuffer buffer)
    {
        return buffer.get(39);
    }
    public byte getATWDFormatFlag1(final ByteBuffer buffer, final int offset)
    {
        return buffer.get(offset + 39);
    }
    public byte getATWDFormatFlag1(final RecordBuffer buffer, final int offset)
    {
        return buffer.getByte(offset + 39);
    }

    public byte getTriggerFlag(final ByteBuffer buffer)
    {
        return buffer.get(40);
    }
    public byte getTriggerFlag(final ByteBuffer buffer, final int offset)
    {
        return buffer.get(offset + 40);
    }
    public byte getTriggerFlag(final RecordBuffer buffer, final int offset)
    {
        return buffer.getByte(offset + 40);
    }

    public byte[] getDOMClock(final ByteBuffer buffer)
    {
        final byte[] data = new byte[6];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.get(42 + i);
        }
        return data;
    }
    public byte[] getDOMClock(final ByteBuffer buffer, final int offset)
    {
        final byte[] data = new byte[6];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.get(offset + 42 + i);
        }
        return data;
    }
    public byte[] getDOMClock(final RecordBuffer buffer, final int offset)
    {
        final byte[] data = new byte[6];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.getByte(offset + 42 + i);

        }
        return data;
    }


    public byte[] getFADCData(final ByteBuffer buffer)
    {
        final byte numSamples = getNumFADCSamples(buffer);
        final byte[] data = new byte[numSamples];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.get(48 + i);
        }
        return data;
    }
    public byte[] getFADCData(final ByteBuffer buffer, final int offset)
    {
        final byte numSamples = getNumFADCSamples(buffer, offset);
        final byte[] data = new byte[numSamples];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.get(offset + 48 + i);
        }
        return data;
    }
    public byte[] getFADCData(final RecordBuffer buffer, final int offset)
    {
        final byte numSamples = getNumFADCSamples(buffer, offset);
        final byte[] data = new byte[numSamples];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.getByte(offset + 48 + i);

        }
        return data;
    }

    public byte[] getATWDData(final ByteBuffer buffer)
    {
        byte numFADCSamples = getNumFADCSamples(buffer);
        final int numSamples = getHitRecordLength(buffer) - 48 -
                numFADCSamples;
        final byte[] data = new byte[numSamples];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.get(48 + numFADCSamples + i);
        }
        return data;
    }
    public byte[] getATWDData(final ByteBuffer buffer, final int offset)
    {
        byte numFADCSamples = getNumFADCSamples(buffer, offset);
        final int numSamples = getHitRecordLength(buffer, offset) - 48 -
                numFADCSamples;
        final byte[] data = new byte[numSamples];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.get(offset + 48 + numFADCSamples + i);
        }
        return data;
    }
    public byte[] getATWDData(final RecordBuffer buffer, final int offset)
    {
        byte numFADCSamples = getNumFADCSamples(buffer, offset);
        final int numSamples = getHitRecordLength(buffer, offset) - 48 -
                numFADCSamples;
        final byte[] data = new byte[numSamples];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.getByte(offset + 48 + numFADCSamples + i);

        }
        return data;
    }


    /**
     * Trigger Mode is derived from bits [0,1] of the trigger flag byte.
     */
    @Override
    public short getTriggerMode(final ByteBuffer buffer)
    {
        return getTriggerMode(getTriggerFlag(buffer));
    }
    @Override
    public short getTriggerMode(final ByteBuffer buffer, final int offset)
    {
        return getTriggerMode(getTriggerFlag(buffer, offset));
    }
    @Override
    public short getTriggerMode(final RecordBuffer buffer, final int offset)
    {
        return getTriggerMode(getTriggerFlag(buffer, offset));
    }
    public short getTriggerMode(byte triggerFlag)
    {
        return (short) (triggerFlag & 0x3);
    }

    /**
     * LC Mode is derived from bits [5,6] of the trigger flag byte.
     */
    @Override
    public short getLCMode(final ByteBuffer buffer)
    {
        return getLCMode(getTriggerFlag(buffer));
    }
    @Override
    public short getLCMode(final ByteBuffer buffer, final int offset)
    {
        return getLCMode(getTriggerFlag(buffer, offset));
    }
    @Override
    public short getLCMode(final RecordBuffer buffer, final int offset)
    {
        return getLCMode(getTriggerFlag(buffer, offset));
    }
    public short getLCMode(final int word0)
    {
        return (short) ((word0 >> 5) & 0x3);
    }

    /**
     * Flasher Board Run is derived from bit [4] of the trigger flag byte.
     */
    public boolean isFBRun(final ByteBuffer buffer)
    {
        return isFBRun(getTriggerFlag(buffer));
    }
    public boolean isFBRun(final ByteBuffer buffer, final int offset)
    {
        return isFBRun(getTriggerFlag(buffer, offset));
    }
    public boolean isFBRun(final RecordBuffer buffer, final int offset)
    {
        return isFBRun(getTriggerFlag(buffer, offset));
    }
    public boolean isFBRun(final int word0)
    {
        return ((word0 >> 2) & 0x1) == 1;
    }

}
