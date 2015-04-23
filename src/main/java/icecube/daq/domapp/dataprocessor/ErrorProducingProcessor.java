package icecube.daq.domapp.dataprocessor;

import icecube.daq.domapp.RunLevel;
import icecube.daq.livemoni.LiveTCalMoni;
import icecube.daq.util.UTC;

import java.nio.ByteBuffer;

/**
 * A processor class that injects errors and/or processing
 * delays. For use in testing and development.
 */
public class ErrorProducingProcessor implements DataProcessor
{
    private final DataProcessor delegate;

    int hitMessagelCount;
    int snMessagelCount;
    int moniMessagelCount;
    int tcalMessagelCount;

    /** Inject an exception after a certain number of messages are processed. */
    private static final int INJECT_HIT_ERROR_ON_MESSAGE =
            Integer.getInteger("icecube.daq.domapp.dataprocessor.inject-hit-error-on-message", -1);

    private static final int INJECT_SN_ERROR_ON_MESSAGE =
            Integer.getInteger("icecube.daq.domapp.dataprocessor.inject-sn-error-on-message", -1);

    private static final int INJECT_MONI_ERROR_ON_MESSAGE =
            Integer.getInteger("icecube.daq.domapp.dataprocessor.inject-moni-error-on-message", -1);

    private static final int INJECT_TCAL_ERROR_ON_MESSAGE =
            Integer.getInteger("icecube.daq.domapp.dataprocessor.inject-tcal-error-on-message", -1);


    /** drop rap cal processing, as if GPS or tcal was broken. */
    private static final boolean SIMULATE_BROKEN_RAPCAL =
            Boolean.getBoolean("icecube.daq.domapp.dataprocessor.no-rapcal");


    /** Injects an unchecked exception */
    private static final boolean INJECT_UNCHECKED =
            Boolean.getBoolean("icecube.daq.domapp.dataprocessor.inject-unchecked");


    /** Inject a processing delay into each message */
    private static final int DELAY_HIT_PROCESSING_MILLIS =
            Integer.getInteger("icecube.daq.domapp.dataprocessor.hit-processing-delay", -1);


    public ErrorProducingProcessor(final DataProcessor delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public DataStats getDataCounters()
    {
        return delegate.getDataCounters();
    }

    @Override
    public void runLevel(final RunLevel runLevel) throws DataProcessorError
    {
        delegate.runLevel(runLevel);
    }

    @Override
    public void process(final StreamType stream, final ByteBuffer data)
            throws DataProcessorError
    {

        switch (stream)
        {
            case HIT:
                if(++hitMessagelCount == INJECT_HIT_ERROR_ON_MESSAGE)
                {
                    generateException(INJECT_HIT_ERROR_ON_MESSAGE);
                }
                sleep(DELAY_HIT_PROCESSING_MILLIS);
                break;
            case SUPERNOVA:
                if(++snMessagelCount == INJECT_SN_ERROR_ON_MESSAGE)
                {
                    generateException(INJECT_SN_ERROR_ON_MESSAGE);
                }
                break;
            case MONI:
                if(++moniMessagelCount == INJECT_MONI_ERROR_ON_MESSAGE)
                {
                    generateException(INJECT_MONI_ERROR_ON_MESSAGE);
                }
                break;
            case TCAL:
                if(++tcalMessagelCount == INJECT_TCAL_ERROR_ON_MESSAGE)
                {
                    generateException(INJECT_TCAL_ERROR_ON_MESSAGE);
                }
                if(SIMULATE_BROKEN_RAPCAL)
                {
                    return;
                }
                break;
            default:
                //let the production implementation handle
        }


        delegate.process(stream, data);
    }

    @Override
    public void eos(final DataProcessor.StreamType stream)
            throws DataProcessorError
    {
        delegate.eos(stream);
    }

    @Override
    public void eos() throws DataProcessorError
    {
        delegate.eos();
    }

    @Override
    public void shutdown() throws DataProcessorError
    {
        delegate.shutdown();
    }

    @Override
    public UTC resolveUTCTime(final long domclock) throws DataProcessorError
    {
        return delegate.resolveUTCTime(domclock);
    }

    @Override
    public void setLiveMoni(final LiveTCalMoni moni) throws DataProcessorError
    {
        delegate.setLiveMoni(moni);
    }

    private void generateException(int messageCount) throws DataProcessorError
    {
        if(INJECT_UNCHECKED)
        {
            throw new Error("Injected Error on message " + messageCount);
        }
        else
        {
            throw new DataProcessorError("Injected Error on message " +
                    messageCount);
        }
    }

    private void sleep(long millis) throws DataProcessorError
    {
        try
        {
            Thread.sleep(millis);
        }
        catch(InterruptedException ie)
        {
            throw new DataProcessorError(ie);

        }
    }
}
