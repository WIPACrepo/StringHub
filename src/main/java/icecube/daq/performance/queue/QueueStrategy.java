package icecube.daq.performance.queue;

import org.jctools.queues.MessagePassingQueue;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;

/**
 * A queue interface that narrows the scope of interfaces provided
 * in the java.util.BlockingQueue package.
 *
 * QueueStrategy supports interchanging java.util.BlockingQueue
 * implementations with non-blocking alternatives including novel
 * implementations that do not meet the full contract of
 * java.util.BlockingQueue.
 *
 * QueueStrategy also provides standard idle strategies that implement
 * queue full/empty waits against a non-blocking queue.
 *
 */
public interface QueueStrategy<T>
{
    /**
     * Insert an element into the queue, waiting for space to become
     * available if necessary.
     *
     * @param element The element to insert.
     * @throws InterruptedException
     */
    public void enqueue(T element) throws InterruptedException;

    /**
     * Remove an element from the queue, waiting for an element to
     * become available if necessary.
     * @return The next element from the queue.
     * @throws InterruptedException
     */
    public T dequeue() throws InterruptedException;

    /**
     * The number of elements in the queue.
     *
     * Note: Watch out for expensive implementations like CLQ.
     *
     * @return The number of elements in the queue.
     */
    public int size();


    /**
     * Defines the operation utilized to realize a wait on queue full/empty
     * conditions.
     */
    interface IdleStrategy
    {
        void idle(int count) throws InterruptedException;
    }


    /**
     * A spin idle strategy.
     */
    class Spin implements IdleStrategy
    {
        // support interruptibility on spin
        private final static int INTERRUPT_CHECK_PERIOD = 10000;

        @Override
        public final void idle(final int count) throws InterruptedException
        {
            if(count % INTERRUPT_CHECK_PERIOD == 0)
            {
                if(Thread.currentThread().isInterrupted())
                {
                    throw new InterruptedException();
                }
            }
        }

    }


    /**
     * A spin/yield idle strategy.
     */
    class Yield implements IdleStrategy
    {
        // support interruptibility on spin
        private final static int INTERRUPT_CHECK_PERIOD = 10000;

        @Override
        public final void idle(final int count) throws InterruptedException
        {
            if(count % INTERRUPT_CHECK_PERIOD == 0)
            {
                if(Thread.currentThread().isInterrupted())
                {
                    throw new InterruptedException();
                }
            }

            Thread.yield();
        }

    }


    /**
     * A polling idle strategy.
     */
    class Poll implements IdleStrategy
    {
        final long pollMillis;

        public Poll(final long pollMillis)
        {
            this.pollMillis = pollMillis;
        }

        @Override
        public final void idle(final int count) throws InterruptedException
        {
            Thread.sleep(pollMillis);
        }

    }


    /**
     * A polling idle strategy with increasing sleep.
     */
    class Backoff implements IdleStrategy
    {
        final long pollMillis;
        final long maxSleep;

        public Backoff(final long pollMillis, final long maxSleep)
        {
            this.pollMillis = pollMillis;
            this.maxSleep = maxSleep;
        }

        @Override
        public final void idle(final int count) throws InterruptedException
        {
            Thread.sleep( Math.min((pollMillis * count), maxSleep) );
        }

    }


    /**
     * A QueueStrategy calling into blocking operations provided by
     * java.util.concurrent.BlockingQueue.
     */
    public class Blocking<T> implements QueueStrategy<T>
    {
        private final BlockingQueue<T> queue;


        public Blocking(final BlockingQueue<T> queue)
        {
            this.queue = queue;
        }

        @Override
        public void enqueue(final T element) throws InterruptedException
        {
            queue.put(element);
        }

        @Override
        public T dequeue() throws InterruptedException
        {
            return queue.take();
        }

        @Override
        public int size()
        {
            return queue.size();
        }

    }


    /**
     * A QueueStrategy calling into non-blocking operations provided by
     * java.util.concurrent.Queue.
     */
    public class NonBlocking<T> implements QueueStrategy<T>
    {
        private final  Queue<T> queue;
        private final IdleStrategy idleStrategy;

        public NonBlocking(final Queue<T> queue,
                           final IdleStrategy idleStrategy)
        {
            this.queue = queue;
            this.idleStrategy = idleStrategy;
        }

