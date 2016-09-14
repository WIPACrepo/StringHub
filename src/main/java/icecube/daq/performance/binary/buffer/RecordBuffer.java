package icecube.daq.performance.binary.buffer;

import icecube.daq.performance.binary.record.RecordReader;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * General interface for a buffer containing icecube record(s).
 *
 * RecordBuffer intends to be:
 *
 * More flexible than ByteBuffer:
 *    Support arbitrary slicing.
 *    Support arbitrary bulk transfers
 *    Support specialized use cases such as RingBuffers and Chained buffers.
 *
 * Less flexible than ByteBuffer:
 *    Codify byte-ordering.
 *    Require indexed reads.
 *    Eliminate position/limit semantics.
 *
 * Notes:
 *    IceCube records are intrinsically big-endian, therefore reads and writes
 *    of multi-byte primitives operate in big-endian fashion against the backing
 *    store. Several record types are internally mixed-endian, in these cases
 *    it is the responsibility of the record-reader to perform the byte-swapping
 *    required to realize the correct field value.
 */
public interface RecordBuffer
{
    /**
     * @return The number of bytes available (for reading) in the buffer.
     */
    public int getLength();

    /**
     * @param index The position to read from.
     * @return The byte value at the position.
     * @throws  IndexOutOfBoundsException
     */
    public byte getByte(int index);

    /**
     * @param index The position to read from.
     * @return The short value at the position.
     * @throws  IndexOutOfBoundsException
     */
    public short getShort(int index);

    /**
     * @param index The position to read from.
     * @return The int value at the position.
     * @throws  IndexOutOfBoundsException
     */
    public int getInt(int index);

    /**
     * @param index The position to read from.
     * @return The long value at the position.
     * @throws  IndexOutOfBoundsException
     */
    public long getLong(int index);

    /**
     * @param start The position to start from.
     * @param length The number of bytes to retrieve.
     * @return A copy of the bytes from start to start + length.
     * @throws  IndexOutOfBoundsException
     */
    public byte[] getBytes(int start, int length);

    /**
     * @param dest The destination for the copied data.
     * @param offset The position in the destination that the copied data will
     *               start.
     * @param length The number of bytes to copy.
     * @throws  IndexOutOfBoundsException
     */
    public void copyBytes(byte[] dest, int offset, int start, int length);

    /**
     * @param start The position to start the view from.
     * @param length The number of bytes in the view.
     * @return A record buffer mapping the data from start to start + length.
     * @throws  IndexOutOfBoundsException
     */
    public RecordBuffer view(int start, int length);

    /**
     * @param start The position to start the view from.
     * @param length The number of bytes in the view.
     * @return A record buffer containing a copy of the data from start
     * to start + length.
     * @throws  IndexOutOfBoundsException
     */
    public RecordBuffer copy(int start, int length);


    /**
     * Iterate the contents of a record buffer, obtaining a view into
     * a (possibly) shared buffer for each record.
     * @param recordReader Defines the format of the record.
     * @return An iterable of buffers containing each record,
     *         the backing store may be shared.
     */
    default Iterable<RecordBuffer> eachRecord(RecordReader recordReader)
    {
        return new Iterable<RecordBuffer>()
        {
            @Override
            public Iterator<RecordBuffer> iterator()
            {
                return new RecordIterator(recordReader,
                        RecordBuffer.this, MemoryMode.SHARED_VIEW);
            }
        };
    }

    /**
     * Iterate the contents of a record buffer, obtaining a copy
     * of each record.
     * @param recordReader Defines the format of the record.
     * @return An iterable of buffers containing a copy of each record.
     */
    default Iterable<RecordBuffer> eachCopy(RecordReader recordReader)
    {
        return new Iterable<RecordBuffer>()
        {
            @Override
            public Iterator<RecordBuffer> iterator()
            {
                return new RecordIterator(recordReader,
                        RecordBuffer.this, MemoryMode.COPY);
            }
        };
    }

    /**
     * Iterate the contents of a record store, obtaining copy of each record
     * in the store in ByteBuffer format.
     * @param recordReader Defines the format of the record.
     * @return An iterable of buffers containing the content of each record
     *         in ByteBuffer format.
     */
    default Iterable<ByteBuffer> eachBuffer(RecordReader recordReader)
    {
        return new Iterable<ByteBuffer>()
        {
            @Override
            public Iterator<ByteBuffer> iterator()
            {
                return new BufferIterator(recordReader,
                        RecordBuffer.this);
            }
        };
    }

    /**
     * Iterate the contents of a record store, obtaining the index of each
     * record in the store.
     * @return An iterable of Integers which are indexes of each record.
     */
    default Iterable<Integer> eachIndex(RecordReader recordReader)
    {
        return new Iterable<Integer>()
        {
            @Override
            public Iterator<Integer> iterator()
            {
                return new IndexIterator(recordReader,
                        RecordBuffer.this);
            }
        };
    }


    /**
     * An enumeration to guide memory policies for operations that return a
     * buffer. In many cases operation results may be returned as a copy of
     * some portion of the source buffer or as a shared view of the source
     * buffer.
     *
     * In general a data copy should be specified, but a client with detailed
     * knowledge of the backing store and/or external synchronization of the
     * shared data against modifications may employ a shared view to improve
     * performance.
     *
     * When in doubt, specify a copy.
     */
    public enum MemoryMode
    {
        /**
         * Extracted data will allocate an independent copy of data from the
         * source buffer.
         */
        COPY
                {
                    @Override
                    public RecordBuffer extractData(final RecordBuffer buffer,
                                                       final int from,
                                                       final int length)
                    {
                        return buffer.copy(from, length);
                    }
                },
        /**
         * Extracted data will be a shared view into the source buffer.
         */
        SHARED_VIEW
                {
                    @Override
                    public RecordBuffer extractData(final RecordBuffer buffer,
                                                       final int from,
                                                       final int length)
                    {
                        return buffer.view(from, length);
                    }

                };

