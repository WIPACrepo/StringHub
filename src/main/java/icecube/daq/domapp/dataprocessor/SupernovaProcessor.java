package icecube.daq.domapp.dataprocessor;

import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.domapp.RunLevel;
import icecube.daq.domapp.SupernovaPacket;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Processes supernova message payloads (in DOMApp payload format), passing
 * each message to the dispatcher.
 *
 * Note:
 * Initial implementation was taken from DataCollector.java revision 15482.
 */
class SupernovaProcessor implements DataProcessor.StreamProcessor
{
    private Logger logger =  Logger.getLogger(HitProcessor.class);

    private final long mbid;

    private final DataDispatcher dispatcher;

    /** Timestamps used for gap and overlap detection. */
    private long nextSupernovaDomClock;
    private int numSupernovaGaps;


    SupernovaProcessor(final long mbid, final DataDispatcher dispatcher)
    {
        this.mbid = mbid;
        this.dispatcher = dispatcher;
    }

    @Override
    public void runLevel(final RunLevel runLevel)
    {
        //noop
    }

    @Override
    public void process(final ByteBuffer in, final DataStats counters)
            throws IOException
    {
        while (in.remaining() > 0)
        {
            SupernovaPacket spkt = SupernovaPacket.createFromBuffer(in);
            // Check for gaps in SN data
            if ((nextSupernovaDomClock != 0L) && (spkt.getClock() != nextSupernovaDomClock) && numSupernovaGaps++ < 100)
            {
                long gapMillis = ((spkt.getClock() - nextSupernovaDomClock) *
                        25) / 1000000;
                logger.warn("Gap or overlap in SN rec: next = " + nextSupernovaDomClock
                        + " - current = " + spkt.getClock() + ", gap = " + gapMillis + " ms");
            }

            nextSupernovaDomClock = spkt.getClock() + (spkt.getScalers().length << 16);
            counters.reportSupernova();

            // an optimization when there is no consumer.
            if (dispatcher.hasConsumer())
            {
                int len = spkt.getLength() + 32;
                ByteBuffer snBuf = ByteBuffer.allocate(len);
                snBuf.putInt(len).putInt(DataProcessor.MAGIC_SUPERNOVA_FMTID).putLong(mbid).putLong(0L);
                snBuf.putLong(spkt.getClock()).put(spkt.getBuffer());
                snBuf.flip();
                dispatcher.dispatchBuffer(snBuf);
            }
        }
    }

    @Override
    public void eos() throws IOException
    {
        dispatcher.eos(MultiChannelMergeSort.eos(mbid));
    }
}
