package icecube.daq.performance.binary.convert;

import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.PayloadException;
import icecube.daq.payload.impl.SimpleHit;
import icecube.daq.payload.impl.SimplerHit;
import icecube.daq.performance.binary.record.pdaq.DomHitRecordReader;
import icecube.daq.util.IDOMRegistry;

import java.nio.ByteBuffer;

/**
 * Provides format conversion routines for DOMHit buffers.
 */
public interface DomHitConverter
{
    /**
     * Produce an abbreviated-format hit record from a DomHit.
     * @param cache For buffer accounting.
     * @param recordReader For accessing fields from the DomHit structure.
     * @param buffer A buffer containing a DomHit record.
     * @return The abbreviated hit record.
     */
    public ByteBuffer convert(final IByteBufferCache cache,
                              final DomHitRecordReader recordReader,
                              final ByteBuffer buffer);

    /**
     * Converts DomHitRecordReader format hits into SimpleHit format.
     */
    public class SimpleHitConverter implements DomHitConverter
    {
        /** Source ID is an added field to the converted record. */
        final ISourceID srcID;

        public SimpleHitConverter(final ISourceID srcID)
        {
            this.srcID = srcID;
        }

        /**
         * Produce a SimpleHit record from a DOMHit record format
         *
         * A simple hit record
         * -----------------------------------------------------------------------------
         * | length [uint4]    |  type [uint4]=1   |           utc [uint8]             |
         * -----------------------------------------------------------------------------
         * | trig-type [uint4] | config-id [uint4] |  srcid [uint4] |  mbid [uint8]... |
         * -----------------------------------------------------------------------------
         * | ...               | trig-mode [uint2] |
         * -----------------------------------------
         */
        @Override
        public ByteBuffer convert(final IByteBufferCache cache,
                                  final DomHitRecordReader recordReader,
                                  final ByteBuffer buffer)
        {
            long utc = recordReader.getUTC(buffer);
            short triggerMode = recordReader.getTriggerMode(buffer);
            long domId = recordReader.getDOMId(buffer);
            int configID = 0; //unused
            try
            {
                return SimpleHit.getBuffer(cache, utc, triggerMode, configID,
                        srcID.getSourceID(), domId, triggerMode);
            }
            catch (PayloadException e)
            {
                // really an error in this code path
                throw new Error(e);
            }
        }

    }

    /**
     * Converts DomHitRecordReader format hits into SimplerHit format.
     */
    public class SimplerHitConverter implements DomHitConverter
    {
        /** Required to derive a channel id to add to the converted record. */
        final IDOMRegistry registry;

        public SimplerHitConverter(final IDOMRegistry registry)
        {
            this.registry = registry;
        }

        /**
         * A simpler hit record
         * -----------------------------------------------------------------------------
         * | length [uint4]    |  type [uint4]=1   |           utc [uint8]             |
         * -----------------------------------------------------------------------------
         * | channelID [uint2] | trig-mode [uint2] |
         * -----------------------------------------
         */
        @Override
        public ByteBuffer convert(final IByteBufferCache cache,
                                  final DomHitRecordReader recordReader,
                                  final ByteBuffer buffer)
        {
            final long utc = recordReader.getUTC(buffer);
            final short triggerMode = recordReader.getTriggerMode(buffer);
            final long domId = recordReader.getDOMId(buffer);

            final short channelId = registry.getChannelId(domId);
            try
            {
                return SimplerHit.getBuffer(cache, utc, channelId, triggerMode);
            }
            catch (PayloadException e)
            {
                // really an error in this code path
                throw new Error(e);
            }
        }
    }


}
