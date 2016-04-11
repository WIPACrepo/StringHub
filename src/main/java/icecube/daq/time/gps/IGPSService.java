package icecube.daq.time.gps;

import icecube.daq.dor.GPSInfo;
import icecube.daq.monitoring.IRunMonitor;

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
     * @param runMonitor The monitor to use for alerts.
     */
    public void setRunMonitor(IRunMonitor runMonitor);

    /**
     * Sets the string number to be used in monitoring/alert messages.
     *
     * @param string string number (values are 0-86 and 201-211)
     */
    public void setStringNumber(int string);
}
