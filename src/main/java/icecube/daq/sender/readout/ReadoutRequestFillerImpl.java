package icecube.daq.sender.readout;

import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IPayload;
import icecube.daq.payload.IReadoutRequest;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.PayloadException;
import icecube.daq.payload.impl.DOMHit;
import icecube.daq.payload.impl.DOMHitFactory;
import icecube.daq.payload.impl.UTCTime;
import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.binary.record.pdaq.DaqBufferRecordReader;
import icecube.daq.performance.binary.store.RecordStore;
import icecube.daq.performance.common.BufferContent;
import icecube.daq.sender.SenderCounters;
import icecube.daq.sender.SenderMethods;
import icecube.daq.util.IDOMRegistry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fulfills readout requests against a binary store of hit records.
 *
 * Adapted from code sources:
 *    icecube.daq.sender.Sender
 *    icecube.daq.reqFiller.RequestFiller
 *
 * Adapted to work from a binary data store and simplified to the data
 * types required by StringHub.
 *
 */
public class ReadoutRequestFillerImpl implements ReadoutRequestFiller
{

    /**  The identity of data source. */
    private final ISourceID sourceId;

    /** Dom registry. */
    private final IDOMRegistry domRegistry;

    /** Buffer cache. */
    private final IByteBufferCache bufferCache;

    /** Store of hit records used to satisfy request. */
    private final RecordStore.Ordered cache;

    /** Provides conversion from binary to DOMHit format. */
    RecordConverter converter = new RecordConverter();

    /** diagnostic counters */
    final SenderCounters counters;

    /**
     * Constructor.
     */
    public ReadoutRequestFillerImpl(final ISourceID sourceId,
                                    final IDOMRegistry domRegistry,
                                    final IByteBufferCache bufferCache,
                                    final RecordStore.Ordered cache,
                                    final SenderCounters counters)
    {
        this.sourceId = sourceId;
        this.domRegistry = domRegistry;
        this.bufferCache = bufferCache;
        this.cache = cache;
        this.counters = counters;
    }

    /**
     * Execute a readout request. Readouts that do not result in hits will
     * result in the sentinel EMPTY_READOUT_DATA instance being returned.
     *
     * @param request A readout request.
     * @return The readout data payload or EMPTY_READOUT_DATA.
     * @throws IOException Error accessing the data.
     * @throws PayloadException Format error within the data.
     */
    @Override
    public ByteBuffer fillRequest(final IReadoutRequest request)
            throws IOException, PayloadException
    {

        // This is needed in many places, prefer to extract it once.
        SenderMethods.TimeRange range = SenderMethods.extractTimeRange(request);

        // track latency
        UTCTime utcNow = new UTCTime();
        counters.readoutLatency = utcNow.longValue() - range.startUTC;

        List<DOMHit> domHits = requestBulkCopy(request, range);

        // Note: by convention, StringHub does not issue empty readouts
        if(domHits.size() > 0)
        {
            IPayload resp = formatResponse(request, range, domHits);

            final ByteBuffer msg;
            if(bufferCache != null)
            {
                msg = bufferCache.acquireBuffer(resp.length());
            }
            else
            {
                msg = ByteBuffer.allocate(resp.length());
            }
            resp.writePayload(false, 0, msg);
            return msg;
        }
        else
        {
            return EMPTY_READOUT_DATA;
        }
    }

    /**
     * An implementation that operates on a bulk copy. This leads to a
     * double copy operation of the data (once for extract, again for convert).
     * @param request The readout request.
     * @param timeRange The time range extracted from the request.
     * @return A list of hits matching the request.
     * @throws IOException Error accessing the data.
     * @throws PayloadException Format error within the data.
     */
    private List<DOMHit> requestBulkCopy(final IReadoutRequest request,
                                        final SenderMethods.TimeRange timeRange)
            throws IOException, PayloadException
    {
        // perform a mass extraction (copy) and then iterate the result
        // to convert and filter.

        // data that meets the time interval constraint
        RecordBuffer data = cache.extractRange(timeRange.startUTC,
                                               timeRange.endUTC);

        // convert to DomHits
        List<DOMHit> reified = converter.convertToDomHitList(sourceId, data);

        // filter against request criteria
        List<DOMHit> filtered = new LinkedList<DOMHit>();
        for(DOMHit hit: reified)
        {
            boolean requested =
                    SenderMethods.isRequested(sourceId, request, hit);
            if (requested)
            {
                filtered.add(hit);
            }
        }
        return filtered;
    }

    /**
     * An implementation that defers data copy until the filtering. This saves
     * an extra copy operation.
     *
     * @param request The readout request.
     * @param timeRange The time range extracted from the request.
     * @return A list of hits matching the request.
     * @throws IOException Error accessing the data.
     * @throws PayloadException Format error within the data.
     */
    private List<DOMHit> requestIterativeCopy(final IReadoutRequest request,
                                       final SenderMethods.TimeRange timeRange)
            throws IOException, PayloadException

