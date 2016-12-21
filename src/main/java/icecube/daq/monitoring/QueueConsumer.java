package icecube.daq.monitoring;

import java.util.LinkedList;

import org.apache.log4j.Logger;

/**
 * Manage, process, and send queued monitoring data.
 */
abstract class QueueConsumer<T>
{
    /** logging object */
    protected static final Logger LOG = Logger.getLogger(QueueConsumer.class);

    /** Parent which sends messages to Live */
    protected IRunMonitor parent;

    /** Queue of data to be consumed */
    private LinkedList<T> queue = new LinkedList<T>();
    /** A single piece of data to be processed outside any locks */
    private T heldValue;

    /**
     * Create a queue consumer
     *
     * @param parent main monitoring object
     */
    QueueConsumer(IRunMonitor parent)
    {
        this.parent = parent;
    }

    /**
     * Hold a value for later processing.
     * This is run while the RunMonitor lock is active, so should do
     * as little as possible.
     * @return <tt>true</tt> if a value was held
     */
    boolean holdValue()
    {
        synchronized (queue) {
            // if we already have a value, complain
            if (heldValue != null) {
                LOG.error("Cannot hold value; already have " +
                          heldValue);
                return true;
            } else if (!isEmpty()) {
                // if there's a value available, hold it in temporary storage
                heldValue = queue.removeFirst();
                return true;
            }
        }

        // no values available
        return false;
    }

    /**
     * Is this consumer's queue empty?
     *
     * @return <tt>true</tt> if the list is empty
     */
    boolean isEmpty()
    {
        synchronized (queue) {
            return queue.size() == 0;
        }
    }

    /**
     * Process one value in the RunMonitor thread.
     * This is run outside any active RunMonitor lock.
     */
    abstract void process(T value);

    /**
     * Process everything in the queue.
     */
    void processAll()
    {
        // while there are values to process
        while (true) {
            synchronized (queue) {
                // if we don't have a value...
                if (heldValue == null) {
                    // ...get one from the queue and temporarily hold it
                    if (!holdValue()) {
                        // no values, we're done
                        break;
                    }
                }
            }

            // process the temporarily held value
            processHeldValue();
        }
    }

    /**
     * Process the held value in the RunMonitor thread.
     * This is run outside any active lock.
     */
    void processHeldValue()
    {
        if (heldValue != null) {
            try {
                process(heldValue);
            } catch (Throwable thr) {
                LOG.error(getClass().getName() + " cannot process " +
                          heldValue, thr);
            }

            heldValue = null;
        }
    }

    /**
     * Push a value onto this consumer's queue.
     *
     * @param value new value
     */
    void push(T value)
    {
        synchronized (queue) {
            queue.addLast(value);
        }
    }

    /**
     * Reset everything back to initial conditions for the next run
     */
    abstract void reset();

    /**
     * Send per-run quantities.
     */
    abstract void sendRunData();
}
