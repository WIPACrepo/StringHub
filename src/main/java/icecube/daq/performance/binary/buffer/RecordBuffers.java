package icecube.daq.performance.binary.buffer;

import icecube.daq.performance.common.BufferContent;
import icecube.daq.performance.common.PowersOfTwo;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains several RecordBuffer implementations and utility factory methods
 * for instantiating them.
 */
public class RecordBuffers
{

    /** An always empty buffer. */
    public static final RecordBuffer EMPTY_BUFFER = new EmptyBuffer();

    /**
     * Factory method for wrapping ByteBuffers.
     * @param buffer The ByteBuffer to wrap.
     * @param mode Defines the location of the data content in the buffer.
     * @return A RecordBuffer containing the data content.
     */
    public static RecordBuffer wrap(final ByteBuffer buffer,
                                    final BufferContent mode)
    {
        return new RecordBuffers.ByteBufferWrapper(buffer, mode);
    }

    /**
     * A factory method for wrapping an array of bytes.
     * @param buffer The array to wrap.
     * @return A RecordBuffer containing the array.
     */
    public static RecordBuffer wrap(final byte[] buffer)
    {
        return new RecordBuffers.ByteBufferWrapper(ByteBuffer.wrap(buffer),
                BufferContent.ZERO_TO_CAPACITY);
    }


    /**
     * A factory method for chaining several RecordBuffers together
     * into a single cohesive RecordBuffer.
     * @param buffers The record buffers to wrap, in order matching
     *                data progression.
     * @return A chained buffer containing the content of all
     *         buffers.
     */
    public static RecordBuffer chain(final RecordBuffer[] buffers)
    {
        return new ChainedBuffer(buffers);
    }

    /**
     * A factory method for chaining several byte arrays together
     * into a single cohesive RecordBuffer.
     * @param buffers The byte arrays to wrap, in order matching
     *                data progression.
     * @return A chained buffer containing the content of all
     *         arrays.
     */
    public static RecordBuffer chain(final byte[][] buffers)
    {
        RecordBuffer[] wraps = new RecordBuffer[buffers.length];
        for (int i = 0; i < wraps.length; i++)
        {
            wraps[i] = wrap(buffers[i]);
        }
        return new ChainedBuffer(wraps);
    }

    /**
     * Factory method for writable buffer storage.
     * @param size The size of the buffer to create.
     * @return A WritableRecordBuffer backed by a ByteBuffer.
     */
    public static WritableRecordBuffer writable(int size)
    {
        return new WritableByteBuffer(size);
    }

    /**
     * Factory method for ring buffer storage.
     * @param size The size of the ring.
     * @return A ring buffer, optimized for power-of-two sizes.
     */
    public static WritableRecordBuffer.Prunable ring(PowersOfTwo size)
    {
        return new FastRingBuffer(size);
    }

    /**
     * Factory method for ring buffer storage.
     *
     * @param size The size of the ring.
     * @return A ring buffer, if size is a power-of-two, the ring
     *         will be optimized.
     */
    public static WritableRecordBuffer.Prunable ring(int size)
    {
        if(PowersOfTwo.isPowerOfTwo(size) && PowersOfTwo.lookup(size) != null)
        {
            return new FastRingBuffer(PowersOfTwo.lookup(size));
        }
        else
        {
            return new RingBuffer(size);
        }
    }

    /**
     * Empty Buffer
     */
    protected static class EmptyBuffer implements RecordBuffer
    {
        @Override
        public int getLength()
        {
            return 0;
        }

        @Override
        public byte getByte(final int index)
        {
            throw new IndexOutOfBoundsException("Empty Buffer, " + index +
                    " out of bounds");
        }

        @Override
        public short getShort(final int index)
        {
            throw new IndexOutOfBoundsException("Empty Buffer, " + index +
                    " out of bounds");
        }

        @Override
        public int getInt(final int index)
        {
            throw new IndexOutOfBoundsException("Empty Buffer, " + index +
                    " out of bounds");
        }

        @Override
        public long getLong(final int index)
        {
            throw new IndexOutOfBoundsException("Empty Buffer, " + index +
                    " out of bounds");
        }

        @Override
        public byte[] getBytes(final int start, final int length)
        {
            BoundsCheck.checkBounds(start, length, this);
            return new byte[0];
        }

