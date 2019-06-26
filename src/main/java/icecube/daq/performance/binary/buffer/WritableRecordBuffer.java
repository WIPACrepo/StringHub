package icecube.daq.performance.binary.buffer;

import icecube.daq.performance.common.BufferContent;
import icecube.daq.performance.common.PowersOfTwo;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Extends the RecordBuffer interface with mutating methods
 */
public interface WritableRecordBuffer extends RecordBuffer
{
    /**
     * Relative write operation.
     * @param data The data to write. Data will be transferred from
     *               the source buffer position to limit.
     * @throws BufferOverflowException Not enough space available in
     *                                 buffer.
     */
    public void put(final ByteBuffer data);

    /**
     * @return The number of bytes available in the buffer.
     */
    public int available();


    /**
     * Extension for buffers that support pruning such as a ring buffer.
     */
    interface Prunable extends WritableRecordBuffer
    {
        public void prune(int count);
    }

}