        public abstract RecordBuffer extractData(RecordBuffer buffer,
                                                    int from, int length);
    }


    /**
     * Common method for bounds checking to standardize
     * exception generation.
     *
     * Exception conditions mimic the behavior of ByteBuffer which
     * prefers IndexOutOfBoundsExceptions on absolute operations
     * and BufferUnderflowException/BufferOverflowException on
     * relative operations.
     *
     *    IndexOutOfBoundsException - reading or writing more data than
     *                                available on an absolute operation,
     *                                or pre-conditions on parameters are
     *                                violated on relative or absolute
     *                                operations.
     *    BufferUnderflowException - More data is requested than available
     *                               for reading on a relative read operation.
     *    BufferOverflowException - More data is written than available
     *                              on a relative write operation.
     * This convention allows implementations that forward requests to
     * ByteBuffer to forgo the (duplicate) bounds check and rely on
     * ByteBuffer to meet the exception contract;
     *
     */
    public static class BoundsCheck
    {

        // Makes coverage testing happy.
        private static BoundsCheck dummy  = new BoundsCheck();
        private BoundsCheck(){};

        /**
         * Bounds check for absolute index operations.
         *
         * @param start The index to start the bounds check from.
         * @param length The number of bytes beyond the index.
         * @param buffer The buffer making the query.
         * @throws IndexOutOfBoundsException If the bounds of the span
         *                                   are out of range.
         */
         public static void checkBounds(final int start, final int length,
                                       final RecordBuffer buffer)
        {
            int available = buffer.getLength();
            if(start < 0 || start >= available ||
                    start+length > available || length<0)
            {
                //special allowance for a zero-based, zero-length request
                // from a zero-length buffer
                if(start==0 && length==0 && available==0 )
                {
                    return;
                }

                int lastIdx = Math.max(available - 1, 0);
                throw new IndexOutOfBoundsException("[" + start + " - " +
                        (start+length) + "] of [0 - " + lastIdx + "]" );
            }
        }

        /**
         * Bounds check for relative read operations.
         *
         * Unused, but implemented for completeness.
         *
         * @param wanted The number of bytes requested to read.
         * @param buffer The buffer making the query.
         * @throws BufferUnderflowException If requested bytes are more than
         *                                  available.
         */
        public static void ensureReadAvailable(final int wanted,
                                               final RecordBuffer buffer)
        {
            if(wanted > buffer.getLength())
            {
                throw new BufferUnderflowException();
            }
        }

        /**
         * Bounds check for relative write operations.
         *
         * @param needed The number of bytes requested to write.
         * @param buffer The buffer making the query.
         * @throws BufferUnderflowException If needed space is more than
         *                                  available.
         */
        public static void ensureWriteAvailable(final int needed,
                                                final WritableRecordBuffer buffer)
        {
            if(needed > buffer.available())
            {
                throw new BufferOverflowException();
            }
        }
    }


    public static class RecordIterator implements Iterator<RecordBuffer>
    {
        final RecordReader recordReader;
        final RecordBuffer buffer;
        final RecordBuffer.MemoryMode mode;
        int idx;


        public RecordIterator(final RecordReader recordReader,
                              final RecordBuffer buffer,
                              final RecordBuffer.MemoryMode mode)
        {
            this.recordReader = recordReader;
            this.buffer = buffer;
            this.mode = mode;
            this.idx=0;
        }

        @Override
        public boolean hasNext()
        {
            return idx < buffer.getLength();
        }

        @Override
        public RecordBuffer next()
        {
            int length = recordReader.getLength(buffer, idx);
            if(length > 0)
            {
                int pos = idx;
                idx+=length;
                return mode.extractData(buffer, pos, length);
            }
            else
            {
                throw new Error("Zero-length record at index: " + idx);
            }
        }

    }


    public static class BufferIterator implements Iterator<ByteBuffer>
    {
        final RecordReader recordReader;
        final RecordBuffer buffer;
        int idx;


        public BufferIterator(final RecordReader recordReader,
                              final RecordBuffer buffer)
        {
            this.recordReader = recordReader;
            this.buffer = buffer;
            this.idx = 0;
        }

        @Override
        public boolean hasNext()
        {
            return idx < buffer.getLength();
        }

        @Override
        public ByteBuffer next()
        {
            int length = recordReader.getLength(buffer, idx);

            if(length > 0)
            {
                byte[] data = buffer.getBytes(idx, length);
                idx+=length;
                return ByteBuffer.wrap(data);
            }
            else
            {
                throw new Error("Zero-length record at index: " + idx);
            }
        }

    }


    public static class IndexIterator implements java.util.Iterator<Integer>
    {
        final RecordReader recordReader;
        final RecordBuffer buffer;
        int idx;


        public IndexIterator(final RecordReader recordReader, final RecordBuffer buffer)
        {
            this.recordReader = recordReader;
            this.buffer = buffer;
        }

        @Override
        public boolean hasNext()
        {
            return idx < buffer.getLength();
        }

        @Override
        public Integer next()
        {
            int ret = idx;
            int length = recordReader.getLength(buffer, idx);
            if(length > 0)
            {
                idx += length;
                return ret;
            }
            else
            {
                throw new Error("Zero-length record at index: " + idx);
            }
        }
    }


}