        @Override
        public void copyBytes(final byte[] dest, final int offset, final int start, final int length)
        {
            BoundsCheck.checkBounds(start, length, this);
            return;
        }

        @Override
        public RecordBuffer view(final int start, final int length)
        {
            BoundsCheck.checkBounds(start, length, this);
            return new EmptyBuffer(); // view(0,0) is permitted
        }

        @Override
        public RecordBuffer copy(final int start, final int length)
        {
            BoundsCheck.checkBounds(start, length, this);
            return new EmptyBuffer();
        }
    }


    /**
     * Wraps a ByteBuffer.
     *
     * Note: Buffer will be accessed in BIG_ENDIAN order
     */
    protected static class ByteBufferWrapper implements RecordBuffer
    {
        private final ByteBuffer buffer;
        private final int length;

        /**
         * Wrap a ByteBuffer.
         * @param buffer The buffer to wrap.
         * @param mode Defines the portion of the buffer that is the data
         *             content.
         */
        public ByteBufferWrapper(final ByteBuffer buffer, BufferContent mode)
        {
            // For clarity on the endianess issue, reject LITTLE_ENDIAN
            // buffers.  This is a design choice.
            if(buffer.order() != ByteOrder.BIG_ENDIAN)
            {
                throw new IllegalArgumentException("Record Buffers are designed for BIG_ENDIAN" +
                        " Storage");
            }
            this.buffer = mode.normalize(buffer);
            this.length = this.buffer.capacity();
        }

        @Override
        public int getLength()
        {
            return length;
        }

        @Override
        public byte getByte(final int index)
        {
            return buffer.get(index);
        }

        @Override
        public short getShort(final int index)
        {
            return buffer.getShort(index);
        }

        @Override
        public int getInt(final int index)
        {
            return buffer.getInt(index);
        }

        @Override
        public long getLong(final int index)
        {
            return buffer.getLong(index);
        }

        @Override
        public byte[] getBytes(final int start, final int length)
        {
            BoundsCheck.checkBounds(start, length, this);
            byte[] ret = new byte[length];
            BufferContent.ZERO_TO_CAPACITY.get(buffer, ret, 0, start, length);
            return ret;
        }

        @Override
        public void copyBytes(final byte[] dest, final int offset,
                              final int start, final int length)
        {
            BoundsCheck.checkBounds(start, length, this);
            BufferContent.ZERO_TO_CAPACITY.get(buffer, dest, offset, start, length);
        }

        @Override
        public RecordBuffer view(final int start, final int length)
        {
            BoundsCheck.checkBounds(start, length, this);
            ByteBuffer data = BufferContent.ZERO_TO_CAPACITY.view(buffer, start, length);

            return new ByteBufferWrapper(data, BufferContent.ZERO_TO_CAPACITY);
        }

        @Override
        public RecordBuffer copy(final int start, final int length)
        {
            return RecordBuffers.wrap(getBytes(start, length));
        }

        @Override
        public String toString()
        {
            return "WRAPPED(" + buffer.toString() +")";
        }
    }


    /**
     * Coalesce several ByteBuffers into a single RecordBuffer
     */
    protected static class ChainedBuffer implements RecordBuffer
    {
        private final RecordBuffer[] buffers;
        private final int[] offsets;
        private final int length;

        public ChainedBuffer(final RecordBuffer[] buffers)
        {
            this.buffers = buffers;
            this.offsets = new int[buffers.length];

            // calc the offset array
            for (int i = 1; i < buffers.length; i++)
            {
                offsets[i] = offsets[i-1] + buffers[i-1].getLength();
            }
            // calc the length
            int tempLength = 0;
            for (int i = 0; i < buffers.length; i++)
            {
                tempLength += buffers[i].getLength();
            }
            length = tempLength;
        }

        @Override
        public int getLength()
        {
            return length;
        }

        @Override
        public byte getByte(final int index)
        {
            int segment = getSegment(index);
            return buffers[segment].getByte(index - offsets[segment]);
        }

