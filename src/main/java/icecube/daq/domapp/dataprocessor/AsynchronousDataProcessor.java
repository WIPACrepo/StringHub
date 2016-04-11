package icecube.daq.domapp.dataprocessor;

import icecube.daq.domapp.RunLevel;
import icecube.daq.monitoring.IRunMonitor;
import icecube.daq.util.UTC;
import org.apache.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
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
import java.util.concurrent.atomic.AtomicReference;

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
 * thread. This race is resolved in the forceShutdown() method. The reason it
 * is resolved there (and not in shutdown) is to let a processing exception
 * preempt a graceful shutdown.
 *
 * Although we use a single threaded "ThreadPool" executor, it should be
 * possible to use a thread pool with thread/channel affinity that is shared
 * by all processors on the hub.  This would reduce thread count and context
 * switching.
 */
public class AsynchronousDataProcessor implements DataProcessor
{


    private static Logger logger = Logger.getLogger(AsynchronousDataProcessor.class);

    /** The root processor */
    private  final DataProcessor delegate;

    /** The processor thread */
    private final ExecutorService executor;

    /** The executor's work queue, exposed for depth monitoring. */
    private final Queue<Runnable> workQueue;

    /** Object holding processor counters*/
    private final DataStats dataStats;

    /** Flag to manage races caused by a forced shutdown.*/
    private AtomicBoolean inForcedShutdown = new AtomicBoolean(false);

    /** Latch to wait a shutdown to complete. */
    private CountDownLatch shutdownLatch = new CountDownLatch(1);

    /** Flag to indicate a completed shutdown.*/
    private volatile boolean isShutdown;

    /** Holds a future associated with a pending synchronous call. */
    private AtomicReference<Future> pendingSynchronousCall
            = new AtomicReference<Future>();


    /** Identifiers for the thread contexts */
    enum Participant
    {
        AcquisitionThread,
        ProcessingThread
    }

    /**
     * Policies which define the behavior when the job queue is full
     */
    public static enum QueueFullPolicy
    {
        Reject,           // Reject job submission on queue full
        Block,            // block submitter on queue full
        BlockShutdownOnly // block shutdown job submission, reject others on
                          // queue full
    }

    /**
     * Configurable default policy
     */
    private static final QueueFullPolicy defaultPolicy =
            QueueFullPolicy.valueOf(System.getProperty(
                    "icecube.daq.domapp.dataprocessor.async-queue-full-policy",
            QueueFullPolicy.Block.name()));

    /**
     * Number of seconds to allow for shutdown to complete
     * during a forced shutdown. Exposed for testing.
     */
    public int FORCED_SHUTDOWN_TIMEOUT_SECONDS = Integer.MAX_VALUE;


