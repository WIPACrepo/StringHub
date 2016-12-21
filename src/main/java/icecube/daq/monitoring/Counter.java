package icecube.daq.monitoring;

/**
 * Simple incremental counter
 */
class Counter
{
    /** Counter value */
    private int count;

    /**
     * Get the current value
     *
     * @return current value
     */
    int get()
    {
        return count;
    }

    /**
     * Increment the counter
     */
    void inc()
    {
        count++;
    }

    /**
     * Return a debugging representation of the counter
     * @return debugging string
     */
    public String toString()
    {
        return String.format("Counter=%d", count);
    }
}