        @Override
        public short getShort(final int index)
        {
            // NOTE: Implemented with underflow exception mechanism
            // as flow control. In the majority of cases, the datum
            // will live in a single buffer so optimistically take
            // that path.  On underflow, fall back to the split-segment
            // read.
            int segment = getSegment(index);
            int segmentIndex = index - offsets[segment];

            try
            {
                return  buffers[segment].getShort(segmentIndex);
            }
            catch (IndexOutOfBoundsException ie)
            {
                // try a segmented read
                return (short) getNumber(index, 2);
            }
        }

        @Override
        public int getInt(final int index)
        {
            // NOTE: Implemented with underflow exception mechanism
            // as flow control. In the majority of cases, the datum
            // will live in a single buffer so optimistically take
            // that path.  On underflow, fall back to the split-segment
            // read.
            int segment = getSegment(index);
            int segmentIndex = index - offsets[segment];

            try
            {
                return  buffers[segment].getInt(segmentIndex);
            }
            catch (IndexOutOfBoundsException ie)
            {
                // try a segmented read
                return (int) getNumber(index, 4);
            }
        }

        @Override
        public long getLong(final int index)
        {
            // NOTE: Implemented with underflow exception mechanism
            // as flow control. In the majority of cases, the datum
            // will live in a single buffer so optimistically take
            // that path.  On underflow, fall back to the split-segment
            // read.
            int segment = getSegment(index);
            int segmentIndex = index - offsets[segment];

            try
            {
                return  buffers[segment].getLong(segmentIndex);
            }
            catch (IndexOutOfBoundsException ie)
            {
                // try a segmented read
                return getNumber(index, 8);
            }
        }

        @Override
        public byte[] getBytes(final int start, final int length)
        {
            BoundsCheck.checkBounds(start, length, this);
            byte[] result = new byte[length];
            copyBytes(result, 0, start, length);
            return result;
        }

        @Override
        public void copyBytes(final byte[] dest, final int offset, final int start, final int length)
        {
            BoundsCheck.checkBounds(start, length, this);
            int segmentIdx = getSegment(start);
            int segmentStartIndex = start - offsets[segmentIdx];

            int copied = 0;
            while(copied < length)
            {
                RecordBuffer segment = buffers[segmentIdx];
                int copyLength = Math.min( (length - copied), (segment.getLength() - segmentStartIndex) );
                segment.copyBytes(dest, (offset + copied), segmentStartIndex, copyLength);

                copied += copyLength;
                segmentIdx++;
                segmentStartIndex = 0;
            }

        }

        @Override
        public RecordBuffer view(final int start, final int length)
        {
            BoundsCheck.checkBounds(start, length, this);
            int segmentIdx = getSegment(start);
            int segmentStartIndex = start - offsets[segmentIdx];

            List<RecordBuffer> views = new ArrayList<RecordBuffer>(getSegment(start + length) - segmentIdx + 1);
            int mapped = 0;
            while(mapped < length)
            {
                RecordBuffer segment = buffers[segmentIdx];
                int mapLength = Math.min( (length - mapped), (segment.getLength() - segmentStartIndex) );
                views.add(segment.view(segmentStartIndex, mapLength));

                mapped += mapLength;
                segmentIdx++;
                segmentStartIndex = 0;
            }


            RecordBuffer[] recordBuffers = views.toArray(new RecordBuffer[views.size()]);
            if(recordBuffers.length == 0)
            {
                return new EmptyBuffer();
            }
            else if (recordBuffers.length == 1)
            {
                return recordBuffers[0];
            }
            else
            {
                return new ChainedBuffer(recordBuffers);
            }
        }

        @Override
        public RecordBuffer copy(final int start, final int length)
        {
            return RecordBuffers.wrap(getBytes(start, length));
        }

        /**
         * Get the ordinal of the segment that contains the global index
         * @param globalIndex
         * @return
         */
        private int getSegment(int globalIndex)
        {
            int segment = 0;
            for (int i = 1; i < offsets.length; i++)
            {
                if(globalIndex < offsets[i])
                {
                    return segment;
                }
                else
                {
                    segment = i;
                }
            }

            return segment;
        }

