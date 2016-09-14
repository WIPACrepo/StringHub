package icecube.daq.performance.binary.record;

import icecube.daq.performance.binary.buffer.RecordBuffer;

import java.nio.ByteBuffer;

/**
 * Base interface for reading a UTC time from a record.
 */
public interface UTCRecordReader
{
    // utilizing ByteBuffer
    public long getUTC(ByteBuffer buffer);
    public long getUTC(ByteBuffer buffer, int offset);

    // utilizing RecordBuffer
    public long getUTC(RecordBuffer buffer, int offset);


    /**
     * Adapter to provide access to the UTC field through a generic
     * method.
     *
     * This indirection allows for generic implementations of searching
     * and pruning UTC ordered records.
     */
    class UTCField implements RecordReader.LongField
    {
        private final UTCRecordReader utcFieldReader;

        public UTCField(final UTCRecordReader utcFieldReader)
        {
            this.utcFieldReader = utcFieldReader;
        }

        @Override
        public long value(final ByteBuffer buffer, final int offset)
        {
            return utcFieldReader.getUTC(buffer, offset);
        }

        @Override
        public long value(final RecordBuffer buffer, final int offset)
        {
            return utcFieldReader.getUTC(buffer, offset);
        }
    }
}
