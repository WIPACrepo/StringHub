package icecube.daq.domapp.dataprocessor;

import icecube.daq.domapp.RunLevel;
import icecube.daq.monitoring.TCalExceptionAlerter;
import icecube.daq.util.UTC;
import org.apache.log4j.Logger;

import java.nio.ByteBuffer;

/**
 * A processor class that injects errors and/or processing
 * delays. For use in testing and development.
 */
public class ErrorProducingProcessor implements DataProcessor
{
    Logger logger = Logger.getLogger(ErrorProducingProcessor.class);

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

    private static final boolean INJECT_ERROR_ON_EOS =
            Boolean.getBoolean("icecube.daq.domapp.dataprocessor.inject-error-on-eos");

    private static final boolean INJECT_ERROR_ON_SYNC =
            Boolean.getBoolean("icecube.daq.domapp.dataprocessor.inject-error-on-sync");

    private static final boolean INJECT_ERROR_ON_SHUTDOWN =
            Boolean.getBoolean("icecube.daq.domapp.dataprocessor.inject-error-on-shutdown");

    /** Causes injected exception to be an unchecked exception. */
    private static final boolean INJECT_UNCHECKED =
            Boolean.getBoolean("icecube.daq.domapp.dataprocessor.inject-unchecked");



    /** drop rap cal processing, as if GPS or tcal was broken. */
    private static final boolean SIMULATE_BROKEN_RAPCAL =
            Boolean.getBoolean("icecube.daq.domapp.dataprocessor.no-rapcal");



    /** Inject a processing delay into each message. */
    private static final int DELAY_HIT_PROCESSING_MILLIS =
            Integer.getInteger("icecube.daq.domapp.dataprocessor.hit-processing-delay", 0);

    /** Inject a delay into eos. */
    private static final int DELAY_EOS_MILLIS =
            Integer.getInteger("icecube.daq.domapp.dataprocessor.eos-delay", 0);

    /** Inject a delay into sync. */
    private static final int DELAY_SYNC_MILLIS =
            Integer.getInteger("icecube.daq.domapp.dataprocessor.sync-delay", 0);

    /** Inject a delay into shutdown. */
    private static final int DELAY_SHUTDOWN_MILLIS =
            Integer.getInteger("icecube.daq.domapp.dataprocessor.shutdown-delay", 0);


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
        if(INJECT_ERROR_ON_EOS)
        {
            generateException();
        }
        if(DELAY_EOS_MILLIS > 0)
        {
            logger.warn("Delaying eos() " + DELAY_EOS_MILLIS + " ms");
            sleep(DELAY_EOS_MILLIS);
        }
        delegate.eos();
    }

    @Override
    public void shutdown(final long waitMillis) throws DataProcessorError
    {
        if(INJECT_ERROR_ON_SHUTDOWN)
        {
            generateException();
        }
        if(DELAY_SHUTDOWN_MILLIS > 0)
        {
            logger.warn("Delaying shutdown() " + DELAY_SHUTDOWN_MILLIS + " ms");
            sleep(DELAY_SHUTDOWN_MILLIS);
        }
        delegate.shutdown(waitMillis);
    }

    @Override
    public void shutdown() throws DataProcessorError
    {
        if(INJECT_ERROR_ON_SHUTDOWN)
        {
            generateException();
        }
        if(DELAY_SHUTDOWN_MILLIS > 0)
        {
            logger.warn("Delaying shutdown() " + DELAY_SHUTDOWN_MILLIS + " ms");
            sleep(DELAY_SHUTDOWN_MILLIS);
        }
        delegate.shutdown();
    }

    @Override
    public void sync() throws DataProcessorError
    {
        if(INJECT_ERROR_ON_SYNC)
        {
            generateException();
        }
        if(DELAY_SYNC_MILLIS > 0)
        {
            logger.warn("Delaying sync() " + DELAY_SYNC_MILLIS + " ms");
            sleep(DELAY_SYNC_MILLIS);
        }
        delegate.sync();
    }

    @Override
    public UTC resolveUTCTime(final long domclock) throws DataProcessorError
    {
        return delegate.resolveUTCTime(domclock);
    }

    @Override
    public void setTCalExceptionAlerter(final TCalExceptionAlerter alerter) throws DataProcessorError
    {
        delegate.setTCalExceptionAlerter(alerter);
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

    private void generateException() throws DataProcessorError
    {
        if(INJECT_UNCHECKED)
        {
            throw new Error("Injected Error");
        }
        else
        {
            throw new DataProcessorError("Injected Error");
        }
    }

    private void sleep(long millis) throws DataProcessorError
    {
        long before = System.nanoTime();
        try
        {
            Thread.sleep(millis);
        }
        catch(InterruptedException ie)
        {
            long after = System.nanoTime()+1;
            throw new DataProcessorError("Interupted after sleeping" +
                    (after-before)/1000000 + " ms", ie);
        }
    }
}