        /**
         * Read a short, int or long that spans a segment boundary.
         *
         * @param index The global index
         * @param bytes The width of the primitive.
         * @return A long containing the number, this long may be downcast
         *         by the caller to the type with the same width.
         */
        private long getNumber(int index, int bytes)
        {
            //starting segment
            int currentSegment = getSegment(index);
            int currentSegmentIndex = index - offsets[currentSegment];

            long acc = 0;
            int bytesRead=0;
            while (bytesRead < bytes)
            {
                int segmentLength = buffers[currentSegment].getLength();
                while (currentSegmentIndex < segmentLength && bytesRead < bytes)
                {
                    acc <<=8;
                    acc |= (buffers[currentSegment].getByte(currentSegmentIndex) &0xFF);
                    currentSegmentIndex++;
                    bytesRead++;
                }
                currentSegment++;
                currentSegmentIndex=0;
            }
            return acc;
        }
    }


    /**
     * Wraps a ByteBuffer.
     *
     * Note: Implementation is un-synchronized.
     */
    protected  static class WritableByteBuffer implements WritableRecordBuffer
    {
        private final ByteBuffer buffer;


        public WritableByteBuffer(final int size)
        {
            this.buffer = ByteBuffer.allocate(size);
            this.buffer.order(ByteOrder.BIG_ENDIAN);
        }

        @Override
        public void put(final ByteBuffer data)
        {
            //todo consider remove bounds check after testing (performance)
            BoundsCheck.ensureWriteAvailable(data.remaining(), this);
            buffer.put(data);
        }

        @Override
        public int getLength()
        {
            return buffer.position();
        }

        @Override
        public int available()
        {
            return buffer.remaining();
        }

        @Override
        public byte getByte(final int index)
        {
            BoundsCheck.checkBounds(index, 1, this);
            return buffer.get(index);
        }

        @Override
        public short getShort(final int index)
        {
            //todo consider remove bounds check after testing (performance)
            BoundsCheck.checkBounds(index, 2, this);
            return buffer.getShort(index);
        }

        @Override
        public int getInt(final int index)
        {
            //todo consider remove bounds check after testing (performance)
            BoundsCheck.checkBounds(index, 4, this);
            return buffer.getInt(index);
        }

        @Override
        public long getLong(final int index)
        {
            //todo consider remove bounds check after testing (performance)
            BoundsCheck.checkBounds(index, 8, this);
            return buffer.getLong(index);
        }

        @Override
        public byte[] getBytes(final int start, final int length)
        {
            BoundsCheck.checkBounds(start, length, this);
            byte[] ret = new byte[length];
            BufferContent.ZERO_TO_POSITION.get(buffer, ret, 0, start, length);
            return ret;
        }

        @Override
        public void copyBytes(final byte[] dest, final int offset,
                              final int start, final int length)
        {
            BoundsCheck.checkBounds(start, length, this);
            BufferContent.ZERO_TO_POSITION.get(buffer, dest, offset, start, length);
        }

        @Override
        public RecordBuffer view(final int start, final int length)
        {
            BoundsCheck.checkBounds(start, length, this);
            ByteBuffer data = BufferContent.ZERO_TO_POSITION.view(buffer, start, length);

            return RecordBuffers.wrap(data, BufferContent.ZERO_TO_CAPACITY); //todo implement BufferDuplicator.SAME to prevent extra copy of pre-normalized buffer
        }

        @Override
        public RecordBuffer copy(final int start, final int length)
        {
            return RecordBuffers.wrap(getBytes(start, length));
        }

        @Override
        public String toString()
        {
            return "WritableByteBuffer(" + buffer.toString() +")";
        }
    }


    /**
     * Implements a circular buffer of arbitrary size.
     *
     * Note: Implementation is un-synchronized.
     */
    protected static class RingBuffer implements WritableRecordBuffer.Prunable
    {
        private final ByteBuffer ring;
        private int size;
        private int head, tail;
        private int numElements;



        public RingBuffer(final int size)
        {
            this.size = size;
            this.ring = ByteBuffer.allocate(size);
            this.numElements = 0;
        }

        @Override
        public void put(final ByteBuffer data)
        {
            final int recordSize = data.remaining();

            if(available() >= recordSize)
            {


                // If the buffer fits without wrapping, use a bulk put
                // operation (which is faster) otherwise use the byte-by-byte
                // operation wrapping at the size modulus
                if((size-head) >= recordSize)
                {
                    ring.position(head);
                    ring.put(data);
                    head+=recordSize;
                    numElements+=recordSize;

                    if(head == size)
                    {
                        head = 0;
                    }
                }
                else
                {
                    while(data.remaining() > 0)
                    {
                        ring.put(head++, data.get());
                        numElements++;
                        if(head == size)
                        {
                            head = 0;
                        }
                    }
                }
            }
            else
            {
                throw new BufferOverflowException();
            }
        }

