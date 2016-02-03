package icecube.daq.domapp.dataacquisition;

/**
 * Interface into the watchdog for code subjected to it.
 *
 * The acquisition package is generally running under time constraints
 * imposed by a watchdog.  As such it pings the watchdog at certain times
 * to indicate liveliness.
 *
 * It is also helpful to look to the watchdog for interruption handling
 * and sleeping since the most interruptions originate from the watchdog.
 */
public interface Watchdog
{

    enum Mode
    {
        INTERRUPT_ONLY,
        ABORT
    }

    /**
     * Notify watchdog of liveliness.
     */
    public void ping();

    /**
     * Set the action mode of the watchdog.
     * @param mode The mode of action to take when the watchdog trips.
     * @return The previous mode.
     */
     public Mode setTimeoutAction(Mode mode);


    /**
     * Sleep under watchdog knowledge.
     *
     * @param millis Duration to sleep.
     */
    public void sleep(long millis);

    /**
     * Report interrupted via the watchdog.
     *
     * @param ie An InterruptedException raised while running under
     *           watchdog control.
     */
    public void handleInterrupted(final InterruptedException ie);

}
