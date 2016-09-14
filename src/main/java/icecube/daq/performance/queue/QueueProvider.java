package icecube.daq.performance.queue;

import icecube.daq.performance.common.PowersOfTwo;
import org.jctools.queues.MpscArrayQueue;
import org.jctools.queues.SpscArrayQueue;

import java.util.concurrent.LinkedBlockingQueue;


/**
 * Centralize the selection of queue implementations.
 *
 * Performance of contended queues is critical to StringHub stability
 * under loaded conditions. Queue selection has been guided by load testing
 * on a fully loaded hub with 50K/hits/sec.
 *
 */
public class QueueProvider
{

     /** Optional Sorter config */
    private static final String sorterConfig =
             System.getProperty("icecube.daq.performance.queue.sorter-input.queue",
                     MPSCOption.RELAXED_BACKOFF.name());


    /**
     * Implemented as an enumeration to emphasize that queue selection details
     * are highly use-case specific.
     */
    public static enum Subsystem
    {
        SORTER_INPUT
                {
                    /**
                     * Creates the MultiChannelMergeSort input queue.
                     *
                     * The MultiChannelMergeSort is a many-producer,
                     * single-consumer queue case.
                     */
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {

                        MPSCOption mpscOptions =
                                MPSCOption.valueOf(sorterConfig.toUpperCase());

                        return mpscOptions.createQueue(size);
                    }

                };

        /**
         * Creates the queue that is optimized for the subsystem.
         *
         * Queues can be optimized by eliminating blocking and by
         * customizing for the number of producer and consumer threads.
         * Further optimizations can be made by constraining the size
         * to a power of two.
         *
         * @param size The bounding size of the queue.
         * @param <T> The type of objects held in the queue.
         * @return The optimized queue implementation for the subsystem.
         */
        public abstract <T> QueueStrategy<T> createQueue(final PowersOfTwo size);

    }

    /**
     * Permitted configurations for a Multiple Producer, Single Consumer
     * case.
     */
    static enum MPSCOption
    {

        LINKED_BLOCKING
                {
                    // The original, poor performing linked
                    // blocking queue
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        LinkedBlockingQueue<T> base =
                                new LinkedBlockingQueue<>(size.value());
                        return new QueueStrategy.Blocking<T>(base);
                    }
                },
        SPIN
                {
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        MpscArrayQueue<T> base = new MpscArrayQueue<>(size.value());
                        return new QueueStrategy.NonBlockingSpin<T>(base);
                    }
                },
        YIELD
        {
            @Override
            public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
            {
                MpscArrayQueue<T> base = new MpscArrayQueue<>(size.value());
                return new QueueStrategy.NonBlockingYield<T>(base);
            }
        },
        POLL
                {
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        MpscArrayQueue<T> base = new MpscArrayQueue<>(size.value());
                        return new QueueStrategy.NonBlockingPoll<T>(base, 10);
                    }
                },
        BACKOFF
                {
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        MpscArrayQueue<T> base = new MpscArrayQueue<>(size.value());
                        return new QueueStrategy.NonBlockingPollBackoff<T>(base, 10);
                    }
                },

        RELAXED_SPIN
                {
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        MpscArrayQueue<T> base = new MpscArrayQueue<>(size.value());
                        return new QueueStrategy.RelaxedSpin<T>(base);
                    }
                },
        RELAXED_YIELD
                {
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        MpscArrayQueue<T> base = new MpscArrayQueue<>(size.value());
                        return new QueueStrategy.RelaxedYield<T>(base);
                    }
                },
        RELAXED_POLL
                {
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        MpscArrayQueue<T> base = new MpscArrayQueue<>(size.value());
                        return new QueueStrategy.RelaxedPoll<T>(base, 10);
                    }
                },
        RELAXED_BACKOFF
                {
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        MpscArrayQueue<T> base = new MpscArrayQueue<>(size.value());
                        return new QueueStrategy.RelaxedPollBackoff<T>(base, 10);
                    }
                };

        public abstract <T> QueueStrategy<T> createQueue(final PowersOfTwo size);
    }

    /**
     * Permitted configurations for a Single Producer, Single Consumer
     * case.
     */
    static enum SPSCOption
    {

        LINKED_BLOCKING
                {
                    // The original, poor performing linked
                    // blocking queue
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        LinkedBlockingQueue<T> base =
                                new LinkedBlockingQueue<>(size.value());
                        return new QueueStrategy.Blocking<T>(base);
                    }
                },
        SPIN
                {
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        SpscArrayQueue<T> base = new SpscArrayQueue<>(size.value());
                        return new QueueStrategy.NonBlockingSpin<T>(base);
                    }
                },
        YIELD
                {
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        SpscArrayQueue<T> base = new SpscArrayQueue<>(size.value());
                        return new QueueStrategy.NonBlockingYield<T>(base);
                    }
                },
        POLL
                {
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        SpscArrayQueue<T> base = new SpscArrayQueue<>(size.value());
                        return new QueueStrategy.NonBlockingPoll<T>(base, 10);
                    }
                },
        BACKOFF
                {
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        SpscArrayQueue<T> base = new SpscArrayQueue<>(size.value());
                        return new QueueStrategy.NonBlockingPollBackoff<T>(base, 10);
                    }
                },

        RELAXED_SPIN
                {
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        SpscArrayQueue<T> base = new SpscArrayQueue<>(size.value());
                        return new QueueStrategy.RelaxedSpin<T>(base);
                    }
                },
        RELAXED_YIELD
                {
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        SpscArrayQueue<T> base = new SpscArrayQueue<>(size.value());
                        return new QueueStrategy.RelaxedYield<T>(base);
                    }
                },
        RELAXED_POLL
                {
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        SpscArrayQueue<T> base = new SpscArrayQueue<>(size.value());
                        return new QueueStrategy.RelaxedPoll<T>(base, 10);
                    }
                },
        RELAXED_BACKOFF
                {
                    @Override
                    public <T> QueueStrategy<T> createQueue(final PowersOfTwo size)
                    {
                        SpscArrayQueue<T> base = new SpscArrayQueue<>(size.value());
                        return new QueueStrategy.RelaxedPollBackoff<T>(base, 10);
                    }
                };

        public abstract <T> QueueStrategy<T> createQueue(final PowersOfTwo size);
    }
}
