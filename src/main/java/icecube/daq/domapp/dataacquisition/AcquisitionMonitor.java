package icecube.daq.domapp.dataacquisition;

import icecube.daq.domapp.dataprocessor.DataStats;
import icecube.daq.util.SimpleMovingAverage;
import org.apache.log4j.Logger;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Collects data acquisition diagnostics for all
 * data collection cycles in a run.
 */
public class AcquisitionMonitor
{

    Logger logger = Logger.getLogger(AcquisitionMonitor.class);

    // Log cycle diagnostics for every cycle
    private static final boolean VERBOSE_CYCLE_LOGGING = Boolean.getBoolean
            ("icecube.daq.domapp.datacollector.verbose-cycle-logging");

    // Log message diagnostics in addition to the cycle diagnostics
    static final boolean INCLUDE_MESSAGE_DETAILS = Boolean.getBoolean
            ("icecube.daq.domapp.datacollector.include-message-details");


    public static final boolean ENABLE_STATS = Boolean.getBoolean(
            "icecube.daq.domapp.datacollector.enableStats");


    private static final DecimalFormat data_fmt = new DecimalFormat("0.###");


    /** Identifies the channel. */
    public final String id;



    long runStartTime; // local system clock
    long runStartNano; // system timer

    long sequence = -1;

    CycleStats currentCycle;

    Queue<CycleStats> history = new LinkedList<CycleStats>();

    SimpleMovingAverage avgCycleDurationMillis =
            new SimpleMovingAverage(10);

    long lastCycleNanos;


    // statistics on data packet size
    // welford's method
    private class WelfordData
    {
        private double data_m_n = 0;
        private double data_s_n = 0;
        private long data_count = 0;
        private long data_max = 0;
        private long data_min = 4092;
        private long data_zero_count = 0;

        private final void trackMessageStats(ByteBuffer msgBuffer)
        {
            // generate some stats as to the average size
            // of hit bytebuffers

            // Compute the mean and variance of data message sizes
            // This is an implementation of welfords method
            // http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance
            int remaining = msgBuffer.remaining();
            if(remaining>0) {
                double m_new = data_m_n + ( remaining - data_m_n) / ( data_count+1.0);
                data_s_n = data_s_n + ( remaining - data_m_n) * ( remaining - m_new);
                data_m_n = m_new;
                data_count++;
                if (remaining>data_max) {
                    data_max = remaining;
                }
                if (remaining<data_min) {
                    data_min = remaining;
                }
            } else {
                data_zero_count++;
            }
        }
    }
    private WelfordData welford = new WelfordData();

    public AcquisitionMonitor(final String id)
    {
        this.id = id;
    }

    final void reportRunStart()
    {
        runStartNano = now();
        runStartTime = System.currentTimeMillis();
        lastCycleNanos = runStartNano;
    }

    public final void initiateCycle()
    {
        if(currentCycle != null)
        {
            throw new Error("Did not complete last cycle.");
        }

        currentCycle = new CycleStats(this, ++sequence, lastCycleNanos);
        currentCycle.reportCycleStart();
    }

    public void reportDataMessageRcv(ByteBuffer buffer)
    {
        currentCycle.reportDataMessageRcv(buffer);

        if(ENABLE_STATS)
        {
            welford.trackMessageStats(buffer);
        }
    }

    public void initiateMessageRead()
    {
        currentCycle.initiateMessageRead();
    }

    public void completeCycle()
    {

        currentCycle.reportCycleStop();

        avgCycleDurationMillis.add(currentCycle.cycleDurationNanos / 1000000);
        lastCycleNanos = currentCycle.cycleStopNano;

        history.add(currentCycle);
        if( history.size() > 10)
        {
            history.remove();
        }


        //debug mode, print details for every cycle
        if(VERBOSE_CYCLE_LOGGING)
        {
            List<StringBuffer> message = currentCycle.verbosePrint();
            for(StringBuffer line : message)
            {
                logger.info(line);
            }
        }

        currentCycle = null;

    }

    final StringBuffer logAverages()
    {
        StringBuffer sb = new StringBuffer(128);
        sb.append("avg-cycle-duration [").append
                (avgCycleDurationMillis.getAverage())
                .append(" ms]");
        return sb;
    }

    final List<StringBuffer> logHistory()
    {
        final List<StringBuffer> lines = new ArrayList<StringBuffer>
                (INCLUDE_MESSAGE_DETAILS ? 250 : 10);
        lines.add(logAverages());

        for(CycleStats cycleStats : history)
        {
            lines.addAll(cycleStats.verbosePrint());
        }

        return lines;
    }

    /**
     * Support printing legacy-style message stats to console.
     */
    final void printWelfordStats(DataStats stats)
    {
        System.out.println(id+":" +
               // " rate: " + config.getPulserRate()+ //not available
                " max: " + welford.data_max+
                " min:" + welford.data_min+
                " mean: " + data_fmt.format(welford.data_m_n)+
                " var: " + data_fmt.format((welford.data_s_n /
                        ( welford.data_count - 1.0 )))+
                " count: " + welford.data_count+
                " zero count: " + welford.data_zero_count+
                " lbm overflows: "+ stats.getNumLBMOverflows()+
                " hit rate: " + stats.getHitRate()+
                " hit rate LC: " + stats.getLCHitRate());
    }

