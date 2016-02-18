package icecube.daq.domapp.dataprocessor;

import icecube.daq.domapp.RunLevel;
import icecube.daq.livemoni.LiveTCalMoni;
import icecube.daq.rapcal.RAPCal;
import icecube.daq.util.UTC;

import java.nio.ByteBuffer;

/**
 * Implements the main data processor data flow.
 *
 * Methods execute synchronously on the calling thread. Additional
 * DataProcessor implementations wrap this implementation to realize
 * multi-threading behaviors.
 */
public class SynchronousDataProcessor implements DataProcessor
{

    /** The processing streams for ech data type. */
    private final StreamProcessor hitProcessor;
    private final StreamProcessor snProcessor;
    private final StreamProcessor moniProcessor;
    private final StreamProcessor tcalProcessor;

    /** The RAPCal instance used throughout the processing stack. */
    private final RAPCal rapcal;

    /** Maintains data counters that provide visibility into progress. */
    private final DataStats dataStats;


    /**
     * Constructor.
     *
     * @param hitProcessor The target for hit messages.
     * @param snProcessor The target for supernova messages.
     * @param moniProcessor The target for moni messages.
     * @param tcalProcessor The target for tcal messages.
     * @param rapcal The rapcal instance that will be updated by the tcal
     *               stream and utilized by all streams.
     * @param dataStats Object responsible for maintaining data counts.
     */
    public SynchronousDataProcessor(final StreamProcessor hitProcessor,
                                     final StreamProcessor snProcessor,
                                     final StreamProcessor moniProcessor,
                                     final StreamProcessor tcalProcessor,
                                     final RAPCal rapcal,
                                     final DataStats dataStats)
    {
        this.hitProcessor = hitProcessor;
        this.snProcessor = snProcessor;
        this.moniProcessor = moniProcessor;
        this.tcalProcessor = tcalProcessor;
        this.rapcal = rapcal;
        this.dataStats = dataStats;
    }

    @Override
    public DataStats getDataCounters()
    {
        return dataStats;
    }

    @Override
    public void runLevel(final RunLevel runLevel) throws DataProcessorError
    {
        hitProcessor.runLevel(runLevel);
        snProcessor.runLevel(runLevel);
        moniProcessor.runLevel(runLevel);
        tcalProcessor.runLevel(runLevel);
    }

    @Override
    public void process(final StreamType streamType, final ByteBuffer data)
            throws DataProcessorError
    {
        try
        {
            dataStats.reportProcessingStart(streamType, data);

            switch(streamType)
            {
                case HIT:
                    hitProcessor.process(data, dataStats);
                    break;
                case SUPERNOVA:
                    snProcessor.process(data, dataStats);
                    break;
                case MONI:
                    moniProcessor.process(data, dataStats);
                    break;
                case TCAL:
                    tcalProcessor.process(data, dataStats);
                    break;
                default:
                    throw new DataProcessorError("No stream defined" +
                            " for type [" + streamType + "]");
            }
        }
        finally
        {
            dataStats.reportProcessingEnd(streamType);
        }
    }

    @Override
    public void eos(final StreamType streamType) throws DataProcessorError
    {
        switch(streamType)
        {
            case HIT:
                hitProcessor.eos();
                break;
            case SUPERNOVA:
                snProcessor.eos();
                break;
            case MONI:
                moniProcessor.eos();
                break;
            case TCAL:
                tcalProcessor.eos();
                break;
            default:
                throw new DataProcessorError("No stream defined" +
                        " for type [" + streamType + "]");
        }
    }

    @Override
    public void eos() throws DataProcessorError
    {
        hitProcessor.eos();
        snProcessor.eos();
        moniProcessor.eos();
        tcalProcessor.eos();
    }

    @Override
    public void shutdown(final long waitMillis) throws DataProcessorError
    {
        shutdown();
    }

    @Override
    public void shutdown() throws DataProcessorError
    {
        eos();
    }

    @Override
    public void sync() throws DataProcessorError
    {
        // Synchronous processor is inherently synced
    }

    @Override
    public UTC resolveUTCTime(final long domclock) throws DataProcessorError
    {
        return rapcal.domToUTC(domclock);
    }

    @Override
    public void setLiveMoni(final LiveTCalMoni moni)
    {
        rapcal.setMoni(moni);
    }
}