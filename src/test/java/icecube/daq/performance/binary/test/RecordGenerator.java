package icecube.daq.performance.binary.test;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.record.RecordReader;
import icecube.daq.performance.binary.record.UTCRecordReader;
import icecube.daq.performance.binary.record.pdaq.DaqBufferRecordReader;
import icecube.daq.performance.binary.store.RecordStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

/**
 * A factory to generate specific-format records
 */
public interface RecordGenerator
{
    RecordReader recordReader();
    RecordReader.LongField orderingField();
    ByteBuffer generate(long value);

    default int generate(long[] values, List<ByteBuffer> bufs)
            throws IOException
    {
        int size=0;
        for (int i = 0; i < values.length; i++)
        {
            ByteBuffer buffer = generate(values[i]);
            size+=buffer.remaining();
            bufs.add(buffer);
        }
        return size;
    }

    /**
     * utility method to realize a data set
     */
    default int fill(long[] values, Consumer<ByteBuffer> sink)
    {
        int size=0;
        for (int i = 0; i < values.length; i++)
        {
            ByteBuffer buffer = generate(values[i]);
            size+=buffer.remaining();
            sink.accept(buffer);
        }
        return size;
    }

    default int fill(long[] values, RecordStore.Writable rs)
            throws IOException
    {
        int size=0;
        for (int i = 0; i < values.length; i++)
        {
            ByteBuffer buffer = generate(values[i]);
            size+=buffer.remaining();
            rs.store(buffer);
        }
        return size;
    }


    /**
     * Simulates DAQ records
     */
    class DAQRecordProvider implements RecordGenerator
    {
        final int maxSize;

        public DAQRecordProvider(final int maxSize)
        {
            if(maxSize < 32)
            {
                throw new IllegalArgumentException("size must be >= 32");
            }
            this.maxSize = maxSize;
        }


        @Override
        public RecordReader recordReader()
        {
            return DaqBufferRecordReader.instance;
        }

        @Override
        public RecordReader.LongField orderingField()
        {
            return new UTCRecordReader.UTCField(DaqBufferRecordReader.instance);
        }

        public ByteBuffer generate(long utc)
        {
            int size = 32 + (int)(Math.random()* (maxSize-31));

            ByteBuffer res = ByteBuffer.allocate(size);
            res.putInt(size);           // length
            res.putInt(99);             // type
            res.putLong(123456L);       // mbid
            res.putLong(0);             // padding
            res.putLong(utc);           // utc
            res.position(res.limit());  // implied garbage payload

            res.flip();
            return res;
        }

    }

    /**
     * A record with fixed length in the format:
     * int length
     * long value
     * byte[n] random
     */
    class FixedLengthRecordProvider implements RecordGenerator
    {
        final int fixedSize;

        public FixedLengthRecordProvider(final int fixedSize)
        {
            if(fixedSize < 12)
            {
                throw new IllegalArgumentException("Size must be >= 12");
            }
            this.fixedSize = fixedSize;
        }

        @Override
        public RecordReader recordReader()
        {
            return new RecordReader()
            {
                @Override
                public int getLength(final ByteBuffer buffer)
                {
                    return buffer.getInt(0);
                }

                @Override
                public int getLength(final ByteBuffer buffer,
                                     final int offset)
                {
                    return buffer.getInt(offset);
                }

                @Override
                public int getLength(final RecordBuffer buffer,
                                     final int offset)
                {
                    return buffer.getInt(offset);
                }
            };
        }

        @Override
        public RecordReader.LongField orderingField()
        {
            return new RecordReader.LongField()
            {
                @Override
                public long value(final ByteBuffer buffer, final int offset)
                {
                    return buffer.getLong(offset + 4);
                }

                @Override
                public long value(final RecordBuffer buffer, final int offset)
                {
                    return buffer.getLong(offset + 4);

                }
            };
        }

        public ByteBuffer generate(long utc)
        {
            ByteBuffer res = ByteBuffer.allocate(fixedSize);
            res.putInt(fixedSize);           // length
            res.putLong(utc);                 // value
            while (res.remaining() > 0)       // garbage
            {
                res.put((byte) (Math.random() * 256));
            }
            res.flip();
            return res;
        }
    }


    /**
     * A record with fixed length in the format:
     * int length
     * long value
     * byte[random] random
     */
    class RandomLengthRecordProvider implements RecordGenerator
    {
        final int maxSize;

        public RandomLengthRecordProvider(final int maxSize)
        {
            this.maxSize = maxSize;
        }

        @Override
        public RecordReader recordReader()
        {
            return new RecordReader()
            {
                @Override
                public int getLength(final ByteBuffer buffer)
                {
                    return buffer.getInt(0);
                }

                @Override
                public int getLength(final ByteBuffer buffer,
                                     final int offset)
                {
                    return buffer.getInt(offset);
                }

                @Override
                public int getLength(final RecordBuffer buffer,
                                     final int offset)
                {
                    return buffer.getInt(offset);
                }
            };
        }

        @Override
        public RecordReader.LongField orderingField()
        {
            return new RecordReader.LongField()
            {
                @Override
                public long value(final ByteBuffer buffer, final int offset)
                {
                    return buffer.getLong(offset + 4);
                }

                @Override
                public long value(final RecordBuffer buffer, final int offset)
                {
                    return buffer.getLong(offset + 4);

                }
            };
        }

        public ByteBuffer generate(long utc)
        {
            int length = 12 + (int) (Math.random() * (maxSize-11));
            ByteBuffer res = ByteBuffer.allocate(length);
            res.putInt(length);              // length
            res.putLong(utc);                 // value
            while (res.remaining() > 0)       // garbage
            {
                res.put((byte) (Math.random() * 256));
            }
            res.flip();
            return res;
        }
    }


    /**
     * A pathological record without an explicit length field.
     */
    class SmallRecordProvider implements RecordGenerator
    {
        @Override
        public RecordReader recordReader()
        {
            return new RecordReader()
            {
                @Override
                public int getLength(final ByteBuffer buffer)
                {
                    return 8;
                }

                @Override
                public int getLength(final ByteBuffer buffer, final int offset)
                {
                    return 8;
                }

                @Override
                public int getLength(final RecordBuffer buffer, final int offset)
                {
                    return 8;
                }
            };
        }

        @Override
        public RecordReader.LongField orderingField()
        {
            return new RecordReader.LongField()
            {
                @Override
                public long value(final ByteBuffer buffer, final int offset)
                {
                    return buffer.getLong(offset);
                }

                @Override
                public long value(final RecordBuffer buffer, final int offset)
                {
                    return buffer.getLong(offset);

                }
            };
        }

        public ByteBuffer generate(long utc)
        {
            ByteBuffer res = ByteBuffer.allocate(8);
            res.putLong(utc);           // value
            res.flip();
            return res;
        }

    }
}
