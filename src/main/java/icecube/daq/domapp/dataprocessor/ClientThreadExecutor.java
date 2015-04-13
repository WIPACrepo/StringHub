package icecube.daq.domapp.dataprocessor;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * An ExecutorService implementation that simply runs all jobs
 * on the caller's thread.
 *
 * Note that this class is not safe for multi thread clients. The design
 * is intended to support exclusive use by a DataAcquisition thread.
 *
 * Running the DataCollector with this executor aids troubleshooting issues
 * caused by asynchronous data processing.
 *
 */
public class ClientThreadExecutor extends AbstractExecutorService
{
    boolean isShutdown;

    public ClientThreadExecutor()
    {
        //instantiation is activation
        isShutdown = false;
    }

    @Override
    public void shutdown()
    {
        isShutdown=true;
    }

    @Override
    public List<Runnable> shutdownNow()
    {
        return new LinkedList<Runnable>();
    }

    @Override
    public boolean isShutdown()
    {
        return isShutdown;
    }

    @Override
    public boolean isTerminated()
    {
        return isShutdown;
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit)
            throws InterruptedException
    {
        if(isShutdown)
        {
            return true;
        }
        else
        {
            throw new IllegalStateException("The caller can not await " +
                    "termination of an active executor");
        }
    }

    @Override
    public void execute(final Runnable command)
    {
        command.run();
    }
}