    private final long now()
    {
        return System.nanoTime();
    }

    /**
     * Collects diagnostics pertaining to a single data
     * collection cycle.
     */
    private class CycleStats
    {

        final AcquisitionMonitor parent;

        final long sequence;

        final long lastCycleNanos;

        long cycleStartNano;
        long cycleStopNano;

        long messagesAcquired;
        long bytesAcquired;

        //calculated at cycle completion
        long cycleDurationNanos;
        long cycleGapDurationNanos;

        MessageStats currentMessage;
        private class MessageStats
        {
            final long cycleNumber;
            final long messageNumber;

            long messageReadStartNano;
            long messageRecvNano;
            long messageStopNano;

            int messageByteCount;

            //calculated at message processing completion
            long messageReadDurationNanos;
            long messageDurationNano;

            public MessageStats(long cycleNumber, long messageNumber)
            {
                this.cycleNumber = cycleNumber;
                this.messageNumber = messageNumber;
            }

            final void reportMessageDataReceived(ByteBuffer data)
            {
                messageRecvNano = now();
                messageByteCount = data.remaining();
            }

            final void reportMessageProcessingComplete(final long stopTimeNanos)
            {
                messageStopNano = stopTimeNanos;

                // system timer durations
                messageReadDurationNanos = messageRecvNano - messageReadStartNano;
                messageDurationNano = messageStopNano - messageReadStartNano;
            }

            final StringBuffer verbosePrint()
            {
                StringBuffer info = new StringBuffer(256);
                long durationMillis = messageDurationNano / 1000000;
                long readMillis = messageReadDurationNanos / 1000000;
                long processMillis = (messageDurationNano-messageReadDurationNanos) / 1000000;

                info.append("[").append(parent.id).append
                        ("]")
                        .append(" message [").append(cycleNumber)
                        .append(".").append(messageNumber).append("]")
                        .append(" bytes [").append(messageByteCount)
                        .append(" b]")
                        .append(" duration [").append(durationMillis)
                        .append(" ms]")
                        .append(" read-time [").append(readMillis).append(" ms]")
                        .append(" process-time [").append(processMillis)
                        .append(" ms]");

                return info;
            }
        }
        List<MessageStats> messageStatsList = new LinkedList<MessageStats>();


        CycleStats(final AcquisitionMonitor parent, long sequence,
                   long lastCycleNanos)
        {
            this.parent = parent;
            this.sequence = sequence;

            //NOTE: carrying last cycle timing data forward makes
            //      gap calculations more compact.
            this.lastCycleNanos = lastCycleNanos;
        }


        final void reportCycleStart()
        {
            cycleStartNano = now();
        }

        /**
         * For tracking time spent in driver
         */
        final void initiateMessageRead()
        {
            // Arbitrary, but close. Using one timestamp
            // makes message durations contiguous.
            long newMessageStartTime = now();

            // manage previous message
            final long messageNumber;
            if(currentMessage != null)
            {
                currentMessage.reportMessageProcessingComplete(newMessageStartTime);
                messageStatsList.add(currentMessage);
                messageNumber = currentMessage.messageNumber + 1;
            }
            else
            {
                messageNumber = 0;
            }

            currentMessage = new MessageStats(sequence, messageNumber);
            currentMessage.messageReadStartNano = newMessageStartTime;
        }

        final void reportDataMessageRcv(ByteBuffer data)
        {
            currentMessage.reportMessageDataReceived(data);

            bytesAcquired += currentMessage.messageByteCount;
            messagesAcquired++;
        }

        final void reportCycleStop()
        {
            cycleStopNano = now();

            //manage last message of cycle
            if(currentMessage != null)
            {
                currentMessage.reportMessageProcessingComplete(cycleStopNano);
                messageStatsList.add(currentMessage);
            }

            // system timer durations
            cycleDurationNanos = cycleStopNano - cycleStartNano;
            cycleGapDurationNanos = cycleStartNano -lastCycleNanos;
        }



        final long now()
        {
            return System.nanoTime();
        }

        final List<StringBuffer> verbosePrint()
        {
            List<StringBuffer> lines = new ArrayList<StringBuffer>
                    (AcquisitionMonitor.INCLUDE_MESSAGE_DETAILS ? 25 : 1);

            if(AcquisitionMonitor.INCLUDE_MESSAGE_DETAILS)
            {
                for (CycleStats.MessageStats messageStats: messageStatsList)
                {
                    lines.add(messageStats.verbosePrint());
                }
            }

            StringBuffer info = new StringBuffer(256);
            long durationMillis = cycleDurationNanos / 1000000;
            long millisSinceLastCycle = cycleGapDurationNanos /1000000;

            info.append("[").append(parent.id).append("]")
                    .append(" cycle [").append(sequence).append("]")
                    .append(" messages [").append(messagesAcquired)
                    .append ("]")
                    .append(" bytes [").append(bytesAcquired / 1024)
                    .append(" kb]")
                    .append(" duration [").append(durationMillis)
                    .append(" ms]")
                    .append(" gap [").append(millisSinceLastCycle).append(" ms]");

            lines.add(info);

            return lines;
        }
    }


}