    /**
     * The maximum number of processing jobs that can be queue, corresponds
     * to the number of data messages that that are acquired but unprocessed.
     */
    public static final int PROCESSING_QUEUE_DEPTH = 5000;



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
        return singleThreadedExecutor(channelID,
                delegate,
                defaultPolicy);
    }

    /**
     * Factory method for a processor that runs in a single, independent thread.
     *
     * @param channelID Identified the channel.
     * @param delegate The root processor implementation.
     * @param policy Defines the behavior of calls when the job queue is
     *               full.
     * @return A single threaded, asynchronous processor.
     */
    public static AsynchronousDataProcessor
    singleThreadedExecutor(final String channelID,
                           final DataProcessor delegate,
                           final QueueFullPolicy policy)
    {
        final RejectedExecutionHandler rejectionHandler;
        switch (policy)
        {
            case Reject:
                rejectionHandler = new SingleThreadedRejectionHandler();
                break;
            case Block:
                rejectionHandler = new BlockingExecutorRejectionHandler();
                break;
            case BlockShutdownOnly:
                throw new Error("Not Implemented.");
            default:
                throw new Error("Unknown policy " + policy);

        }
        ExecutorService executor =
                new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<Runnable>(PROCESSING_QUEUE_DEPTH),
                        new SingleThreadFactory(channelID),
                        rejectionHandler);

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
        this.dataStats = delegate.getDataCounters();

        // nuisance hack, to support monitoring
        if(executor instanceof ThreadPoolExecutor)
        {
            this.workQueue = ((ThreadPoolExecutor)executor).getQueue();
        }
        else
        {
            this.workQueue = new LinkedList<Runnable>();
        }
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
        catch (CancellationException ce)
        {
            // processor forced shutdown before executing job
            throw new DataProcessorError("Cancelled while resolving" +
                    " UTC time", ce);
        }

    }

    @Override
    public void sync() throws DataProcessorError
    {
        // Queue the sync job and wait for processor to execute
        Future<Void> sync = enqueWork(new Callable<Void>()
        {
            @Override
            public Void call() throws Exception
            {
                try
                {
                    delegate.sync();
                    return null;
                }
                catch (Throwable th)
                {
                    //unusual formulation ... because the caller is blocking
                    // on this method and should see the error.
                    handleException(th);
                    throw new DataProcessorError("Error while syncing",th);
                }
            }
        });
        try
        {
            sync.get();
        }
        catch (InterruptedException e)
        {
            //likely watchdog did not like the wait
            throw new DataProcessorError("Interrupted while syncing", e);
        }
        catch (ExecutionException e)
        {
            //inconceivable
            throw new DataProcessorError("Error while syncing", e);
        }
        catch (CancellationException ce)
        {
            throw new DataProcessorError("Cancelled while syncing", ce);
        }
    }

    @Override
    public void setRunMonitor(final IRunMonitor runMonitor) throws DataProcessorError
    {
        enqueWork(new Callable<Void>()
        {
            @Override
            public Void call() throws Exception
            {
                delegate.setRunMonitor(runMonitor);
                return null;
            }
        });
    }

    @Override
    public void shutdown() throws DataProcessorError
    {
        shutdown(Long.MAX_VALUE);
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
    public void shutdown(final long waitMillis) throws DataProcessorError
    {

        // DataCollector always calls shutdown, in the case of processing
        // initiated shutdown there is nothing more to do.
        if(isShutdown)
        {
            return;
        }

        boolean graceful = gracefulShutdown(waitMillis);

        if(!graceful)
        {
            logger.warn("Graceful shutdown failed, forcing shutdown.");
            forceShutdown(Participant.AcquisitionThread);
        }
        else
        {
           completeShutdown();
        }
    }

    /**
     * Submit work to the processor with knowledge of the shutdown race
     * hazards.
     *
     * @param callable the work to submit.
     * @return A shutdown-aware future.
     */
    private <T> Future<T> enqueWork(Callable<T> callable)
            throws DataProcessorError
    {
        try
        {
            Future<T> future = executor.submit(callable);

            //monitor the queue depth
            int queuedJobs = workQueue.size();
            dataStats.reportProcessorQueueDepth(queuedJobs);

            //NOTE: The raw future is not safe to wait on due to
            //      shutdown hazards
            return new ShutdownSafeFuture<T>(future);
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
    private boolean gracefulShutdown(long timeoutMillis)
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
                        return true;
                    }
                    catch (Throwable th)
                    {
                        handleException(th);
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


        // wait for shutdown job to complete. Errors are suppressed, caller
        // will proceed to a forced shutdown.
        try
        {
            return eosFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
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
        catch (CancellationException ce)
        {
            logger.warn("Processor forced shut down" +
                    " during graceful shutdown.", ce);
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
     * @throws DataProcessorError Indicates a failure to shutdown.
     */
    private void forceShutdown(Participant thread) throws DataProcessorError
    {
        switch (thread)
        {
            case AcquisitionThread:
                forceShutdownFromAcquisitionThread();
                break;
            case ProcessingThread:
                forceShutdownFromProcessingThread();
                break;
            default:
                throw new Error("Unknown participant: " + thread);
        }

        // Design Details:
        //
        // To avoid a race condition, the acquisition thread can not call into
        // the delegate while the processor thread is alive. Normally, shutdown
        // can execute on the processor thread after it is interrupted by the
        // forced shutdown.  There are some edge cases when the processing
        // thread exits cleanly without shutdown.
        //
        // I. If called by Acquisition thread first:
        //
        //   The Acquisition Thread:
        //
        //   Discard queued Executor jobs.
        //   Wait for executor to complete last job.
        //
        //      Check if processor thread completed the shutdown.
        //
        //         Yes: Nothing to do
        //         No:  Complete shutdown of the delegate
        //
        //
        //   The Processor Thread:
        //
        //      If current job interrupted:
        //            will end up in forcedShutdown as second caller and
        //            complete the shutdown.
        //
        //      If  current job completed:
        //            If Processing job: Will exit, no shutdown.
        //            If Shutdown job:   Will exit, shutdown already complete
        //                               gracefully.
        //
        //      If Blocked:
        //            Untennable. Acquisition thread will wait until
        //                        external interruption and then throw
        //                        an exception.
        //
        //
        //
        // II. If called by Processor thread first:
        //
        //   The Processor thread:
        //
        //      Discard queued Executor jobs.
        //      Complete the shutdown.
        //
        //
        //   The Acquisition Thread:
        //
        //      Wait for processor thread to complete.
        //

    }

    /**
     * The Processing thread side of a forced shutdown.
     *
     * @throws DataProcessorError Shutdown did not happen.
     */
    private void forceShutdownFromProcessingThread() throws DataProcessorError
    {
        // resolve the race between processor and acquisition thread.
        boolean first = inForcedShutdown.compareAndSet(false, true);

        if(first)
        {
            // This is the shutdown path for errors encountered during
            // processing.

            // Since we are on the processor thread, this will interrupt
            // ourselves. The interrupt status must be cleared
            List<Runnable> discardedWork = executor.shutdownNow();
            Thread.interrupted();

            logger.error("Processing shutting down with [" +
                    discardedWork.size() + "] processing jobs pending.");

            try
            {
                delegate.shutdown();
            }
            catch(Throwable th)
            {
                throw new DataProcessorError("Processor unable to" +
                        " shutdown delegate", th);
            }
            finally
            {
                completeShutdown();
            }

        }
        else
        {
            // When processing is lagging, this is the typical shutdown path
            // arrived at.
            //
            // The mechanism occurs when the acquisition thread fires a
            // shutdown and the processor has a large buffered load, or a
            // blocked job. After waiting for a graceful shutdown, the
            // acquisition thread escalates to a forced shutdown which drives
            // an interrupt into the processor thread and it ends up
            // here as the second participant of the forced shutdown.
            //
            // In order to preserve the threading model, the processor thread
            // should shut down the delegate and exit.
            //
            try
            {
                delegate.shutdown();
            }
            catch(Throwable th)
            {
                throw new DataProcessorError("Processor unable to" +
                        " shutdown delegate", th);
            }
            finally
            {
                completeShutdown();
            }
        }

    }

    /**
     * The Acquisition side of a forced shutdown.
     *
     * @throws DataProcessorError Shutdown did not happen.
     */
    private void forceShutdownFromAcquisitionThread() throws DataProcessorError
    {
        // resolve the race between processor and acquisition thread.
        boolean first = inForcedShutdown.compareAndSet(false, true);

        if(first)
        {

            // Should interrupt the processor thread.
            List<Runnable> discardedWork = executor.shutdownNow();

            logger.error("Processing shutting down with [" +
                    discardedWork.size() + "] processing jobs pending.");


            // We have no idea where or if the processor quit. We have to
            // account for:
            //
            // The interrupt generated an exception of the current job.
            // The current job completed normally and the executor exited.
            //    The current job was a processing job
            //    The current job was the shutdown job
            //
            // The executor shutdown between jobs and exited.
            //
            // The current job is blocked uninterruptibly.
            //
            //
            // Wait for the processing thread exit. If it exited without
            // shutting down, then and only then can we issue the delegate
            // shutdown on the acquisition thread. Otherwise, any interaction
            // with the delegate on this thread is a race condition against
            // the processor.
            try
            {
                boolean processorDone =
                        executor.awaitTermination(FORCED_SHUTDOWN_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS);

                if(processorDone)
                {
                    // did the processor shut down, or exit without
                    // shutdown.
                    if(isShutdown)
                    {
                        //nothing to do
                    }
                    else
                    {
                        try
                        {
                            delegate.shutdown();
                        }
                        catch (Throwable th)
                        {
                            throw new DataProcessorError("Processor unable to" +
                                    " shutdown delegate", th);
                        }
                        finally
                        {
                            completeShutdown();
                        }
                    }
                }
                else
                {
                    // unforgivable, the processor thread  will be running
                    // uncontrolled. Hence the massive timeout.
                    throw new DataProcessorError("Timed out waiting for shutdown");
                }
            }
            catch (InterruptedException e)
            {
                throw new DataProcessorError("Interrupted waiting for shutdown");
            }
        }
        else
        {
            // Unusual case. Wait for the processor thread to complete shutdown.
            logger.warn("Simultaneous shutdown.");
            try
            {
                boolean clean = shutdownLatch.await(
                        FORCED_SHUTDOWN_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS);
                if(!clean)
                {
                    throw new DataProcessorError("Timed out waiting for shutdown");
                }
            }
            catch (InterruptedException e)
            {
                throw new DataProcessorError("Interrupted waiting for shutdown");
            }
        }
    }

    /**
     * Complete shutdown.
     *
     * This should be the final code executed at shutdown.
     */
    private void completeShutdown()
    {
        // cancel any futures with active waiter
        Future future = pendingSynchronousCall.get();
        if(future != null)
        {
            future.cancel(false);
        }

        isShutdown=true;
        shutdownLatch.countDown();
    }

    /**
     * Handle Exceptions encountered in processing thread.
     */
    private void handleException(Throwable error)
    {

        // A forced shutdown from the acquisition thread almost always
        // leads to an interrupted exception on the processor thread
        // suppress logging by looking at the shutdown flag
        if(!inForcedShutdown.get())
        {
         logger.error("Processing encountered an error, will force" +
                " a shutdown.", error );
        }

        // Even if we know are already shutting down on the acquisition
        // thread, call into the forceShutdown method for clarity
        try
        {
            forceShutdown(Participant.ProcessingThread);
        }
        catch (DataProcessorError dpe)
        {
            //now much can be done at this point
            logger.error("Error shuting down processor thread", dpe);
        }
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
     * Wraps futures generated by the executor with shutdown-aware
     * get() methods.
     *
     * @param <T>
     */
    private class ShutdownSafeFuture<T> implements Future<T>
    {
        private final Future<T> delegate;

        private ShutdownSafeFuture(final Future<T> delegate)
        {
            this.delegate = delegate;
        }


        @Override
        public boolean cancel(final boolean mayInterruptIfRunning)
        {
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled()
        {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone()
        {
            return delegate.isDone();
        }

        @Override
        public T get() throws InterruptedException, ExecutionException
        {
            return safelyWaitForFuture(delegate);
        }

        @Override
        public T get(final long timeout, final TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException
        {
            return safelyWaitForFuture(delegate, timeout, unit);
        }

        /**
         * Wait on a future with the added twist of guaranteeing that it will
         * be canceled on a forced shutdown.
         *
         * This method is necessitated by the fact that the processor may cause
         * a forced shutdown at any time.  If the acquisition thread waits on a
         * future without knowledge of the processor thread, it may deadlock.
         *
         * Note: This method throws unchecked exceptions, callers should handle
         * them non the less..
         *
         * @param future The future to wait on.
         * @param timeout Amount of time to wait.
         * @param unit Time unit for wait.
         * @return The result of the future.
         * @throws ExecutionException An error occurred during execution of
         *                            the future.
         * @throws InterruptedException Interrupted.
         * @throws CancellationException The processor shutdown prior to executing
         *                               the future.
         * @throws TimeoutException Timed Out waiting for the future.
         */
        private <T> T safelyWaitForFuture(Future<T> future, long timeout,
                                          TimeUnit unit)
                throws ExecutionException, InterruptedException,
                CancellationException, TimeoutException
        {
            // Do not commit to waiting for a future unless we know that
            // 1. The processor is running and the future is registered to be
            //    canceled on forced shutdown
            // or
            // 2. We explicitly cancel the future
            boolean registered =
                    pendingSynchronousCall.compareAndSet(null, this);

            if(!registered)
            {
                //coding or threading error
                throw new Error("Could not register future with shutdown.");
            }
            else
            {

                try
                {
                    if(!inForcedShutdown.get())
                    {
                        // The future will be either
                        // 1. completed
                        // or
                        // 2. canceled as part of a forced shutdown.
                        return future.get(timeout, unit);
                    }
                    else
                    {
                        // The future could be
                        // 1. completed
                        // 2.canceled
                        // or
                        // 3. abandoned by the processor
                        // We won't know until shutdown is completed.
                        shutdownLatch.await();

                        if(!future.isDone())
                        {
                            future.cancel(false);
                        }

                        return future.get(timeout, unit);
                    }
                }
                finally
                {
                    boolean unregistered =
                            pendingSynchronousCall.compareAndSet(this, null);
                    if(!unregistered)
                    {
                        throw new Error("Could not unregister future" +
                                " with shutdown.");
                    }
                }
            }

        }

        /**
         * Wait on a future with the added twist of guaranteeing that it will
         * be canceled on a forced shutdown.
         *
         * This method is necessitated by the fact that the processor may cause
         * a forced shutdown at any time.  If the acquisition thread waits on a
         * future without knowledge of the processor thread, it may deadlock.
         *
         * Note: This method throws unchecked exceptions, callers should handle
         * them non the less..
         *
         * @param future The future to wait on.
         * @return The result of the future.
         * @throws ExecutionException An error occurred during execution of
         *                            the future.
         * @throws InterruptedException Interrupted.
         * @throws CancellationException The processor shutdown prior to executing
         *                               the future.
         */
        private <T> T safelyWaitForFuture(Future<T> future)
                throws ExecutionException, InterruptedException,
                CancellationException
        {
            // Do not commit to waiting for a future unless we know that
            // 1. The processor is running and the future is registered to be
            //    canceled on forced shutdown
            // or
            // 2. We explicitly cancel the future
            boolean registered =
                    pendingSynchronousCall.compareAndSet(null, this);

            if(!registered)
            {
                //coding or threading error
                throw new Error("Could not register future with shutdown.");
            }
            else
            {


                try
                {
                    if(!inForcedShutdown.get())
                    {
                        // The future will be either
                        // 1. completed
                        // or
                        // 2. canceled as part of a forced shutdown.
                        return future.get();
                    }
                    else
                    {
                        // The future could be
                        // 1. completed
                        // 2.canceled
                        // or
                        // 3. abandoned by the processor
                        // We won't know until shutdown is completed.
                        shutdownLatch.await();

                        if(!future.isDone())
                        {
                            future.cancel(false);
                        }

                        return future.get();
                    }
                }
                finally
                {
                    boolean unregistered =
                            pendingSynchronousCall.compareAndSet(this, null);
                    if(!unregistered)
                    {
                        throw new Error("Could not unregister future" +
                                " with shutdown.");
                    }
                }

            }
        }


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


    /**
     * Realizes a bounded executor that blocks on job submission when the
     * job queue is full.
     *
     * Note: Surprised to find this policy absent in the JDK.
     *
     *
     * The assumption is that jobs are only rejected for two reasons.
     *
     *    The work queue is full.
     *    The executor is shutdown.
     *
     * We can't realistically use the queue size as an indicator as we are
     * not synchronous with the consumer.
     *
     */
    private static class BlockingExecutorRejectionHandler
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
                // Assume that the queue was full, and move to a blocking
                // job insert.
                //
                // This is a huge assumption, but it is all that the jdk
                // has offered.
                try
                {
                    executor.getQueue().put(r);
                }
                catch (InterruptedException e)
                {
                    throw new RejectedExecutionException(e);
                }

            }
        }
    }

}
