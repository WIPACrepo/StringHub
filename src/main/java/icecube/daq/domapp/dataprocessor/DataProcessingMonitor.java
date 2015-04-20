package icecube.daq.domapp.dataprocessor;

import icecube.daq.util.SimpleMovingAverage;
import org.apache.log4j.Logger;

import java.nio.ByteBuffer;

/**
 * A development-mode class that decorates DataStats with
 * verbose logging of hit processing performance.
 */
public class DataProcessingMonitor extends DataStats
{

    Logger logger = Logger.getLogger(DataProcessingMonitor.class);

    private long sequence;
    private ProcessingInterval current;


    @Override
    protected void reportProcessingStart(
            final DataProcessor.StreamType streamType,
            final ByteBuffer data)
    {
        super.reportProcessingStart(streamType, data);

        if(streamType == DataProcessor.StreamType.HIT)
        {
            current = new ProcessingInterval(sequence, data.remaining(), now());
            sequence++;
        }
    }

    @Override
    protected void reportProcessingEnd(
            final DataProcessor.StreamType streamType)
    {
        super.reportProcessingEnd(streamType);

        if(streamType == DataProcessor.StreamType.HIT)
        {
            current.reportStop();
            logger.info(current.verbose());
        }
    }

    @Override
    protected void reportHit(final boolean isLC,
                             final long domclk,
                             final long utc)
    {
        super.reportHit(isLC,domclk, utc);

        current.reportHit(domclk, utc);
    }

    private long now()
    {
        return System.nanoTime();
    }


    private class ProcessingInterval
    {
        final long sequence;
        final int size;

        final long intervalStartNanos;
        long intervalStopNanos;

        int intervalHitCount;
        long firstHitUTC = -1;
        long lastHitUTC = -1;

        long firstHitDOMClk = -1;
        long lastHitDOMClk = -1;

        long intervalDurationNanos;
        long intervalDataSpanDOMClkNanos;
        long intervalDataSpanUTCNanos;

        SimpleMovingAverage nanosPerHit = new SimpleMovingAverage(100);


        private ProcessingInterval(final long sequence,
                                   final int size,
                                   final long intervalStartNanos)
        {
            this.sequence = sequence;
            this.size = size;
            this.intervalStartNanos = intervalStartNanos;
        }

        void reportHit(long domclk, long utc)
        {
            intervalHitCount++;

            if(firstHitDOMClk < 0)
            {
                firstHitDOMClk = domclk;
            }

            if(firstHitUTC < 0)
            {
                firstHitUTC = utc;
            }

            lastHitDOMClk = domclk;
            lastHitUTC = utc;
        }

        void reportStop()
        {
            intervalStopNanos = now();

            intervalDurationNanos = intervalStopNanos - intervalStartNanos;
            intervalDataSpanDOMClkNanos = (lastHitDOMClk-firstHitDOMClk) * 25;
            intervalDataSpanUTCNanos = (lastHitUTC-firstHitUTC) / 10;

            if(intervalHitCount > 0)
            {
                nanosPerHit.add(intervalDurationNanos/intervalHitCount);
            }

        }

        String verbose()
        {
            StringBuilder info = new StringBuilder(256);
            long durationMillis = intervalDurationNanos / 1000000;
            long dataMillis = intervalDataSpanDOMClkNanos / 1000000;
            //long dataMillis = intervalDataSpanUTCNanos / 1000000;

            long avgNanoPerHit = (long)nanosPerHit.getAverage();

            info.append("[").append("X").append("X").append("X").append
                    ("]")
                    .append(" message [").append(sequence).append("]")
                    .append(" bytes [").append(size).append("]")
                    .append(" hits [").append(intervalHitCount).append("]")
                    .append(" duration [").append(durationMillis)
                    .append(" ms]")
                    .append(" data-span [").append(dataMillis)
                    .append(" ms]")
                    .append(" avg-nano-per-hit [").append(avgNanoPerHit)
                    .append(" ns]");

            return info.toString();

        }

    }


}
