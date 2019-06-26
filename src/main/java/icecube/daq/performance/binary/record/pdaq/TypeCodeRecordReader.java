package icecube.daq.performance.binary.record.pdaq;

import icecube.daq.performance.binary.buffer.RecordBuffer;

import java.nio.ByteBuffer;

/**
 * A base definition of PDAQ records with a four-byte type-code specifier
 * after the 4-byte length field.
 *
 * ----------------------------------------------------------------------
 * | length [uint4] |  type (uint4)  |            ...                   |
 * ----------------------------------------------------------------------
 *
 * @see DaqBufferRecordReader , SimpleHitRecordReader
 *
 */
public class TypeCodeRecordReader extends LengthPrependedRecordReader
{

    public static final TypeCodeRecordReader instance =
            new TypeCodeRecordReader();

    protected TypeCodeRecordReader(){}

    public int getTypeId(final ByteBuffer buffer)
    {
        return buffer.getInt(4);
    }
    public int getTypeId(final ByteBuffer buffer, final int offset)
    {
        return buffer.getInt(offset + 4);
    }
    public int getTypeId(final RecordBuffer buffer, final int offset)
    {
        return buffer.getInt(offset + 4);
    }
}
