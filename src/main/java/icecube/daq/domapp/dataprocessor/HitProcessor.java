package icecube.daq.domapp.dataprocessor;

import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.domapp.DOMAppUtil;
import icecube.daq.domapp.RunLevel;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Processes hit message payloads (in DOMApp payload format), passing
 * each hit to the dispatcher.
 *
 * Note:
 * Initial implementation was taken from DataCollector.java revision 15482.
 */
class HitProcessor implements DataProcessor.StreamProcessor
{

    private Logger logger =  Logger.getLogger(HitProcessor.class);

    private final long mbid;

    private final boolean pedistalSubtract;
    private final boolean atwdChargeStamp;

    private final DataDispatcher dispatcher;


    HitProcessor(final long mbid,
                 final boolean pedistalSubtract,
                 final boolean atwdChargeStamp,
                 final DataDispatcher dispatcher)
    {
        this.mbid = mbid;
        this.pedistalSubtract = pedistalSubtract;
        this.atwdChargeStamp = atwdChargeStamp;
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

        // an optimization when there is no consumer.
        if (!dispatcher.hasConsumer()) return;

        int buffer_limit = in.limit();

        // create records from aggregate message data returned from DOMApp
        while (in.remaining() > 0)
        {
            int pos = in.position();
            short len = in.getShort(pos);
            short fmt = in.getShort(pos+2);

            int atwdChip;
            long domClock;
            ByteBuffer outputBuffer;

            switch (fmt)
            {
                case 0x01: /* Early engineering hit data format (pre-pDAQ, in fact) */
                case 0x02: /* Later engineering hit data format */
                    atwdChip = in.get(pos+4) & 1;
                    domClock = DOMAppUtil.decodeClock6B(in, pos + 10);
                    in.limit(pos + len);
                    outputBuffer = ByteBuffer.allocate(len + 32);
                    outputBuffer.putInt(len + 32);
                    outputBuffer.putInt(DataProcessor.MAGIC_ENGINEERING_HIT_FMTID);
                    outputBuffer.putLong(mbid);
                    outputBuffer.putLong(0L);
                    outputBuffer.putLong(domClock);
                    outputBuffer.put(in).flip();
                    in.limit(buffer_limit);

                    ////////
                    //
                    // DO the A/B stuff¡
                    //
                    ////////
                    dispatcher.dispatchHitBuffer(atwdChip, outputBuffer, counters);

                    break;

                case 0x90: /* SLC or other compressed hit format */
                    // get unsigned MSB for clock
                    int clkMSB = in.getShort(pos+4) & 0xffff;
                    ByteOrder lastOrder = in.order();
                    in.order(ByteOrder.LITTLE_ENDIAN);
                    in.position(pos + 8);
                    while (in.remaining() > 0)
                    {
                        pos = in.position();
                        int word1 = in.getInt();
                        int word2 = in.getInt();
                        int word3 = in.getInt();
                        int hitSize = word1 & 0x7ff;
                        atwdChip = (word1 >> 11) & 1;
                        domClock = (((long) clkMSB) << 32) | (((long) word2) & 0xffffffffL);
                        if (logger.isDebugEnabled())
                        {
                            int trigMask = (word1 >> 18) & 0x1fff;
                            logger.debug("DELTA HIT - CLK: " + domClock + " TRIG: " + Integer.toHexString(trigMask));
                        }
                        short version = 0x02;
                        short fpq = (short) (
                                (pedistalSubtract ? 1 : 0) |
                                        (atwdChargeStamp ? 2 : 0)
                        );
                        in.limit(pos + hitSize);
                        outputBuffer = ByteBuffer.allocate(hitSize + 42);
                        // Standard Header
                        outputBuffer.putInt(hitSize + 42);
                        outputBuffer.putInt(DataProcessor.MAGIC_COMPRESSED_HIT_FMTID);
                        outputBuffer.putLong(mbid); // +8
                        outputBuffer.putLong(0L);    // +16
                        outputBuffer.putLong(domClock);         // +24
                        // Compressed hit extra info
                        // This is the 'byte order' word
                        outputBuffer.putShort((short) 1);  // +32
                        outputBuffer.putShort(version);    // +34
                        outputBuffer.putShort(fpq);        // +36
                        outputBuffer.putLong(domClock);    // +38
                        outputBuffer.putInt(word1);        // +46
                        outputBuffer.putInt(word3);        // +50
                        outputBuffer.put(in).flip();
                        in.limit(buffer_limit);
                        // DO the A/B stuff
                        dispatcher.dispatchHitBuffer(atwdChip, outputBuffer, counters);
                    }
                    // Restore previous byte order
                    in.order(lastOrder);
                    break;

                default:
                    logger.error("Unknown DOMApp format ID: " + fmt);
                    in.position(pos + len);
            }
        }
    }

    @Override
    public void eos() throws IOException
    {
        dispatcher.eos(MultiChannelMergeSort.eos(mbid));
    }

}
