package icecube.daq.performance.binary.record.pdaq;

import icecube.daq.payload.PayloadException;
import icecube.daq.payload.impl.DOMHitFactory;
import icecube.daq.performance.binary.buffer.RecordBuffer;

import java.nio.ByteBuffer;

/**
 * Defines derived attributes common to hit records sent by doms.
 *
 * Implementations should be registered by type code via the
 * resolve(int typeId) methods to support dynamic lookup.
 *
 * @see EngineeringHitRecordReader, DeltaCompressedHitRecordReader
 */
public abstract class DomHitRecordReader extends DaqBufferRecordReader
{

    /** Generic reader for extracting type codes. */
    private static final TypeCodeRecordReader TYPE_CODE_RECORD_READER =
            TypeCodeRecordReader.instance;

    /**
     * Resolve concrete implementation by type code.
     * @param buffer A buffer containing dom hit record.
     * @return A DomHitRecordReader implementation specific to the hit format.
     * @throws PayloadException An unrecognized type.
     */
    public static DomHitRecordReader resolve(final ByteBuffer buffer)
            throws PayloadException
    {
        final int typeId = TYPE_CODE_RECORD_READER.getTypeId(buffer);
        return resolve(typeId);
    }
    /**
     * Resolve concrete implementation by type code.
     * @param buffer A buffer containing dom hit record.
     * @param offset The offset of the hit record within the buffer.
     * @return A DomHitRecordReader implementation specific to the hit format.
     * @throws PayloadException An unrecognized type.
     */
    public static DomHitRecordReader resolve(final ByteBuffer buffer,
                                             final int offset)
            throws PayloadException
    {
        final int typeId = TYPE_CODE_RECORD_READER.getTypeId(buffer, offset);
        return resolve(typeId);
    }
    /**
     * Resolve concrete implementation by type code.
     * @param buffer A record buffer containing dom hit record.
     * @param offset The offset of the hit record within the buffer.
     * @return A DomHitRecordReader implementation specific to the hit format.
     * @throws PayloadException An unrecognized type.
     */
    public static DomHitRecordReader resolve(final RecordBuffer buffer,
                                             final int offset)
            throws PayloadException
    {
        final int typeId = TYPE_CODE_RECORD_READER.getTypeId(buffer, offset);
        return resolve(typeId);
    }
    /**
     * Resolve concrete implementation by type code.
     * @param typeId The hit format type.
     * @return A DomHitRecordReader implementation specific to the hit format.
     * @throws PayloadException An unrecognized type.
     */
    public static DomHitRecordReader resolve(final int typeId)
            throws PayloadException
    {
        switch (typeId)
        {
            case DOMHitFactory.TYPE_ENG_HIT:
                return EngineeringHitRecordReader.instance;
            case DOMHitFactory.TYPE_DELTA_HIT:
            case DOMHitFactory.TYPE_DELTA_PAYLOAD:
                return DeltaCompressedHitRecordReader.instance;
            default:
                throw new PayloadException("Unsuported type " + typeId);
        }
    }

    public static interface DOMHitConsumer
    {
        public void consume(DomHitRecordReader recordReader,
                            RecordBuffer recordBuffer);
    }

    /**
     * Convenience method for iterating a dom hit buffer, resolving
     * the specific hit type each record. The iteration requests data
     * views which must not be accessed outside the callback scope.
     *
     * @param consumer The target of the iteration.
     * @param recordBuffer A record buffer containing Dom Hit records.
     * @throws PayloadException An unknown hit type encountered.
     */
    public static void viewIterate(DOMHitConsumer consumer,
                                   RecordBuffer recordBuffer)
            throws PayloadException
    {
        for(RecordBuffer record :
                recordBuffer.eachRecord(TYPE_CODE_RECORD_READER))
        {
            DomHitRecordReader domHitRecordReader =
                    DomHitRecordReader.resolve(
                            TYPE_CODE_RECORD_READER.getTypeId(record, 0));

            consumer.consume(domHitRecordReader, recordBuffer);
        }
    }

    /**
     * Convenience method for iterating a dom hit buffer, resolving
     * the specific hit type each record. The iteration requests data
     * copies which may be held and accessed outside the scope of the
     * callback.
     *
     * @param consumer The target of the iteration.
     * @param recordBuffer A record buffer containing Dom Hit records.
     * @throws PayloadException An unknown hit type encountered.
     */
    public static void copyIterate(DOMHitConsumer consumer,
                                   RecordBuffer recordBuffer)
            throws PayloadException
    {
        for(RecordBuffer record :
                recordBuffer.eachCopy(TYPE_CODE_RECORD_READER))
        {
            DomHitRecordReader domHitRecordReader =
                    DomHitRecordReader.resolve(
                            TYPE_CODE_RECORD_READER.getTypeId(record, 0));

            consumer.consume(domHitRecordReader, recordBuffer);
        }
    }


    public abstract short getLCMode(ByteBuffer buffer);
    public abstract short getLCMode(ByteBuffer buffer, int offset);
    public abstract short getLCMode(RecordBuffer buffer, int offset);

    public abstract short getTriggerMode(final ByteBuffer buffer);
    public abstract short getTriggerMode(ByteBuffer buffer, int offset);
    public abstract short getTriggerMode(RecordBuffer buffer, int offset);

}
