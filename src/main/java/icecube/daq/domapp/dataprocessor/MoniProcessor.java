package icecube.daq.domapp.dataprocessor;

import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.domapp.AsciiMonitorRecord;
import icecube.daq.domapp.MonitorRecord;
import icecube.daq.domapp.MonitorRecordFactory;
import icecube.daq.domapp.RunLevel;
import org.apache.log4j.Logger;

import java.nio.ByteBuffer;

/**
 * Processes moni message payloads (in DOMApp payload format), passing
 * each message to the dispatcher.
 *
 * Note:
 * Initial implementation was taken from DataCollector.java revision 15482.
 */
class MoniProcessor implements DataProcessor.StreamProcessor
{
    private Logger logger =  Logger.getLogger(HitProcessor.class);

    private final long mbid;

    private final DataDispatcher dispatcher;


    MoniProcessor(final long mbid, final DataDispatcher dispatcher)
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
            throws DataProcessorError
    {
        // an optimization when there is no consumer.
        if (!dispatcher.hasConsumer()) return;

        while (in.remaining() > 0)
        {
            MonitorRecord monitor = MonitorRecordFactory.createFromBuffer(in);
            if (monitor instanceof AsciiMonitorRecord)
            {
                String moniMsg = monitor.toString();
                if (moniMsg.contains("LBM OVERFLOW")) {
                    String msg = String.format("LBM Overflow ["
                            + moniMsg + "] on %12x", mbid);
                    logger.error(msg);
                    counters.reportLBMOverflow();
                } else if (logger.isDebugEnabled()) {
                    logger.debug(moniMsg);
                }
            }
            counters.reportMoni();
            ByteBuffer moniBuffer = ByteBuffer.allocate(monitor.getLength()+32);
            moniBuffer.putInt(monitor.getLength()+32);
            moniBuffer.putInt(DataProcessor.MAGIC_MONITOR_FMTID);
            moniBuffer.putLong(mbid);
            moniBuffer.putLong(0L);
            moniBuffer.putLong(monitor.getClock());
            moniBuffer.put(monitor.getBuffer());
            moniBuffer.flip();
            dispatcher.dispatchBuffer(moniBuffer);
        }
    }

    @Override
    public void eos() throws DataProcessorError
    {
        dispatcher.eos(MultiChannelMergeSort.eos(mbid));
    }

}
