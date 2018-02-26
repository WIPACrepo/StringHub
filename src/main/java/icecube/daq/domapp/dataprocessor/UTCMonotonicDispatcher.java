package icecube.daq.domapp.dataprocessor;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.rapcal.RAPCal;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * Wraps the UTC dispatcher, delaying time reconstruction util the rapcal
 * instance contains tcal measurements that bound the dom clock timestamp.
 * Under normal conditions of a monotonic DOM clock, this will result
 * in a dispatch stream with monotonically increasing UTC timestamps.
 *
 * Note: UTC ordering is still checked and enforced for ordering violations.
 *       Data with out-of-order utc timestamps will be dropped.  This is
 *       implemented in the base UTCDispatcher.
 *
 * todo: Once monotonic ordering is stable, UTCDispatcher code can be moved
 *       here and class extension can be eliminated.
 */
class UTCMonotonicDispatcher extends UTCDispatcher
{

    /**
     * The maximum number of records that can be held waiting before an
     * error is generated.
     */
    static final int MAX_DEFERRED_RECORDS =
    Integer.getInteger("icecube.daq.domapp.dataprocessor.max-deferred-records",
            5000);

    private static final Logger logger =
        Logger.getLogger(UTCMonotonicDispatcher.class);

    /**
     * If the "gate" is closed, defer buffers until we reach the maximum
     */
    private boolean gateClosed;

    /**
     * Structure to hold deferred data.
     */
    private static class DeferredDataRecord
    {
        final ByteBuffer data;
        final DispatchCallback callback;

        private DeferredDataRecord(final ByteBuffer data,
                                   final DispatchCallback callback)
        {
            this.data = data;
            this.callback = callback;
        }
    }

    /** Holds data for deferred dispatch. */
    private LinkedList<DeferredDataRecord> deferred =
            new LinkedList<DeferredDataRecord>();


    /**
     * Constructor.
     *
     * @param target The consumer of dispatched buffers.
     * @param type Identifies the type of data in the stream.
     * @param rapcal The rapcal instance servicing the stream.
     * @param mbid Mainboard ID for this DOM
     */
    public UTCMonotonicDispatcher(final BufferConsumer target,
                                  final DataProcessor.StreamType type,
                                  final RAPCal rapcal,
                                  final long mbid)
    {
        this(target, type, rapcal, mbid, false);
    }


    /**
     * Constructor.
     *
     * @param target The consumer of dispatched buffers.
     * @param type Identifies the type of data in the stream.
     * @param rapcal The rapcal instance servicing the stream.
     * @param mbid Mainboard ID for this DOM
     * @param gateClosed if <tt>true</tt>, do not dispatch records until either
     *                   clearDeferred() is called or the maximum number of
     *                   records have been deferred
     */
    public UTCMonotonicDispatcher(final BufferConsumer target,
                                  final DataProcessor.StreamType type,
                                  final RAPCal rapcal,
                                  final long mbid,
                                  final boolean gateClosed)
    {
        super(target, type, rapcal, 0, mbid);

        this.gateClosed = gateClosed;
    }


    /**
     * Clear all deferred records and allow future records to be dispatched
     */
    public void clearDeferred()
    {
        if (gateClosed)
        {
            deferred.clear();
            gateClosed = false;
        }
    }


    @Override
    public void eos(final ByteBuffer eos) throws DataProcessorError
    {
        // At EOS, deferred data will be dispatched regardless
        // of the rapcal bounding.
        try
        {
            while(deferred.size() > 0)
            {
                DeferredDataRecord record = deferred.removeFirst();
                super.dispatchBuffer(record.data, record.callback);
            }
        }
        finally
        {
            super.eos(eos);
        }

    }


    /**
     * Adds a buffer on top of standard UTC dispatching which holds records
     * until rapcal has a bounding tcal.
     */
    @Override
    public void dispatchBuffer(final ByteBuffer buf,
                               final DispatchCallback callback)
            throws DataProcessorError
    {
        deferred.add(new DeferredDataRecord(buf, callback));

        if(gateClosed)
        {
            if(deferred.size() < MAX_DEFERRED_RECORDS)
            {
                return;
            }

            // if we've deferred the maximum amount,
            //  stop waiting and release everything
            gateClosed = false;
            logger.error("Giving up on run start message for " + mbid +
                         "; releasing all deferred records");
        }
        else if(deferred.size() >= MAX_DEFERRED_RECORDS)
        {
            // indicates an unusual  problem with rapcal updates,
            // capture debugging details
            long firstDOMClk = deferred.peekFirst().data.getLong(24);
            long lastDOMClk = deferred.peekLast().data.getLong(24);
            String msg = String.format("Over limit of %d records waiting for" +
                    " rapcal DOM clock range [%d, %d], mbid: %12x",
                    deferred.size(), firstDOMClk, lastDOMClk, mbid);
            throw new DataProcessorError(msg);
        }

        // release any deferred records which can be assigned valid times
        while(true)
        {
            DeferredDataRecord record = deferred.peekFirst();
            if(record != null && (rapcal.laterThan(record.data.getLong(24))) )
            {
                record = deferred.removeFirst();
                super.dispatchBuffer(record.data, record.callback);
            }
            else
            {
                return;
            }
        }
    }

    /**
     * Accessor for the depth of the deferred record count;
     * @return The number of deferred records.
     */
    int getDeferredRecordCount()
    {
        return deferred.size();
    }

}
