package icecube.daq.sender;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.common.DAQCmdInterface;
import icecube.daq.io.DAQOutputChannelManager;
import icecube.daq.io.OutputChannel;
import icecube.daq.monitoring.BatchHLCReporter;
import icecube.daq.monitoring.IRunMonitor;
import icecube.daq.monitoring.SenderMXBean;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.binary.record.RecordReader;
import icecube.daq.performance.binary.record.pdaq.DaqBufferRecordReader;
import icecube.daq.performance.binary.store.RecordStore;
import icecube.daq.performance.common.BufferContent;
import icecube.daq.sender.readout.ReadoutRequestFiller;
import icecube.daq.sender.readout.ReadoutRequestFillerImpl;
import icecube.daq.util.IDOMRegistry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Joins the acquisition stream, hit spool, hit stream channel and the
 * readout request channel so that hit data may be dispensed cohesively.
 *
 * Analogous in functionality to icecube.daq.sender.Sender, but utilizing
 * less memory intensive hit storage mechanisms, notably the ability
 * to satisfy hit request
 */
public class NewSender implements BufferConsumer, SenderSubsystem
{

    /** A collection of diagnostic counters. */
    private final SenderCounters counters = new SenderCounters();

    /** Provides the interface for monitoring functionality. */
    private final MonitoringData monitorInterface =
            new MonitoringData(counters);

    /** The source id of the data*/
    private final ISourceID sourceID;

    /** Defines the data type for buffers under management. */
    private final DaqBufferRecordReader DATA_TYPE =
            DaqBufferRecordReader.instance;

    /** Store of Hit Records. */
    private final RecordStore.OrderedWritable spool;

    /** handler for readout requests. */
    private final ReadoutRequestHandler requestHandler;

    /** Required for data verification and formatting. */
    private final IDOMRegistry domRegistry;

    /** Target for forwarding hits. */
    private OutputChannel streamingOutput;

    /** Config option controlling hit channel output. */
    private boolean forwardLC0Hits = false;

    /** Buffer accounting for hit buffers. */
    private final IByteBufferCache hitCache;
    private final IByteBufferCache readoutCache;

    /**
     * A placeholder for the hit channel which is not plumbed until
     * startup.
     */
    private final OutputChannelFuture hitOutFuture;

    /**
     * A placeholder for the readout channel which is not plumbed until
     * startup.
     */
    private final OutputChannelFuture dataOutFuture;

    /**
     * HLS hit reporting is propagated to an independent monitor, the
     * reporting is batched to minimize inter-thread transfer overhead.
     *
     * Hubs vary from 200-1200 HLC hits per second so batch up to 150
     * and the reporting latency to 1 second.
     */
    long ONE_SECOND = 10000000000L;
    BatchHLCReporter hlcReporter =
            new BatchHLCReporter.TimedAutoFlush(150, ONE_SECOND);

    /**
     * Constructor
     */
    public NewSender(final int hubID,
                     final IByteBufferCache hitCache,
                     final IByteBufferCache readoutCache,
                     final IDOMRegistry domRegistry,
                     final RecordStore.OrderedWritable spool)
    {
        this.sourceID = getSourceId(hubID % 1000);
        this.hitCache = hitCache;
        this.readoutCache = readoutCache;
        this.domRegistry = domRegistry;

        this.spool = spool;

        this.hitOutFuture  = new OutputChannelFuture();
        this.dataOutFuture  = new OutputChannelFuture();

        ReadoutRequestFiller filler =
                new ReadoutRequestFillerImpl(sourceID, domRegistry,
                        readoutCache, this.spool, counters);

        this.requestHandler =
                new ReadoutRequestHandler(counters, filler, dataOutFuture);

        this.streamingOutput =
                new FailFastOutputChannel("Sender was not started");
    }

    @Override
    public void consume(final ByteBuffer buf) throws IOException
    {
        final long utc = DATA_TYPE.getUTC(buf);

        if (DATA_TYPE.isEOS(buf))
        {
            streamingOutput.sendLastAndStop();
            spool.closeWrite();

            counters.isEndOfStream = true;
            return;
        }
        else
        {
            counters.numHitsReceived++;
            counters.latestAcquiredTime = utc;

            spool.store(buf);
            buf.rewind();

            streamingOutput.receiveByteBuffer(buf);
        }
    }

    @Override
    public void endOfStream(final long token) throws IOException
    {
       consume(MultiChannelMergeSort.eos(token));
    }

    @Override
    public void setRunMonitor(final IRunMonitor runMonitor)
    {
        this.hlcReporter.setRunMonitor(runMonitor);
    }

    @Override
    public void forwardIsolatedHitsToTrigger()
    {
        forwardLC0Hits = true;
    }

    @Override
    public void setHitOutput(final DAQOutputChannelManager hitOut)
    {
        hitOutFuture.setChannelManager(hitOut);
    }

    @Override
    public void setDataOutput(final DAQOutputChannelManager dataOut)
    {
        dataOutFuture.setChannelManager(dataOut);
    }

    @Override
    public BufferConsumer getHitInput()
    {
        return this;
    }

    @Override
    public RequestHandler getReadoutRequestHandler()
    {
        return requestHandler;
    }

    @Override
    public SenderMXBean getMonitor()
    {
        return monitorInterface;
    }

    @Override
    public void startup()
    {
        //
        // Note: configuration via setters should be final at this point.
        //       The construction of the hit output stack is deferred
        //       to this point.
        //
        this.streamingOutput =  TriggerChannel.wrap(hitOutFuture, sourceID,
                hitCache, domRegistry, forwardLC0Hits, hlcReporter);

        this.requestHandler.startup();
    }

    private static ISourceID getSourceId(int compId)
    {
        final String compName = DAQCmdInterface.DAQ_STRING_HUB;

        return SourceIdRegistry.getISourceIDFromNameAndId(compName, compId);
    }

    /**
     * Exports diagnostic counters.
     */
    private static class MonitoringData implements SenderMXBean
    {
        private final SenderCounters counters;

        private MonitoringData(final SenderCounters counters)
        {
            this.counters = counters;
        }

        @Override
        public int getNumHitsQueued()
        {
            // this implementation does not queue hits.
            return 0;
        }

        @Override
        public long getNumHitsReceived()
        {
            return counters.numHitsReceived;
        }

        @Override
        public long getNumReadoutRequestsQueued()
        {
            long sent = counters.numReadoutsSent;
            long ignored = counters.numOutputsIgnored;
            return counters.numReadoutRequestsReceived - sent - ignored;
        }

        @Override
        public long getNumReadoutRequestsReceived()
        {
            return counters.numReadoutRequestsReceived;
        }

        @Override
        public long getNumReadoutsSent()
        {
            return counters.numReadoutsSent;
        }

        @Override
        public long getReadoutLatency()
        {
            return counters.readoutLatency;
        }

    }


    private static class FailFastOutputChannel implements OutputChannel
    {
        final String errMsg;

        private FailFastOutputChannel(final String errMsg)
        {
            this.errMsg = errMsg;
        }

        @Override
        public void receiveByteBuffer(final ByteBuffer buf)
        {
            throw new Error(errMsg);
        }

        @Override
        public void sendLastAndStop()
        {
            throw new Error(errMsg);
        }
    }


}