        /**
         * Advance the tail.  When using the buffer to store records,
         * clients must manage the tail in whole-record units so that the
         * tail is always located at the first byte of a record.
         * @param count The number of bytes to advance the tail.
         */
        public void prune(final int count)
        {
            RecordBuffer.BoundsCheck.ensureReadAvailable(count, this);
            tail = toPhysicalIndex(count);
            numElements -= count;
        }

        @Override
        public int getLength()
        {
            return numElements;
        }

        @Override
        public int available()
        {
            return size - numElements;
        }

        @Override
        public byte getByte(final int index)
        {
            RecordBuffer.BoundsCheck.checkBounds(index, 1, this);

            return ring.get(toPhysicalIndex(index));
        }

        @Override
        public short getShort(final int index)
        {
            RecordBuffer.BoundsCheck.checkBounds(index, 2, this);

            int readIndex = toPhysicalIndex(index);
            try
            {
                return ring.getShort(readIndex);
            }
            catch (IndexOutOfBoundsException e)
            {
                return (short) getNumber(readIndex, 2);
            }
        }

        @Override
        public int getInt(final int index)
        {
            RecordBuffer.BoundsCheck.checkBounds(index, 4, this);

            int readIndex = toPhysicalIndex(index);

            try
            {
                return ring.getInt(readIndex);
            }
            catch (IndexOutOfBoundsException e)
            {
                return (int) getNumber(readIndex, 4);
            }
        }

        @Override
        public long getLong(final int index)
        {
            RecordBuffer.BoundsCheck.checkBounds(index, 8, this);

            int readIndex = toPhysicalIndex(index);

            try
            {
                return ring.getLong(readIndex);
            }
            catch (IndexOutOfBoundsException e)
            {
                return getNumber(readIndex, 8);
            }
        }

        private int toPhysicalIndex(final int logicalIndex)
        {
            if((size - tail) > logicalIndex)
            {
                return (tail + logicalIndex);
            }
            else
            {
                return (logicalIndex - (size-tail));
            }
        }

        /**
         * Read a short, int or long that wraps around the ring.
         *
         * @param index The starting index
         * @param bytes The width of the primitive.
         * @return A long containing the number, this long may be downcast
         *         by the caller to the type with the same width.
         */
        private long getNumber(final int index, final int bytes)
        {
            int currentIdx = index;
            long acc = 0;
            int bytesRead=0;
            while (bytesRead < bytes)
            {
                acc <<=8;
                acc |= (ring.get(currentIdx) &0xFF);
                bytesRead++;
                currentIdx++;
                if(currentIdx == size)
                {
                    currentIdx = 0;
                }
            }
            return acc;
        }

        @Override
        public byte[] getBytes(final int start, final int length)
        {
            BoundsCheck.checkBounds(start, length, this);
            byte[] result = new byte[length];
            copyBytes(result, 0, start, length);
            return result;
        }

