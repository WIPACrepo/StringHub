package icecube.daq.domapp.dataprocessor;

import icecube.daq.domapp.RunLevel;
import icecube.daq.livemoni.LiveTCalMoni;
import icecube.daq.util.UTC;
import org.apache.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps a data processor with a configurable threading model.
 *
 * This class respects two design goals:
 *
 *    Support low latency data acquisition.
 *    Encapsulate and hide the ugly complexity of asynchronous processing.
 *
 *
 * Design Notes:
 *
 * Exception conditions in processing code that were synchronous with respect
 * to data acquisition in the single threaded design are now asynchronous.
 * By convention, these exceptions have been used to halt acquisition and
 * processing and transition the DataCollector to a ZOMBIE state.
 *
 * In the case of the threaded processor the only recourse is to log the error
 * condition and shutdown the processor. The DataCollector will ultimately
 * observe this condition in a subsequent call (as a DataProcessingException)
 * and respond by terminating itself to a ZOMBIE state.  This is likely to
 * occur in a call to process(), but can happen in any call including
 * shutdown().
 *
 * Since we are operating asynchronously, it is possible for the DataCollector
 * to initiate shutdown prior to observing an error condition in the processing
 * thread. This race is resolved in forceShutdown() method. The reason it is
 * resolved here (and not in shutdown) is to let a processing exception
 * preempt a graceful shutdown.
 *
 * Although we use a single threaded "ThreadPool" executor, it should be
 * possible to use a thread pool with thread/channel affinity that is shared
 * by all processors on the hub.  This would reduce thread count and context
 * switching.
 */
public class AsynchronousDataProcessor implements DataProcessor
{


    Logger logger = Logger.getLogger(AsynchronousDataProcessor.class);

    /** The root processor */
    private  final DataProcessor delegate;

    /** The processor thread */
    private final ExecutorService executor;

    /** Flag to manage races caused by a forced shutdown.*/
    private AtomicBoolean inForcedShutdown = new AtomicBoolean(false);

    /** Latch to wait a shutdown to complete. */
    CountDownLatch shutdownLatch = new CountDownLatch(1);

    /** Flag to indicate a completed shutdown.*/
    private volatile boolean isShutdown;


    /**
     * Number of seconds to allow for queued tasks to complete
     * during shutdown.
     */
    public static final int GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS = 10;

    /**
     * Number of seconds to allow for shutdown to complete
     * during a forced shutdown.
     */
    public static final int FORCED_SHUTDOWN_TIMEOUT_SECONDS = 10;


    /**
     * The maximum number of processing jobs that can be queue, corresponds
     * to the number of data messages that that are acquired but unprocessed.
     */
    public static final int PROCESSING_QUEUE_DEPTH = 1000;



    /**
     * Factory method for a processor that runs in a single, independent thread.
     * This is the production implementation that offloads the acquisition
     * thread.
     *
     * @param channelID Identified the channel.
     * @param delegate The root processor implementation.
     * @return A single threaded, asynchronous processor.
     */
    public static AsynchronousDataProcessor
    singleThreadedExecutor(final String channelID,
                           final DataProcessor delegate)
    {
        ExecutorService executor =
                new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<Runnable>(PROCESSING_QUEUE_DEPTH),
                        new SingleThreadFactory(channelID),
                        new SingleThreadedRejectionHandler());

