package icecube.daq.domapp.dataprocessor;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.domapp.AtwdChipSelect;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.rapcal.RAPCal;
import org.apache.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * Extends the UTCDispatcher by implementing the hit-specific dispatching
 * method.
 *
 * Adds A/B ordering to stream.
 * Maintains hit rate data counters.
 *
 * Note:
 * Initial implementation was taken from DataCollector.java revision 15482.
 */
public class UTCHitDispatcher extends UTCDispatcher
{
    private final HitBufferAB abBuffer;


    /**
     * The waitForRAPCal flag if true will force the dispatcher
     * to only output DOM timestamped objects when a RAPCal before and
     * a RAPCal after the object time have been registered.  This may
     * give some improvement to the reconstructed UTC time because
     * interpolation instead of extrapolation is used.
     */
    private final boolean       waitForRAPCal = Boolean.getBoolean(
            "icecube.daq.domapp.datacollector.waitForRAPCal");



    public UTCHitDispatcher(final BufferConsumer target,
                            DOMConfiguration config,
                            final RAPCal rapcal)
    {
        super(target, DataProcessor.StreamType.HIT, rapcal);
        abBuffer = new HitBufferAB(config.getAtwdChipSelect());
    }

    @Override
    public void dispatchHitBuffer(final int atwdChip, final ByteBuffer hitBuf,
                           final DataStats counters)
            throws DataProcessorError
    {
        if (atwdChip == 0)
            abBuffer.pushA(hitBuf);
        else
            abBuffer.pushB(hitBuf);
        while (true)
        {
            ByteBuffer buffer = abBuffer.pop();
            if (buffer == null) return;
            final long domclk = buffer.getLong(24);

            //todo, Consider moving HLC/SLC detection to to processor
            //      object and pass argument here. This would
            //      avoid the double-dip into the data format.
            //
            // Collect HLC / SLC hit statistics ...
            final int formatID = hitBuf.getInt(4);
            final boolean isLCHit;
            switch (formatID)
            {
                case DataProcessor.MAGIC_COMPRESSED_HIT_FMTID:
                    int flagsLC = (hitBuf.getInt(46) & 0x30000) >> 16;
                    isLCHit = (flagsLC != 0);
                    break;
                case DataProcessor.MAGIC_ENGINEERING_HIT_FMTID:
                    isLCHit = false;
                    break;
                default:
                    throw new DataProcessorError("Unrecognized hit format: [" +
                            formatID + "]");
            }

            //NOTE: The sole purpose of the callback is to track the hit
            //      rate in utc time.  It is implemented as a callback to
            //      support deferred dispatching.
            super.dispatchBuffer(buffer, new DispatchCallback()
            {
                @Override
                public void wasDispatched(final long utc)
                {
                    counters.reportHit(isLCHit, domclk, utc);
                }
            });

        }
    }

    /**
     * A helper class to deal with the now-less-than-trivial
     * hit buffering which circumvents the hit out-of-order
     * issues.
     *
     * Also Feb-2008 added ability to buffer hits waiting for RAPCal
     *
     * @author kael
     *
     */
    class HitBufferAB
    {
        Logger logger = Logger.getLogger(HitBufferAB.class);

        private final LinkedList<ByteBuffer> alist, blist;
        private final AtwdChipSelect atwdChipSelect;

        HitBufferAB(final AtwdChipSelect atwdChipSelect)
        {
            this.atwdChipSelect = atwdChipSelect;
            alist = new LinkedList<ByteBuffer>();
            blist = new LinkedList<ByteBuffer>();
        }

        void pushA(ByteBuffer buf)
        {
            alist.addLast(buf);
        }

        void pushB(ByteBuffer buf)
        {
            blist.addLast(buf);
        }

        private ByteBuffer popA()
        {
            return alist.removeFirst();
        }

        private ByteBuffer popB()
        {
            return blist.removeFirst();
        }

        ByteBuffer pop()
        {
            /*
             * Handle the special cases where only one ATWD is activated
             * presumably because of broken hardware.
             */
            if (atwdChipSelect == AtwdChipSelect.ATWD_A)
            {
                if (alist.isEmpty()) return null;
                return popA();
            }
            if (atwdChipSelect == AtwdChipSelect.ATWD_B)
            {
                if (blist.isEmpty()) return null;
                return popB();
            }
            if (alist.isEmpty() || blist.isEmpty()) return null;
            long aclk = alist.getFirst().getLong(24);
            long bclk = blist.getFirst().getLong(24);
            if (aclk < bclk)
            {
                if (!waitForRAPCal || rapcal.laterThan(aclk))
                {
                    return popA();
                }
                else
                {
                    if (logger.isDebugEnabled())
                    {
                        logger.debug("Holding back A hit at " + aclk);
                    }
                    return null;
                }
            }
            else if (!waitForRAPCal || rapcal.laterThan(bclk))
            {
                return popB();
            }
            if (logger.isDebugEnabled())
            {
                logger.debug("Holding back B hit at " + bclk);
            }
            return null;
        }
    }


}