        @Override
        public void copyBytes(final byte[] dest, final int offset,
                              final int start, final int length)
        {

            //Note: Incoming start index is zero-based with 0 defined by the
            // current position of the tail.

            RecordBuffer.BoundsCheck.checkBounds(start, length, this);

            // convert to the backing indexes
            final int fromIdx = toPhysicalIndex(start);
            final int toIdx;
            if(size - fromIdx > length)
            {
                toIdx = fromIdx+length;
            }
            else
            {
                toIdx = length - (size - fromIdx);
            }

            // from < to: view is in contiguous memory, one part copy
            // from > to:  view wraps past the end of the backing buffer,
            //               two part copy
            // from == to: special case
            if(fromIdx < toIdx)
            {
                BufferContent.ZERO_TO_CAPACITY.get(ring, dest, offset, fromIdx, length);
            }
            else if (fromIdx > toIdx)
            {
                //head behind the tail
                int partOneLength = size - fromIdx;
                int partTwoLength = length - partOneLength;

                BufferContent.ZERO_TO_CAPACITY.get(ring, dest, offset, fromIdx, partOneLength);
                BufferContent.ZERO_TO_CAPACITY.get(ring, dest, (offset + partOneLength), 0, partTwoLength);
            }
            else
            {
                // disregarding a bounds check fail, there are
                // three possibilities:
                // 1. zero-length request
                // 2. capacity-length request
                //    2a. tail at 0: can wrap contiguous memory
                //    2b. tail > 0:  must chain wrapping segments

                if(length == 0)
                {
                    return;
                }
                else if(fromIdx == 0)
                {
                    BufferContent.ZERO_TO_CAPACITY.get(ring, dest, offset, fromIdx, length);
                }
                else
                {
                    //head behind the tail
                    int partOneLength = size - fromIdx;
                    int partTwoLength = length - partOneLength;

                    BufferContent.ZERO_TO_CAPACITY.get(ring, dest, offset, fromIdx, partOneLength);
                    BufferContent.ZERO_TO_CAPACITY.get(ring, dest, (offset + partOneLength), 0, partTwoLength);
                }
            }
        }

        @Override
        public RecordBuffer view(final int start, final int length)
        {
            //Note: Incoming start index is zero-based with 0 defined by the
            // current position of the tail.
            RecordBuffer.BoundsCheck.checkBounds(start, length, this);

            // convert to the backing indexes
            final int fromIdx = toPhysicalIndex(start);
            final int toIdx;
            if(size - fromIdx > length)
            {
                toIdx = fromIdx+length;
            }
            else
            {
                toIdx = length - (size - fromIdx);
            }

            // from < to: view is in contiguous memory, project view
            //               directly into backing buffer
            // from > to:  view wraps past the end of the backing buffer,
            //               chain segments
            // from == to: special case
            if(fromIdx < toIdx)
            {
                ByteBuffer view = BufferContent.ZERO_TO_CAPACITY.view(ring, fromIdx, length );
                return RecordBuffers.wrap(view, BufferContent.ZERO_TO_CAPACITY);
            }
            else if(fromIdx > toIdx)
            {
                ByteBuffer leftSegment = BufferContent.ZERO_TO_CAPACITY.view(ring, fromIdx, size-fromIdx);
                RecordBuffer left = RecordBuffers.wrap(leftSegment, BufferContent.ZERO_TO_CAPACITY);

                ByteBuffer rightSegment = BufferContent.ZERO_TO_CAPACITY.view(ring, 0, toIdx);
                RecordBuffer right = RecordBuffers.wrap(rightSegment, BufferContent.ZERO_TO_CAPACITY);

                return RecordBuffers.chain(new RecordBuffer[]{left, right});
            }
            else
            {
                // disregarding a bounds check fail, there are
                // three possibilities:
                // 1. zero-length request
                // 2. capacity-length request
                //    2a. tail at 0: can wrap contiguous memory
                //    2b. tail > 0:  must chain wrapping segments

                if(length == 0)
                {
                    return RecordBuffers.EMPTY_BUFFER;
                }
                else if(fromIdx == 0)
                {
                    ByteBuffer view = BufferContent.ZERO_TO_CAPACITY.view(ring, fromIdx, length );
                    return RecordBuffers.wrap(view, BufferContent.ZERO_TO_CAPACITY);
                }
                else
                {
                    ByteBuffer leftSegment = BufferContent.ZERO_TO_CAPACITY.view(ring, fromIdx, size-fromIdx);
                    RecordBuffer left = RecordBuffers.wrap(leftSegment, BufferContent.ZERO_TO_CAPACITY);

                    ByteBuffer rightSegment = BufferContent.ZERO_TO_CAPACITY.view(ring, 0, toIdx);
                    RecordBuffer right = RecordBuffers.wrap(rightSegment, BufferContent.ZERO_TO_CAPACITY);

                    return RecordBuffers.chain(new RecordBuffer[]{left, right});
                }
            }

        }

        @Override
        public RecordBuffer copy(final int start, final int length)
        {
            return RecordBuffers.wrap(getBytes(start, length));
        }
    }


