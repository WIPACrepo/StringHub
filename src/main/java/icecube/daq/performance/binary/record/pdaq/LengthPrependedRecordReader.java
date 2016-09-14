package icecube.daq.performance.binary.record.pdaq;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.record.RecordReader;

import java.nio.ByteBuffer;

/**
 * A base definition of a length-prepended record reader.
 *
 * ----------------------------------------------------------------------
 * | length [uint4] |                     ...                           |
 * ----------------------------------------------------------------------
 *
 */
public class LengthPrependedRecordReader implements RecordReader
{

    public static final LengthPrependedRecordReader instance =
            new LengthPrependedRecordReader();

    protected LengthPrependedRecordReader(){}

    @Override
    public int getLength(final ByteBuffer buffer)
    {
        return buffer.getInt(0);
    }

    @Override
    public int getLength(final ByteBuffer buffer, final int offset)
    {
        return buffer.getInt(offset);
    }

    @Override
    public int getLength(final RecordBuffer buffer, final int offset)
    {
        return buffer.getInt(offset);
    }
}
