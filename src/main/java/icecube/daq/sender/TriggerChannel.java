package icecube.daq.sender;

import icecube.daq.io.OutputChannel;
import icecube.daq.monitoring.BatchHLCReporter;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.PayloadException;
import icecube.daq.payload.impl.DOMHit;
import icecube.daq.payload.impl.DOMHitFactory;
import icecube.daq.performance.binary.convert.DomHitConverter;
import icecube.daq.performance.binary.record.pdaq.DomHitRecordReader;
import icecube.daq.util.IDOMRegistry;
import org.apache.log4j.Logger;

import java.nio.ByteBuffer;

/**
 * Holds logic specific to the hub-to-trigger hit stream.
 *
 * The trigger stream is filtered to reduce the number of hits forwarded.
 * In addition the hit data is reformatted into an abbreviated format to
 * reduce the transmission load.
 *
 * This class encapsulates this logic to reduce the footprint of the sender
 * class.
 */
public class TriggerChannel
{
    private static Logger logger = Logger.getLogger(TriggerChannel.class);

    //todo Evaluate the performance benefit of the streamlined implementation
    static enum Mode
    {
        LEGACY,
        PERFORMANCE
    }
    private static Mode mode = Mode.PERFORMANCE;

    /**
     * Decorate an output stream with the trigger filtering logic.
     * @param destination The target channel.
     * @param sourceID The hit source.
     * @param hitCache Buffer cache managing hit buffers.
     * @param domRegistry The dom registry.
     * @param forwardLC0Hits If true, non-lc hits will not be filtered.
     * @return A wrapper around the original channel that provides filtering
     *         and formatting appropriate for the trigger channel.
     */
    static OutputChannel wrap(final OutputChannel destination,
                       final ISourceID sourceID,
                       final IByteBufferCache hitCache,
                       final IDOMRegistry domRegistry,
                       final boolean forwardLC0Hits,
                       final BatchHLCReporter hlcReporter)
    {

        switch (mode)
        {
            case LEGACY:
                return new FilteredOutputTransitional(destination, sourceID,
                        hitCache, domRegistry, forwardLC0Hits, hlcReporter);
            case PERFORMANCE:
                return new FilteredOutput(destination, sourceID, hitCache,
                        domRegistry, forwardLC0Hits, hlcReporter);
            default:
                throw new Error("Unknown mode: " + mode);
        }
    }

    /**
     * Filters the hit output channel based on established rules
     * for sending hits to the trigger component.
     *
     * This implementation is based on the original implementation which
     * utilizes a DOMHit object instantiation to access filter criterion
     * fields and to reformat into an abbreviated hit format.
     */
    static class FilteredOutputTransitional implements OutputChannel
    {
        private final OutputChannel delegate;

        private final ISourceID sourceID;
        private final IByteBufferCache hitCache;
        private final IDOMRegistry domRegistry;
        private final boolean forwardLC0Hits;

        final BatchHLCReporter hlcReporter;


        FilteredOutputTransitional(final OutputChannel delegate,
                                   final ISourceID sourceID,
                                   final IByteBufferCache hitCache,
                                   final IDOMRegistry domRegistry,
                                   final boolean forwardLC0Hits,
                                   final BatchHLCReporter hlcReporter)
        {
            this.delegate = delegate;
            this.sourceID = sourceID;
            this.hitCache = hitCache;
            this.domRegistry = domRegistry;
            this.forwardLC0Hits = forwardLC0Hits;
            this.hlcReporter = hlcReporter;
        }

        @Override
        public void receiveByteBuffer(final ByteBuffer buf)
        {
            try
            {
                DOMHit tinyHit = DOMHitFactory.getHit(sourceID, buf, 0);

                boolean isHLC = tinyHit.getLocalCoincidenceMode() != 0;
                if(isHLC)
                {
                    hlcReporter.reportHLCHit(tinyHit.getDOMID(),
                            tinyHit.getTimestamp());
                }

                if ( forwardLC0Hits || isHLC || tinyHit.getTriggerMode() == 4)
                {
                    ByteBuffer payBuf = tinyHit.getHitBuffer(hitCache,
                            domRegistry);
                    delegate.receiveByteBuffer(payBuf);
                }
            }
            catch (PayloadException pe)
            {
                // todo log and ignore?
                logger.warn("Ignoring PayloadException:", pe);
            }
        }

        @Override
        public void sendLastAndStop()
        {
            delegate.sendLastAndStop();
        }
    }

    /**
     * Filters the hit output channel based on established rules
     * for sending hits to the trigger component.
     *
     * This implementation does not instationate a DOMHit object. The
     * filter criterion fields are accessed directly from the buffer and
     * and a converter object is used to reformat into the abbreviated hit
     * format sent to the trigger.
     */
    static class FilteredOutput implements OutputChannel
    {
        public static final boolean USE_SIMPLER_HITS =
                System.getProperty("useSimpleHits") == null;

        private final OutputChannel delegate;

        private final ISourceID sourceID;
        private final IByteBufferCache hitCache;
        private final IDOMRegistry domRegistry;
        private final boolean forwardLC0Hits;

        final BatchHLCReporter hlcReporter;

        private final DomHitConverter hitConverter;


        FilteredOutput(final OutputChannel delegate,
                       final ISourceID sourceID,
                       final IByteBufferCache hitCache,
                       final IDOMRegistry domRegistry,
                       final boolean forwardLC0Hits,
                       final BatchHLCReporter hlcReporter)
        {
            this.delegate = delegate;
            this.sourceID = sourceID;
            this.hitCache = hitCache;
            this.domRegistry = domRegistry;
            this.forwardLC0Hits = forwardLC0Hits;
            this.hlcReporter = hlcReporter;
            if(USE_SIMPLER_HITS)
            {
                this.hitConverter =
                        new DomHitConverter.SimplerHitConverter(domRegistry);
            }
            else
            {
                this.hitConverter =
                        new DomHitConverter.SimpleHitConverter(sourceID);
            }
        }

        @Override
        public void receiveByteBuffer(final ByteBuffer buf)
        {
            try
            {
                DomHitRecordReader hitReader = DomHitRecordReader.resolve(buf);
                final short lcMode = hitReader.getLCMode(buf);
                final short triggerMode = hitReader.getTriggerMode(buf);
                final boolean isHLC = lcMode != 0;
                if(isHLC)
                {
                    hlcReporter.reportHLCHit(hitReader.getDOMID(buf),
                            hitReader.getUTC(buf));
                }

                if ( forwardLC0Hits || isHLC || triggerMode == 4)
                {
                    final ByteBuffer tinyHit =
                            hitConverter.convert(hitCache, hitReader, buf);

                    delegate.receiveByteBuffer(tinyHit);
                }
            }
            catch (PayloadException pe)
            {
                // todo log and ignore?
                logger.warn("Ignoring PayloadException:", pe);
            }
        }

        @Override
        public void sendLastAndStop()
        {
            delegate.sendLastAndStop();
        }
    }
}
