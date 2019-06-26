package icecube.daq.bindery;

/**
 * Common interface for StringHub sorters
 */
public interface ChannelSorter
    extends BufferConsumer
{
    /**
     * Get the time of the last object added to the sorter
     *
     * @return last input time
     */
    long getLastInputTime();

    /**
     * Get the time of the last object to leave the sorter
     *
     * @return last output time
     */
    long getLastOutputTime();

    /**
     * Get the total number of inputs seen by the sorter
     *
     * @return number of inputs
     */
    long getNumberOfInputs();

    /**
     * Get the total number of outputs sent by the sorter
     *
     * @return number of outputs
     */
    long getNumberOfOutputs();

    /**
     * Get the number of items currently in transit through the sorter
     *
     * @return number of queued items
     */
    int getQueueSize();

    /**
     * Are any sorter threads still active?
     * @return <tt>true</tt> if one or more sorter threads are active
     */
    boolean isRunning();

    /**
     * Wait for all sorter-related threads to finish
     */
    void join(long millis)
        throws Exception;

    /**
     * Register a channel with the sort.
     * @param mbid
     */
    void register(long mbid);

    /**
     * Start sorter.
     */
    void start();
}