        return new AsynchronousDataProcessor(channelID, executor, delegate);
    }

    /**
     * Factory method for a processor that runs in the callers thread, but still
     * performs the code gyrations to support the asynchronous relationship.
     * Exist mainly to support development verification.
     *
     * @param channelID Identified the channel.
     * @param delegate The root processor implementation.
     * @return A single threaded, asynchronous processor.
     */
    public static AsynchronousDataProcessor
    clientThreadExecutor(final String channelID,
                         final DataProcessor delegate)
    {
        ExecutorService executor = new ClientThreadExecutor();
        return new AsynchronousDataProcessor(channelID, executor, delegate);
    }


    /**
     * Constructor.
     *
     * Note that multi-threaded pool-based executors are not supported. A data
     * channel must be processed by one thread.
     *
     * @param channelID Identifies the channel.
     * @param executor The executor.
     * @param delegate The root processor.
     */
    private AsynchronousDataProcessor(final String channelID,
                              final ExecutorService executor,
                              final DataProcessor delegate)
    {
        this.delegate = delegate;
        this.executor = executor;
    }

    @Override
    public DataStats getDataCounters()
    {
        return delegate.getDataCounters();
    }

    @Override
    public void runLevel(final RunLevel runLevel)
    {
        try
        {
            enqueWork(new Callable<Void>()
            {
                @Override
                public Void call()
                {
                    try
                    {
                        delegate.runLevel(runLevel);
                    }
                    catch (Throwable th)
                    {
                        handleException(th);
                    }
                    return null;
                }
            });
        }
        catch (DataProcessorError dataProcessorError)
        {
            //todo consider propagating, in the current design
            //     we only take action on transition to/from
            //     RUNNING.
            //
            //     ZOMBIE state presents a problem since the
            //     transition occurs after shutdown.
            //
            //     suppressing the exception is really a work-around
            //     rather than a good practice.
        }
    }

    @Override
    public void eos(final StreamType stream) throws DataProcessorError
    {

        enqueWork(new Callable<Void>()
        {
            @Override
            public Void call()
            {
                try
                {
                    delegate.eos(stream);
                }
                catch (Throwable th)
                {
                    handleException(th);
                }
                return null;
            }
        });
    }

    @Override
    public void eos() throws DataProcessorError
    {
        enqueWork(new Callable<Void>()
        {
            @Override
            public Void call()
            {
                try
                {
                    delegate.eos();
                }
                catch (Throwable th)
                {
                    handleException(th);
                }
                return null;
            }
        });
    }

    @Override
    public void process(final StreamType stream, final ByteBuffer data)
            throws DataProcessorError
    {

        final ByteBuffer copy = copy(data);

        enqueWork(new Callable<Void>()
        {
            @Override
            public Void call()
            {
                try
                {
                    delegate.process(stream, copy);
                }
                catch (Throwable th)
                {
                    handleException(th);
                }
                return null;
            }
        });

    }

    /**
     * Resolve a UTC timestamp from a dom clock timestamp.
     *
     * Noe that this implementation blocks the caller and waits for
     * the processing thread to execute the call.  Exceptions are
     * thrown directly to client.
     *
     */
    @Override
    public UTC resolveUTCTime(final long domclock) throws DataProcessorError
    {
        //NOTE: rapcal is not thread safe. It is being updated
        //      (and used heavily) on the processor thread. Occasional
        //      off-thread usage is mediated here by executing
        //      the rapcal access on the processor thread and
        //      returning the result synchronously by waiting
        //      on the future.
        //
        //      Also note that rapcal exceptions do not shutdown
        //      the processor.


        Future<UTC> answer = enqueWork(new Callable<UTC>()
        {
            @Override
            public UTC call() throws Exception
            {
                return delegate.resolveUTCTime(domclock);
            }
        });

        try
        {
            return answer.get();
        }
        catch (InterruptedException e)
        {
            //likely watchdog did not like the wait
            throw new DataProcessorError("Error while resolving UTC time", e);
        }
        catch (ExecutionException e)
        {
            //rapcal threw an exception
            throw new DataProcessorError("Error while resolving UTC time", e);
        }

    }

    @Override
    public void setLiveMoni(final LiveTCalMoni moni) throws DataProcessorError
    {
        enqueWork(new Callable<Void>()
        {
            @Override
            public Void call() throws Exception
            {
                delegate.setLiveMoni(moni);
                return null;
            }
        });
    }

    /**
     * Shutdown the processor thread.
     *
     * An effort is made to shut the processor down gracefully by submitting
     * an shutdown job and waiting for work to complete. If a graceful
     * exit cannot be achieved, work is discarded and shutdown is forced.
     *
     * In either case, it is imperative that an EOS marker is sent.
     *
     */
    @Override
    public void shutdown() throws DataProcessorError
    {

        // DataCollector always calls shutdown, in the case of processing
        // initiated shutdown there is nothing more to do.
        if(isShutdown)
        {
            return;
        }

        boolean graceful = gracefulShutdown();

        if(!graceful)
        {
            logger.warn("Graceful shutdown failed, forcing shutdown.");
            forceShutdown();
        }
    }

    /**
     * Submit work to the processor with knowledge of the shutdown race
     * hazards.
     *
     * @param callable the work to submit
     */
    private <T> Future<T> enqueWork(Callable<T> callable)
            throws DataProcessorError
    {
        try
        {
            return executor.submit(callable);
        }
        catch (RejectedExecutionException ree)
        {
            throw new DataProcessorError("Error submitting work to" +
                    " the processor", ree);
        }
        catch (Throwable th)
        {
            throw new DataProcessorError("Error submitting work to" +
                    " the processor", th);
        }
    }

    /**
     * Attempt to shutdown the processor gracefully.
     *
     * This method is only is only called on the acquisition thread.
     *
     * @return true if the shutdown was graceful
     */
    private boolean gracefulShutdown()
    {
        Future<Boolean> eosFuture;
        try
        {
            eosFuture = enqueWork(new Callable<Boolean>()
            {
                @Override
                public Boolean call()
                {
                    try
                    {
                        delegate.shutdown();
                        isShutdown = true;
                        return true;
                    }
                    catch (Throwable th)
                    {
                        handleException(th);
                        logger.error("Error shutting down data processor", th);
                        return false;
                    }
                }
            });

        }
        catch (DataProcessorError dataProcessorError)
        {
            //todo
            // If we make the queue full condition cause discernible,
            // it may be better to log the error since any other
            // condition is unexpected.
            //
            // The most likely causes:
            //    Queue full
            //    Already shut down
            //    mis-code.

            return false;
        }

        executor.shutdown();


        // wait for shutdown job to complete.
        try
        {
            return eosFuture.get(GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS);
        }
        catch (InterruptedException ie)
        {
            logger.warn("Interrupted while waiting for data processor" +
                    " to complete.");
        }
        catch (ExecutionException ee)
        {
            logger.warn("Error running shutdown job.", ee);
        }
        catch (TimeoutException te)
        {
            logger.warn("Timed out while waiting for data processor" +
                    " to complete.");
        }

        return false;

    }

    /**
     * Failsafe shutdown.
     *
     * Used in exigent circumstances where processing
     * termination is unavoidable and graceless. Ensures that EOS markers
     * are sent despite incomplete processing job.
     *
     * This method can be called from both the processing thread and the
     * acquisition thread.
     *
     * Note that a forced shutdown on the processor thread preempts a
     * graceful shutdown.  This is why the shutdown race is resolve here.
     *
     */
    private void forceShutdown()
    {
        // resolve the race between processor and acquisition thread.
        boolean first = inForcedShutdown.compareAndSet(false, true);

        if(first)
        {

            // if we are on the processor thread, this will interrupt ourselves.
            // if we are on the acquisition thread, this will interrupt the
            // processor.
            //
            // we must clear our interrupt.
            List<Runnable> discardedWork = executor.shutdownNow();
            Thread.interrupted();

            logger.error("Processing shutting down with [" +
                    discardedWork.size() + "] processing jobs pending.");

            try
            {
                delegate.eos();
            }
            catch(Throwable th)
            {
                logger.error("Processor unable to issue EOS markers");
            }

            isShutdown = true;
            shutdownLatch.countDown();
        }
        else
        {
            // let the other thread shut us down, but log the race and
            // wait.
            logger.warn("Simultaneous shutdown.");
            try
            {
                boolean clean = shutdownLatch.await(
                        FORCED_SHUTDOWN_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS);
                if(!clean)
                {
                    logger.warn("Timed out waiting for shutdown");
                }
            }
            catch (InterruptedException e)
            {
                logger.warn("Interrupted waiting for shutdown");
            }
        }
    }

    /**
     * Handle Exceptions encountered in processing thread.
     */
    private void handleException(Throwable error)
    {

        // A forced shutdown from the acquisition almost always
        // leads to an interrupted exception on the processor thread
        // suppress logging by looking st the shutdown flag
        if(!inForcedShutdown.get())
        {
         logger.error("Processing encountered an error, will force" +
                " a shutdown.", error );
        }

        // Even if we are already shutting down, allow forceShutdown
        // to handle the wait
        forceShutdown();

    }

    /**
     * A utility method to copy a buffer.
     */
    private ByteBuffer copy(ByteBuffer original)
    {
        ByteBuffer data = original.asReadOnlyBuffer();
        final ByteBuffer copy = ByteBuffer.allocate(data.remaining());
        copy.put(data);
        copy.flip();
        return copy;
    }


    /**
     * Factory for defining the processing thread.
     *
     *    Encoded the channel id in the thread name.
     *    Prohibit generation of a replacement thread.
     */
    private static class SingleThreadFactory implements ThreadFactory
    {
        final String channelID;
        int instanceNumber;

        private SingleThreadFactory(final String channelID)
        {
            this.channelID = channelID;
        }

        @Override
        public Thread newThread(final Runnable runnable)
        {
            synchronized (this)
            {
                // prohibit thread restoration, In our single thread design,
                // this would only occur via a submitted job with improper
                // exception handling, or a catastrophic error internal to
                // the executor.
                if(instanceNumber == 1 )
                {
                    throw new Error("Unexpected processor thread death.");
                }
                else
                {
                    Thread thread = new Thread(runnable);
                    thread.setName("Processor-" + channelID);

                    instanceNumber++;
                    return thread;
                }
            }

        }
    }

    /**
     * Handler for when the threaded executor rejects a job. In the single
     * thread design this happens when:
     *
     *    The work queue is full.
     *    The executor is shutdown.
     *
     * Customize the error message accordingly.
     */
    private static class SingleThreadedRejectionHandler
            implements RejectedExecutionHandler
    {
        @Override
        public void rejectedExecution(final Runnable r,
                                      final ThreadPoolExecutor executor)
        {
            if(executor.isShutdown())
            {
                throw new RejectedExecutionException("Executor is shutdown");
            }
            else
            {
                int size = executor.getQueue().size();
                throw new RejectedExecutionException("Processing queue is" +
                        " full, queue size [" + size + "]");
            }
        }
    }


}