    /**
     * Implements a circular buffer.
     *
     * Optimized for buffers sized to a power of two.
     *
     * Note: Implementation is un-synchronized.
     */
    protected static class FastRingBuffer implements WritableRecordBuffer.Prunable
    {
        private final ByteBuffer ring;
        private PowersOfTwo size;
        private int head, tail;


        public FastRingBuffer(final PowersOfTwo size)
        {
            this.size = size;
            this.ring = ByteBuffer.allocate(size.value());
        }

        @Override
        public void put(final ByteBuffer data)
        {
            final int recordSize = data.remaining();

            RecordBuffer.BoundsCheck.ensureWriteAvailable(recordSize, this);

            // If the buffer fits without wrapping, use a bulk put
            // operation (which is faster) otherwise use the byte-by-byte
            // operation wrapping at the size modulus
            int position = head & size.mask();
            if( (position + recordSize) < size.value())
            {
                ring.position(position);
                ring.put(data);
                head+=recordSize;
            }
            else
            {
                while(data.remaining() > 0)
                {
                    ring.put(head++ & size.mask(), data.get());
                }
            }
        }

        /**
         * Advance the tail.  When using the buffer to store records,
         * clients must manage the tail in whole-record units so that the
         * tail is always located at the first byte of a record.
         * @param count The number of bytes to advance the tail.
         */
        public void prune(final int count)
        {
            RecordBuffer.BoundsCheck.ensureReadAvailable(count, this);
            tail+=count;
        }

        @Override
        public int getLength()
        {
            return head-tail;
        }

        @Override
        public int available()
        {
            return (size.value() - (head-tail));
        }

        @Override
        public byte getByte(final int index)
        {
            RecordBuffer.BoundsCheck.checkBounds(index, 1, this);
            int startIdx = (tail + index) & size.mask();
            return ring.get(startIdx);
        }

        @Override
        public short getShort(final int index)
        {
            RecordBuffer.BoundsCheck.checkBounds(index, 2, this);

            int startIdx = (tail + index) & size.mask();
            try
            {
                return ring.getShort(startIdx);
            }
            catch (IndexOutOfBoundsException e)
            {
                return (short) getNumber(startIdx, 2);
            }
        }

        @Override
        public int getInt(final int index)
        {
            RecordBuffer.BoundsCheck.checkBounds(index, 4, this);

            int startIdx = (tail + index) & size.mask();
            try
            {
                return ring.getInt(startIdx);
            }
            catch (IndexOutOfBoundsException e)
            {
                return (int) getNumber(startIdx, 4);
            }
        }

        @Override
        public long getLong(final int index)
        {
            RecordBuffer.BoundsCheck.checkBounds(index, 8, this);

            int startIdx = (tail + index) & size.mask();
            try
            {
                return ring.getLong(startIdx);
            }
            catch (IndexOutOfBoundsException e)
            {
                return getNumber(startIdx, 8);
            }
        }


        /**
         * Read a short, int or long that wraps around the ring.
         *
         * @param index The starting index
         * @param bytes The width of the primitive.
         * @return A long containing the number, this long may be downcast
         *         by the caller to the type with the same width.
         */
        private long getNumber(final int index, final int bytes)
        {
            int currentIdx = index;
            long acc = 0;
            int bytesRead=0;
            while (bytesRead < bytes)
            {
                acc <<=8;
                acc |= (ring.get(currentIdx & size.mask()) &0xFF);
                bytesRead++;
                currentIdx++;
            }
            return acc;
        }

        @Override
        public byte[] getBytes(final int start, final int length)
        {
            RecordBuffer.BoundsCheck.checkBounds(start, length, this);
            byte[] result = new byte[length];
            copyBytes(result, 0, start, length);
            return result;
        }

