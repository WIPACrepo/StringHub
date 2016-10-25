package icecube.daq.bindery;


import icecube.daq.performance.diagnostic.Metered;
import icecube.daq.performance.common.PowersOfTwo;
import icecube.daq.performance.queue.QueueProvider;
import icecube.daq.performance.queue.QueueStrategy;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Provides the asynchronous delivery of sorted buffers.
 *
 * Similar in function to BufferConsumerAsync, but stripped down
 * and optimized for the thread model and throughput of the
 * sorter output.
 *
 */
public class AsyncSorterOutput implements BufferConsumer, Runnable
{
    private final BufferConsumer delegate;
    private final Thread executor;
    private volatile boolean running;


    private final QueueStrategy<ByteBuffer> q;

    private final Metered.Buffered meter;


    private static Logger logger = Logger.getLogger(AsyncSorterOutput.class);


    public AsyncSorterOutput(final BufferConsumer delegate,
                             final PowersOfTwo capacity)
    {
        this(delegate, capacity, "AsyncConsumer", new Metered.NullMeter());
    }

    public AsyncSorterOutput(final BufferConsumer delegate,
                             final PowersOfTwo capacity,
                             final String threadName)
    {
        this(delegate, capacity, threadName, new Metered.NullMeter());
    }

    public AsyncSorterOutput(final BufferConsumer delegate,
                             final PowersOfTwo capacity,
                             final String threadName,
                             final Metered.Buffered meter)
    {
        this.delegate = delegate;

        this.q = QueueProvider.Subsystem.SORTER_OUTPUT.createQueue(capacity);

        executor = new Thread(this);
        executor.setName(threadName);
        executor.start();
        running = true;

        this.meter = meter;
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
        executor.join();
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
        executor.join(waitMillis);
        return executor.isAlive();
    }



    @Override
    public void consume(final ByteBuffer buf) throws IOException
    {
        if(running)
        {
            final int sz = buf.remaining();
            meter.reportIn(sz);

            try
            {
                q.enqueue(buf);
            }
            catch (InterruptedException e)
            {
                throw new IOException(e);
            }
        }
        else
        {
            throw new IOException("Consumer is not running");
        }
    }

    @Override
    public void endOfStream(final long token) throws IOException
    {
        consume(MultiChannelMergeSort.eos(token));
    }


    @Override
    public void run()
    {
        try
        {
            while (running)
            {
                ByteBuffer buf = q.dequeue();
                meter.reportOut(buf.remaining());

                // todo use record reader
                if(buf.getLong(24) == Integer.MAX_VALUE)
                {
                    running = false;
                }

                delegate.consume(buf);
            }
        }
        catch (InterruptedException ie)
        {
            logger.error("Consumer interrupted:", ie);
            running = false;
        }
        catch (IOException ioe)
        {
            logger.error("Consumer io error:", ioe);
            running = false;
        }
        catch (Throwable th)
        {
            logger.error("Consumer error:", th);
            running = false;
        }
        finally
        {
            logger.info("Consumer thread stopped.");
            running = false;
        }
    }
}