    {
        // iterate the record store, copying/converting and filtering
        // hit by hit
        Accumulator accumulator = new Accumulator(request);

        cache.forEach(accumulator, timeRange.startUTC, timeRange.endUTC);

        return accumulator.getResult();
    }

    /**
     * Format event data as HitRecordList.
     * @param request The request.
     * @param timeRange The time range extracted from the request.
     * @param data The hit data satisfying the request.
     * @return The event data formatted into a payload.
     * @throws PayloadException Format error within the data.
     * */
    private IPayload formatResponse(final IReadoutRequest request,
                                    final SenderMethods.TimeRange timeRange,
                                    final List<DOMHit> data )
            throws PayloadException
    {
        return SenderMethods.makeDataPayload(request.getUID(),
                timeRange.startUTC, timeRange.endUTC,
                data, sourceId, domRegistry);
    }

    /**
     * An accumulating filter for use in the iterative model of
     * copying hit data.
     */
    private class Accumulator implements Consumer<RecordBuffer>
    {
        private List<DOMHit> accepted = new LinkedList<>();
        final IReadoutRequest request;

        private PayloadException deferredException;
        private boolean inErr;

        private Accumulator(final IReadoutRequest request)
        {
            this.request = request;
        }

        @Override
        public void accept(final RecordBuffer recordBuffer)
        {
            if(inErr)
            {
                return;
            }

            try
            {
                // This performs the required copy of the volatile
                // buffer.
                DOMHit domHit =
                        converter.convertToDomHit(sourceId, recordBuffer);
                boolean requested =
                        SenderMethods.isRequested(sourceId, request, domHit);
                if (requested)
                {
                    accepted.add(domHit);
                }
            }
            catch (PayloadException pe)
            {
                deferredException = pe;
                inErr = true;
            }
        }

        /**
         * Extract accumulated result.
         * @throws PayloadException An exception deferred during accumulation
         *                          indicating a format error within the data.
         */
        public List<DOMHit> getResult() throws PayloadException
        {
            if(inErr)
            {
                throw deferredException;
            }
            else
            {
                return accepted;
            }
        }
    }

    /**
     * Converts a buffer of binary DAQ hit records into a list of DOMHit
     * Objects.
     */
    static class RecordConverter
    {

        /** Parses individual records from a buffer.*/
        private final DaqBufferRecordReader DAQ_RECORD_READER =
                DaqBufferRecordReader.instance;


        /**
         * Convert a buffer of DAQ buffer records into a list of DOMHits. The
         * underlying byte buffers will be copied into the DOMHit instances.
         *
         *
         * @param srcid Populates the source ID in the DomHits.
         * @param buffer A buffer of DAQBufferRecord structures.
         * @return A list of DOMHits.
         * @throws PayloadException The data does not conform to
         *         a DOMHit format.
         */
        public List<DOMHit> convertToDomHitList(final ISourceID srcid,
                                                final ByteBuffer buffer)
                throws PayloadException
        {
            RecordBuffer recordBuffer = RecordBuffers.wrap(buffer,
                    BufferContent.ZERO_TO_CAPACITY);
            return convertToDomHitList(srcid, recordBuffer);
        }

        /**
         * Convert a buffer of DAQ buffer records into a list of DOMHits. The
         * underlying byte buffers will be copied into the DOMHit instances.
         *
         *
         * @param srcid Populates the source ID in the DomHits.
         * @param buffer A buffer of DAQBufferRecord structures.
         * @return A list of DOMHits.
         * @throws PayloadException The data does not conform to
         *         a DOMHit format.
         */
        public List<DOMHit> convertToDomHitList(final ISourceID srcid,
                                                final RecordBuffer buffer)
                throws PayloadException
        {
            List<DOMHit> result = new LinkedList<DOMHit>();

            for(ByteBuffer copy : buffer.eachBuffer(DAQ_RECORD_READER))
            {
                result.add(DOMHitFactory.getHit(srcid, copy, 0));
            }
            return result;
        }


        /**
         * Convert a RecordBuffer containing a DAQ buffer into a DOMHit. The
         * underlying byte buffers will be copied into the DOMHit instances.
         *
         *
         * @param srcid Populates the source ID in the DomHits.
         * @param buffer A buffer containg exactly one DAQBufferRecord
         *               structure.
         * @return A DOMHit representing the buffer.
         * @throws PayloadException The data does not conform to
         *         a DOMHit format.
         */
        public DOMHit convertToDomHit(final ISourceID srcid,
                                      final RecordBuffer buffer)
                throws PayloadException
        {

            byte[] data = buffer.getBytes(0, buffer.getLength());
            return DOMHitFactory.getHit(srcid, ByteBuffer.wrap(data), 0);
        }

    }


}