        @Override
        public void copyBytes(final byte[] dest, final int offset,
                              final int start, final int length)
        {

            //Note: Incoming start index is zero-based with 0 defined by the
            // current position of the tail.

            RecordBuffer.BoundsCheck.checkBounds(start, length, this);

            // convert to the backing indexes
            int fromIdx = (tail + start) & size.mask();
            int toIdx = (tail + start + length) & size.mask();
            // from < to: view is in contiguous memory, one part copy
            // from > to:  view wraps past the end of the backing buffer,
            //               two part copy
            // from == to: special case
            if(fromIdx < toIdx)
            {
                BufferContent.ZERO_TO_CAPACITY.get(ring, dest, offset, fromIdx, length);
            }
            else if (fromIdx > toIdx)
            {
                //head behind the tail
                int partOneLength = size.value() - fromIdx;
                int partTwoLength = length - partOneLength;

                BufferContent.ZERO_TO_CAPACITY.get(ring, dest, offset, fromIdx, partOneLength);
                BufferContent.ZERO_TO_CAPACITY.get(ring, dest, (offset + partOneLength), 0, partTwoLength);
            }
            else
            {
                // disregarding a bounds check fail, there are
                // three possibilities:
                // 1. zero-length request
                // 2. capacity-length request
                //    2a. tail at 0: can wrap contiguous memory
                //    2b. tail > 0:  must chain wrapping segments

                if(length == 0)
                {
                    return;
                }
                else if(fromIdx == 0)
                {
                    BufferContent.ZERO_TO_CAPACITY.get(ring, dest, offset, fromIdx, length);
                }
                else
                {
                    //head behind the tail
                    int partOneLength = size.value() - fromIdx;
                    int partTwoLength = length - partOneLength;

                    BufferContent.ZERO_TO_CAPACITY.get(ring, dest, offset, fromIdx, partOneLength);
                    BufferContent.ZERO_TO_CAPACITY.get(ring, dest, (offset + partOneLength), 0, partTwoLength);
                }
            }
        }

        @Override
        public RecordBuffer view(final int start, final int length)
        {
            //Note: Incoming start index is zero-based with 0 defined by the
            // current position of the tail.

            RecordBuffer.BoundsCheck.checkBounds(start, length, this);

            // convert to the backing indexes
            int fromIdx = (tail + start) & size.mask();
            int toIdx = (tail + start + length) & size.mask();

            // from < to: view is in contiguous memory, project view
            //               directly into backing buffer
            // from > to:  view wraps past the end of the backing buffer,
            //               chain segments
            //
            // from == to: special case
            if(fromIdx < toIdx)
            {
                ByteBuffer view = BufferContent.ZERO_TO_CAPACITY.view(ring, fromIdx, length );
                return RecordBuffers.wrap(view, BufferContent.ZERO_TO_CAPACITY);
            }
            else if(fromIdx > toIdx)
            {
                ByteBuffer leftSegment = BufferContent.ZERO_TO_CAPACITY.view(ring, fromIdx, size.value()-fromIdx);
                RecordBuffer left = RecordBuffers.wrap(leftSegment, BufferContent.ZERO_TO_CAPACITY);

                ByteBuffer rightSegment = BufferContent.ZERO_TO_CAPACITY.view(ring, 0, toIdx);
                RecordBuffer right = RecordBuffers.wrap(rightSegment, BufferContent.ZERO_TO_CAPACITY);

                return RecordBuffers.chain(new RecordBuffer[]{left, right});
            }
            else
            {
                // disregarding a bounds check fail, there are
                // three possibilities:
                // 1. zero-length request
                // 2. capacity-length request
                //    2a. tail at 0: can wrap contiguous memory
                //    2b. tail > 0:  must chain wrapping segments

                if(length == 0)
                {
                    return RecordBuffers.EMPTY_BUFFER;
                }
                else if(fromIdx == 0)
                {
                    ByteBuffer view = BufferContent.ZERO_TO_CAPACITY.view(ring, fromIdx, length );
                    return RecordBuffers.wrap(view, BufferContent.ZERO_TO_CAPACITY);
                }
                else
                {
                    ByteBuffer leftSegment = BufferContent.ZERO_TO_CAPACITY.view(ring, fromIdx, size.value()-fromIdx);
                    RecordBuffer left = RecordBuffers.wrap(leftSegment, BufferContent.ZERO_TO_CAPACITY);

                    ByteBuffer rightSegment = BufferContent.ZERO_TO_CAPACITY.view(ring, 0, toIdx);
                    RecordBuffer right = RecordBuffers.wrap(rightSegment, BufferContent.ZERO_TO_CAPACITY);

                    return RecordBuffers.chain(new RecordBuffer[]{left, right});
                }
            }
        }

        @Override
        public RecordBuffer copy(final int start, final int length)
        {
            return RecordBuffers.wrap(getBytes(start, length));
        }
    }


}
