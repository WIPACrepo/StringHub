package icecube.daq.performance.binary.record;

import icecube.daq.performance.binary.buffer.RecordBuffer;

import java.nio.ByteBuffer;

/**
 * Base interface for reading binary records from a buffer.
 *
 * A record reader is a stateless object that provides member
 * accessor functions that act against a binary store of records.
 *
 * The intention is to allow for the management of a large number
 * of records without a allocating a large nuber of containment
 * objects.
 *
 * Record readers can be thought of as a static class hierarchy. Java
 * does not support static inheritance. Implementing classes should be
 * modeled as a singleton to reinforce this design concept.
 */
public interface RecordReader
{
    // utilizing ByteBuffer
    public int getLength(ByteBuffer buffer);
    public int getLength(ByteBuffer buffer, int offset);

    // utilizing RecordBuffer
    public int getLength(RecordBuffer buffer, int offset);


    /**
     * Generic access to a long field of a record in a buffer.
     */
    interface LongField
    {
        long value(ByteBuffer buffer, int offset);

        long value(RecordBuffer buffer, int offset);
    }
}
