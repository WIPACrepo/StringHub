package icecube.daq.performance.binary.buffer.test;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.record.pdaq.LengthPrependedRecordReader;
import icecube.daq.performance.binary.record.UTCRecordReader;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A fake record defined by:
 * uint4:     length
 * byte[19]:  data-1
 * uint8:     utc
 * uint-8:    long-1
 * uint4:     int-1
 * uint2:     short-1
 * byte[n]:   data-2, dynamic size
 */
public class DummyRecord extends LengthPrependedRecordReader
        implements UTCRecordReader
{

    public final static DummyRecord instance = new DummyRecord();

    private DummyRecord(){}

    @Override
    public long getUTC(final ByteBuffer buffer)
    {
        return buffer.getLong(23);
    }

    public byte[] getData1(final ByteBuffer buffer)
    {
        byte[] data = new byte[19];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.get(4 + i);
        }
        return data;
    }

    public byte[] getData1(final ByteBuffer buffer, final int offset)
    {
        byte[] data = new byte[19];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.get(offset + 4 + i);
        }
        return data;
    }

    public byte[] getData1(final RecordBuffer buffer)
    {
        byte[] data = new byte[19];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.getByte(4 + i);
        }
        return data;
    }

    public byte[] getData1(final RecordBuffer buffer, final int offset)
    {
        byte[] data = new byte[19];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.getByte(offset + 4 + i);
        }
        return data;
    }

    @Override
    public long getUTC(final ByteBuffer buffer, final int offset)
    {
        return buffer.getLong(offset + 23);
    }
    @Override
    public long getUTC(final RecordBuffer buffer, final int offset)
    {
        return buffer.getLong(offset + 23);
    }

    public long getLong1(final RecordBuffer buffer, final int offset)
    {
        return buffer.getLong(offset + 31);
    }
    public long getLong1(final ByteBuffer buffer, final int offset)
    {
        return buffer.getLong(offset + 31);
    }

    public int getInt1(final RecordBuffer buffer, final int offset)
    {
        return buffer.getInt(offset + 39);
    }

    public int getInt1(final ByteBuffer buffer, final int offset)
    {
        return buffer.getInt(offset + 39);
    }

    public short getShort1(final RecordBuffer buffer, final int offset)
    {
        return buffer.getShort(offset + 43);
    }
    public short getShort1(final ByteBuffer buffer, final int offset)
    {
        return buffer.getShort(offset + 43);
    }
    public byte[] getData2(final ByteBuffer buffer)
    {
        // Note: there may be no data
        byte[] data = new byte[getLength(buffer)-45];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.get(45 + i);
        }
        return data;
    }
    public byte[] getData2(final ByteBuffer buffer, final int offset)
    {
        // Note: there may be no data
        byte[] data = new byte[getLength(buffer) - (offset + 45)];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.get(offset + 45 + i);
        }
        return data;
    }

    public byte[] getData2(final RecordBuffer buffer)
    {
        // Note: there may be no data
        byte[] data = new byte[getLength(buffer, 0)-45];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.getByte(45 + i);
        }
        return data;
    }
    public byte[] getData2(final RecordBuffer buffer, final int offset)
    {
        // Note: there may be no data
        byte[] data = new byte[getLength(buffer, offset) - (offset + 45)];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = buffer.getByte(offset + 45 + i);
        }
        return data;
    }

    public static ByteBuffer generateDummyBuffer(long utc, long seq)
    {
        byte[] data1 = new byte[19];
        for (int i = 0; i < data1.length; i++)
        {
            data1[i] = (byte) ThreadLocalRandom.current().nextInt(-128, 128);
        }

        byte[] data2 = new byte[ThreadLocalRandom.current().nextInt(0, 599)];
        for (int i = 0; i < data1.length; i++)
        {
            data1[i] = (byte) ThreadLocalRandom.current().nextInt(-128, 128);
        }

        return generateDummyBuffer(utc, seq, 2727272, (short) 55, data1, data2);
    }

    public static ByteBuffer generateDummyBuffer(long utc, long long1, int int1,
                                         short sort1, byte[] data1, byte[] data2)
    {
        if(data1.length!=19)
        {
            throw new Error("Illegal size " + data1.length);
        }
        int size = 45 + data2.length;
        ByteBuffer res = ByteBuffer.allocate(size);

        res.putInt(size);    // length
        res.put(data1);      // data1
        res.putLong(utc);    // utc
        res.putLong(long1);  // long-1
        res.putInt(int1);    // int-1
        res.putShort(sort1); // short-1
        res.put(data2);      // data2
        res.flip();
        return res;
    }

    public static String prettyPrint(ByteBuffer buffer, int offset)
    {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("DummyRecord[").append(instance.getLength(buffer, offset)).append("]").append("\n");
        sb.append("   data1[").append(instance.getData1(buffer, offset).length).append("]\n");
        sb.append("   utc: ").append(instance.getUTC(buffer, offset)).append("\n");
        sb.append("   long-1: ").append(instance.getLong1(buffer, offset)).append("\n");
        sb.append("   int-1: ").append(instance.getInt1(buffer, offset)).append("\n");
        sb.append("   short-1: ").append(instance.getShort1(buffer, offset)).append("\n");
        sb.append("   data-2[").append(instance.getData2(buffer, offset).length).append("]\n");
        return sb.toString();
    }

    public static String prettyPrint(RecordBuffer buffer, int offset)
    {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("DummyRecord[").append(instance.getLength(buffer, offset)).append("]").append("\n");
        sb.append("   data1[").append(instance.getData1(buffer, offset).length).append("]\n");
        sb.append("   utc: ").append(instance.getUTC(buffer, offset)).append("\n");
        sb.append("   long-1: ").append(instance.getLong1(buffer, offset)).append("\n");
        sb.append("   int-1: ").append(instance.getInt1(buffer, offset)).append("\n");
        sb.append("   short-1: ").append(instance.getShort1(buffer, offset)).append("\n");
        sb.append("   data-2[").append(instance.getData2(buffer, offset).length).append("]\n");
        return sb.toString();
    }
}