        @Override
        public void enqueue(final T element) throws InterruptedException
        {
            int count=0;
            while(!queue.offer(element))
            {
                idleStrategy.idle(count++);
            }
        }

        @Override
        public T dequeue() throws InterruptedException
        {
            T element;
            int count = 0;
            while( (element = queue.poll()) == null)
            {
                idleStrategy.idle(count++);
            }
            return element;
        }

        @Override
        public int size()
        {
            return queue.size();
        }

    }


    /**
     * A QueueStrategy based on a non-standard, non-blocking queue available
     * from the jctools library. The MessagePassingQueue provides an offer/poll
     * contract that relaxes the contract of java.util.Queue in exchange for
     * better performance.
     */
    public class Relaxed<T> implements QueueStrategy<T>
    {
        private final MessagePassingQueue<T> queue;
        final IdleStrategy idleStrategy;


        public Relaxed(final MessagePassingQueue<T> queue, final IdleStrategy idleStrategy)
        {
            this.queue = queue;
            this.idleStrategy = idleStrategy;
        }

        @Override
        public void enqueue(final T element) throws InterruptedException
        {
            int count=0;
            while(!queue.relaxedOffer(element))
            {
                idleStrategy.idle(count++);
            }
        }

        @Override
        public T dequeue() throws InterruptedException
        {
            T element;
            int count=0;
            while( (element = queue.relaxedPoll()) == null)
            {
                idleStrategy.idle(count++);
            }
            return element;
        }

        @Override
        public int size()
        {
            return queue.size();
        }

    }


    /**
     * A QueueStrategy based on a non-blocking queue utilizing a spin loop
     * for queue full and empty conditions
     */
    public class NonBlockingSpin<T> extends NonBlocking<T>
    {

        public NonBlockingSpin(final Queue<T> queue)
        {
            super(queue, new Spin());
        }

    }


    /**
     * A QueueStrategy based on a non-blocking queue utilizing a spin-yield
     * loop for queue full and empty conditions
     */
    public class NonBlockingYield<T> extends NonBlocking<T>
    {

        public NonBlockingYield(final Queue<T> queue)
        {
            super(queue, new Yield());
        }

    }


    /**
     * A QueueStrategy based on a non-blocking queue utilizing a spin-poll
     * loop for queue full and empty conditions
     */
    public class NonBlockingPoll<T> extends NonBlocking<T>
    {

        public NonBlockingPoll(final Queue<T> queue, final int pollInterval)
        {
            super(queue, new Poll(pollInterval));
        }

    }


    /**
     * A QueueStrategy based on a non-blocking queue utilizing a spin-poll
     * loop for queue full and empty conditions. polling interval will
     * increase during spin loop.
     */
    public class NonBlockingPollBackoff<T> extends NonBlocking<T>
    {

        public NonBlockingPollBackoff(final Queue<T> queue,
                                      final int pollInterval,
                                      final long maxSleep)
        {
            super(queue, new Backoff(pollInterval, maxSleep));
        }

    }


    /**
     * A QueueStrategy with a spin wait calling into a relaxed non-blocking
     * queue api.
     */
    public class RelaxedSpin<T> extends Relaxed<T>
    {

        public RelaxedSpin(final MessagePassingQueue<T> queue)
        {
            super(queue, new Spin());
        }

    }


    /**
     * A QueueStrategy with a yielding spin wait calling into a relaxed non-blocking
     * queue api.
     */
    public class RelaxedYield<T> extends Relaxed<T>
    {

        public RelaxedYield(final MessagePassingQueue<T> queue)
        {
            super(queue, new Yield());
        }

    }


    /**
     * A QueueStrategy with a polling wait calling into a relaxed non-blocking
     * queue api.
     */
    public class RelaxedPoll<T> extends Relaxed<T>
    {

        public RelaxedPoll(final MessagePassingQueue<T> queue, final int pollInterval)
        {
            super(queue, new Poll(pollInterval));
        }

    }


    /**
     * A QueueStrategy with a back-off polling wait calling into a relaxed
     * non-blocking queue api.
     */
    public class RelaxedPollBackoff<T> extends Relaxed<T>
    {

        public RelaxedPollBackoff(final MessagePassingQueue<T> queue,
                                  final int pollInterval,
                                  final int maxSleep)
        {
            super(queue, new Backoff(pollInterval, maxSleep));
        }

    }


}
