package icecube.daq.bindery;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Callable;
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

/**
 * Wraps a BufferConsumer with a thread to provide for asynchronous
 * buffer consumption.
 *
 * Note: This Implementation does not recognize EOS markers in the
 *       stream. The endOfStream() method must be utilized to stop
 *       the thread.
 *
 * Note: This implementation may use a bounded queue to feed the consumer
 *       thread. The caller does not block when the bound is reached,
 *       rather an error will be issued.  This is a limitation of
 *       java's executor package.
 *
 */
public class BufferConsumerAsync implements BufferConsumer
{
    private final BufferConsumer delegate;
    private final ExecutorService executor;

    private static final int MAX_QUEUED_BUFFERS = Integer.MAX_VALUE;

    private static Logger logger = Logger.getLogger(BufferConsumerAsync.class);

    /**
     * Policies which define the behavior when the buffer queue is full
     */
    public static enum QueueFullPolicy
    {
        Reject,           // Reject buffer submission on queue full
        Block,            // block submitter on queue full
    }

    public BufferConsumerAsync(final BufferConsumer delegate)
    {
       this(delegate, MAX_QUEUED_BUFFERS, QueueFullPolicy.Block);
    }

    public BufferConsumerAsync(final BufferConsumer delegate,
                               final int capacity,
                               QueueFullPolicy policy)
    {
        this(delegate, capacity, policy, "AsyncConsumer");
    }

    public BufferConsumerAsync(final BufferConsumer delegate,
                               final int capacity,
                               final String threadName)
    {
         this(delegate, capacity, QueueFullPolicy.Block, threadName);
    }

    public BufferConsumerAsync(final BufferConsumer delegate,
                               final int capacity,
                               final QueueFullPolicy policy,
                               final String threadName)
    {
        this.delegate = delegate;

        switch (policy)
        {
            case Block:
                this.executor =
                        new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                                new LinkedBlockingQueue<Runnable>(capacity),
                                new SingleThreadFactory(threadName),
                                new BlockingExecutorRejectionHandler());
                break;
            case Reject:
                this.executor =
                        new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                                new LinkedBlockingQueue<Runnable>(capacity),
                                new SingleThreadFactory(threadName));
                break;
            default:
                throw new Error("Unknown policy: " + policy);
        }

    }

    public int getQueueSize()
    {
        return ((ThreadPoolExecutor)executor).getQueue().size();
    }

    /**
     * Wait for consumption thread to complete. Generally
     * called after endOfStream() to wait for buffer delivery to
     * complete.
     *
     * @throws InterruptedException
     */
    public void join() throws InterruptedException
    {
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    /**
     * Wait for consumption thread to complete. Generally
     * called after endOfStream() to wait for buffer delivery to
     * complete.
     *
     * @param waitMillis How long to wait for the service to terminate.
     * @return True if the service is terminated.
     * @throws InterruptedException
     */
    public boolean join(long waitMillis) throws InterruptedException
    {
        return executor.awaitTermination(waitMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Allow a client to become synchronized to the consumption Queue
     * @throws IOException
     */
    public void sync(long waitMillis) throws IOException
    {
        Future<Void> sync = null;
        try
        {
            sync = executor.submit(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    return null;
                }
            });
        }
        catch (Throwable th)
        {
            throw new IOException("Failed to sync", th);
        }

        try
        {
            sync.get(waitMillis, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            throw new IOException("Interrupted while syncing", e);
        }
        catch (TimeoutException e)
        {
            throw new IOException("Timed out while syncing", e);
        }
        catch (ExecutionException e)
        {
            throw new IOException("Error while syncing", e);
        }
    }

    @Override
    public void consume(final ByteBuffer buf) throws IOException
    {
        try
        {
            executor.submit(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    try
                    {
                        delegate.consume(buf);
                    }
                    catch (Throwable th)
                    {

                        //NOTE: this runs on the executor which will interrupt
                        //      itself which the current thread.
                        List<Runnable> abandonedWork = executor.shutdownNow();
                        Thread.interrupted();

                        logger.error("Async consumer encountered an error," +
                                " abandoning " + abandonedWork.size()
                                + " buffers" , th);
                    }
                    return null;
                }
            });
        }
        catch (Throwable th)
        {
            throw new IOException("Error on consume",th);
        }
    }

    @Override
    public void endOfStream(final long token) throws IOException
    {

        try
        {
            executor.submit(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    try
                    {
                        delegate.endOfStream(token);
                    }
                    catch (Throwable th)
                    {

                        //NOTE: this runs on the executor which will interrupt
                        //      itself which the current thread.
                        List<Runnable> abandonedWork = executor.shutdownNow();
                        Thread.interrupted();

                        logger.error("Async consumer encountered an error," +
                                " abandoning " + abandonedWork.size() +
                                " buffers" , th);
                    }
                    return null;
                }
            });
        }
        catch (Throwable th)
        {
           throw new IOException("Error on EOS", th);
        }

        executor.shutdown();
    }


    /**
     * Factory for defining the consumption thread.
     *
     *    Encoded the channel id in the thread name.
     *    Prohibit generation of a replacement thread.
     */
    private static class SingleThreadFactory implements ThreadFactory
    {
        final String threadName;
        int instanceNumber;

        private SingleThreadFactory(final String threadName)
        {
            this.threadName = threadName;
        }

        @Override
        public Thread newThread(final Runnable runnable)
        {
            synchronized (this)
            {
                // prohibit thread restoration
                if(instanceNumber == 1 )
                {
                    throw new Error("Unexpected executor thread death.");
                }
                else
                {
                    Thread thread = new Thread(runnable);
                    thread.setName(threadName);

                    instanceNumber++;
                    return thread;
                }
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
