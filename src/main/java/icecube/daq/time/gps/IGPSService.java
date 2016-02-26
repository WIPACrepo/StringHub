package icecube.daq.time.gps;

import icecube.daq.dor.GPSInfo;
import icecube.daq.monitoring.TCalExceptionAlerter;

/**
 * Defines the interface provided by the GPS service.
 */
public interface IGPSService
{
    /**
     * Get the latest GPS snapshot from the specified card. Service
     * must be initialized before use by calling startService() and
     * waitForReady().
     *
     * @param card The card to obtain the reading from.
     * @return The most recent snapshot from the card.
     * @throws GPSServiceError Indicates a failure of the GPS service for
     *                  this card or attempt to use before ready.
     */
    public GPSInfo getGps(int card) throws GPSServiceError;

    /**
     * Start acquiring GPS snapshots from the specified card. Must be
     * called before using waitForReady() and getGps() methods.
     *
     * @param card The card to start polling.
     */
    public void startService(int card);

    /**
     * Wait for a valid gps info reading to be available from the card.
     *
     * @param card The card to wait for.
     * @param waitMillis Time period to wait.
     * @return True if a valid gps info reading is available.
     * @throws InterruptedException Thread is interrupted.
     */
    public boolean waitForReady(int card, int waitMillis)
            throws InterruptedException;

    /**
     * Stop acquiring gps snapshots for all cards.
     */
    public void shutdownAll();

    /**
     * Sets the monitor object to be used for alert conditions.
     *
     * @param alerter The monitor to use for alerts.
     */
    public void setMoni(TCalExceptionAlerter alerter);

}
