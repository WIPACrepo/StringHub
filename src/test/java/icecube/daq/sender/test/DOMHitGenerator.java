package icecube.daq.sender.test;

import cern.jet.random.Exponential;
import cern.jet.random.engine.MersenneTwister;
import cern.jet.random.engine.RandomEngine;

import icecube.daq.bindery.StreamBinder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DOMHitGenerator
    extends Thread
{
    private static Log logger = LogFactory.getLog(DOMHitGenerator.class);
    private static RandomEngine engine =
        new MersenneTwister(new java.util.Date());

    private Pipe.SinkChannel sink;
    private double time;
    private long id;
    private Exponential rv;
    private double max_time;

    public DOMHitGenerator(long id, double max_time, double rate)
    {
        this(id, null, max_time, rate);
    }

    public DOMHitGenerator(long id, Pipe.SinkChannel sink, double max_time,
                           double rate)
    {
        this.sink = sink;
        this.id   = id;
        rv = new Exponential(rate, engine);
        time = 0;
        this.max_time = max_time;

        setName("DOMHitGenerator#" + id);
    }

    void fireEvent()
        throws IOException, InterruptedException
    {
        ByteBuffer buf = ByteBuffer.allocate(4092);
        double startTime = time;

        final int dataLen = 48;
        while (hasMoreData() && buf.remaining() >= dataLen) {
            final long domClock = getNextDomClock();

            buf.putInt(48).putInt(601).putLong(id);
            buf.putInt(0).putInt(0).putLong(domClock);

            putHit(buf);
        }

        ByteBuffer eos = StreamBinder.endOfStream();
        if (!hasMoreData() && buf.remaining() >= eos.limit()) {
            buf.put(eos);
            eos.clear();
        }   

        final double delay = time - startTime;
        logger.debug("Delay = " + delay + " time = " + time);
        Thread.sleep((long) (1000.0 * delay));
        buf.flip();
        int nw = sink.write(buf);
        logger.debug("Wrote " + nw + " bytes.");
    }

    private long getNextDomClock()
    {
        time += rv.nextDouble();

        return (long) (time * 1.0E+10);
    }

    public boolean hasMoreData()
    {
        return (time < max_time);
    }

    private void putDomClock(ByteBuffer bb, long domClock)
    {
        int shift = 40;
        for (int i = 0; i < 6; i++) {
            bb.put((byte) ((int) (domClock >> shift) & 0xff));
            shift -= 8;
        }
    }

    public boolean putHit(ByteBuffer bb)
    {
        if (!hasMoreData()) {
            return false;
        }

        final long domClock = getNextDomClock();

        logger.debug("New generator hit at time = " + time +
                     " domclock = " + domClock);

        short len = 16;
        bb.putShort(len);

        final short fmt = 2;
        bb.putShort(fmt);

        final byte atwdChip = 1;
        bb.put(atwdChip);

        final byte numFADCs = 0;
        bb.put(numFADCs);

        final byte atwdByte0 = 0;
        bb.put(atwdByte0);

        final byte atwdByte1 = 0;
        bb.put(atwdByte1);

        final byte trigFlags = 1;
        bb.put(trigFlags);

        // filler
        bb.put((byte) 0);

        putDomClock(bb, domClock);
        return true;
    }

    public void run()
    {
        if (sink != null) {
            while (hasMoreData()) {
                try {
                    fireEvent();
                    logger.debug("fired event - current time is " + time);
                } catch (IOException iox) {
                    iox.printStackTrace();
                } catch (InterruptedException intx) {
                    intx.printStackTrace();
                }
            }

            try {
                int nw = sink.write(StreamBinder.endOfStream());
                logger.info("DOMHitGenerator#" + id + " end-of-stream - " +
                            nw + " bytes.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
