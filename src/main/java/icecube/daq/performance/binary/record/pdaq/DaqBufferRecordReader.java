package icecube.daq.performance.binary.record.pdaq;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.record.UTCRecordReader;

import java.nio.ByteBuffer;

/**
 * A base definition of DAQ Buffer records.
 *
 * ----------------------------------------------------------------------
 * | length [uint4] |  type (uint4)  |         mbid[uint8]              |
 * ----------------------------------------------------------------------
 * |          padding [unit8]        |         utc[uint8]               |
 * ----------------------------------------------------------------------
 * |              ...                                                   |
 * ----------------------------------------------------------------------
 *
 *
 *
 */
public class DaqBufferRecordReader extends TypeCodeRecordReader
        implements UTCRecordReader
{

    public static final DaqBufferRecordReader instance =
            new DaqBufferRecordReader();

    public static final int DOM_ID_OFFSET = 8;
    public static final int PADDING_OFFSET = 16;
    public static final int UTC_OFFSET = 24;

    protected DaqBufferRecordReader(){}

    public long getDOMID(final ByteBuffer buffer)
    {
        return buffer.getLong(DOM_ID_OFFSET);
    }
    public long getDOMID(final ByteBuffer buffer, final int offset)
    {
        return buffer.getLong(offset + DOM_ID_OFFSET);
    }
    public long getDOMID(final RecordBuffer buffer, final int offset)
    {
        return buffer.getLong(offset + DOM_ID_OFFSET);
    }

    public long getPadding(final ByteBuffer buffer)
    {
        return buffer.getLong(PADDING_OFFSET);
    }
    public long getPadding(final ByteBuffer buffer, final int offset)
    {
        return buffer.getLong(offset + PADDING_OFFSET);
    }
    public long getPadding(final RecordBuffer buffer, final int offset)
    {
        return buffer.getLong(offset + PADDING_OFFSET);
    }

    @Override
    public long getUTC(final ByteBuffer buffer)
    {
        return buffer.getLong(UTC_OFFSET);
    }
    @Override
    public long getUTC(final ByteBuffer buffer, final int offset)
    {
        return buffer.getLong(offset + UTC_OFFSET);
    }
    @Override
    public long getUTC(final RecordBuffer buffer, final int offset)
    {
        return buffer.getLong(offset + UTC_OFFSET);
    }

    public boolean isEOS(ByteBuffer buffer)
    {
        return (getLength(buffer) == 32 && getUTC(buffer) == Long.MAX_VALUE);
    }

}
