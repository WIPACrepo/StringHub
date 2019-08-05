package icecube.daq.spool;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.common.BufferContent;
import org.apache.log4j.Logger;
import sun.nio.ch.DirectBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;


/**
 * A file-backed record buffer.
 *
 */
public interface MemoryMappedSpoolFile extends RecordBuffer
{

    /**
     * Write a record.
     * @param data The record data.
     * @throws IOException Error writing the data.
     */
    public void write(final ByteBuffer data) throws IOException;

    /**
     * Flush in-memory changes to the file.
     * @throws IOException Error writing the data.
     */
    public void flush() throws IOException;

    /**
     * Close the file
     * @throws IOException
     */
    public void close() throws IOException;


    /**
     * Create a spool file implemented with memory mapped IO
     * @param file
     * @param blockSize
     * @return
     * @throws IOException
     */
    public static MemoryMappedSpoolFile createMappedSpoolFile(final File file,
                                                        final int blockSize)
            throws IOException
    {
        return new MappedSpoolFileImplementation(file, blockSize);
    }


    /**
     * Implements a readable/writable spool file via memory mapped IO.
     *
     * Memory mapped IO provides a performance benefit for high rate
     * writes. It also provides a unified memory buffer for satisfying
     * data readouts.
     *
     * Note that the backing file will be over-allocated, resulting in
     * the presence of zero-filled data beyond the written data. The file
     * will be truncated to the correct size on close.
     */
    public static class MappedSpoolFileImplementation
            implements MemoryMappedSpoolFile
    {
        private final File file;
        private final int allocationBlockSize;
        private int blocks;

        private final FileChannel fileChannel;
        private MappedByteBuffer buffer;

        private static final Logger logger =
                Logger.getLogger(MappedSpoolFileImplementation.class);

        private MappedSpoolFileImplementation(final File file,
                                              final int allocationBlockSize)
                throws IOException
        {
            if(allocationBlockSize == 0)
            {
                throw new IllegalArgumentException("Minimum block size is one");
            }
            this.file = file;
            this.allocationBlockSize = allocationBlockSize;
            blocks = 1;

            fileChannel = FileChannel.open(file.toPath(),
                    StandardOpenOption.READ, StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            buffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0,
                    (allocationBlockSize * blocks));
        }

        @Override
        public int getLength()
        {
            return buffer.position();
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
            BufferContent.ZERO_TO_POSITION.get(buffer, dest, offset,
                    start, length);
        }

        @Override
        public RecordBuffer view(final int start, final int length)
        {
            BoundsCheck.checkBounds(start, length, this);
            ByteBuffer data =
                    BufferContent.ZERO_TO_POSITION.view(buffer, start, length);

            //todo implement BufferDuplicator.SAME to prevent extra copy
            //     of pre-normalized buffer
            return RecordBuffers.wrap(data, BufferContent.ZERO_TO_CAPACITY);
        }

        @Override
        public RecordBuffer copy(final int start, final int length)
        {
            return RecordBuffers.wrap(getBytes(start, length));
        }

        @Override
        public String toString()
        {
            if (buffer != null)
            {
                return "MemoryMappedFileRecordBuffer(" + buffer.toString() +")";
            }
            else
            {
                return "MemoryMappedFileRecordBuffer(closed)";
            }
        }

        @Override
        public void write(final ByteBuffer data) throws IOException
        {
            // using exception mechanism to avoid recurring cost
            // of a null check;
            try
            {
                while(data.remaining() > buffer.remaining())
                {
                    extend();
                }
                buffer.put(data);
            }
            catch (NullPointerException npe)
            {
                if(buffer == null)
                {
                    throw new IOException("File is closed");
                }
                else
                {
                    throw new Error(npe);
                }
            }
        }

        @Override
        public void flush() throws IOException
        {
            buffer.force();
        }

        @Override
        public void close() throws IOException
        {
            flush();

            // truncate the over-allocated portion
            final long originalSize = fileChannel.size();
            final int overAllocated = buffer.remaining();
            final long targetSize = originalSize - overAllocated;

            fileChannel.truncate(targetSize);

            if(fileChannel.size() != targetSize)
            {
                logger.warn("Failed to truncate " + file.getName()
                        + " from: " + originalSize +" to: " + targetSize
                +", result is " + fileChannel.size());
            }

            fileChannel.close();

            forceUnmap(buffer);
            buffer = null;
        }

        private void extend() throws IOException
        {
            final int position = buffer.position();

            blocks++;
            final int newSize = allocationBlockSize * blocks;

            MappedByteBuffer previous = buffer;

            buffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0,
                    newSize);

            buffer.position(position);

            // if we don't unmap the prior buffer it will be mapped
            // until the next full GC event.
            forceUnmap(previous);
        }

        /**
         * Force a memory-mapped buffer to be unmapped.
         *
         * ALERT: This method utilizes some trickery to accomplish what the
         *        java engineers have deemed unsafe. Caller must ensure that
         *        the buffer is never utilized after this call.
         *
         * @param buffer The mapped buffer to unmap.
         */
        private static void forceUnmap(MappedByteBuffer buffer)
        {
            try
            {
                sun.misc.Cleaner cleaner = ((DirectBuffer) buffer).cleaner();
                cleaner.clean();
            }
            catch (Throwable th)
            {
                logger.error("Error unmapping buffer", th);
            }
        }

    }


}
